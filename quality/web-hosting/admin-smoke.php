<?php
declare(strict_types=1);
require_once __DIR__.'/../../web/lib.php';

use FakturivaSelfHost\HttpFailure;
use function FakturivaSelfHost\adminReadAccounts;
use function FakturivaSelfHost\adminManageAccount;
use function FakturivaSelfHost\signUp;
use function FakturivaSelfHost\signIn;
use function FakturivaSelfHost\authorized;
use function FakturivaSelfHost\deleteAccount;
use function FakturivaSelfHost\accountByName;
function checkAdmin(bool $result,string $message):void{
    if(!$result)throw new RuntimeException($message);
}
$nonce=bin2hex(random_bytes(8));
$a='account_admin_a_'.$nonce;
$b='account_admin_b_'.$nonce;
$secret='A_secure_admin_test_passphrase_'.$nonce;
$ip='192.0.2.77';
signUp($a,$secret,$ip);
signUp($b,$secret,$ip);
try{
    $first=FakturivaSelfHost\accountByName($a);
    $second=FakturivaSelfHost\accountByName($b);
    checkAdmin($first!==null&&$second!==null,'Account creation failed');
    checkAdmin($first['tier']==='FREE','Default tier must be free');
    $token=signIn($a,$secret,$ip)['token'];
    adminManageAccount($first['id'],'tier','PRO');
    checkAdmin(FakturivaSelfHost\accountByName($a)['tier']==='PRO',
        'Tier not saved');
    checkAdmin(FakturivaSelfHost\accountByName($b)['tier']==='FREE',
        'Cross-tenant tier contamination');
    adminManageAccount($first['id'],'suspend');
    try{
        authorized($token);
        throw new RuntimeException('Suspended account still authorized');
    }catch(HttpFailure $e){checkAdmin($e->statusCode===403,
        'Suspended account must return forbidden');}
    adminManageAccount($first['id'],'restore');
    checkAdmin(FakturivaSelfHost\accountByName($a)['suspended']===false,
        'Account not restored');
    try{
        authorized($token);
        throw new RuntimeException('Revoked API token restored unexpectedly');
    }catch(HttpFailure $e){checkAdmin($e->statusCode===401,
        'Suspension must revoke old sessions');}
    $rows=adminReadAccounts();
    $matches=array_values(array_filter($rows,
        static fn(array $row):bool=>$row['username']===$a));
    checkAdmin(count($matches)===1,'Admin metadata list missing account');
    checkAdmin(!array_key_exists('backup',$matches[0]),
        'Admin listing exposed financial backup');
    adminManageAccount($first['id'],'delete');
    checkAdmin(accountByName($a)===null,'Admin delete left username index');
    checkAdmin(accountByName($b)!==null,'Admin delete touched another account');
    try{
        adminManageAccount($second['id'],'tier','UNSUPPORTED');
        throw new RuntimeException('Unknown tier accepted');
    }catch(HttpFailure $e){checkAdmin($e->statusCode===422,'Invalid tier error');}
    echo "Fakturiva admin tenant isolation, tier, ban, session revocation and deletion: OK\n";
}finally{
    if(accountByName($a)!==null)deleteAccount($a,$secret);
    if(accountByName($b)!==null)deleteAccount($b,$secret);
}
