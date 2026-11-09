<?php
declare(strict_types=1);
require_once __DIR__.'/../../web/lib.php';

use function FakturivaSelfHost\signUp;
use function FakturivaSelfHost\signIn;
use function FakturivaSelfHost\loadBackup;
use function FakturivaSelfHost\saveBackup;
use function FakturivaSelfHost\authorized;
use function FakturivaSelfHost\deleteAuthorized;
use function FakturivaSelfHost\accountPath;
use FakturivaSelfHost\HttpFailure;

function check(bool $value,string $message): void {
    if(!$value)throw new RuntimeException($message);
}
$tag=bin2hex(random_bytes(5));
$a='qa_'.$tag.'_a';
$b='qa_'.$tag.'_b';
$pwA='Strong test-only A passphrase '.$tag;
$pwB='Strong test-only B passphrase '.$tag;
$ip='127.0.0.1';
$first=signUp($a,$pwA,$ip);
$second=signUp($b,$pwB,$ip);
$ta=$first['token'];$tb=$second['token'];
check($first['revision']===0 && $second['revision']===0,'Wrong starting revision');
check(loadBackup($ta)['backup_b64']===null,'New A account must start empty');
check(loadBackup($tb)['backup_b64']===null,'New B account must start empty');
check(signIn($a,$pwA,$ip)['username']===$a,'Sign in A failed');
$fake=base64_encode('FAK1'.random_bytes(56));
$saved=saveBackup($ta,$fake,0);
check($saved['revision']===1,'A revision not incremented');
check(loadBackup($ta)['backup_b64']===$fake,'A cannot load its own backup');
check(loadBackup($tb)['backup_b64']===null,'B can see A data');
try {
    saveBackup($ta,$fake,0);
    throw new RuntimeException('Stale revision overwrote current backup');
}catch(HttpFailure $error){check($error->statusCode===409,'Wrong conflict response');}
try{
    $different=substr($ta,-1)==='0'?'1':'0';
    authorized(substr($ta,0,-1).$different);
    throw new RuntimeException('Mutated bearer token was accepted');
}catch(HttpFailure $error){check($error->statusCode===401,'Wrong authentication failure');}
$bytes=file_get_contents(accountPath(explode('.',$ta,2)[0]));
check(is_string($bytes),'No encrypted account file');
check(strpos($bytes,$a)===false,'Username exposed in JSON storage');
check(strpos($bytes,'FAK1')===false,'Backup exposed in JSON storage');
deleteAuthorized($ta,$pwA);
try{
    loadBackup($ta);
    throw new RuntimeException('Deleted A token remained active');
}catch(HttpFailure $error){check($error->statusCode===401,'Deletion did not revoke token');}
check(loadBackup($tb)['backup_b64']===null,'Deleting A damaged B');
deleteAuthorized($tb,$pwB);
echo "Self-host tenant isolation, encrypted JSON, auth, revision and deletion: OK\n";
