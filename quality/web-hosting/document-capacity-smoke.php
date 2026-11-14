<?php
declare(strict_types=1);
// Isolated, synthetic boundary regression: never touches production accounts.
if(PHP_SAPI!=='cli')exit(2);
require_once __DIR__.'/../../web/lib_business_sync.php';

use FakturivaSelfHost\HttpFailure;
use function FakturivaWebBusiness\documentModified;

function capacityRoot():array {
    return [
        'format'=>'fakturiva-backup-v1','schema'=>8,
        'company'=>[
            ['key'=>'name','value'=>'Boundary fixture'],
            ['key'=>'address','value'=>'Synthetic address'],
            ['key'=>'currency','value'=>'EUR']
        ],
        'clients'=>[[
            'id'=>1,'name'=>'Synthetic customer','address'=>'',
            'oib'=>'','email'=>'','phone'=>'','vat_id'=>''
        ]],
        'catalog'=>[],'documents'=>[],'lines'=>[],'payments'=>[],
        'sequences'=>[],'branding_assets'=>[]
    ];
}
function capacityDoc(int $id,string $type='INVOICE',string $status='DRAFT'):array {
    return [
        'id'=>$id,'type'=>$type,'status'=>$status,
        'number'=>$status==='SENT'?'P-2026-0001':null,
        'client_id'=>1,'issue_date'=>'2026-11-14',
        'net_cents'=>100,'tax_cents'=>0,'total_cents'=>100,
        'currency_code'=>'EUR'
    ];
}
function capacityLine(int $id,int $documentId):array {
    return [
        'id'=>$id,'document_id'=>$documentId,'name'=>'Synthetic line',
        'qty_milli'=>1000,'unit_cents'=>100,'tax_rate'=>0,
        'net_cents'=>100,'tax_cents'=>0
    ];
}
function capacityInput(int $count=1):array {
    $lines=[];
    for($i=0;$i<$count;$i++)
        $lines[]=['name'=>'Synthetic line','qty'=>'1','price'=>'1.00','tax_rate'=>'0'];
    return [
        'type'=>'INVOICE','client_id'=>1,'issue_date'=>'2026-11-14',
        'due_date'=>'2026-11-30','supply_date'=>'2026-11-14',
        'notes'=>'','lines'=>$lines
    ];
}
function mustRejectCapacity(callable $operation,string $case):void {
    try {
        $operation();
    }catch(HttpFailure $exception){
        if($exception->statusCode===413)return;
        throw new RuntimeException($case.' returned HTTP '.$exception->statusCode);
    }
    throw new RuntimeException($case.' published an Android-incompatible snapshot');
}
function mustHaveCapacity(bool $condition,string $case):void {
    if(!$condition)throw new RuntimeException($case);
}

// Adding a 100001st document must fail rather than publish a broken revision.
$root=capacityRoot();
for($i=1;$i<=100000;$i++)$root['documents'][]=capacityDoc($i);
mustRejectCapacity(
    static fn()=>documentModified($root,'doc_add',capacityInput()),
    '100001st document');
mustHaveCapacity(count($root['documents'])===100000,'Rejected document changed source');
unset($root);

// Adding a new document line must not exceed Android's 100,000-line restore limit.
$root=capacityRoot();
$root['documents'][]=capacityDoc(1);
for($i=1;$i<=100000;$i++)$root['lines'][]=capacityLine($i,1);
mustRejectCapacity(
    static fn()=>documentModified($root,'doc_add',capacityInput()),
    '100001st line on draft creation');
unset($root);

// Editing replaces the old lines: check final size, not the original size.
$root=capacityRoot();
$root['documents']=[capacityDoc(1),capacityDoc(2)];
for($i=1;$i<=99999;$i++)$root['lines'][]=capacityLine($i,2);
$root['lines'][]=capacityLine(100000,1);
$edit=capacityInput(2);
$edit['id']=1;
mustRejectCapacity(
    static fn()=>documentModified($root,'doc_edit',$edit),
    'Draft edit exceeding line capacity');
$edit=capacityInput(1);
$edit['id']=1;
$updated=documentModified($root,'doc_edit',$edit);
mustHaveCapacity(count($updated['lines'])===100000,
    'Editing a draft at the exact line limit must remain possible');
unset($root,$updated,$edit);

// Quote conversion creates a new document and duplicates its lines.
$root=capacityRoot();
$root['documents'][]=capacityDoc(1,'QUOTE','SENT');
for($i=2;$i<=100000;$i++)$root['documents'][]=capacityDoc($i);
$root['lines'][]=capacityLine(1,1);
mustRejectCapacity(
    static fn()=>documentModified($root,'quote_convert',['id'=>1]),
    'Quote conversion at document capacity');
unset($root);

$root=capacityRoot();
$root['documents']=[capacityDoc(1,'QUOTE','SENT'),capacityDoc(2)];
for($i=1;$i<=99999;$i++)$root['lines'][]=capacityLine($i,2);
$root['lines'][]=capacityLine(100000,1);
mustRejectCapacity(
    static fn()=>documentModified($root,'quote_convert',['id'=>1]),
    'Quote conversion at line capacity');
unset($root);

$root=capacityRoot();
$root['documents']=[capacityDoc(1,'QUOTE','SENT'),capacityDoc(2)];
for($i=1;$i<=99998;$i++)$root['lines'][]=capacityLine($i,2);
$root['lines'][]=capacityLine(99999,1);
$converted=documentModified($root,'quote_convert',['id'=>1]);
mustHaveCapacity(count($converted['documents'])===3 &&
    count($converted['lines'])===100000 &&
    $converted['documents'][2]['status']==='DRAFT',
    'Quote conversion at exactly the line limit failed');

echo 'Document/line snapshot capacity boundaries: OK'.PHP_EOL;
