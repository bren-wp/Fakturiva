<?php
declare(strict_types=1);
namespace FakturivaWebBusiness;

require_once __DIR__.'/lib_finance.php';

use FakturivaSelfHost\HttpFailure;

/**
 * Web writes are complete, FAK1-encrypted SQLite snapshot revisions, not
 * parallel untracked JSON records. No server-side plaintext is persisted.
 * Every write is authenticated and serialized with the Android API lock.
 */
function requiredField(array $data,string $name,int $max,bool $required=false):string {
    $value=$data[$name]??'';
    if(!is_string($value) || strpos($value,chr(0))!==false ||
       !preg_match('//u',$value))throw new HttpFailure(422,'Neispravno polje: '.$name);
    $value=trim($value);
    if(strlen($value)>$max || ($required&&$value===''))
        throw new HttpFailure(422,'Neispravna duljina polja: '.$name);
    return $value;
}

/** The same Croatian ISO 7064 MOD 11,10 OIB as Android Oib.valid(). */
function validOib(string $raw):bool {
    if(!preg_match('/^[0-9]{11}$/D',$raw))return false;
    $a=10;
    for($i=0;$i<10;$i++){
        $a=($a+(int)$raw[$i])%10;
        if($a===0)$a=10;
        $a=($a*2)%11;
    }
    return (11-$a)%10===(int)$raw[10];
}

/** Strict 2-decimal monetary parser; never store PHP floating point prices. */
function priceInCents(mixed $value):int {
    if(!is_string($value)||!preg_match('/^([0-9]{1,10})(?:[.,]([0-9]{1,2}))?$/D',$value,$parts))
        throw new HttpFailure(422,'Cijena mora imati najviše dvije decimale.');
    $cents=(int)$parts[1]*100 + (int)str_pad($parts[2]??'0',2,'0');
    if($cents<0 || $cents>999999999999)
        throw new HttpFailure(422,'Cijena je izvan dopuštenog raspona.');
    return $cents;
}

function modified(array $root,string $operation,array $input):array {
    if(($root['format']??null)!=='fakturiva-backup-v1' ||
       !is_int($root['schema']??null) ||
       $root['schema']!==8 ||
       !isset($root['company'],$root['clients'],$root['documents'],
               $root['lines'],$root['payments'],$root['sequences'],$root['catalog']) ||
       !is_array($root['company']) || !is_array($root['clients']) ||
       !array_is_list($root['company']) || !array_is_list($root['clients']))
        throw new HttpFailure(422,
            'Za web uređivanje potrebna je baza sheme 8. Starije podatke najprije '.
            'otvorite i sinkronizirajte u novoj Android aplikaciji; ništa nije promijenjeno.');
    if($operation==='profile'){
        $fields=[
            'name'=>requiredField($input,'name',160,true),
            'oib'=>requiredField($input,'oib',40),
            'address'=>requiredField($input,'address',320),
            'city'=>requiredField($input,'city',160),
            'postal_code'=>requiredField($input,'postal_code',40),
            'email'=>requiredField($input,'email',254),
            'phone'=>requiredField($input,'phone',64)
        ];
        if($fields['email']!=='' && filter_var($fields['email'],FILTER_VALIDATE_EMAIL)===false)
            throw new HttpFailure(422,'Nevaljana e-adresa tvrtke.');
        // Match Android BusinessProfileRules: a Croatian company profile
        // must have an OIB and location before it can be persisted.
        $country='';
        foreach($root['company'] as $existing)
            if(is_array($existing)&&($existing['key']??'')==='country')
                $country=strtoupper(trim((string)($existing['value']??'')));
        if(in_array($country,['HR','HRVATSKA','CROATIA'],true)){
            if(!validOib($fields['oib']))
                throw new HttpFailure(422,'OIB mora imati 11 ispravnih znamenki.');
            if($fields['address']==='' || $fields['city']==='')
                throw new HttpFailure(422,'Adresa i grad izdavatelja obvezni su.');
        }
        // Leave current country/legal_form untouched and never revise the
        // historical immutable issuer snapshot on an issued document.
        $found=[];
        foreach($root['company'] as &$row){
            if(!is_array($row) || !is_string($row['key']??null))
                throw new HttpFailure(422,'Neispravni podatci tvrtke.');
            $key=$row['key'];
            if(array_key_exists($key,$fields)){
                if(isset($found[$key]))throw new HttpFailure(422,'Duplicirano polje tvrtke.');
                $row['value']=$fields[$key];
                $found[$key]=true;
            }
        }
        unset($row);
        foreach($fields as $key=>$value)
            if(!isset($found[$key]))$root['company'][]=['key'=>$key,'value'=>$value];
    }elseif($operation==='client_add' || $operation==='client_edit'){
        $fields=[
            'name'=>requiredField($input,'name',200,true),
            'oib'=>requiredField($input,'oib',40),
            'address'=>requiredField($input,'address',320),
            'email'=>requiredField($input,'email',254),
            'phone'=>requiredField($input,'phone',64),
            'vat_id'=>requiredField($input,'vat_id',50)
        ];
        if($fields['email']!=='' && filter_var($fields['email'],FILTER_VALIDATE_EMAIL)===false)
            throw new HttpFailure(422,'Nevaljana e-adresa klijenta.');
        $largest=0;
        $ids=[];
        foreach($root['clients'] as $row){
            if(!is_array($row) || !is_int($row['id']??null) ||
               $row['id']<1 || $row['id']>2147483646 || isset($ids[$row['id']]))
                throw new HttpFailure(422,'Neispravni identifikatori klijenata.');
            $ids[$row['id']]=true;
            $largest=max($largest,$row['id']);
        }
        if($operation==='client_add'){
            if($largest>=2147483646 || count($root['clients'])>=100000)
                throw new HttpFailure(413,'Previše klijenata.');
            $root['clients'][]=['id'=>$largest+1]+$fields;
        }else{
            $id=$input['id']??null;
            if(!is_int($id) || $id<1 || !isset($ids[$id]))
                throw new HttpFailure(404,'Klijent nije pronađen.');
            foreach($root['clients'] as &$row){
                if($row['id']===$id){
                    foreach($fields as $key=>$value)$row[$key]=$value;
                    break;
                }
            }
            unset($row);
        }
    }elseif($operation==='catalog_add'||$operation==='catalog_edit'){
        if(!is_array($root['catalog']) || !array_is_list($root['catalog']))
            throw new HttpFailure(422,'Neispravan katalog.');
        $name=requiredField($input,'name',200,true);
        $unit=requiredField($input,'unit',20,true);
        $kind=requiredField($input,'kind',20,true);
        if(!preg_match('/^[\\p{L}0-9 ._-]{1,20}$/uD',$unit) ||
           !in_array($kind,['SERVICE','PRODUCT','UNSPECIFIED'],true))
            throw new HttpFailure(422,'Neispravna vrsta ili mjera artikla.');
        $price=priceInCents($input['price']??null);
        $rate=filter_var($input['tax_rate']??null,FILTER_VALIDATE_INT);
        if($rate===false||$rate===null||
           !($rate>=0&&$rate<=100) &&
           !($rate>=10001&&$rate<=20000))
            throw new HttpFailure(422,'Neispravna stopa PDV-a.');
        $fields=['name'=>$name,'price_cents'=>$price,
            'tax_rate'=>$rate,'kind'=>$kind,'unit'=>$unit];
        $ids=[];$largest=0;
        foreach($root['catalog'] as $row){
            if(!is_array($row)||!is_int($row['id']??null)||
               $row['id']<1||$row['id']>2147483646||isset($ids[$row['id']]))
                throw new HttpFailure(422,'Neispravni identifikatori kataloga.');
            $ids[$row['id']]=true;
            $largest=max($largest,$row['id']);
        }
        if($operation==='catalog_add'){
            if($largest>=2147483646||count($root['catalog'])>=100000)
                throw new HttpFailure(413,'Katalog je prevelik.');
            $currency='EUR';
            foreach($root['company'] as $row)
                if(is_array($row)&&($row['key']??null)==='currency'&&
                   is_string($row['value']??null))
                    $currency=strtoupper(trim($row['value']));
            if(!in_array($currency,['EUR','USD','GBP','CHF','CAD','AUD','NZD',
                'SGD','HKD','CNY','INR','BRL','MXN','ZAR','PLN','CZK','SEK',
                'NOK','DKK','RON','HUF','BGN','TRY','ILS','THB','MYR',
                'PHP','TWD'],true))$currency='EUR';
            $root['catalog'][]=['id'=>$largest+1]+$fields+
                ['currency_code'=>$currency];
        }else{
            $id=$input['id']??null;
            if(!is_int($id)||!isset($ids[$id]))
                throw new HttpFailure(404,'Artikl ili usluga nisu pronađeni.');
            foreach($root['catalog'] as &$row){
                if($row['id']===$id){
                    foreach($fields as $key=>$value)$row[$key]=$value;
                    break;
                }
            }
            unset($row);
        }
    }else{
        throw new HttpFailure(422,'Nepoznata poslovna radnja.');
    }
    return $root;
}

/** Re-encrypt exactly as Android SecureBackup: FAK1/salt/nonce/GCM tag. */
function sealSnapshot(array $root,string $passphrase):string {
    $json=json_encode($root,JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);
    if(strlen($json)>\FakturivaSelfHost\MAX_BACKUP_BYTES-48)
        throw new HttpFailure(413,'Preslika je prevelika za hosting.');
    $salt=random_bytes(16);
    $nonce=random_bytes(12);
    $key=hash_pbkdf2('sha1',$passphrase,$salt,210000,32,true);
    $tag='';
    $cipher=openssl_encrypt($json,'aes-256-gcm',$key,
        OPENSSL_RAW_DATA,$nonce,$tag,'FAK1',16);
    unset($key,$json);
    if(!is_string($cipher)||strlen($tag)!==16)
        throw new \RuntimeException('Šifriranje nije uspjelo.');
    return 'FAK1'.$salt.$nonce.$cipher.$tag;
}

/** Atomically write one web mutation under the same revision lock as Android. */
function publish(string $id,string $webToken,string $passphrase,int $expected,
                 string $operation,array $input):array {
    if($expected<1||$expected>2147483645)
        throw new HttpFailure(422,'Nevaljana očekivana revizija.');
    return \FakturivaSelfHost\transaction(
        static function()use($id,$webToken,$passphrase,$expected,$operation,$input):array {
            $account=\FakturivaSelfHost\webAuthorized($id,$webToken);
            if((int)($account['revision']??-1)!==$expected)
                throw new HttpFailure(409,
                    'Poslovni podatci izmijenjeni su na drugom uređaju. Ponovno ih učitajte.');
            $root=\FakturivaWebFinance\openSnapshot($account,$passphrase);
            $root=modified($root,$operation,$input);
            $sealed=sealSnapshot($root,$passphrase);
            if(strlen($sealed)>\FakturivaSelfHost\MAX_BACKUP_BYTES)
                throw new HttpFailure(413,'Preslika je prevelika.');
            $b64=base64_encode($sealed);
            $sha=hash('sha256',$sealed);
            \FakturivaSelfHost\appendPreviousBackup($account,strlen($b64));
            $account['revision']=$expected+1;
            $account['backup']=$b64;
            $account['saved_at']=gmdate('c');
            \FakturivaSelfHost\writeRecord(
                \FakturivaSelfHost\accountPath($id),$account);
            return ['revision'=>$account['revision'],'sha256'=>$sha,
                'saved_at'=>$account['saved_at']];
        }
    );
}
