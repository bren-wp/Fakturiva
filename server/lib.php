<?php
declare(strict_types=1);
namespace FakturivaSelfHost;

/**
 * Fakturiva optional self-hosted storage engine (PHP 8.1+, OpenSSL).
 * No database, cookies, analytics or third-party APIs. Never expose this
 * library as an account API: api.php enforces TLS and JSON POST.
 */
const MAX_BACKUP_BYTES = 25000000;
const MAX_ACCOUNT_BYTES = 60000000;
const SESSION_SECONDS = 86400;
const AAD = 'Fakturiva:encrypted-JSON:v1';

function fail(int $status, string $message): never {
    throw new HttpFailure($status, $message);
}
final class HttpFailure extends \RuntimeException {
    public function __construct(public readonly int $statusCode, string $message) {
        parent::__construct($message);
    }
}

function storage(): string { return __DIR__ . '/storage/data'; }
function keyFile(): string { return __DIR__ . '/storage/secret.php'; }

function ensureStorage(): void {
    foreach ([__DIR__.'/storage',storage(),storage().'/accounts',
              storage().'/index',storage().'/limits'] as $path) {
        if (!is_dir($path) && !mkdir($path,0700,true) && !is_dir($path))
            throw new \RuntimeException('Cannot create storage');
    }
    $deny = "<IfModule mod_authz_core.c>\nRequire all denied\n</IfModule>\n".
        "<IfModule !mod_authz_core.c>\nDeny from all\n</IfModule>\n";
    foreach ([__DIR__.'/storage',storage()] as $dir) {
        $file=$dir.'/.htaccess';
        if (!is_file($file) && file_put_contents($file,$deny,LOCK_EX)===false)
            throw new \RuntimeException('Cannot protect storage');
    }
}

function secret(): string {
    ensureStorage();
    $filename=keyFile();
    if (!is_file($filename)) {
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
    $bytes=@file_get_contents($path,false,null,0,MAX_ACCOUNT_BYTES+1000000);
    if($bytes===false||strlen($bytes)>MAX_ACCOUNT_BYTES+500000)
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

function normalizeName(string $name): string {
    $name=trim($name);
    if(!preg_match('/^[A-Za-z0-9][A-Za-z0-9._+@-]{2,79}$/D',$name))
        fail(422,'Invalid username');
    return strtolower($name);
}
function accountPath(string $id): string {
    if(!preg_match('/^[a-f0-9]{32}$/D',$id))fail(400,'Invalid account ID');
    return storage().'/accounts/'.$id.'.json';
}
function indexPath(string $username): string {
    $digest=hash_hmac('sha256',normalizeName($username),secret());
    return storage().'/index/'.$digest.'.json';
}
function accountByName(string $username): ?array {
    $index=readRecord(indexPath($username));
    if($index===null)return null;
    $id=$index['id']??'';
    if(!is_string($id)||!preg_match('/^[a-f0-9]{32}$/D',$id))
        throw new \RuntimeException('Corrupt username index');
    return readRecord(accountPath($id));
}
function requirePassphrase(string $password): void {
    if(strlen($password)<12||strlen($password)>256)
        fail(422,'Use a dedicated server passphrase of 12–256 bytes');
}
function mintToken(array &$account): string {
    $random=bin2hex(random_bytes(32));
    $token=$account['id'].'.'.$random;
    if(!isset($account['sessions'])||!is_array($account['sessions']))
        $account['sessions']=[];
    $now=time();
    foreach($account['sessions'] as $hash=>$expires)
        if(!is_int($expires)||$expires<=$now)unset($account['sessions'][$hash]);
    // Bound retained sessions; least-recently-expiring tokens go first.
    asort($account['sessions'],SORT_NUMERIC);
    while(count($account['sessions'])>=8)array_shift($account['sessions']);
    $account['sessions'][hash('sha256',$token)]=$now+SESSION_SECONDS;
    return $token;
}

function rateLimit(string $address,bool $failed): void {
    // REMOTE_ADDR is server-owned; never trust user-supplied forwarded IP headers.
    $ip=substr($address,0,64);
    $key=hash_hmac('sha256',$ip,secret());
    $path=storage().'/limits/'.$key.'.json';
    $item=readRecord($path)??['since'=>time(),'count'=>0];
    if(time()-(int)$item['since']>=300)$item=['since'=>time(),'count'=>0];
    if((int)$item['count']>=20)fail(429,'Too many attempts; try later');
    if($failed){
        $item['count']=min(21,(int)$item['count']+1);
        writeRecord($path,$item);
    }
}
function signUp(string $username,string $password,string $address): array {
    requirePassphrase($password);
    return transaction(function() use($username,$password,$address): array {
        rateLimit($address,false);
        $index=indexPath($username);
        if(is_file($index))fail(409,'Username already registered');
        $id=bin2hex(random_bytes(16));
        $account=['id'=>$id,'username'=>trim($username),
            'password_hash'=>password_hash($password,
                defined('PASSWORD_ARGON2ID')?PASSWORD_ARGON2ID:PASSWORD_BCRYPT),
            'revision'=>0,'backup'=>null,'sessions'=>[],
            'created_at'=>gmdate('c')];
        $token=mintToken($account);
        $path=accountPath($id);
        writeRecord($path,$account);
        try { writeRecord($index,['id'=>$id]); }
        catch(\Throwable $error){@unlink($path);throw $error;}
        return ['token'=>$token,'revision'=>0,'username'=>$account['username']];
    });
}
function signIn(string $username,string $password,string $address): array {
    return transaction(function() use($username,$password,$address): array {
        rateLimit($address,false);
        $account=accountByName($username);
        if(!$account || !password_verify($password,(string)$account['password_hash'])){
            rateLimit($address,true);
            fail(401,'Invalid username or passphrase');
        }
        $token=mintToken($account);
        writeRecord(accountPath($account['id']),$account);
        return ['token'=>$token,'revision'=>(int)$account['revision'],
            'username'=>$account['username']];
    });
}
function authorized(string $token): array {
    if(!preg_match('/^([a-f0-9]{32})\.([a-f0-9]{64})$/D',$token,$matches))
        fail(401,'Invalid session');
    $account=readRecord(accountPath($matches[1]));
    if(!$account)fail(401,'Invalid session');
    $hash=hash('sha256',$token);
    if(!isset($account['sessions'][$hash])||
        (int)$account['sessions'][$hash]<=time())fail(401,'Session expired');
    return $account;
}
function loadBackup(string $token): array {
    return transaction(function() use($token): array {
        $account=authorized($token);
        return ['revision'=>(int)$account['revision'],
            'backup_b64'=>$account['backup']];
    });
}
function saveBackup(string $token,string $backup,int $expected): array {
    $binary=base64_decode($backup,true);
    if($binary===false||strlen($binary)<48||
        strlen($binary)>MAX_BACKUP_BYTES||substr($binary,0,4)!=='FAK1')
        fail(422,'Expected a valid encrypted Fakturiva FAK1 backup');
    return transaction(function() use($token,$backup,$expected): array {
        $account=authorized($token);
        if((int)$account['revision']!==$expected)
            fail(409,'Remote revision changed. Download and review before replacing it');
        $account['revision']=(int)$account['revision']+1;
        $account['backup']=$backup;
        writeRecord(accountPath($account['id']),$account);
        return ['revision'=>$account['revision']];
    });
}
function signOut(string $token): array {
    return transaction(function() use($token): array {
        $account=authorized($token);
        unset($account['sessions'][hash('sha256',$token)]);
        writeRecord(accountPath($account['id']),$account);
        return ['logged_out'=>true];
    });
}
function deleteAccount(string $username,string $password): void {
    transaction(function() use($username,$password): void {
        $account=accountByName($username);
        if(!$account||!password_verify($password,(string)$account['password_hash']))
            fail(401,'Invalid username or passphrase');
        // Revoke all access first. A stale index is safe to purge on retry.
        $account['sessions']=[];
        $account['deleted']=true;
        writeRecord(accountPath($account['id']),$account);
        if(!unlink(accountPath($account['id'])))
            throw new \RuntimeException('Could not remove account data');
        if(!unlink(indexPath($username)))
            throw new \RuntimeException('Could not remove username index');
    });
}
function deleteAuthorized(string $token,string $password): void {
    transaction(function() use($token,$password): void {
        $account=authorized($token);
        if(!password_verify($password,(string)$account['password_hash']))
            fail(401,'Passphrase verification failed');
        $account['sessions']=[];
        $account['deleted']=true;
        writeRecord(accountPath($account['id']),$account);
        if(!unlink(accountPath($account['id']))||
           !unlink(indexPath($account['username'])))
            throw new \RuntimeException('Account deletion incomplete');
    });
}
function enforceHttps(): void {
    if(PHP_SAPI==='cli')return;
    $https=(!empty($_SERVER['HTTPS'])&&$_SERVER['HTTPS']!=='off')||
        (($_SERVER['SERVER_PORT']??'')==='443');
    // Trusted reverse-proxy deployments must configure both variables.
    if(!$https && getenv('FAKTURIVA_TRUSTED_PROXY_IP')!==false &&
        getenv('FAKTURIVA_TRUSTED_PROXY_IP')===($_SERVER['REMOTE_ADDR']??'') &&
        (($_SERVER['HTTP_X_FORWARDED_PROTO']??'')==='https'))
        $https=true;
    if(!$https && getenv('FAKTURIVA_TEST_HTTP')==='1' &&
        in_array(PHP_SAPI,['cli-server'],true) &&
        in_array(($_SERVER['REMOTE_ADDR']??''),['127.0.0.1','::1'],true))
        $https=true;
    if(!$https)fail(403,'HTTPS is required');
}
