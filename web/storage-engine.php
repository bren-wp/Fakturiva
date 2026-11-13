<?php
declare(strict_types=1);
namespace FakturivaSelfHost;

/**
 * Filesystem, AES-256-GCM encrypted record I/O and atomic transactions.
 * Included once by lib.php after the shared constants and HttpFailure type.
 * Kept in the same folder so existing privateRoot() path semantics remain.
 */
/**
 * Production operators can keep both encrypted records and the master key
 * OUTSIDE the public PHP document root. Never migrate existing data silently:
 * changing FAKTURIVA_STORAGE_ROOT requires copying data and secret.php together
 * while the service is offline and verifying recovery before reopening.
 */
function privateRoot(): string {
    $configured=getenv('FAKTURIVA_STORAGE_ROOT');
    if($configured===false||trim($configured)==='')return __DIR__.'/storage';
    $root=rtrim(trim($configured),'/');
    if($root===''||strlen($root)>1024||$root[0]!=='/'||
       strpos($root,chr(0))!==false||
       preg_match('~(?:^|/)\\.\\.?(?:/|$)~D',$root))
        throw new \RuntimeException('Invalid private storage root');
    return $root;
}
function storage(): string { return privateRoot().'/data'; }
function keyFile(): string { return privateRoot().'/secret.php'; }

function ensureStorage(): void {
    foreach ([privateRoot(),storage(),storage().'/accounts',
              storage().'/index',storage().'/limits'] as $path) {
        if (!is_dir($path) && !mkdir($path,0700,true) && !is_dir($path))
            throw new \RuntimeException('Cannot create storage');
    }
    $deny = "<IfModule mod_authz_core.c>\nRequire all denied\n</IfModule>\n".
        "<IfModule !mod_authz_core.c>\nDeny from all\n</IfModule>\n";
    foreach ([privateRoot(),storage()] as $dir) {
        $file=$dir.'/.htaccess';
        if (!is_file($file) && file_put_contents($file,$deny,LOCK_EX)===false)
            throw new \RuntimeException('Cannot protect storage');
    }
}

function secret(): string {
    ensureStorage();
    $filename=keyFile();
    if (!is_file($filename)) {
        // A missing key with existing ciphertext is a recovery emergency.
        // Never silently generate another key and orphan other users' data.
        if(count(glob(storage().'/accounts/*.json')?:[])>0 ||
           count(glob(storage().'/index/*.json')?:[])>0)
            throw new \RuntimeException('Master key missing; restore secret.php from backup');
        $fp=@fopen($filename,'x');
        if ($fp !== false) {
            try {
                chmod($filename,0600);
                $php="<?php\nreturn '".bin2hex(random_bytes(32))."';\n";
                if (fwrite($fp,$php)!==strlen($php))
                    throw new \RuntimeException('Cannot store encryption key');
                fflush($fp);
                if(function_exists('fsync'))fsync($fp);
            } finally { fclose($fp); }
        }
    }
    // Never regenerate a corrupt/missing key over existing account records.
    $hex=@include $filename;
    if (!is_string($hex) || !preg_match('/^[a-f0-9]{64}$/D',$hex))
        throw new \RuntimeException('Server encryption key unavailable');
    return hex2bin($hex);
}

function transaction(callable $callback): mixed {
    ensureStorage();
    $fd=@fopen(storage().'/.transaction.lock','c');
    if ($fd===false || !flock($fd,LOCK_EX))
        throw new \RuntimeException('Storage lock unavailable');
    try { return $callback(); }
    finally { flock($fd,LOCK_UN); fclose($fd); }
}

function encryptRecord(array $record): string {
    $plaintext=json_encode($record,JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);
    if(strlen($plaintext)>MAX_ACCOUNT_BYTES)
        fail(413,'Stored record too large');
    $iv=random_bytes(12);$tag='';
    $cipher=openssl_encrypt($plaintext,'aes-256-gcm',secret(),
        OPENSSL_RAW_DATA,$iv,$tag,AAD,16);
    if ($cipher===false)throw new \RuntimeException('Encryption failure');
    return json_encode(['v'=>1,'nonce'=>base64_encode($iv),
        'tag'=>base64_encode($tag),'ciphertext'=>base64_encode($cipher)],
        JSON_THROW_ON_ERROR);
}

function decryptRecord(string $encoded): array {
    $envelope=json_decode($encoded,true,16,JSON_THROW_ON_ERROR);
    if(!is_array($envelope)||($envelope['v']??null)!==1)
        throw new \RuntimeException('Invalid storage format');
    $iv=base64_decode($envelope['nonce']??'',true);
    $tag=base64_decode($envelope['tag']??'',true);
    $cipher=base64_decode($envelope['ciphertext']??'',true);
    if ($iv===false||strlen($iv)!==12||$tag===false||strlen($tag)!==16||
        $cipher===false)throw new \RuntimeException('Corrupt storage record');
    $plaintext=openssl_decrypt($cipher,'aes-256-gcm',secret(),
        OPENSSL_RAW_DATA,$iv,$tag,AAD);
    if($plaintext===false)throw new \RuntimeException('Storage authentication failed');
    $data=json_decode($plaintext,true,64,JSON_THROW_ON_ERROR);
    if(!is_array($data))throw new \RuntimeException('Corrupt account data');
    return $data;
}

function readRecord(string $path): ?array {
    if (!is_file($path))return null;
    $bytes=@file_get_contents($path,false,null,0,MAX_ENVELOPE_BYTES+1);
    if($bytes===false||strlen($bytes)>MAX_ENVELOPE_BYTES)
        throw new \RuntimeException('Cannot read account record');
    return decryptRecord($bytes);
}

function writeRecord(string $path,array $record): void {
    $data=encryptRecord($record);
    $temporary=$path.'.'.bin2hex(random_bytes(8)).'.tmp';
    $fp=@fopen($temporary,'x');
    if($fp===false)throw new \RuntimeException('Temporary storage unavailable');
    try {
        chmod($temporary,0600);
        if (fwrite($fp,$data)!==strlen($data))
            throw new \RuntimeException('Incomplete storage write');
        fflush($fp);
        if(function_exists('fsync'))fsync($fp);
    } catch (\Throwable $error) {
        fclose($fp);@unlink($temporary);throw $error;
    }
    fclose($fp);
    if(!rename($temporary,$path)){
        @unlink($temporary);
        throw new \RuntimeException('Cannot commit JSON storage');
    }
}
