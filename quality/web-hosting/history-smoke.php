<?php
declare(strict_types=1);
require __DIR__.'/../../web/lib.php';

use FakturivaSelfHost\HttpFailure;
use function FakturivaSelfHost\signUp;
use function FakturivaSelfHost\loadBackup;
use function FakturivaSelfHost\saveBackup;
use function FakturivaSelfHost\restoreEncryptedRevision;
use function FakturivaSelfHost\deleteAccount;

function checkHistory(bool $condition,string $error):void{
    if(!$condition)throw new RuntimeException($error);
}
$random=bin2hex(random_bytes(9));
$user='history_'.$random;
$pass='A-unique-16-character-history-secret-'.$random;
$ip='192.0.2.221';
signUp($user,$pass,$ip);
$login=FakturivaSelfHost\signIn($user,$pass,$ip);
$token=$login['token'];
try{
    $backup=static function(string $salt):string{
        // Synthetic encrypted envelope, not customer or real financial data.
        return base64_encode('FAK1'.random_bytes(44).$salt);
    };
    $first=$backup('a');$second=$backup('b');$third=$backup('c');
    $a=saveBackup($token,$first,0);
    checkHistory($a['revision']===1,'First revision failed');
    checkHistory($a['sha256']===hash('sha256',base64_decode($first)),
        'First checksum invalid');
    $b=saveBackup($token,$second,1);
    $c=saveBackup($token,$third,2);
    $loaded=loadBackup($token);
    checkHistory($loaded['revision']===3,'Bad revision counter');
    checkHistory($loaded['backup_b64']===$third,'Most recent encrypted copy lost');
    checkHistory(count($loaded['history'])===3,'Two prior versions not retained');
    checkHistory($loaded['history'][1]['revision']===2,'History sort wrong');
    try{
        restoreEncryptedRevision($token,1,2);
        throw new RuntimeException('Stale revision overwrote latest data');
    }catch(HttpFailure $e){
        checkHistory($e->statusCode===409,'Expected HTTP 409 conflict');
    }
    $restore=restoreEncryptedRevision($token,1,3);
    checkHistory($restore['revision']===4,'Restore must create NEW revision');
    $confirmed=loadBackup($token);
    checkHistory($confirmed['backup_b64']===$first,'Failed rollback to encrypted copy');
    checkHistory($confirmed['history'][0]['revision']===4,
        'Restored revision was not latest');
    checkHistory($confirmed['history'][0]['sha256']===$a['sha256'],
        'Checksum after recovery changed');
    echo "Encrypted JSON history, SHA, safe rollback and stale conflict: OK\n";
}finally{
    deleteAccount($user,$pass);
}
