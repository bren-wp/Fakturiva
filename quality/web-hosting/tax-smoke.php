<?php
declare(strict_types=1);
require_once __DIR__.'/../../web/lib_tax.php';
use FakturivaSelfHost\HttpFailure;

function verifyTax(bool $condition,string $text):void{
    if(!$condition)throw new RuntimeException($text);
}
function sampleOib():string{
    $stem='1234567890';$n=10;
    for($i=0;$i<10;$i++){
        $n=($n+(int)$stem[$i])%10;
        if($n===0)$n=10;
        $n=($n*2)%11;
    }
    return $stem.((11-$n)%10);
}
$company=[
    'country'=>'HR','hr_tax_model'=>'PAUSAL','name'=>'Ogledni obrt',
    'hr_tax_owner'=>'Primjer Obveznik','hr_tax_activity'=>'Računalne usluge',
    'hr_tax_residence'=>'Ogledna ulica 1','hr_tax_place'=>'Ogledna ulica 1',
    'hr_tax_nkd'=>'62.10.0','oib'=>sampleOib()
];
$rows=[];
foreach($company as $key=>$value)$rows[]=['key'=>$key,'value'=>$value];
$base=[
    'company'=>$rows,
    'documents'=>[['id'=>1,'type'=>'INVOICE','status'=>'PAID',
        'number'=>'R-2026-0001','currency_code'=>'EUR']],
    'payments'=>[
        ['id'=>1,'document_id'=>1,'paid_on'=>'2026-12-30',
            'amount_cents'=>10000,'channel'=>'CASH','business_unit'=>'PP1'],
        ['id'=>2,'document_id'=>1,'paid_on'=>'2026-12-31',
            'amount_cents'=>20000,'channel'=>'BANK','business_unit'=>'PP1'],
        ['id'=>3,'document_id'=>1,'paid_on'=>'2027-01-02',
            'amount_cents'=>5000,'channel'=>'CARD','business_unit'=>'PP1']
    ],
    'clients'=>[]
];
$kpr=FakturivaWebTax\book($base,'2026-01-01','2026-12-31','PP1');
verifyTax($kpr['cash']===10000&&$kpr['noncash']===20000&&
    $kpr['total']===30000&&count($kpr['rows'])===2,
    'Cash-basis/partial payments are incorrect');
$posd=FakturivaWebTax\posd($base,2026,
    '2026-01-01','2026-12-31','0,00');
verifyTax($posd['months']===12&&$posd['tax']===20340&&
    $posd['income']===169500&&$posd['bracket']===1,
    'PO-SD annual tax mismatch');
$xml=FakturivaWebTax\workingXml($posd);
verifyTax(str_contains($xml,'NIJE-ZA-UVOZ-U-EPOREZNU')&&
    str_contains($xml,'300.00'),'XML must be labelled as working data');
$other=$base;
$other['company'][1]['value']='DOHODAK';
$kpr=FakturivaWebTax\book($other,'2026-01-01','2026-12-31','PP1');
verifyTax($kpr['cash']===10000&&$kpr['noncash']===0,
    'Non-paušal KPR must omit non-cash entries');
try{
    FakturivaWebTax\posd($other,2026,'2026-01-01','2026-12-31','0,00');
    throw new RuntimeException('Wrong taxpayer was allowed PO-SD');
}catch(HttpFailure $e){verifyTax($e->statusCode===422,'Wrong PO-SD error');}
$invalid=$base;
$invalid['payments'][0]['channel']='UNKNOWN';
try{
    FakturivaWebTax\book($invalid,'2026-01-01','2026-12-31','PP1');
    throw new RuntimeException('Unknown payment mode silently accepted');
}catch(HttpFailure $e){verifyTax($e->statusCode===422,'Wrong unknown-channel error');}
try{
    FakturivaWebTax\posd($base,2027,'2027-01-01','2027-12-31','0,00');
    throw new RuntimeException('Unverified 2027 brackets accepted');
}catch(HttpFailure $e){verifyTax($e->statusCode===422,'Wrong year guard');}
echo "Fakturiva server-side KPR/PO-SD calculations, year, partial payments and XML draft: OK\n";
