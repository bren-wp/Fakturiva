<?php
declare(strict_types=1);
namespace FakturivaWebTax;
require_once __DIR__.'/lib_finance.php';
use FakturivaSelfHost\HttpFailure;

/** Same 2025–2026 euro-cent bracket rules as Android. */
const BOUNDS=[1130000,1530000,1990000,3060000,4000000,5000000,6000000];
const INCOME=[169500,229500,298500,459000,600000,750000,900000];
const ANNUAL_TAX=[20340,27540,35820,55080,72000,90000,108000];

function company(array $root):array{
    $map=[];
    foreach($root['company']??[] as $row){
        if(is_array($row)&&is_string($row['key']??null))
            $map[$row['key']]=(string)($row['value']??'');
    }
    return $map;
}
function date(string $value):string{
    $d=\DateTimeImmutable::createFromFormat('!Y-m-d',$value,new \DateTimeZone('UTC'));
    $errors=\DateTimeImmutable::getLastErrors();
    if(!$d || ($errors!==false&&($errors['error_count']>0||$errors['warning_count']>0))||
       $d->format('Y-m-d')!==$value)
        throw new HttpFailure(422,'Datumi moraju biti u obliku GGGG-MM-DD');
    return $value;
}
function validateOib(string $number):bool{
    if(!preg_match('/^\d{11}$/D',$number))return false;
    $n=10;
    for($i=0;$i<10;$i++){
        $n=($n+(int)$number[$i])%10;
        if($n===0)$n=10;
        $n=($n*2)%11;
    }
    return ((11-$n)%10)===(int)$number[10];
}
function validateBusiness(array $business):string{
    $country=strtoupper(trim((string)($business['country']??'')));
    if(!in_array($country,['HR','HRVATSKA','CROATIA'],true))
        throw new HttpFailure(422,'Ovaj obrazac vrijedi za Hrvatsku.');
    $model=(string)($business['hr_tax_model']??'OTHER');
    if(!in_array($model,['PAUSAL','DOHODAK'],true))
        throw new HttpFailure(422,'Odaberite odgovarajući porezni status obrta.');
    foreach(['name','hr_tax_owner','hr_tax_activity','hr_tax_residence',
            'hr_tax_place','hr_tax_nkd'] as $field)
        if(trim((string)($business[$field]??''))==='')
            throw new HttpFailure(422,'Nedostaje obvezan podatak: '.$field);
    if(!validateOib((string)($business['oib']??'')))
        throw new HttpFailure(422,'Porezni obveznik nema valjan hrvatski OIB.');
    return $model;
}
function book(array $root,string $start,string $end,string $unit='SVE'):array{
    date($start);date($end);
    if($start>$end)throw new HttpFailure(422,'Razdoblje od mora biti prije razdoblja do');
    $profile=company($root);
    $model=validateBusiness($profile);
    $docs=[];
    foreach($root['documents']??[] as $document){
        if(is_array($document)&&($document['type']??'')==='INVOICE'&&
            !in_array((string)($document['status']??''),['DRAFT','VOID'],true)){
            $docs[(string)($document['id']??'')]=$document;
        }
    }
    $rows=[];$cash=0;$noncash=0;
    foreach($root['payments']??[] as $payment){
        if(!is_array($payment))continue;
        $when=(string)($payment['paid_on']??'');
        if($when<$start||$when>$end)continue;
        date($when);
        $document=$docs[(string)($payment['document_id']??'')]??null;
        if($document===null)
            throw new HttpFailure(422,'U evidenciji postoji uplata bez valjanog izdanog računa.');
        if((string)($document['currency_code']??'EUR')!=='EUR')
            throw new HttpFailure(422,'U odabranom razdoblju postoji neobračunata druga valuta.');
        $place=(string)($payment['business_unit']??'PP1');
        if($unit!=='SVE'&&$unit!==$place)continue;
        $channel=(string)($payment['channel']??'UNKNOWN');
        $amount=(int)($payment['amount_cents']??0);
        if($amount<=0||$amount>100000000000)
            throw new HttpFailure(422,'Neispravan iznos uplate');
        if(!in_array($channel,['CASH','CHEQUE','CARD','BANK'],true))
            throw new HttpFailure(422,'Postoji uplata bez potvrđenog načina naplate.');
        $c=in_array($channel,['CASH','CHEQUE'],true)?$amount:0;
        $n=in_array($channel,['BANK','CARD'],true)?$amount:0;
        if($model==='DOHODAK'&&$n>0)continue;
        $cash+=$c;$noncash+=$n;
        $rows[]=[
            'date'=>$when,'number'=>(string)($document['number']??''),
            'id'=>(int)($payment['id']??0),'unit'=>$place,
            'cash'=>$c,'noncash'=>$n,'total'=>$c+$n,
            'description'=>'Naplata računa '.(string)($document['number']??'')
        ];
    }
    usort($rows,static fn($a,$b)=>strcmp($a['date'],$b['date'])
        ?:($a['id']<=>$b['id']));
    return ['from'=>$start,'to'=>$end,'model'=>$model,'unit'=>$unit,
        'rows'=>$rows,'cash'=>$cash,'noncash'=>$noncash,'total'=>$cash+$noncash,
        'business'=>$profile];
}
function roundedDiv(int $numerator,int $denominator):int{
    if($numerator<0||$denominator<=0)throw new HttpFailure(422,'Nevaljan obračun');
    return intdiv($numerator+intdiv($denominator,2),$denominator);
}
function months(int $year,string $from,string $to):int{
    date($from);date($to);
    if($from>$to||(int)substr($from,0,4)!==$year||
        (int)substr($to,0,4)!==$year)
        throw new HttpFailure(422,'Odaberite valjano razdoblje unutar porezne godine');
    $count=(int)substr($to,5,2)-(int)substr($from,5,2)+1;
    if((int)substr($from,8,2)>1&&$count>1)$count--;
    return $count;
}
function paidCents(string $value):int{
    $s=str_replace([' ','€'],'',trim($value));
    if(str_contains($s,','))$s=str_replace(',','.',str_replace('.','',$s));
    if(!preg_match('/^\d{1,10}(\.\d{1,2})?$/D',$s))
        throw new HttpFailure(422,'Uplaćeni porez mora biti valjan EUR iznos.');
    $parts=explode('.',$s,2);
    return ((int)$parts[0])*100+(int)str_pad($parts[1]??'',2,'0');
}
function posd(array $root,int $year,string $from,string $to,
              string $paidTax):array{
    if($year!==2025&&$year!==2026)
        throw new HttpFailure(422,'PO-SD razredi potvrđeni su samo za 2025. i 2026.');
    $profile=company($root);
    if(validateBusiness($profile)!=='PAUSAL')
        throw new HttpFailure(422,'PO-SD je namijenjen paušalnom obvezniku.');
    $periods=months($year,$from,$to);
    $kpr=book($root,$year.'-01-01',$year.'-12-31');
    $annual=roundedDiv($kpr['total']*12,$periods);
    if($annual>6000000)throw new HttpFailure(422,
        'Godišnji preračunati primitci prelaze 60.000 EUR.');
    $bracket=0;
    while($bracket<6&&$annual>BOUNDS[$bracket])$bracket++;
    $income=roundedDiv(INCOME[$bracket]*$periods,12);
    $tax=roundedDiv(ANNUAL_TAX[$bracket]*$periods,12);
    $reduction=($profile['hr_tax_special']??'')==='yes'?
        roundedDiv($tax*75,100):0;
    $already=paidCents($paidTax);
    return ['year'=>$year,'from'=>$from,'to'=>$to,'months'=>$periods,
        'bracket'=>$bracket+1,'annualized'=>$annual,
        'income'=>$income,'assessedTax'=>$tax,'reduction'=>$reduction,
        'tax'=>$tax-$reduction,'paid'=>$already,
        'difference'=>$tax-$reduction-$already,
        'book'=>$kpr,'business'=>$profile];
}
function workingXml(array $report):string{
    $esc=static fn(string $x):string=>htmlspecialchars($x,
        ENT_XML1|ENT_QUOTES,'UTF-8');
    $b=$report['business'];
    $fields=[
        'OIB'=>(string)($b['oib']??''),
        'PorezniObveznik'=>(string)($b['hr_tax_owner']??''),
        'RazdobljeOd'=>(string)$report['from'],
        'RazdobljeDo'=>(string)$report['to'],
        'BrojMjeseci'=>(string)$report['months'],
        'UkupniPrimiciEUR'=>number_format($report['book']['total']/100,2,'.',''),
        'GodišnjiPaušalniPorezEUR'=>number_format($report['assessedTax']/100,2,'.',''),
        'PlaćeniPorezEUR'=>number_format($report['paid']/100,2,'.','')
    ];
    $xml='<?xml version="1.0" encoding="UTF-8"?>'."\n";
    $xml.='<FakturivaPOSD status="RADNI-PODACI-NIJE-ZA-UVOZ-U-EPOREZNU"'.
        ' godina="'.$report['year'].'">'."\n";
    foreach($fields as $tag=>$value)$xml.="  <".$tag.">".$esc($value)."</".$tag.">\n";
    return $xml."</FakturivaPOSD>\n";
}
