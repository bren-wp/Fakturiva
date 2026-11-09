<?php
declare(strict_types=1);
require_once __DIR__.'/../../web/lib.php';

use function FakturivaSelfHost\webRegister;
use function FakturivaSelfHost\webLogin;
use function FakturivaSelfHost\webAuthorized;
use function FakturivaSelfHost\webLogout;
use function FakturivaSelfHost\webDeleteAccount;
use function FakturivaSelfHost\webChangeAndroidPassphrase;
use function FakturivaSelfHost\webLinkAndroidAccount;
use function FakturivaSelfHost\signUp;
use function FakturivaSelfHost\signIn;
use function FakturivaSelfHost\loadBackup;
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

$suffix=bin2hex(random_bytes(6));
$ip='192.0.2.'.random_int(30,100);
$userA='web_'.$suffix.'_a';
$userB='web_'.$suffix.'_b';
$pinA='123456';$pinB='456789';
$serverPassA='Different-long-server-passphrase-'.$suffix;
$serverPassB='Another-long-server-passphrase-'.$suffix;

$a=webRegister($userA,$pinA,$ip);
$b=webRegister($userB,$pinB,$ip);
assertWeb($a['id']!==$b['id'],'Profiles share an ID');
assertWeb(webAuthorized($a['id'],$a['token'])['username']===$userA,
    'Web A session not bound to account');
assertWeb(webAuthorized($b['id'],$b['token'])['username']===$userB,
    'Web B session not bound to account');
mustDeny(fn()=>webAuthorized($b['id'],$a['token']),401,
    'Web A token authorizes Web B');
mustDeny(fn()=>signIn($userA,$serverPassA,$ip),401,
    'New web PIN authorizes Android API');
webChangeAndroidPassphrase($a['id'],$a['token'],$pinA,$serverPassA);
$android=signIn($userA,$serverPassA,$ip);
assertWeb(loadBackup($android['token'])['revision']===0,
    'Android link cannot access correct web account');
assertWeb(webAuthorized($b['id'],$b['token'])['backup']===null,
    'Web B gained Web A backup');
webLogout($a['id'],$a['token']);
mustDeny(fn()=>webAuthorized($a['id'],$a['token']),401,'Logout token still works');
$newA=webLogin($userA,$pinA,$ip);
assertWeb($newA['id']===$a['id'],'Login opened wrong profile');
webDeleteAccount($a['id'],$newA['token'],$pinA);
mustDeny(fn()=>webAuthorized($a['id'],$newA['token']),401,'Deleted web user stays active');
assertWeb(webAuthorized($b['id'],$b['token'])['username']===$userB,
    'Deleting A corrupted B');

$apiUser='api_'.$suffix;
$api=signUp($apiUser,$serverPassB,$ip);
$link=webLinkAndroidAccount($apiUser,$serverPassB,$pinB,$ip);
assertWeb($link['id']===explode('.',$api['token'])[0],
    'Linking Android account created a duplicate tenant');
$linkedLogin=webLogin($apiUser,$pinB,$ip);
assertWeb($linkedLogin['username']===$apiUser,'Linked Android PIN login failed');
webDeleteAccount($link['id'],$linkedLogin['token'],$pinB);
webDeleteAccount($b['id'],$b['token'],$pinB);
echo "Web PIN register/login, tenant isolation, separate Android passphrase, link and deletion: OK\n";
