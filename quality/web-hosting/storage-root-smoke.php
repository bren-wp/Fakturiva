<?php
declare(strict_types=1);
require_once __DIR__.'/../../web/lib.php';

/**
 * Separate-process test of external storage location: server ciphertext and
 * its master key must remain outside the public web document root.
 */
$root=sys_get_temp_dir().'/fakturiva-private-test-'.bin2hex(random_bytes(6));
putenv('FAKTURIVA_STORAGE_ROOT='.$root);
try {
    if(FakturivaSelfHost\storage()!==$root.'/data')
        throw new RuntimeException('External storage was not selected');
    if(FakturivaSelfHost\keyFile()!==$root.'/secret.php')
        throw new RuntimeException('Master key remained inside the web root');
    $user='outside_'.bin2hex(random_bytes(6)).'@example.test';
    $password='Strong-external-storage-test-'.bin2hex(random_bytes(8));
    $registered=FakturivaSelfHost\signUp($user,$password,'192.0.2.210');
    if(!is_file($root.'/secret.php')||
       count(glob($root.'/data/accounts/*.json')?:[])!==1)
        throw new RuntimeException('Encrypted account files missing from external storage');
    if(FakturivaSelfHost\loadBackup($registered['token'])['revision']!==0)
        throw new RuntimeException('External account is not readable');
    if(FakturivaSelfHost\signIn($user,$password,'192.0.2.210')['username']!==$user)
        throw new RuntimeException('External account login failed');
    FakturivaSelfHost\deleteAuthorized($registered['token'],$password);
    putenv('FAKTURIVA_STORAGE_ROOT=relative/untrusted/path');
    try {
        FakturivaSelfHost\privateRoot();
        throw new RuntimeException('Relative private storage root was accepted');
    } catch(RuntimeException $expected) {
        if($expected->getMessage()==='Relative private storage root was accepted')
            throw $expected;
    }
    echo "External private JSON storage, key isolation, login and validation: OK\n";
} finally {
    putenv('FAKTURIVA_STORAGE_ROOT');
    $clean=static function(string $dir) use (&$clean): void {
        if(!is_dir($dir))return;
        foreach(scandir($dir)?:[] as $name){
            if($name==='.'||$name==='..')continue;
            $path=$dir.'/'.$name;
            if(is_dir($path)&&!is_link($path))$clean($path);
            elseif(!unlink($path))throw new RuntimeException('Cannot clean test file');
        }
        if(!rmdir($dir))throw new RuntimeException('Cannot clean test directory');
    };
    $clean($root);
}
