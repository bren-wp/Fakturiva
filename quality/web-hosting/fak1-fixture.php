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
    'company'=>[
        ['key'=>'name','value'=>'Brendigo CI testni izdavatelj'],
        ['key'=>'address','value'=>'Testna 1'],
        ['key'=>'city','value'=>'Rijeka'],
        ['key'=>'postal_code','value'=>'51000'],
        ['key'=>'oib','value'=>'12345678903'],
        ['key'=>'country','value'=>'HR'],
        ['key'=>'currency','value'=>'EUR']
    ],
    'clients'=>[['id'=>1,'name'=>'Brendigo HTTP E2E Klijent',
        'oib'=>'','address'=>'Testna adresa','email'=>'','phone'=>'',
        'vat_id'=>'']],
    'catalog'=>[],
    'documents'=>[[
        'id'=>1,'type'=>'INVOICE','number'=>'R-2026-WEB-E2E',
        'client_id'=>1,'client_name'=>'Brendigo HTTP E2E Klijent',
        'client_address'=>'Testna adresa','client_oib'=>'',
        'issuer_name'=>'Brendigo CI testni izdavatelj',
        'issuer_address'=>'Testna 1','issuer_oib'=>'12345678903',
        'issue_date'=>'2026-11-09','due_date'=>'2026-11-30',
        'supply_date'=>'2026-11-09','created_at'=>1762646400000,
        'currency_code'=>'EUR','status'=>'ISSUED',
        'net_cents'=>10000,'tax_cents'=>2500,'total_cents'=>12500
    ]],
    'lines'=>[['id'=>1,'document_id'=>1,'name'=>'Testna usluga',
        'qty_milli'=>1000,'unit_cents'=>10000,'tax_rate'=>25,
        'net_cents'=>10000,'tax_cents'=>2500]],
    'payments'=>[],'sequences'=>[],'branding_assets'=>[]
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
