<?php
declare(strict_types=1);
// CLI fixture validator: reads synthetic API JSON on stdin; no production data,
// no plaintext file and no credentials in command-line arguments.
if(PHP_SAPI!=='cli')exit(2);
$password=(string)getenv('FAKTURIVA_FIXTURE_PASS');
$phase=(string)getenv('FAKTURIVA_VERIFY_PHASE');
if(strlen($password)<12||!in_array($phase,['draft','confirmed'],true))
    throw new RuntimeException('Fixture password or verification phase missing');
$raw=stream_get_contents(STDIN,36000000);
if(!is_string($raw)||$raw===''||strlen($raw)>35000000)
    throw new RuntimeException('Invalid API response size');
$response=json_decode($raw,true,32,JSON_THROW_ON_ERROR);
if(!is_array($response)||($response['ok']??false)!==true)
    throw new RuntimeException('API response not successful');
$data=$response['data']??[];
if(($data['revision']??null)!==($phase==='draft'?2:3))
    throw new RuntimeException('Wrong optimistic revision after web form');
$sealed=base64_decode($data['backup_b64']??'',true);
if(!is_string($sealed)||substr($sealed,0,4)!=='FAK1'||strlen($sealed)<48)
    throw new RuntimeException('Missing encrypted FAK1 snapshot');
if(!hash_equals(hash('sha256',$sealed),(string)($data['sha256']??'')))
    throw new RuntimeException('Encrypted snapshot hash mismatch');
$salt=substr($sealed,4,16);
$nonce=substr($sealed,20,12);
$body=substr($sealed,32);
$cipher=substr($body,0,-16);
$tag=substr($body,-16);
$key=hash_pbkdf2('sha1',$password,$salt,210000,32,true);
$plain=openssl_decrypt($cipher,'aes-256-gcm',$key,OPENSSL_RAW_DATA,
    $nonce,$tag,'FAK1');
unset($key,$sealed,$cipher,$body,$salt,$nonce,$tag);
if(!is_string($plain))throw new RuntimeException('Web snapshot could not decrypt');
$root=json_decode($plain,true,32,JSON_THROW_ON_ERROR);
unset($plain);
if(($root['schema']??null)!==8 ||
   ($root['format']??'')!=='fakturiva-backup-v1'||
   count($root['documents']??[])!==2||count($root['lines']??[])!==2||
   ($root['documents'][0]['number']??null)!=='R-2026-WEB-E2E'||
   ($root['documents'][0]['total_cents']??null)!==12500)
    throw new RuntimeException('Existing Android-compatible document was changed');
$doc=$root['documents'][1];
$line=$root['lines'][1];
if(($doc['id']??null)!==2||($doc['client_id']??null)!==1||
   ($doc['type']??null)!=='INVOICE'||($doc['total_cents']??null)!==31250||
   ($doc['net_cents']??null)!==25000||($doc['tax_cents']??null)!==6250||
   ($line['document_id']??null)!==2||
   ($line['qty_milli']??null)!==2500||
   ($line['net_cents']??null)!==25000||
   ($line['tax_cents']??null)!==6250)
    throw new RuntimeException('Web document is not compatible with Android integer-money schema');
if($phase==='draft'){
    if(($doc['status']??'')!=='DRAFT'||($doc['number']??false)!==null)
        throw new RuntimeException('Draft received a premature document number');
}else{
    if(($doc['status']??null)!=='ISSUED'||
       ($doc['number']??null)!=='R-2026-0001'||
       ($doc['issuer_name']??null)!=='Brendigo CI testni izdavatelj'||
       ($root['sequences'][0]['last_num']??null)!==1)
        throw new RuntimeException('Invoice issue sequence or immutable issuer snapshot corrupt');
}
echo 'Real HTTP web form -> Android FAK1 snapshot: '.$phase.' OK'.PHP_EOL;
