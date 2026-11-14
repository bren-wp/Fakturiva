<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(2);
$temporary=sys_get_temp_dir().'/fakturiva-business-sync-'.bin2hex(random_bytes(8));
if(!mkdir($temporary,0700,true))throw new RuntimeException('Cannot create fixture directory');
putenv('FAKTURIVA_STORAGE_ROOT='.$temporary);
require_once __DIR__.'/../../web/lib_business_sync.php';

use FakturivaSelfHost\HttpFailure;

function checkSync(bool $ok,string $reason):void {
    if(!$ok)throw new RuntimeException($reason);
}
function deniedSync(callable $task,int $code,string $reason):void{
    try{$task();throw new RuntimeException('Unexpectedly accepted: '.$reason);}
    catch(HttpFailure $e){
        checkSync($e->statusCode===$code,$reason.' status '.$e->statusCode);
    }
}
function removeFixture(string $directory):void {
    foreach(scandir($directory)?:[] as $name){
        if($name==='.'||$name==='..')continue;
        $file=$directory.'/'.$name;
        if(is_dir($file))removeFixture($file);
        elseif(!unlink($file))throw new RuntimeException('Cleanup failed');
    }
    if(!rmdir($directory))throw new RuntimeException('Cannot remove fixture');
}
try{
    $pass='Test-Fakturiva-LocalOnly-2026';
    $user='biz-sync-'.bin2hex(random_bytes(4)).'@example.invalid';
    $ip='192.0.2.44';
    $web=FakturivaSelfHost\webRegister($user,$ip,$pass);
    $api=FakturivaSelfHost\signIn($user,$pass,$ip);
    checkSync(explode('.',$api['token'])[0]===$web['id'],
        'Web and API do not share exact account');
    $root=[
        'format'=>'fakturiva-backup-v1','schema'=>8,
        'company'=>[
            ['key'=>'name','value'=>'Stari izdavatelj'],
            ['key'=>'oib','value'=>'12345678903'],
            ['key'=>'address','value'=>'Stara adresa'],
            ['key'=>'country','value'=>'HR']
        ],
        'clients'=>[['id'=>3,'name'=>'Stari klijent',
            'oib'=>'','address'=>'Izvorna adresa','email'=>'',
            'phone'=>'','vat_id'=>'']],
        'catalog'=>[],
        'documents'=>[['id'=>7,'type'=>'INVOICE',
            'number'=>'R-2026-009','client_name'=>'Stari klijent',
            'issuer_name'=>'Stari izdavatelj','total_cents'=>12500]],
        'lines'=>[],'payments'=>[],'sequences'=>[],'branding_assets'=>[]
    ];
    $backup=FakturivaWebBusiness\sealSnapshot($root,$pass);
    checkSync(substr($backup,0,4)==='FAK1'&&strlen($backup)>48,
        'FAK1 envelope incompatible with Android');
    $first=FakturivaSelfHost\saveBackup(
        $api['token'],base64_encode($backup),0);
    checkSync($first['revision']===1,'Initial Android-compatible snapshot missing');
    $probe=FakturivaSelfHost\backupStatus($api['token']);
    checkSync($probe['revision']===1 &&
        $probe['has_backup']===true &&
        !array_key_exists('backup_b64',$probe),
        'Lightweight authenticated status leaked ciphertext or wrong revision');
    deniedSync(fn()=>FakturivaSelfHost\backupStatus('invalid-token'),401,
        'Unauthenticated status disclosure');
    $fromWeb=FakturivaWebFinance\openSnapshot(
        FakturivaSelfHost\webAuthorized($web['id'],$web['token']),$pass);
    checkSync($fromWeb===$root,'Android-style payload failed PHP decrypt');

    $updated=FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,1,'profile',[
            'name'=>'Novi izdavatelj','oib'=>'12345678903',
            'address'=>'Nova adresa','city'=>'Rijeka',
            'postal_code'=>'51000','email'=>'firma@example.invalid',
            'phone'=>'+385 51 111 111']);
    checkSync($updated['revision']===2,'Profile did not increment revision');
    $server=FakturivaSelfHost\loadBackup($api['token']);
    checkSync(FakturivaSelfHost\backupStatus($api['token'])['revision']===2,
        'Android revision probe did not reflect web-side edit');
    checkSync($server['revision']===2 &&
        hash('sha256',base64_decode($server['backup_b64'],true))===$server['sha256'],
        'Android API did not expose correctly hashed web revision');
    $current=FakturivaWebFinance\openSnapshot(
        FakturivaSelfHost\webAuthorized($web['id'],$web['token']),$pass);
    checkSync($current['company'][0]['value']==='Novi izdavatelj',
        'Company name not synced');
    checkSync($current['documents']===$root['documents'],
        'Historical invoice was rewritten while changing issuer');

    deniedSync(fn()=>FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,1,'profile',[
            'name'=>'Stale','oib'=>'','address'=>'','city'=>'',
            'postal_code'=>'','email'=>'','phone'=>'']),409,
        'Stale concurrent write');
    deniedSync(fn()=>FakturivaWebBusiness\publish(
        $web['id'],$web['token'],'Wrong-business-password-2026',2,
        'client_add',['name'=>'Malicious','oib'=>'',
            'address'=>'','email'=>'','phone'=>'','vat_id'=>'']),401,
        'Wrong encryption password');

    $add=FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,2,'client_add',[
            'name'=>'Novi klijent','oib'=>'','address'=>'Zagreb',
            'email'=>'client@example.invalid','phone'=>'','vat_id'=>'']);
    checkSync($add['revision']===3,'New client revision not bumped');
    $client=FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,3,'client_edit',[
            'id'=>4,'name'=>'Izmijenjeni klijent','oib'=>'','address'=>'Rijeka',
            'email'=>'client@example.invalid','phone'=>'+385 91',
            'vat_id'=>'']);
    checkSync($client['revision']===4,'Client edit revision not bumped');

    $copy=FakturivaWebFinance\openSnapshot(
        FakturivaSelfHost\webAuthorized($web['id'],$web['token']),$pass);
    checkSync(count($copy['clients'])===2 &&
        $copy['clients'][0]['id']===3 &&
        $copy['clients'][1]['id']===4 &&
        $copy['clients'][1]['name']==='Izmijenjeni klijent',
        'Existing clients changed IDs during sync');
    checkSync($copy['documents']===$root['documents'],
        'Existing invoices were changed while saving clients');

    deniedSync(fn()=>FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,4,'client_edit',[
            'id'=>9999,'name'=>'Invalid','oib'=>'','address'=>'',
            'email'=>'','phone'=>'','vat_id'=>'']),404,'Nonexistent client');
    deniedSync(fn()=>FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,4,'client_add',[
            'name'=>'<script>','oib'=>'','address'=>'',
            'email'=>'invalid-email','phone'=>'','vat_id'=>'']),422,'Invalid email');

    $other=FakturivaSelfHost\webRegister(
        'other-'.bin2hex(random_bytes(4)).'@example.invalid',$ip,$pass);
    deniedSync(fn()=>FakturivaWebBusiness\publish(
        $web['id'],$other['token'],$pass,4,'client_add',[
            'name'=>'Cross account','oib'=>'','address'=>'',
            'email'=>'','phone'=>'','vat_id'=>'']),401,'Cross-tenant token');
    checkSync(FakturivaSelfHost\webAuthorized($other['id'],$other['token'])['backup']===null,
        'Other tenant was modified');

    // Product/service catalog is shared through the same sealed snapshot.
    $addedItem=FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,4,'catalog_add',[
            'name'=>'Web usluga','price'=>'125,50',
            'tax_rate'=>'25','kind'=>'SERVICE','unit'=>'sat']);
    checkSync($addedItem['revision']===5,'New catalog item not versioned');
    $changedItem=FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,5,'catalog_edit',[
            'id'=>1,'name'=>'Poboljšana usluga','price'=>'129.95',
            'tax_rate'=>'25','kind'=>'SERVICE','unit'=>'sat']);
    checkSync($changedItem['revision']===6,'Catalog update not versioned');
    $afterProduct=FakturivaWebFinance\openSnapshot(
        FakturivaSelfHost\webAuthorized($web['id'],$web['token']),$pass);
    checkSync(count($afterProduct['catalog'])===1 &&
        $afterProduct['catalog'][0]['id']===1 &&
        $afterProduct['catalog'][0]['price_cents']===12995 &&
        $afterProduct['catalog'][0]['name']==='Poboljšana usluga' &&
        $afterProduct['documents']===$root['documents'],
        'Catalog update modified existing invoice or lost exact cents');
    deniedSync(fn()=>FakturivaWebBusiness\publish(
        $web['id'],$web['token'],$pass,6,'catalog_add',[
            'name'=>'Bad price','price'=>'2.999',
            'tax_rate'=>'25','kind'=>'SERVICE','unit'=>'sat']),422,
        'Fractional-cent price accepted');

    // Legacy schema 1/2 restores derive historical issuer/client fields.
    // Editing the profile before Android migration would rewrite issued
    // document identities. Fail closed and keep original encrypted bytes.
    foreach([1,2] as $legacySchema){
        $legacyName='legacy-'.$legacySchema.'-'.
            bin2hex(random_bytes(4)).'@example.invalid';
        $legacy=FakturivaSelfHost\webRegister($legacyName,$ip,$pass);
        $legacyApi=FakturivaSelfHost\signIn($legacyName,$pass,$ip);
        $oldRoot=$root;
        $oldRoot['schema']=$legacySchema;
        $sealedOld=FakturivaWebBusiness\sealSnapshot($oldRoot,$pass);
        FakturivaSelfHost\saveBackup($legacyApi['token'],
            base64_encode($sealedOld),0);
        deniedSync(fn()=>FakturivaWebBusiness\publish(
            $legacy['id'],$legacy['token'],$pass,1,'profile',[
                'name'=>'Risky overwrite','oib'=>'','address'=>'Other address',
                'city'=>'Rijeka','postal_code'=>'51000',
                'email'=>'change@example.invalid','phone'=>'']),422,
            'Legacy schema '.$legacySchema.' cannot edit issuer snapshots');
        $still=FakturivaSelfHost\loadBackup($legacyApi['token']);
        checkSync($still['revision']===1 &&
            hash_equals($still['backup_b64'],base64_encode($sealedOld)),
            'Rejected legacy web change unexpectedly replaced backup');
    }
    $history=FakturivaSelfHost\loadBackup($api['token'])['history'];
    checkSync(count($history)>=2 && $history[0]['revision']===6 &&
        $history[1]['revision']===5,'Encrypted revision history was not preserved');
    echo "Web and Android FAK1 sync, CAS, history, isolation and immutable invoices: OK\n";
}finally{
    removeFixture($temporary);
}
