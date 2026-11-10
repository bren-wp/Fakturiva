<?php
declare(strict_types=1);
require_once __DIR__.'/../../web/lib.php';

use function FakturivaSelfHost\webRegister;
use function FakturivaSelfHost\webLoginPassword;
use function FakturivaSelfHost\webAuthorized;
use function FakturivaSelfHost\webLogout;
use function FakturivaSelfHost\webDeleteAccount;
use function FakturivaSelfHost\webChangeAndroidPassphrase;
use function FakturivaSelfHost\signUp;
use function FakturivaSelfHost\signIn;
use function FakturivaSelfHost\loadBackup;
use function FakturivaSelfHost\saveBackup;
use function FakturivaSelfHost\deleteAuthorized;
use FakturivaSelfHost\HttpFailure;

function assertWeb(bool $yes,string $message): void {
    if(!$yes)throw new RuntimeException($message);
}
function mustDeny(callable $call,int $status,string $description):void{
    try{$call();throw new RuntimeException('Access unexpectedly accepted: '.$description);}
    catch(HttpFailure $error){
        assertWeb($error->statusCode===$status,
            $description.' expected HTTP '.$status.', got '.$error->statusCode);
    }
}

// New email/password accounts have no PIN, but still own the exact same
// Android API identity and can verify sensitive operations with their password.
$noPinSuffix=bin2hex(random_bytes(7));
$noPinUser='mobile_'.$noPinSuffix.'@example.test';
$noPinPassword='Brendigo-cross-platform-test-'.$noPinSuffix;
$noPinIp='192.0.2.141';
$noPinAccount=webRegister($noPinUser,'',$noPinIp,$noPinPassword);
assertWeb(webAuthorized($noPinAccount['id'],$noPinAccount['token'])
    ['username']===$noPinUser,'Password-only account creation failed');
$noPinApi=signIn($noPinUser,$noPinPassword,$noPinIp);
assertWeb(explode('.',$noPinApi['token'])[0]===$noPinAccount['id'],
    'Web and Android created distinct accounts for one email');
mustDeny(fn()=>webLoginPassword($noPinUser,'incorrect-password-2026',$noPinIp),401,
    'Incorrect password must never open a user account');
mustDeny(fn()=>webDeleteAccount($noPinAccount['id'],$noPinAccount['token'],
    'wrong-password-2026'),401,'Deletion accepted incorrect password');
$newNoPinPassword='Brendigo-rotated-test-'.$noPinSuffix;
webChangeAndroidPassphrase($noPinAccount['id'],$noPinAccount['token'],
    $noPinPassword,$newNoPinPassword);
mustDeny(fn()=>signIn($noPinUser,$noPinPassword,$noPinIp),401,
    'Old web password still signs into Android after rotation');
assertWeb(signIn($noPinUser,$newNoPinPassword,$noPinIp)
    ['username']===$noPinUser,'New password cannot authenticate Android');
webDeleteAccount($noPinAccount['id'],$noPinAccount['token'],$newNoPinPassword);
mustDeny(fn()=>signIn($noPinUser,$newNoPinPassword,$noPinIp),401,
    'Deleted email/password account still authorizes Android');

$suffix=bin2hex(random_bytes(6));
$ip='192.0.2.'.random_int(30,100);
$userA='web_'.$suffix.'_a';
$userB='web_'.$suffix.'_b';
$pinA='123456';$pinB='456789';
$serverPassA='Different-long-server-passphrase-'.$suffix;
$serverPassB='Another-long-server-passphrase-'.$suffix;

$a=webRegister($userA,'',$ip,$serverPassA);
$b=webRegister($userB,'',$ip,$serverPassB);
assertWeb($a['id']!==$b['id'],'Profiles share an ID');
$passwordLogin=webLoginPassword($userA,$serverPassA,$ip);
assertWeb($passwordLogin['id']===$a['id'],
    'Password login must resolve exactly the same web tenant');
$a['token']=$passwordLogin['token'];
assertWeb(webAuthorized($a['id'],$passwordLogin['token'])['username']===$userA,
    'Password session does not authorize account');
assertWeb(webAuthorized($a['id'],$a['token'])['username']===$userA,
    'Web A session not bound to account');
assertWeb(webAuthorized($b['id'],$b['token'])['username']===$userB,
    'Web B session not bound to account');
mustDeny(fn()=>webAuthorized($b['id'],$a['token']),401,
    'Web A token authorizes Web B');
mustDeny(fn()=>signIn($userA,'Incorrect-long-passphrase',$ip),401,
    'Incorrect passphrase authorizes Android API');
$android=signIn($userA,$serverPassA,$ip);
assertWeb(loadBackup($android['token'])['revision']===0,
    'Android API cannot access correct web account');
// Unprotected credential rotation would irreversibly strand encrypted data.
$sealed=base64_encode('FAK1'.str_repeat('X',60));
$ack=saveBackup($android['token'],$sealed,0);
assertWeb($ack['revision']===1,'Encrypted revision was not stored');
mustDeny(fn()=>webChangeAndroidPassphrase($a['id'],$a['token'],$serverPassA,
    'New-long-secret-'.$suffix),409,'Rotation stranded the encrypted snapshot');
assertWeb(signIn($userA,$serverPassA,$ip)['username']===$userA,
    'Blocked rotation still changed the active passphrase');
assertWeb(webAuthorized($b['id'],$b['token'])['backup']===null,
    'Web B gained Web A backup');
webLogout($a['id'],$a['token']);
mustDeny(fn()=>webAuthorized($a['id'],$a['token']),401,'Logout token still works');
$newA=webLoginPassword($userA,$serverPassA,$ip);
assertWeb($newA['id']===$a['id'],'Login opened wrong profile');
webDeleteAccount($a['id'],$newA['token'],$serverPassA);
mustDeny(fn()=>webAuthorized($a['id'],$newA['token']),401,'Deleted web user stays active');
assertWeb(webAuthorized($b['id'],$b['token'])['username']===$userB,
    'Deleting A corrupted B');

$apiUser='api_'.$suffix;
$api=signUp($apiUser,$serverPassB,$ip);
$apiWeb=webLoginPassword($apiUser,$serverPassB,$ip);
assertWeb($apiWeb['id']===explode('.',$api['token'])[0],
    'Same password must resolve the existing Android account');
webDeleteAccount($apiWeb['id'],$apiWeb['token'],$serverPassB);
$nativeUser='native_'.$suffix;
$native=signUp($nativeUser,$serverPassB,$ip,$pinB);
$nativeWeb=webLoginPassword($nativeUser,$serverPassB,$ip);
assertWeb($nativeWeb['id']===explode('.',$native['token'])[0],
    'Android and web registration created different tenants');
assertWeb(!isset(webAuthorized($nativeWeb['id'],$nativeWeb['token'])['web_pin_hash']),
    'Legacy PIN verifier was not retired after verified password login');
webDeleteAccount($nativeWeb['id'],$nativeWeb['token'],$serverPassB);
webDeleteAccount($b['id'],$b['token'],$serverPassB);
echo "Email/password web/API identity, migration, isolation and deletion: OK\n";
