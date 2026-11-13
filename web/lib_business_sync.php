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

function modified(array $root,string $operation,array $input):array {
    if(($root['format']??null)!=='fakturiva-backup-v1' ||
       !is_int($root['schema']??null) ||
       $root['schema']<1 || $root['schema']>8 ||
       !isset($root['company'],$root['clients'],$root['documents'],
               $root['lines'],$root['payments'],$root['sequences'],$root['catalog']) ||
       !is_array($root['company']) || !is_array($root['clients']) ||
       !array_is_list($root['company']) || !array_is_list($root['clients']))
        throw new HttpFailure(422,'Neispravna struktura poslovne preslike.');
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
