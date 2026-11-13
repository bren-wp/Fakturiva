<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
require_once __DIR__.'/lib_business_sync.php';

$account=requireWeb();
$error='';$success='';$snapshot=null;
$revision=(int)($account['revision']??0);
$mode=(string)($_POST['mode']??'');
if(($_SERVER['REQUEST_METHOD']??'')==='POST'){
    try{
        checkCsrf();
        if((int)($_SERVER['CONTENT_LENGTH']??0)>10000)
            throw new FakturivaSelfHost\HttpFailure(413,'Prevelik obrazac.');
        $password=(string)($_POST['passphrase']??'');
        if(strlen($password)<12 || strlen($password)>256)
            throw new FakturivaSelfHost\HttpFailure(422,'Unesite lozinku poslovnih podataka.');
        if($mode==='profile'||$mode==='client_add'||$mode==='client_edit'){
            $expected=filter_var($_POST['revision']??null,FILTER_VALIDATE_INT);
            if($expected===false||$expected===null)
                throw new FakturivaSelfHost\HttpFailure(422,'Nevaljana revizija.');
            $fields=[];
            foreach(['name','oib','address','city','postal_code','email',
                     'phone','vat_id'] as $key)
                if(array_key_exists($key,$_POST))$fields[$key]=$_POST[$key];
            if($mode==='client_edit'){
                $id=filter_var($_POST['id']??null,FILTER_VALIDATE_INT);
                if($id===false||$id===null)
                    throw new FakturivaSelfHost\HttpFailure(422,'Nevaljan klijent.');
                $fields['id']=$id;
            }
            $ack=FakturivaWebBusiness\publish(
                (string)$_SESSION['web_id'],(string)$_SESSION['web_token'],
                $password,$expected,$mode,$fields);
            $success='Poslovna promjena spremljena na server (revizija '.
                $ack['revision'].'). Na Androidu odaberite preuzimanje novijih podataka.';
            $account=FakturivaSelfHost\webAuthorized(
                (string)$_SESSION['web_id'],(string)$_SESSION['web_token']);
        }elseif($mode!=='unlock'){
            throw new FakturivaSelfHost\HttpFailure(422,'Nepoznata radnja.');
        }
        // Plaintext lives only in this HTTPS request. No session/cache storage.
        $snapshot=FakturivaWebFinance\openSnapshot($account,$password);
        $revision=(int)$account['revision'];
        unset($password);
    }catch(Throwable $e){
        error_log('Fakturiva business sync: '.get_class($e));
        $error=$e instanceof FakturivaSelfHost\HttpFailure?
            $e->getMessage():'Poslovni podatci nisu mogli biti obrađeni.';
        $snapshot=null;
        unset($password);
    }
}
$company=[];$clients=[];
if(is_array($snapshot)){
    foreach($snapshot['company'] as $row)
        if(is_array($row)&&is_string($row['key']??null))
            $company[$row['key']]=(string)($row['value']??'');
    foreach($snapshot['clients'] as $row)
        if(is_array($row)&&isset($row['id'],$row['name']))$clients[]=$row;
    usort($clients,static fn($a,$b)=>strcasecmp(
        (string)($a['name']??''),(string)($b['name']??'')));
}
function syncField(string $name,string $label,string $value='',bool $required=false):void {
    $id='sync-'.preg_replace('/[^a-z0-9_-]/','',strtolower($name)).'-'.
        bin2hex(random_bytes(3));
    echo '<label for="'.h($id).'">'.h($label).'</label>'.
        '<input id="'.h($id).'" name="'.h($name).'" type="'.
        ($name==='email'?'email':'text').'" value="'.h($value).'"'.
        ($required?' required':''). ' maxlength="'.
        (in_array($name,['name','address'],true)?320:254).'" autocomplete="off">';
}
function syncPassword():void {
    echo '<label>Potvrdite lozinku poslovnih podataka'.
        '<input type="password" name="passphrase" minlength="12" maxlength="256"'.
        ' autocomplete="off" required></label>';
}
function syncHidden(string $mode,int $revision):void {
    echo '<input type="hidden" name="csrf" value="'.h(csrf()).'">'.
        '<input type="hidden" name="mode" value="'.h($mode).'">'.
        '<input type="hidden" name="revision" value="'.$revision.'">';
}
?><!doctype html>
<html lang="hr"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow"><meta name="theme-color" content="#ffffff">
<title>Sinkronizacija poslovanja — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
</head><body><main class="account-page finance-page sync-business">
<?=logoMarkup()?>
<h1>Poslovni podatci na serveru</h1>
<p>Prijavljeni ste kao <strong><?=h((string)$account['username'])?></strong>.
Promjene tvrtke i klijenata zapisuju se kao nova šifrirana revizija iste
sigurnosne kopije koju koristi Android. Windows prikazuje ovaj web portal.
Već izdani dokumenti i njihove povijesne vrijednosti ne mijenjaju se.</p>
<p><strong>Revizija servera:</strong> <?=$revision?></p>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<?php if($success!==''):?><p class="success-note" role="status"><?=h($success)?></p><?php endif;?>
<section class="panel">
<h2>Otključavanje poslovnih podataka</h2>
<p>Lozinka nije spremljena u web sesiju. Za pregled ili spremanje
unesite je ponovno; stranica ne pohranjuje otključane poslovne podatke.</p>
<form action="business.php" method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="mode" value="unlock">
<label for="unlock-secret">Lozinka računa</label>
<input id="unlock-secret" type="password" name="passphrase"
minlength="12" maxlength="256" autocomplete="off" required>
<button class="primary-action" type="submit">Prikaži moje poslovne podatke</button>
</form>
</section>
<?php if($snapshot!==null):?>
<section class="panel">
<h2>Profil izdavatelja</h2>
<p>Uređujete samo nove podatke tvrtke. Stari izdani računi i ponude
zadržavaju izvorne podatke izdavatelja.</p>
<form action="business.php" method="post" autocomplete="off">
<?php syncHidden('profile',$revision); ?>
<?php foreach(['name'=>'Naziv tvrtke','oib'=>'OIB / porezni broj',
'address'=>'Adresa','city'=>'Grad','postal_code'=>'Poštanski broj',
'email'=>'E-adresa','phone'=>'Telefon'] as $key=>$label)
syncField($key,$label,$company[$key]??'', $key==='name'); ?>
<?php syncPassword(); ?>
<button class="primary-action" type="submit">Spremi podatke tvrtke</button>
</form>
</section>
<section class="panel">
<h2>Novi klijent</h2>
<form action="business.php" method="post" autocomplete="off">
<?php syncHidden('client_add',$revision); ?>
<?php foreach(['name'=>'Naziv klijenta','oib'=>'Porezni broj',
'address'=>'Adresa','email'=>'E-adresa','phone'=>'Telefon',
'vat_id'=>'PDV broj'] as $key=>$label)
syncField($key,$label,'',$key==='name'); ?>
<?php syncPassword(); ?>
<button class="primary-action" type="submit">Dodaj klijenta</button>
</form>
</section>
<section class="panel">
<h2>Uređivanje klijenata</h2>
<p>Prikazano je prvih 30 klijenata po nazivu.
Podatci se spremaju po zasebnom ID-u bez mijenjanja povezanih dokumenata.</p>
<?php foreach(array_slice($clients,0,30) as $client):?>
<details class="sync-client">
<summary><?=h((string)$client['name'])?> <small>· ID <?= (int)$client['id']?></small></summary>
<form action="business.php" method="post" autocomplete="off">
<?php syncHidden('client_edit',$revision); ?>
<input type="hidden" name="id" value="<?=(int)$client['id']?>">
<?php foreach(['name'=>'Naziv klijenta','oib'=>'Porezni broj',
'address'=>'Adresa','email'=>'E-adresa','phone'=>'Telefon',
'vat_id'=>'PDV broj'] as $key=>$label)
syncField($key,$label,(string)($client[$key]??''),$key==='name'); ?>
<?php syncPassword(); ?>
<button class="primary-action" type="submit">Spremi izmjene klijenta</button>
</form>
</details>
<?php endforeach;?>
<?php if(count($clients)>30):?>
<p>Preostali klijenti dostupni su u Android aplikaciji.
Izmjene postojećih podataka ne zahtijevaju ponovno numeriranje računa.</p>
<?php endif;?>
</section>
<?php endif;?>
<nav class="account-links" aria-label="Navigacija">
<a href="dashboard.php">Moj račun</a>
<a href="finance.php">Financijski pregled</a>
<a href="tax-reports.php">Porezni izvještaji</a>
</nav></main></body></html>
