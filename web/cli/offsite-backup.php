<?php
declare(strict_types=1);
/**
 * CLI only. Configure FAKTURIVA_OFFSITE_BACKUP_ROOT to a separate mounted
 * volume OUTSIDE the public_html tree and outside the application tree.
 * Copy encrypted JSON, session-free metadata and master key under an
 * advisory transaction lock. Never upload this archive into public webroot.
 *
 * Example:
 * FAKTURIVA_OFFSITE_BACKUP_ROOT=/mnt/private-fakturiva-backup php web/cli/offsite-backup.php
 */
if(PHP_SAPI!=='cli'){
    http_response_code(404);exit;
}
require dirname(__DIR__).'/lib.php';
use function FakturivaSelfHost\storage;
use function FakturivaSelfHost\keyFile;
use function FakturivaSelfHost\transaction;

$provided=getenv('FAKTURIVA_OFFSITE_BACKUP_ROOT');
if(!is_string($provided)||$provided==='')
    throw new RuntimeException('Set FAKTURIVA_OFFSITE_BACKUP_ROOT');
$destination=realpath($provided);
$app=realpath(dirname(__DIR__));
if($destination===false||$app===false||!is_dir($destination)||
   !str_starts_with($destination,DIRECTORY_SEPARATOR)||
   $destination===$app||
   str_starts_with($destination,$app.DIRECTORY_SEPARATOR)||
   str_starts_with($app,$destination.DIRECTORY_SEPARATOR))
    throw new RuntimeException('Backup destination must be separate from the web application');
if(!is_writable($destination))
    throw new RuntimeException('Offsite directory is not writable');
$stamp=gmdate('Ymd-His').'-'.bin2hex(random_bytes(4));
$tmp=$destination.'/pending-'.$stamp;
$final=$destination.'/backup-'.$stamp;
if(!mkdir($tmp,0700,true))throw new RuntimeException('Cannot start protected snapshot');
try{
    transaction(function()use($tmp):void{
        $root=storage();
        $key=keyFile();
        if(!is_file($key))throw new RuntimeException('Server master key is missing');
        $manifest=['version'=>1,'created_at'=>gmdate('c'),'files'=>[]];
        $paths=[$key];
        foreach(['accounts','index'] as $subdir){
            foreach(glob($root.'/'.$subdir.'/*.json')?:[] as $file)
                $paths[]=$file;
        }
        foreach($paths as $src){
            if(!is_file($src)||is_link($src))
                throw new RuntimeException('Unsafe backup file');
            $rel=$src===$key?'secret.php':
                substr($src,strlen($root)+1);
            if($rel==='secret.php'){
                $target=$tmp.'/secret.php';
            }else{
                if(!preg_match('~^(accounts|index)/[a-f0-9]{64}?\.json$~',$rel)&&
                   !preg_match('~^accounts/[a-f0-9]{32}\.json$~',$rel))
                    throw new RuntimeException('Unexpected backup filename');
                $target=$tmp.'/'.$rel;
            }
            if(!is_dir(dirname($target))&&!mkdir(dirname($target),0700,true))
                throw new RuntimeException('Cannot create backup directory');
            if(!copy($src,$target))throw new RuntimeException('Cannot copy encrypted file');
            chmod($target,0600);
            $a=hash_file('sha256',$src);$b=hash_file('sha256',$target);
            if(!hash_equals($a,$b))throw new RuntimeException('Backup integrity check failed');
            $manifest['files'][$rel]=['sha256'=>$b,'bytes'=>filesize($target)];
        }
        $json=json_encode($manifest,JSON_PRETTY_PRINT|JSON_THROW_ON_ERROR);
        if(file_put_contents($tmp.'/manifest.json',$json,LOCK_EX)===false)
            throw new RuntimeException('Cannot write manifest');
        chmod($tmp.'/manifest.json',0600);
    });
    if(!rename($tmp,$final))throw new RuntimeException('Cannot finalize backup');
    echo "Fakturiva encrypted independent copy verified: ".$final.PHP_EOL;
    echo "Test restoring key and account records in a staging location regularly.".PHP_EOL;
}catch(Throwable $error){
    // Do not leave an incomplete backup looking valid.
    $iter=new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($tmp,FilesystemIterator::SKIP_DOTS),
        RecursiveIteratorIterator::CHILD_FIRST);
    foreach($iter as $f)$f->isDir()?rmdir($f->getPathname()):unlink($f->getPathname());
    @rmdir($tmp);
    throw $error;
}
