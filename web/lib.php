<?php
declare(strict_types=1);
namespace FakturivaSelfHost;

/**
 * Fakturiva optional self-hosted storage engine (PHP 8.1+, OpenSSL).
 * No database, cookies, analytics or third-party APIs. Never expose this
 * library as an account API: api.php enforces TLS and JSON POST.
 */
const MAX_BACKUP_BYTES = 25000000;
const MAX_ACCOUNT_BYTES = 45000000;
const MAX_ENVELOPE_BYTES = 62500000;
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
    // Bcrypt truncates input after 72 bytes on hosts without Argon2id.
    if(!defined('PASSWORD_ARGON2ID') && strlen($password)>72)
        fail(422,'This host supports passphrases of at most 72 bytes');
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
function signUp(string $username,string $password,string $address,?string $webPin=null): array {
    requirePassphrase($password);
    if($webPin!==null)checkWebPin($webPin);
    return transaction(function() use($username,$password,$address,$webPin): array {
        // Account creation also consumes the IP budget to prevent unbounded
        // JSON-file creation on shared hosting from scripted registrations.
        rateLimit($address,true);
        $index=indexPath($username);
        if(is_file($index)){
            $entry=readRecord($index);
            $existingId=is_array($entry)?($entry['id']??null):null;
            if(is_string($existingId) &&
                preg_match('/^[a-f0-9]{32}$/D',$existingId) &&
                is_file(accountPath($existingId)))
                fail(409,'Username already registered');
            // Recover an orphan username index left by an interrupted deletion;
            // an old account file must NEVER be silently reassigned.
            if(!unlink($index))
                throw new \RuntimeException('Cannot clear stale username index');
        }
        $id=bin2hex(random_bytes(16));
        $account=['id'=>$id,'username'=>trim($username),
            'tier'=>'FREE','suspended'=>false,
            'password_hash'=>password_hash($password,
                defined('PASSWORD_ARGON2ID')?PASSWORD_ARGON2ID:PASSWORD_BCRYPT),
            'revision'=>0,'backup'=>null,'sessions'=>[],
            'created_at'=>gmdate('c')];
        // API-created accounts may also sign into the web with their PIN.
        if($webPin!==null){
            $account['web_pin_hash']=pinHash($webPin);
            $account['web_attempts']=0;
            $account['web_block_until']=0;
        }
        $token=mintToken($account);
        $path=accountPath($id);
        writeRecord($path,$account);
        try { writeRecord($index,['id'=>$id]); }
        catch(\Throwable $error){@unlink($path);throw $error;}
        return ['token'=>$token,'revision'=>0,'username'=>$account['username']];
    });
}
function signIn(string $username,string $password,string $address): array {
    requirePassphrase($password);
    return transaction(function() use($username,$password,$address): array {
        rateLimit($address,false);
        $account=accountByName($username);
        if(!$account || !password_verify($password,(string)$account['password_hash'])){
            rateLimit($address,true);
            fail(401,'Invalid username or passphrase');
        }
        if(!empty($account['suspended']))fail(403,'Account access is suspended');
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
    if(!empty($account['suspended']))fail(403,'Account suspended');
    $hash=hash('sha256',$token);
    if(!isset($account['sessions'][$hash])||
        (int)$account['sessions'][$hash]<=time())fail(401,'Session expired');
    return $account;
}
/** Account data remains authoritative on this server, not Android storage.
 * Revision history keeps bounded previous encrypted snapshots for recovery
 * from an accidental overwrite. It is deleted together with the account.
 */
function encryptedRevisionList(array $account): array {
    $history=$account['backup_history']??[];
    if(!is_array($history))throw new \RuntimeException('Invalid backup history');
    $rows=[];
    if(is_string($account['backup']??null)){
        $binary=base64_decode($account['backup'],true);
        if($binary===false)throw new \RuntimeException('Corrupt latest backup');
        $rows[]=['revision'=>(int)$account['revision'],
            'sha256'=>hash('sha256',$binary),
            'bytes'=>strlen($binary),'saved_at'=>$account['saved_at']??null,
            'current'=>true];
    }
    foreach($history as $entry){
        if(!is_array($entry)||!isset($entry['revision'],$entry['backup_b64']))continue;
        $binary=base64_decode((string)$entry['backup_b64'],true);
        if($binary===false)throw new \RuntimeException('Corrupt backup history');
        $rows[]=['revision'=>(int)$entry['revision'],
            'sha256'=>hash('sha256',$binary),'bytes'=>strlen($binary),
            'saved_at'=>$entry['saved_at']??null,'current'=>false];
    }
    return $rows;
}
function loadBackup(string $token): array {
    return transaction(function() use($token): array {
        $account=authorized($token);
        $rows=encryptedRevisionList($account);
        return ['revision'=>(int)$account['revision'],
            'backup_b64'=>$account['backup'],
            'sha256'=>count($rows)?$rows[0]['sha256']:null,
            'saved_at'=>$account['saved_at']??null,
            'history'=>array_map(static fn($row)=>[
                'revision'=>$row['revision'],'sha256'=>$row['sha256'],
                'bytes'=>$row['bytes'],'saved_at'=>$row['saved_at'],
                'current'=>$row['current']],$rows)];
    });
}
function appendPreviousBackup(array &$account,int $incomingLength): void {
    if(!is_string($account['backup']??null))return;
    $history=$account['backup_history']??[];
    if(!is_array($history))$history=[];
    array_unshift($history,[
        'revision'=>(int)$account['revision'],
        'saved_at'=>$account['saved_at']??null,
        'backup_b64'=>$account['backup']
    ]);
    // Preserve the latest plus at most two prior snapshots, within the 60MB
    // account record cap. Never drop the current snapshot.
    $limit=44000000; $used=$incomingLength;
    $kept=[];
    foreach($history as $candidate){
        if(!is_array($candidate)||!is_string($candidate['backup_b64']??null))continue;
        $bytes=strlen($candidate['backup_b64']);
        if(count($kept)>=2||$used+$bytes>$limit)continue;
        $kept[]=$candidate;$used+=$bytes;
    }
    $account['backup_history']=$kept;
}
function saveBackup(string $token,string $backup,int $expected): array {
    $binary=base64_decode($backup,true);
    if($binary===false||strlen($binary)<48||
        strlen($binary)>MAX_BACKUP_BYTES||substr($binary,0,4)!=='FAK1')
        fail(422,'Expected a valid encrypted Fakturiva FAK1 backup');
    $sha=hash('sha256',$binary);
    return transaction(function() use($token,$backup,$expected,$sha): array {
        $account=authorized($token);
        if((int)$account['revision']!==$expected)
            fail(409,'Remote revision changed. Download and review before replacing it');
        appendPreviousBackup($account,strlen($backup));
        $account['revision']=(int)$account['revision']+1;
        $account['backup']=$backup;
        $account['saved_at']=gmdate('c');
        // Try record-size checks before replacing an existing revision.
        writeRecord(accountPath($account['id']),$account);
        return ['revision'=>$account['revision'],'sha256'=>$sha,
            'saved_at'=>$account['saved_at']];
    });
}
function restoreEncryptedRevision(string $token,int $revision,int $expected): array {
    if($revision<1||$expected<0)fail(422,'Invalid revision');
    return transaction(function()use($token,$revision,$expected):array{
        $account=authorized($token);
        if((int)$account['revision']!==$expected)
            fail(409,'Remote revision changed');
        $copy=null;
        foreach($account['backup_history']??[] as $item){
            if(is_array($item)&&(int)($item['revision']??-1)===$revision){
                $copy=$item['backup_b64']??null;break;
            }
        }
        if(!is_string($copy))fail(404,'Encrypted revision not retained');
        appendPreviousBackup($account,strlen($copy));
        $account['backup']=$copy;
        $account['revision']=$expected+1;
        $account['saved_at']=gmdate('c');
        writeRecord(accountPath($account['id']),$account);
        $raw=base64_decode($copy,true);
        return ['revision'=>$account['revision'],'sha256'=>hash('sha256',$raw),
            'saved_at'=>$account['saved_at']];
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


/**
 * Browser PIN is separate from Android's 12+ character sync passphrase.
 * A 4–6 digit PIN is only acceptable with per-account AND per-IP throttling,
 * encrypted server records, session expiry and HTTPS. It is not strong enough
 * to encrypt synced business backups.
 */
function checkWebPin(string $pin): void {
    if(!preg_match('/^[0-9]{4,6}$/D',$pin))fail(422,'PIN mora imati 4–6 znamenki');
}
function webPinDigest(string $pin): string {
    return hash_hmac('sha256',"fakturiva-web-pin-v1\0".$pin,secret());
}
function pinHash(string $pin): string {
    return password_hash(webPinDigest($pin),
        defined('PASSWORD_ARGON2ID') ? PASSWORD_ARGON2ID : PASSWORD_BCRYPT);
}
function pinMatches(array $account,string $pin): bool {
    $hash=$account['web_pin_hash']??null;
    return is_string($hash) && preg_match('/^[0-9]{4,6}$/D',$pin)===1 &&
        password_verify(webPinDigest($pin),$hash);
}
/** PIN-confirmed destructive and credential-changing operations are throttled too. */
function requireSensitiveWebPin(array &$account,string $pin):void {
    if((int)($account['web_block_until']??0)>time())
        fail(429,'Previše pokušaja. Pokušajte ponovno kasnije.');
    if(!pinMatches($account,$pin)){
        $n=min(20,1+(int)($account['web_attempts']??0));
        $account['web_attempts']=$n;
        if($n>=5)$account['web_block_until']=time()+min(3600,
            60*(1 << min($n-5,6)));
        writeRecord(accountPath($account['id']),$account);
        fail(401,'Neispravan PIN');
    }
    $account['web_attempts']=0;
    $account['web_block_until']=0;
}
function issueWebSession(array &$account): string {
    $token=bin2hex(random_bytes(32));
    // One active browser token per account. Re-login invalidates old tabs.
    $account['web_token_hash']=hash('sha256',$token);
    $account['web_token_until']=time()+1800;
    return $token;
}
function webRegister(string $username,string $pin,string $address,string $passphrase): array {
    checkWebPin($pin);
    requirePassphrase($passphrase);
    return transaction(function()use($username,$pin,$address,$passphrase):array{
        rateLimit($address,true);
        $index=indexPath($username);
        if(is_file($index)) {
            $existing=readRecord($index);
            if(is_array($existing) && is_string($existing['id']??null) &&
               is_file(accountPath($existing['id'])))
                fail(409,'Korisničko ime je već zauzeto');
            if(!unlink($index))throw new \RuntimeException('Stale user index cannot be removed');
        }
        $id=bin2hex(random_bytes(16));
        $account=[
            'id'=>$id,
            'username'=>trim($username),
            'tier'=>'FREE',
            'suspended'=>false,
            // Strong independent passphrase is required for recoverable backups.
            'password_hash'=>password_hash($passphrase,
                defined('PASSWORD_ARGON2ID')?PASSWORD_ARGON2ID:PASSWORD_BCRYPT),
            'web_pin_hash'=>pinHash($pin),
            'web_attempts'=>0,
            'web_block_until'=>0,
            'revision'=>0,
            'backup'=>null,
            'sessions'=>[],
            'created_at'=>gmdate('c')
        ];
        $token=issueWebSession($account);
        writeRecord(accountPath($id),$account);
        try{writeRecord($index,['id'=>$id]);}
        catch(\Throwable $e){@unlink(accountPath($id));throw $e;}
        return ['id'=>$id,'token'=>$token,'username'=>$account['username']];
    });
}
function webLogin(string $username,string $pin,string $address): array {
    return transaction(function()use($username,$pin,$address):array{
        rateLimit($address,false);
        $account=accountByName($username);
        if($account===null || !isset($account['web_pin_hash'])) {
            rateLimit($address,true);
            fail(401,'Korisničko ime ili PIN nisu ispravni');
        }
        if(!empty($account['suspended']))fail(403,'Račun je privremeno onemogućen.');
        if((int)($account['web_block_until']??0)>time())
            fail(429,'Previše pokušaja. Pokušajte ponovno kasnije.');
        if(!pinMatches($account,$pin)){
            $n=min(20,1+(int)($account['web_attempts']??0));
            $account['web_attempts']=$n;
            if($n>=5) {
                $seconds=min(3600,60*(1 << min($n-5,6)));
                $account['web_block_until']=time()+$seconds;
            }
            writeRecord(accountPath($account['id']),$account);
            rateLimit($address,true);
            fail(401,'Korisničko ime ili PIN nisu ispravni');
        }
        $account['web_attempts']=0;
        $account['web_block_until']=0;
        $token=issueWebSession($account);
        writeRecord(accountPath($account['id']),$account);
        return ['id'=>$account['id'],'token'=>$token,
            'username'=>$account['username']];
    });
}
function webAuthorized(string $id,string $token): array {
    if(!preg_match('/^[a-f0-9]{32}$/D',$id) ||
       !preg_match('/^[a-f0-9]{64}$/D',$token))
        fail(401,'Web sesija nije valjana');
    $account=readRecord(accountPath($id));
    if($account===null || !empty($account['suspended']) ||
       !is_string($account['web_token_hash']??null) ||
       (int)($account['web_token_until']??0)<=time() ||
       !hash_equals($account['web_token_hash'],hash('sha256',$token)))
        fail(401,'Web sesija je istekla');
    return $account;
}
function webLogout(string $id,string $token): void {
    transaction(function()use($id,$token):void{
        $account=webAuthorized($id,$token);
        unset($account['web_token_hash'],$account['web_token_until']);
        writeRecord(accountPath($id),$account);
    });
}
function webLinkAndroidAccount(string $username,string $passphrase,
                               string $newPin,string $address):array {
    requirePassphrase($passphrase);checkWebPin($newPin);
    return transaction(function()use($username,$passphrase,$newPin,$address):array{
        rateLimit($address,false);
        $account=accountByName($username);
        if($account===null ||
           !is_string($account['password_hash']??null) ||
           !password_verify($passphrase,$account['password_hash'])){
            rateLimit($address,true);
            fail(401,'Neispravno korisničko ime ili lozinka hostinga');
        }
        if(!empty($account['suspended']))fail(403,'Račun je onemogućen');
        $account['web_pin_hash']=pinHash($newPin);
        $account['web_attempts']=0;
        $account['web_block_until']=0;
        $token=issueWebSession($account);
        writeRecord(accountPath($account['id']),$account);
        return ['id'=>$account['id'],'username'=>$account['username'],'token'=>$token];
    });
}
function webChangeAndroidPassphrase(string $id,string $token,string $pin,
                                    string $newPassphrase):void {
    requirePassphrase($newPassphrase);
    transaction(function()use($id,$token,$pin,$newPassphrase):void{
        $account=webAuthorized($id,$token);
        requireSensitiveWebPin($account,$pin);
        // Changing only the authentication hash would orphan all FAK1
        // archives encrypted with the old passphrase. Fail closed.
        if(is_string($account['backup']??null) && $account['backup']!=='')
            fail(409,'Postoji šifrirana kopija. Promjena lozinke zahtijeva '.
                'prethodno provjereno ponovno šifriranje svih podataka.');
        $account['password_hash']=password_hash($newPassphrase,
            defined('PASSWORD_ARGON2ID')?PASSWORD_ARGON2ID:PASSWORD_BCRYPT);
        // Existing Android API sessions are invalid after password change.
        $account['sessions']=[];
        writeRecord(accountPath($id),$account);
    });
}
function webDeleteAccount(string $id,string $token,string $pin):void {
    transaction(function()use($id,$token,$pin):void{
        $account=webAuthorized($id,$token);
        requireSensitiveWebPin($account,$pin);
        // Clear credentials before file deletion, so interruption
        // cannot leave a valid session for a half-removed profile.
        $account['sessions']=[];
        unset($account['web_token_hash'],$account['web_token_until']);
        $account['deleted']=true;
        writeRecord(accountPath($id),$account);
        if(!unlink(accountPath($id)))throw new \RuntimeException('Cannot delete account');
        if(!unlink(indexPath($account['username'])))
            throw new \RuntimeException('Cannot delete account index');
    });
}


/**
 * Administrator can only inspect account metadata. Users' financial backup
 * ciphertext is client-encrypted and never exposed through admin actions.
 * There is no default administrator login or secret in the repository.
 */
function adminReadAccounts(): array {
    return transaction(function():array{
        $items=[];
        foreach(glob(storage().'/accounts/*.json')?:[] as $file){
            if(count($items)>=10000)throw new \RuntimeException('Administration limit exceeded');
            $account=readRecord($file);
            if($account===null || !empty($account['deleted']))continue;
            $items[]=[
                'id'=>(string)$account['id'],
                'username'=>(string)$account['username'],
                'created_at'=>(string)($account['created_at']??''),
                'tier'=>(string)($account['tier']??'FREE'),
                'suspended'=>!empty($account['suspended']),
                'revision'=>(int)($account['revision']??0),
                'backup_present'=>is_string($account['backup']??null),
                'backup_bytes'=>strlen((string)($account['backup']??'')),
                'last_backup_at'=>(string)($account['saved_at']??''),
            ];
        }
        usort($items,static fn($a,$b)=>strcmp($a['username'],$b['username']));
        return $items;
    });
}
function adminManageAccount(string $id,string $action,string $value=''):void{
    if(!preg_match('/^[a-f0-9]{32}$/D',$id))fail(422,'Invalid account id');
    transaction(function()use($id,$action,$value):void{
        $path=accountPath($id);
        $account=readRecord($path);
        if($account===null||!empty($account['deleted']))fail(404,'Unknown account');
        if($action==='tier'){
            if(!in_array($value,['FREE','PRO','BUSINESS'],true))
                fail(422,'Invalid plan');
            $account['tier']=$value;
        }elseif($action==='suspend'||$action==='restore'){
            $account['suspended']=$action==='suspend';
            $account['sessions']=[];
            unset($account['web_token_hash'],$account['web_token_until']);
        }elseif($action==='delete'){
            $account['sessions']=[];
            unset($account['web_token_hash'],$account['web_token_until']);
            $account['deleted']=true;
            // Mark deleted before unlink. Account restore from an old file
            // must not silently reinstate a live session.
            writeRecord($path,$account);
            if(!unlink($path))throw new \RuntimeException('Account delete failed');
            if(!unlink(indexPath((string)$account['username'])))
                throw new \RuntimeException('Username index delete failed');
            return;
        }else fail(422,'Invalid admin action');
        writeRecord($path,$account);
    });
}
