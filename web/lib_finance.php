<?php
declare(strict_types=1);
namespace FakturivaWebFinance;

use FakturivaSelfHost\HttpFailure;

require_once __DIR__.'/lib.php';

/** Unlock authenticated FAK1 content for one HTTPS request only.
 * Browser login with a 4–6 digit PIN alone does NOT decrypt finances.
 */
function openSnapshot(array $account,string $passphrase): array {
    $hash=$account['password_hash']??null;
    if(!is_string($hash)||strlen($passphrase)<12||
       !password_verify($passphrase,$hash))
        throw new HttpFailure(401,'Neispravna lozinka za poslovne podatke.');
    $base64=$account['backup']??null;
    if(!is_string($base64)||$base64==='')
        throw new HttpFailure(404,'Na serveru još nema pohranjene preslike.');
    $data=base64_decode($base64,true);
    if($data===false||strlen($data)<48||strlen($data)>25000000||
       substr($data,0,4)!=='FAK1')
        throw new \RuntimeException('Neispravan format šifrirane kopije');
    $salt=substr($data,4,16);
    $nonce=substr($data,20,12);
    $body=substr($data,32);
    if(strlen($body)<16)throw new \RuntimeException('Neispravni šifrirani podatci');
    $cipher=substr($body,0,-16);
    $tag=substr($body,-16);
    // Mirrors Android SecureBackup PBKDF2WithHmacSHA1, 210000, 256-bit.
    $key=hash_pbkdf2('sha1',$passphrase,$salt,210000,32,true);
    $plaintext=openssl_decrypt($cipher,'aes-256-gcm',$key,
        OPENSSL_RAW_DATA,$nonce,$tag,'FAK1');
    unset($key,$salt,$nonce,$cipher,$tag,$body,$data);
    if(!is_string($plaintext))
        throw new HttpFailure(422,'Presliku nije moguće otključati ovom lozinkom.');
    $root=json_decode($plaintext,true,32,JSON_THROW_ON_ERROR);
    unset($plaintext);
    if(!is_array($root)||($root['format']??'')!=='fakturiva-backup-v1' ||
       !isset($root['documents'],$root['payments'],$root['clients']) ||
       !is_array($root['documents'])||!is_array($root['payments'])||
       !is_array($root['clients']))
        throw new \RuntimeException('Preslika nije valjan Fakturiva dokument');
    if(count($root['documents'])>100000||count($root['payments'])>100000)
        throw new \RuntimeException('Preslika je prevelika za web pregled');
    return $root;
}
function eur(int $cents):string{
    return number_format($cents/100,2,',','.') . ' €';
}
function snapshotOverview(array $root):array{
    $documents=$root['documents'];
    $payments=$root['payments'];
    $clients=$root['clients'];
    $byId=[];$invoices=[];$quotes=[];$totalReceived=0;
    foreach($documents as $raw){
        if(!is_array($raw))continue;
        $id=(int)($raw['id']??0);
        if($id<=0)continue;
        $byId[$id]=$raw;
        if(($raw['type']??'')==='INVOICE')$invoices[]=$raw;
        if(($raw['type']??'')==='QUOTE')$quotes[]=$raw;
    }
    $rows=[];$unknown=0;
    foreach($payments as $pay){
        if(!is_array($pay))continue;
        $id=(int)($pay['document_id']??0);
        if(!isset($byId[$id])||($byId[$id]['type']??'')!=='INVOICE')continue;
        if(($byId[$id]['currency_code']??'EUR')!=='EUR')continue;
        $amount=(int)($pay['amount_cents']??0);
        if($amount<=0)continue;
        $totalReceived+= $amount;
        $channel=(string)($pay['channel']??'UNKNOWN');
        if($channel==='UNKNOWN')$unknown++;
        $rows[]=['paid_on'=>(string)($pay['paid_on']??''),
            'number'=>(string)($byId[$id]['number']??''),
            'amount'=>$amount,'channel'=>$channel,
            'unit'=>(string)($pay['business_unit']??'PP1')];
    }
    usort($invoices,static function($a,$b){
        return strcmp((string)($b['issue_date']??''),
            (string)($a['issue_date']??''));
    });
    usort($rows,static function($a,$b){
        return strcmp($b['paid_on'],$a['paid_on']);
    });
    return ['invoices'=>$invoices,'quotes'=>count($quotes),
        'clients'=>count($clients),'payments'=>$rows,'received'=>$totalReceived,
        'unknown'=>$unknown];
}
