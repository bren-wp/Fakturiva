<?php
declare(strict_types=1);
/**
 * CLI-only synthetic FAK1 fixture, byte-compatible with Android SecureBackup.
 * This contains NO real invoice, no private credentials, and no test shortcut
 * in the production application or web API.
 */
if(PHP_SAPI!=='cli'){
    http_response_code(403);
    exit;
}
$pass=(string)getenv('FAKTURIVA_FIXTURE_PASS');
if(strlen($pass)<12)throw new RuntimeException('Missing fixture passphrase');
$root=[
    'format'=>'fakturiva-backup-v1',
    'schema'=>8,
    'company'=>[],
    'clients'=>[['id'=>1,'name'=>'Brendigo HTTP E2E Klijent']],
    'catalog'=>[],
    'documents'=>[[
        'id'=>1,'type'=>'INVOICE','number'=>'R-2026-WEB-E2E',
        'client_id'=>1,'client_name'=>'Brendigo HTTP E2E Klijent',
        'issue_date'=>'2026-11-09','due_date'=>'2026-11-30',
        'currency_code'=>'EUR','status'=>'ISSUED','total_cents'=>12500
    ]],
    'lines'=>[],'payments'=>[],'sequences'=>[],'branding_assets'=>[]
];
$plain=json_encode($root,JSON_UNESCAPED_UNICODE|JSON_THROW_ON_ERROR);
$salt=random_bytes(16);
$nonce=random_bytes(12);
$key=hash_pbkdf2('sha1',$pass,$salt,210000,32,true);
$tag='';
$cipher=openssl_encrypt($plain,'aes-256-gcm',$key,
    OPENSSL_RAW_DATA,$nonce,$tag,'FAK1');
if(!is_string($cipher)||strlen($tag)!==16)
    throw new RuntimeException('FAK1 test encryption failed');
echo base64_encode('FAK1'.$salt.$nonce.$cipher.$tag);
