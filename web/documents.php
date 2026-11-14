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
        if((int)($_SERVER['CONTENT_LENGTH']??0)>60000)
            throw new FakturivaSelfHost\HttpFailure(413,'Prevelik obrazac dokumenta.');
        $password=(string)($_POST['passphrase']??'');
        if(strlen($password)<12||strlen($password)>256)
            throw new FakturivaSelfHost\HttpFailure(422,'Unesite lozinku računa.');
        $operations=['doc_add','doc_edit','doc_delete','doc_confirm','quote_accept','quote_convert'];
        if(in_array($mode,$operations,true)){
            $expected=filter_var($_POST['revision']??null,FILTER_VALIDATE_INT);
            if($expected===false||$expected===null)
                throw new FakturivaSelfHost\HttpFailure(422,'Neispravna revizija.');
            $fields=[];
            foreach(['type','issue_date','due_date','supply_date','notes',
                     'quote_title'] as $key)
                if(isset($_POST[$key]))$fields[$key]=$_POST[$key];
            foreach(['id','client_id'] as $key)
                if(isset($_POST[$key])){
                    $number=filter_var($_POST[$key],FILTER_VALIDATE_INT);
                    if($number===false||$number===null)
                        throw new FakturivaSelfHost\HttpFailure(422,'Neispravan ID.');
                    $fields[$key]=$number;
                }
            if(in_array($mode,['doc_add','doc_edit'],true)){
                $rows=$_POST['lines']??null;
                if(!is_array($rows)||count($rows)>40)
                    throw new FakturivaSelfHost\HttpFailure(422,'Neispravne stavke.');
                $fields['lines']=array_values($rows);
            }
            $ack=FakturivaWebBusiness\publish(
                (string)$_SESSION['web_id'],(string)$_SESSION['web_token'],
                $password,$expected,$mode,$fields);
            $success='Promjena dokumenta spremljena kao potvrđena revizija '.
                $ack['revision'].'. Android preuzima izmjenu u čist profil.';
            $account=FakturivaSelfHost\webAuthorized(
                (string)$_SESSION['web_id'],(string)$_SESSION['web_token']);
        }elseif($mode!=='unlock'){
            throw new FakturivaSelfHost\HttpFailure(422,'Nepoznata radnja.');
        }
        $snapshot=FakturivaWebFinance\openSnapshot($account,$password);
        $revision=(int)$account['revision'];
        unset($password);
    }catch(Throwable $e){
        error_log('Fakturiva document editor: '.get_class($e));
        $error=$e instanceof FakturivaSelfHost\HttpFailure?
            $e->getMessage():'Dokumenti se nisu mogli sigurno obraditi.';
        $snapshot=null;
        unset($password);
    }
}
function documentFormHidden(string $mode,int $rev,int $id=0):void {
    echo '<input type="hidden" name="csrf" value="'.h(csrf()).'">'.
        '<input type="hidden" name="revision" value="'.$rev.'">'.
        '<input type="hidden" name="mode" value="'.h($mode).'">';
    if($id>0)echo '<input type="hidden" name="id" value="'.$id.'">';
}
function documentPassword():void {
    echo '<label>Lozinka računa za potvrdu promjene'.
        '<input type="password" name="passphrase" minlength="12" maxlength="256"'.
        ' autocomplete="off" required></label>';
}
function documentDecimal(int $value,int $digits):string {
    $scale=10**$digits;
    return (string)intdiv($value,$scale).'.'.
        str_pad((string)($value%$scale),$digits,'0',STR_PAD_LEFT);
}
function documentEditor(array $clients,array $doc,array $lines,int $revision):void {
    $edit=isset($doc['id']);
    $type=(string)($doc['type']??'INVOICE');
    $id=(int)($doc['id']??0);
    $date=date('Y-m-d');
    echo '<form action="documents.php" method="post" autocomplete="off" class="document-editor">';
    documentFormHidden($edit?'doc_edit':'doc_add',$revision,$id);
    echo '<label>Vrsta dokumenta<select name="type" required>'.
        '<option value="INVOICE"'.($type==='INVOICE'?' selected':'').'>Račun</option>'.
        '<option value="QUOTE"'.($type==='QUOTE'?' selected':'').'>Ponuda</option>'.
        '</select></label>';
    echo '<label>Klijent<select name="client_id" required>';
    echo '<option value="">Odaberite klijenta</option>';
    foreach($clients as $client){
        $selected=(int)($doc['client_id']??0)===(int)$client['id'];
        echo '<option value="'.(int)$client['id'].'"'.($selected?' selected':'').'>'.
            h((string)$client['name']).'</option>';
    }
    echo '</select></label>';
    foreach(['issue_date'=>'Datum izdavanja','due_date'=>'Datum dospijeća',
             'supply_date'=>'Datum isporuke'] as $key=>$label){
        $value=(string)($doc[$key]??$date);
        echo '<label>'.h($label).'<input type="date" name="'.h($key).'" value="'.
            h($value).'" required></label>';
    }
    echo '<label>Naslov ponude (ako je ponuda)<input name="quote_title" maxlength="200" value="'.
        h((string)($doc['quote_title']??'')).'"></label>';
    echo '<label>Napomena<textarea name="notes" maxlength="4000" rows="2">'.
        h((string)($doc['notes']??'')).'</textarea></label>';
    echo '<h3>Stavke</h3><p>Dodajte jedan ili više redaka. Iznos i PDV ponovno'.
        ' računa server, ne preglednik.</p>';
    echo '<div class="document-lines" data-document-lines>';
    foreach($lines as $i=>$line){
        $name=(string)($line['name']??'');
        $qty=isset($line['qty_milli'])?
            documentDecimal((int)$line['qty_milli'],3):'';
        $price=isset($line['unit_cents'])?
            documentDecimal((int)$line['unit_cents'],2):'';
        $rate=(string)($line['tax_rate']??'');
        echo '<fieldset class="document-line"><legend>Stavka '.($i+1).'</legend>'.
            '<label>Naziv<input name="lines['.$i.'][name]" maxlength="200" value="'.h($name).'"></label>'.
            '<label>Količina<input inputmode="decimal" name="lines['.$i.'][qty]" maxlength="24" value="'.h($qty).'"></label>'.
            '<label>Jedinična cijena<input inputmode="decimal" name="lines['.$i.'][price]" maxlength="20" value="'.h($price).'"></label>'.
            '<label>PDV kod (npr. 25 ili 11250 za 12,5 %)<input inputmode="numeric"'.
            ' name="lines['.$i.'][tax_rate]" maxlength="5" value="'.h($rate).'"></label>'.
            '</fieldset>';
    }
    echo '</div><button type="button" class="secondary" data-add-document-line>'.
        'Dodaj stavku</button>';
    documentPassword();
    echo '<button class="primary-action" type="submit">'.
        ($edit?'Spremi nacrt':'Spremi novi nacrt').'</button></form>';
}
$clients=[];$docs=[];$allLines=[];
if(is_array($snapshot)){
    foreach($snapshot['clients'] as $row){
        if(is_array($row)&&is_int($row['id']??null)&&is_string($row['name']??null))
            $clients[]=$row;
    }
    foreach($snapshot['documents'] as $row)
        if(is_array($row)&&is_int($row['id']??null))
            $docs[]=$row;
    foreach($snapshot['lines'] as $line){
        if(is_array($line)&&is_int($line['document_id']??null))
            $allLines[$line['document_id']][]=$line;
    }
    usort($docs,static fn($a,$b)=>
        ((int)($b['created_at']??0))<=>((int)($a['created_at']??0)));
    usort($clients,static fn($a,$b)=>strcasecmp($a['name'],$b['name']));
}
?><!doctype html><html lang="hr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow"><meta name="theme-color" content="#fff">
<title>Računi i ponude — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
<script src="assets/document-lines.js" defer></script>
</head><body><main class="account-page finance-page sync-business document-page">
<?=logoMarkup()?>
<h1>Računi i ponude</h1>
<p>Prijavljeni ste kao <strong><?=h((string)$account['username'])?></strong>.
Dokumenti se zapisuju u istu šifriranu presliku kao Android i Windows portal.
Samo nacrti mogu se uređivati ili brisati. Potvrđeni računi i ponude čuvaju
povijest, broj, identitet izdavatelja i iznose.</p>
<p><strong>Revizija servera:</strong> <?=$revision?></p>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<?php if($success!==''):?><p class="success-note" role="status"><?=h($success)?></p><?php endif;?>
<section class="panel">
<h2>Otključajte dokumente</h2>
<p>Lozinka se ne pamti u pregledniku, web sesiji ni serverovoj tekstualnoj datoteci.
Pri svakoj izmjeni unosi se ponovno.</p>
<form action="documents.php" method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="mode" value="unlock">
<label for="doc-unlock">Lozinka računa</label>
<input id="doc-unlock" type="password" name="passphrase" minlength="12" maxlength="256" autocomplete="off" required>
<button class="primary-action" type="submit">Prikaži moje dokumente</button>
</form>
</section>
<?php if($snapshot!==null):?>
<?php if(!$clients):?>
<p class="error-note">Za izradu dokumenta najprije dodajte klijenta u
<a href="business.php">Poslovnim podatcima</a>.</p>
<?php else:?>
<section class="panel"><h2>Novi račun ili ponuda</h2>
<?php documentEditor($clients,[],[[],[]],$revision); ?>
</section>
<?php endif;?>
<section class="panel"><h2>Dokumenti (najnoviji prvi)</h2>
<?php foreach(array_slice($docs,0,200) as $doc):
$id=(int)$doc['id'];$status=(string)($doc['status']??'');
$documentLines=$allLines[$id]??[];?>
<details class="sync-client">
<summary><?=h((string)($doc['number']??('Nacrt #'.$id)))?>
 · <?=h(($doc['type']??'')==='INVOICE'?'Račun':'Ponuda')?>
 · <?=h($status)?>
 · <?=h((string)($doc['client_name']??''))?>
 · <?=h(documentDecimal((int)($doc['total_cents']??0),2).' '.
    (string)($doc['currency_code']??'EUR'))?></summary>
<p>Datum: <?=h((string)($doc['issue_date']??''))?>.
Stavki: <?=count($documentLines)?>.</p>
<?php if($status==='DRAFT'):?>
<?php if($clients):?>
<?php documentEditor($clients,$doc,$documentLines,$revision); ?>
<?php endif;?>
<form action="documents.php" method="post" autocomplete="off">
<?php documentFormHidden('doc_confirm',$revision,$id);documentPassword();?>
<button class="primary-action" type="submit">Potvrdi i dodijeli broj</button>
</form>
<form action="documents.php" method="post" autocomplete="off">
<?php documentFormHidden('doc_delete',$revision,$id);documentPassword();?>
<button class="danger" type="submit" data-confirm-delete>Trajno izbriši samo nacrt</button>
</form>
<?php elseif(($doc['type']??'')==='QUOTE' && $status==='SENT'):?>
<form action="documents.php" method="post" autocomplete="off">
<?php documentFormHidden('quote_accept',$revision,$id);documentPassword();?>
<button class="secondary" type="submit">Označi potvrđenu ponudu prihvaćenom</button>
</form>
<?php endif;?>
</details>
<?php endforeach;?>
<?php if(count($docs)>200):?>
<p>Prikazano je 200 najnovijih dokumenata. Ostale dokumente otvorite u Android aplikaciji.</p>
<?php endif;?>
</section>
<?php endif;?>
<template id="document-line-template">
<fieldset class="document-line"><legend>Nova stavka</legend>
<label>Naziv<input name="lines[__INDEX__][name]" maxlength="200"></label>
<label>Količina<input name="lines[__INDEX__][qty]" inputmode="decimal" maxlength="24"></label>
<label>Jedinična cijena<input name="lines[__INDEX__][price]" inputmode="decimal" maxlength="20"></label>
<label>PDV kod<input name="lines[__INDEX__][tax_rate]" inputmode="numeric" maxlength="5"></label>
</fieldset>
</template>
<nav class="account-links" aria-label="Navigacija">
<a href="dashboard.php">Moj račun</a>
<a href="business.php">Tvrtka i klijenti</a>
<a href="finance.php">Financijski pregled</a>
</nav>
</main></body></html>