<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
require_once __DIR__.'/lib_business_sync.php';

$account=requireWeb();
$error='';$success='';$snapshot=null;
$revision=(int)($account['revision']??0);
$mode=(string)($_POST['mode']??'');
$clientSearch=trim((string)($_POST['client_search']??''));
$catalogSearch=trim((string)($_POST['catalog_search']??''));
if(strlen($clientSearch)>120)$clientSearch=substr($clientSearch,0,120);
if(strlen($catalogSearch)>120)$catalogSearch=substr($catalogSearch,0,120);
if(($_SERVER['REQUEST_METHOD']??'')==='POST'){
    try{
        checkCsrf();
        if((int)($_SERVER['CONTENT_LENGTH']??0)>10000)
            throw new FakturivaSelfHost\HttpFailure(413,'Prevelik obrazac.');
        $password=(string)($_POST['passphrase']??'');
        if(strlen($password)<12 || strlen($password)>256)
            throw new FakturivaSelfHost\HttpFailure(422,'Unesite lozinku poslovnih podataka.');
        if(in_array($mode,['profile','client_add','client_edit',
                       'catalog_add','catalog_edit','payment_add'],true)){
            $expected=filter_var($_POST['revision']??null,FILTER_VALIDATE_INT);
            if($expected===false||$expected===null)
                throw new FakturivaSelfHost\HttpFailure(422,'Nevaljana revizija.');
            $fields=[];
            foreach(['name','oib','address','city','postal_code','email',
                     'phone','vat_id','price','tax_rate','kind','unit',
                     'amount','paid_on','channel'] as $key)
                if(array_key_exists($key,$_POST))$fields[$key]=$_POST[$key];
            if($mode==='client_edit'||$mode==='catalog_edit'){
                $id=filter_var($_POST['id']??null,FILTER_VALIDATE_INT);
                if($id===false||$id===null)
                    throw new FakturivaSelfHost\HttpFailure(422,'Nevaljan klijent.');
                $fields['id']=$id;
            }
            if($mode==='payment_add'){
                $docId=filter_var($_POST['document_id']??null,FILTER_VALIDATE_INT);
                if($docId===false||$docId===null||$docId<1)
                    throw new FakturivaSelfHost\HttpFailure(422,'Odaberite izdani račun.');
                $fields['document_id']=$docId;
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
$company=[];$clients=[];$catalog=[];$payableInvoices=[];
if(is_array($snapshot)){
    foreach($snapshot['company'] as $row)
        if(is_array($row)&&is_string($row['key']??null))
            $company[$row['key']]=(string)($row['value']??'');
    foreach($snapshot['clients'] as $row)
        if(is_array($row)&&isset($row['id'],$row['name']))$clients[]=$row;
    usort($clients,static fn($a,$b)=>strcasecmp(
        (string)($a['name']??''),(string)($b['name']??'')));
    foreach($snapshot['catalog']??[] as $row)
        if(is_array($row)&&isset($row['id'],$row['name']))$catalog[]=$row;
    usort($catalog,static fn($a,$b)=>strcasecmp(
        (string)($a['name']??''),(string)($b['name']??'')));
    if($clientSearch!=='')$clients=array_values(array_filter($clients,
        static fn($item)=>stripos((string)($item['name']??''),$clientSearch)!==false ||
            stripos((string)($item['email']??''),$clientSearch)!==false));
    if($catalogSearch!=='')$catalog=array_values(array_filter($catalog,
        static fn($item)=>stripos((string)($item['name']??''),$catalogSearch)!==false));
    try{$payableInvoices=FakturivaWebBusiness\payableInvoices($snapshot);}
    catch(FakturivaSelfHost\HttpFailure $e){
        $error=$e->getMessage();
        $payableInvoices=[];
    }
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
function syncCatalogFields(array $item=[]):void {
    syncField('name','Naziv artikla / usluge',(string)($item['name']??''),true);
    $raw=(int)($item['price_cents']??0);
    $price=(string)intdiv($raw,100).'.'.str_pad((string)($raw%100),2,'0',STR_PAD_LEFT);
    syncField('price','Jedinična cijena (decimalno, npr. 125,50)',$price,true);
    syncField('unit','Mjerna jedinica',(string)($item['unit']??'kom'),true);
    $kind=(string)($item['kind']??'SERVICE');
    echo '<label>Vrsta artikla / usluge<select name="kind">';
    foreach(['SERVICE'=>'Usluga','PRODUCT'=>'Proizvod','UNSPECIFIED'=>'Ostalo'] as $code=>$label)
        echo '<option value="'.h($code).'"'.
            ($code===$kind?' selected':'').'>'.h($label).'</option>';
    echo '</select></label>';
    syncField('tax_rate','PDV stopa (0–100 % ili postojeći prošireni kod)',
        (string)($item['tax_rate']??25),true);
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
Promjene tvrtke, klijenata, artikala i uplata zapisuju se kao nova šifrirana revizija iste
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
<label for="client-search">Traži klijenta (naziv ili e-adresa)</label>
<input id="client-search" name="client_search" type="search"
maxlength="120" value="<?=h($clientSearch)?>" autocomplete="off">
<label for="catalog-search">Traži artikl ili uslugu</label>
<input id="catalog-search" name="catalog_search" type="search"
maxlength="120" value="<?=h($catalogSearch)?>" autocomplete="off">
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
<p>Prikazano do 30 <?= $clientSearch!==''?'pronađenih ':'' ?>klijenata po nazivu.
Za dodatne klijente upotrijebite pretragu pri otključavanju.
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
<p>Za preostale klijente koristite polje za pretraživanje pri otključavanju
ili Android aplikaciju. Dokumenti zadržavaju postojeće identifikatore.</p>
<?php endif;?>
</section>
<section class="panel">
<h2>Artikli i usluge — dodavanje</h2>
<p>Iznosi su pohranjeni u najmanjim novčanim jedinicama bez
zaokruživanja preko floating-point izračuna.</p>
<form action="business.php" method="post" autocomplete="off">
<?php syncHidden('catalog_add',$revision);syncCatalogFields();syncPassword(); ?>
<button class="primary-action" type="submit">Dodaj artikl ili uslugu</button>
</form>
</section>
<section class="panel">
<h2>Artikli i usluge — uređivanje</h2>
<p>Uređivanje kataloga ne mijenja stavke ili cijene već izdanih računa.</p>
<?php foreach(array_slice($catalog,0,30) as $item):?>
<details class="sync-client">
<summary><?=h((string)$item['name'])?>
<small>· ID <?=(int)$item['id']?></small></summary>
<form action="business.php" method="post" autocomplete="off">
<?php syncHidden('catalog_edit',$revision);?>
<input type="hidden" name="id" value="<?=(int)$item['id']?>">
<?php syncCatalogFields($item);syncPassword(); ?>
<button class="primary-action" type="submit">Spremi stavku kataloga</button>
</form>
</details>
<?php endforeach;?>
<?php if(count($catalog)>30):?>
<p>Za preostale stavke kataloga koristite pretragu pri otključavanju
ili Android aplikaciju.</p>
<?php endif;?>
</section>
<section class="panel">
<h2>Evidencija primljenih uplata</h2>
<p>Nova uplata evidentira se na istom serveru kao Android računi i čuva
u šifriranoj kopiji. Iznos se ne može evidentirati ako je račun već
podmiren ili nije izdan. Uplaćenu svotu i datum potvrdite prema stvarnoj uplati.</p>
<?php if($payableInvoices):?>
<form action="business.php" method="post" autocomplete="off">
<?php syncHidden('payment_add',$revision); ?>
<label for="payment-document">Izdani račun s nepodmirenim iznosom</label>
<select id="payment-document" name="document_id" required>
<option value="">Odaberite račun</option>
<?php foreach(array_slice($payableInvoices,0,100,true) as $id=>$balance):
$doc=$balance['document'];
$currency=(string)($doc['currency_code']??'EUR');
$displayAmount=number_format($balance['outstanding']/100,2,',','.');
?>
<option value="<?=(int)$id?>"><?=h((string)($doc['number']??('ID '.$id)))?>
 — <?=h($displayAmount.' '.$currency)?> preostalo</option>
<?php endforeach;?>
</select>
<?php if(count($payableInvoices)>100):?>
<p>Prikazano je najnovijih 100 otvorenih računa. Za ostale uplate
koristite Android aplikaciju.</p>
<?php endif;?>
<label for="payment-amount">Stvarno primljeni iznos (npr. 125,50)</label>
<input id="payment-amount" name="amount" inputmode="decimal"
type="text" maxlength="20" autocomplete="off" required>
<label for="payment-date">Datum primitka</label>
<input id="payment-date" name="paid_on" type="date"
value="<?=h(date('Y-m-d'))?>" required>
<label for="payment-channel">Način primitka</label>
<select id="payment-channel" name="channel" required>
<option value="BANK">Uplata na račun</option>
<option value="CASH">Gotovina</option>
<option value="CARD">Kartica</option>
<option value="CHEQUE">Ček</option>
</select>
<?php syncPassword(); ?>
<button class="primary-action" type="submit">Evidentiraj stvarnu uplatu</button>
</form>
<?php else:?>
<p>Trenutačno nema izdanih računa s iznosom za naplatu ili podatke
nije moguće sigurno provjeriti.</p>
<?php endif;?>
</section>
<?php endif;?>
<nav class="account-links" aria-label="Navigacija">
<a href="dashboard.php">Moj račun</a>
<a href="finance.php">Financijski pregled</a>
<a href="documents.php">Računi i ponude</a>
<a href="tax-reports.php">Porezni izvještaji</a>
</nav></main></body></html>
