<?php
declare(strict_types=1);
/** Accept only a synthetic FAK1 stream re-encrypted by the actual Android Java class. */
if(PHP_SAPI!=='cli')exit(2);
$password=(string)getenv('FAKTURIVA_FIXTURE_PASS');
if(strlen($password)<12)throw new RuntimeException('Missing synthetic test passphrase');
$encoded=stream_get_contents(STDIN,36000000);
if(!is_string($encoded)||strlen($encoded)<64||strlen($encoded)>35000000)
    throw new RuntimeException('Invalid Java-encrypted stream size');
$sealed=base64_decode(trim($encoded),true);
if(!is_string($sealed)||substr($sealed,0,4)!=='FAK1'||strlen($sealed)<49)
    throw new RuntimeException('Java output does not have FAK1 header');
$salt=substr($sealed,4,16);
$nonce=substr($sealed,20,12);
$body=substr($sealed,32);
$cipher=substr($body,0,-16);
$tag=substr($body,-16);
$key=hash_pbkdf2('sha1',$password,$salt,210000,32,true);
$plain=openssl_decrypt($cipher,'aes-256-gcm',$key,
    OPENSSL_RAW_DATA,$nonce,$tag,'FAK1');
unset($key,$sealed,$encoded,$cipher,$body,$salt,$nonce,$tag);
if(!is_string($plain))throw new RuntimeException('PHP could not decrypt Android FAK1');
$root=json_decode($plain,true,32,JSON_THROW_ON_ERROR);
unset($plain);
if(!is_array($root)||($root['format']??null)!=='fakturiva-backup-v1'||
   ($root['schema']??null)!==8||
   count($root['documents']??[])!==1||
   count($root['lines']??[])!==1||
   ($root['documents'][0]['number']??null)!=='R-2026-WEB-E2E'||
   ($root['documents'][0]['total_cents']??null)!==12500||
   ($root['lines'][0]['net_cents']??null)!==10000||
   ($root['lines'][0]['tax_cents']??null)!==2500)
    throw new RuntimeException('PHP/Java FAK1 business data mismatch');
echo 'PHP -> Android SecureBackup -> PHP AES-GCM schema-8 round trip: OK'.PHP_EOL;
