<?php
declare(strict_types=1);
namespace FakturivaWebBusiness;

use FakturivaSelfHost\HttpFailure;

/**
 * Derive payment balances from the same schema-8 snapshot Android restores.
 * Never trust a browser-supplied balance, invoice number, amount or currency.
 */
function payableInvoices(array $root):array {
    $documents=$root['documents']??null;
    $payments=$root['payments']??null;
    if(!is_array($documents)||!array_is_list($documents)||
       !is_array($payments)||!array_is_list($payments)||
       count($documents)>100000||count($payments)>100000)
        throw new HttpFailure(422,'Neispravan popis računa ili uplata.');
    $allIds=[];$invoices=[];
    foreach($documents as $row){
        if(!is_array($row)||!is_int($row['id']??null)||$row['id']<1||
           isset($allIds[$row['id']]))
            throw new HttpFailure(422,'Neispravni identifikatori dokumenata.');
        $allIds[$row['id']]=true;
        if(($row['type']??null)!=='INVOICE')continue;
        $total=$row['total_cents']??null;
        if(!is_int($total)||$total<0)
            throw new HttpFailure(422,'Neispravan iznos računa.');
        $invoices[$row['id']]=['document'=>$row,'paid'=>0,
            'outstanding'=>$total];
    }
    $paymentIds=[];
    foreach($payments as $row){
        if(!is_array($row)||!is_int($row['id']??null)||$row['id']<1||
           $row['id']>2147483646||isset($paymentIds[$row['id']])||
           !is_int($row['document_id']??null)||
           !isset($invoices[$row['document_id']])||
           !is_int($row['amount_cents']??null)||$row['amount_cents']<=0)
            throw new HttpFailure(422,'Neispravna povijest uplata.');
        $paymentIds[$row['id']]=true;
        $docId=$row['document_id'];
        if($row['amount_cents']>$invoices[$docId]['outstanding'])
            throw new HttpFailure(422,'Uplate premašuju iznos računa.');
        $invoices[$docId]['paid']+=$row['amount_cents'];
        $invoices[$docId]['outstanding']-=$row['amount_cents'];
    }
    $available=[];
    foreach($invoices as $id=>$invoice){
        $status=$invoice['document']['status']??'';
        if($status==='PAID' && $invoice['outstanding']!==0)
            throw new HttpFailure(422,'Plaćeni račun nema potpunu evidenciju uplata.');
        if($status==='ISSUED' && $invoice['outstanding']>0)
            $available[$id]=$invoice;
    }
    uasort($available,static function($a,$b){
        return strcmp((string)($b['document']['issue_date']??''),
            (string)($a['document']['issue_date']??''));
    });
    return $available;
}

/** Add an actual receipt, never a simulated invoice payment or tax event. */
function paymentModified(array $root,array $input):array {
    $id=$input['document_id']??null;
    if(!is_int($id)||$id<1)
        throw new HttpFailure(422,'Odaberite valjan izdani račun.');
    $available=payableInvoices($root);
    if(!isset($available[$id]))
        throw new HttpFailure(422,'Račun nije izdan ili nema nepodmirenog iznosa.');
    $amount=priceInCents($input['amount']??null);
    if($amount<=0||$amount>$available[$id]['outstanding'])
        throw new HttpFailure(422,'Uplata mora biti pozitivna i ne smije premašiti dug.');
    $date=requiredField($input,'paid_on',10,true);
    if(!preg_match('/^\d{4}-\d{2}-\d{2}$/D',$date)||
       !checkdate((int)substr($date,5,2),(int)substr($date,8,2),
           (int)substr($date,0,4)))
        throw new HttpFailure(422,'Nevaljan datum uplate.');
    $channel=requiredField($input,'channel',12,true);
    if(!in_array($channel,['CASH','BANK','CARD','CHEQUE'],true))
        throw new HttpFailure(422,'Odaberite stvarni način naplate.');
    $largest=0;
    foreach($root['payments'] as $payment)
        $largest=max($largest,$payment['id']);
    if($largest>=2147483646||count($root['payments'])>=100000)
        throw new HttpFailure(413,'Dosegnut je dopušten broj uplata.');
    $unit=$available[$id]['document']['issuer_business_unit']??'';
    if(!is_string($unit)||strlen($unit)>64||!preg_match('//u',$unit))
        throw new HttpFailure(422,'Neispravna poslovna jedinica računa.');
    $unit=trim($unit);
    $root['payments'][]=[
        'id'=>$largest+1,'document_id'=>$id,'amount_cents'=>$amount,
        'paid_on'=>$date,'channel'=>$channel,
        'business_unit'=>$unit===''?'PP1':$unit
    ];
    if($amount===$available[$id]['outstanding']){
        foreach($root['documents'] as &$document){
            if($document['id']===$id){$document['status']='PAID';break;}
        }
        unset($document);
    }
    return $root;
}
