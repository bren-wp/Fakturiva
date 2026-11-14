<?php
declare(strict_types=1);
// Synthetic isolated PHP/server storage fixture; never uses a production account.
if(PHP_SAPI!=='cli')exit(2);
$originalStorage=getenv('FAKTURIVA_STORAGE_ROOT');
$fixture=sys_get_temp_dir().'/fakturiva-atomic-row-cap-'.bin2hex(random_bytes(10));
if(!mkdir($fixture,0700,true))throw new RuntimeException('Fixture unavailable');
putenv('FAKTURIVA_STORAGE_ROOT='.$fixture);
require_once __DIR__.'/../../web/lib_business_sync.php';

use FakturivaSelfHost\HttpFailure;

function atomicRoot():array {
    return [
        'format'=>'fakturiva-backup-v1','schema'=>8,
        'company'=>[['key'=>'name','value'=>'Synthetic issuer'],
            ['key'=>'address','value'=>'Fictional street'],
            ['key'=>'currency','value'=>'EUR']],
        'clients'=>[['id'=>1,'name'=>'Synthetic client','address'=>'',
            'oib'=>'','email'=>'','phone'=>'','vat_id'=>'']],
        'catalog'=>[],'documents'=>[],'lines'=>[],
        'payments'=>[],'sequences'=>[],'branding_assets'=>[]
    ];
}
function atomicForm():array {
    return ['type'=>'INVOICE','client_id'=>1,
        'issue_date'=>'2026-11-14','due_date'=>'2026-11-30',
        'supply_date'=>'2026-11-14','notes'=>'',
        'lines'=>[['name'=>'Test line','qty'=>'1','price'=>'1.00','tax_rate'=>'0']]];
}
function atomicAssert(bool $test,string $message):void {
    if(!$test)throw new RuntimeException($message);
}
function verifyAtomicReject(array $root,string $operation,array $input,string $label):void {
    $password='Synthetic-Atomic-Password-2026';
    $user='atomic-'.bin2hex(random_bytes(6)).'@example.invalid';
    $ip='192.0.2.188';
    $web=FakturivaSelfHost\webRegister($user,$ip,$password);
    $android=FakturivaSelfHost\signIn($user,$password,$ip);
    $sealed=FakturivaWebBusiness\sealSnapshot($root,$password);
    $saved=FakturivaSelfHost\saveBackup($android['token'],base64_encode($sealed),0);
    atomicAssert($saved['revision']===1,$label.': initial backup revision');
    // Compare both on-disk ciphertext and externally observable Android state.
    $path=FakturivaSelfHost\accountPath($web['id']);
    $recordBefore=file_get_contents($path);
    $apiBefore=FakturivaSelfHost\loadBackup($android['token']);
    $historyBefore=FakturivaSelfHost\encryptedRevisionList(
        FakturivaSelfHost\authorized($android['token']));
    atomicAssert(is_string($recordBefore),$label.': fixture record missing');
    try {
        FakturivaWebBusiness\publish($web['id'],$web['token'],$password,1,$operation,$input);
        throw new RuntimeException($label.': rejected write was accepted');
    }catch(HttpFailure $failure){
        atomicAssert($failure->statusCode===413,
            $label.': expected HTTP 413, got '.$failure->statusCode);
    }
    $recordAfter=file_get_contents($path);
    $apiAfter=FakturivaSelfHost\loadBackup($android['token']);
    $historyAfter=FakturivaSelfHost\encryptedRevisionList(
        FakturivaSelfHost\authorized($android['token']));
    $status=FakturivaSelfHost\backupStatus($android['token']);
    atomicAssert($recordAfter===$recordBefore,$label.': encrypted account rewritten');
    atomicAssert($apiAfter===$apiBefore,$label.': Android load changed');
    atomicAssert($historyAfter===$historyBefore,$label.': history mutated');
    atomicAssert($status['revision']===1,$label.': revision advanced');
}
function atomicRemoveTree(string $path):void {
    foreach(scandir($path)?:[] as $entry){
        if($entry==='.'||$entry==='..')continue;
        $target=$path.'/'.$entry;
        if(is_dir($target))atomicRemoveTree($target);
        elseif(!unlink($target))throw new RuntimeException('Fixture cleanup failed');
    }
    if(!rmdir($path))throw new RuntimeException('Fixture directory cleanup failed');
}
try {
    $root=atomicRoot();
    for($i=1;$i<=100000;$i++)
        $root['documents'][]=['id'=>$i,'type'=>'INVOICE','status'=>'DRAFT'];
    verifyAtomicReject($root,'doc_add',atomicForm(),'Document capacity');
    unset($root);

    $root=atomicRoot();
    $root['documents'][]=['id'=>1,'type'=>'INVOICE','status'=>'DRAFT'];
    for($i=1;$i<=100000;$i++)
        $root['lines'][]=['id'=>$i,'document_id'=>1,
            'name'=>'Synthetic','qty_milli'=>1000,'unit_cents'=>100,
            'tax_rate'=>0,'net_cents'=>100,'tax_cents'=>0];
    verifyAtomicReject($root,'doc_add',atomicForm(),'Line capacity');
    unset($root);

    $root=atomicRoot();
    $root['documents'][]=['id'=>1,'type'=>'QUOTE','status'=>'SENT',
        'number'=>'P-2026-0001','client_id'=>1,
        'issue_date'=>'2026-11-14','total_cents'=>10000000,
        'net_cents'=>10000000,'tax_cents'=>0,'currency_code'=>'EUR'];
    for($i=1;$i<=100000;$i++)
        $root['lines'][]=['id'=>$i,'document_id'=>1,
            'name'=>'Synthetic','qty_milli'=>1000,'unit_cents'=>100,
            'tax_rate'=>0,'net_cents'=>100,'tax_cents'=>0];
    verifyAtomicReject($root,'quote_convert',['id'=>1],'Quote conversion line capacity');
    unset($root);

    echo 'Published FAK1 snapshot, revision and history stay intact on capacity rejects: OK'.PHP_EOL;
}finally {
    putenv($originalStorage===false?'FAKTURIVA_STORAGE_ROOT':
        'FAKTURIVA_STORAGE_ROOT='.$originalStorage);
    atomicRemoveTree($fixture);
}
