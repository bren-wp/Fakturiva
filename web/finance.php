<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
require_once __DIR__.'/lib_finance.php';
$account=requireWeb();
$error='';
$overview=null;
$rows=[];
$cash=0;$noncash=0;$excluded=0;
$start=(string)($_POST['from']??date('Y-01-01'));
$end=(string)($_POST['to']??date('Y-12-31'));
if(($_SERVER['REQUEST_METHOD']??'')==='POST'){
    try{
        checkCsrf();
        if((int)($_SERVER['CONTENT_LENGTH']??0)>4096)
            throw new FakturivaSelfHost\HttpFailure(413,'Obrazac je prevelik');
        if(!preg_match('/^\d{4}-\d{2}-\d{2}$/D',$start)||
           !preg_match('/^\d{4}-\d{2}-\d{2}$/D',$end)||
           $start>$end||
           !checkdate((int)substr($start,5,2),(int)substr($start,8,2),
                (int)substr($start,0,4))||
           !checkdate((int)substr($end,5,2),(int)substr($end,8,2),
                (int)substr($end,0,4)))
            throw new FakturivaSelfHost\HttpFailure(422,'Nevaljano razdoblje');
        $password=(string)($_POST['passphrase']??'');
        $snapshot=FakturivaWebFinance\openSnapshot($account,$password);
        unset($password);
        $overview=FakturivaWebFinance\snapshotOverview($snapshot);
        unset($snapshot);
        foreach($overview['payments'] as $item){
            if($item['paid_on']<$start||$item['paid_on']>$end)continue;
            $channel=$item['channel'];
            if($channel==='CASH'||$channel==='CHEQUE')$cash+=$item['amount'];
            elseif($channel==='CARD'||$channel==='BANK')$noncash+=$item['amount'];
            else $excluded++;
            $rows[]=$item;
        }
    }catch(Throwable $e){
        error_log('Fakturiva finance unlock: '.get_class($e));
        $error=$e instanceof FakturivaSelfHost\HttpFailure?
            $e->getMessage():'Nije moguće otvoriti šifrirane poslovne podatke.';
        $overview=null;
    }
}
?><!doctype html><html lang="hr">
<head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff"><meta name="robots" content="noindex,nofollow">
<title>Poslovni podatci — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css"><script src="assets/finance.js" defer></script>
</head><body><main class="account-page finance-page">
<?=logoMarkup()?>
<h1>Pregled poslovnih podataka</h1>
<p>Prijavljeni račun: <strong><?=h((string)$account['username'])?></strong>.
Dokumenti su šifrirani na serveru. Za pregled je potrebna zasebna
lozinka kojom se štiti Android sigurnosna kopija.</p>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<section class="panel">
<h2>Otključajte podatke</h2>
<p>Lozinka se koristi samo za ovaj zahtjev i ne sprema se u web sesiju.
Kratki PIN nikada nije zamjena za jaku lozinku dokumentacije.</p>
<form action="finance.php" method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<label for="passphrase">Lozinka za sigurnosnu kopiju</label>
<input id="passphrase" type="password" name="passphrase" minlength="12"
maxlength="256" autocomplete="off" required>
<div class="finance-dates">
<div><label for="from">Razdoblje od</label>
<input id="from" name="from" type="date" value="<?=h($start)?>" required></div>
<div><label for="to">Razdoblje do</label>
<input id="to" name="to" type="date" value="<?=h($end)?>" required></div>
</div>
<button class="primary-action" type="submit">Prikaži izvještaj</button>
</form>
</section>
<?php if($overview!==null):?>
<section class="panel print-report">
<h2>Poslovni pregled · <?=h($start)?> – <?=h($end)?></h2>
<div class="finance-kpis">
<div><small>Računi</small><strong><?=count($overview['invoices'])?></strong></div>
<div><small>Klijenti</small><strong><?=$overview['clients']?></strong></div>
<div><small>Ponude</small><strong><?=$overview['quotes']?></strong></div>
<div><small>Naplaćeno EUR (ukupno)</small>
<strong><?=h(FakturivaWebFinance\eur($overview['received']))?></strong></div>
</div>
<h2>KPR — pregled naplaćenih primitaka</h2>
<p>Promet je preuzet iz evidentiranih uplata za odabrano razdoblje.
Naplata u karticama i bankovnim prijenosima vodi se odvojeno od
gotovine i čekova. Ovo je pomoćni web pregled, ne porezna prijava.</p>
<?php if($excluded>0):?><p class="error-note">
<?=$excluded?> uplata nema potvrđen način naplate. Provjerite ih u Android
aplikaciji prije izvoza službene Knjige prometa.</p><?php endif;?>
<div class="finance-table-wrap"><table class="finance-table">
<thead><tr><th>Datum</th><th>Račun</th><th>Način naplate</th><th>Jedinica</th><th>Iznos</th></tr></thead><tbody>
<?php foreach(array_slice($rows,0,500) as $row):?>
<tr><td><?=h($row['paid_on'])?></td>
<td><?=h($row['number'])?></td><td><?=h($row['channel'])?></td>
<td><?=h($row['unit'])?></td>
<td><?=h(FakturivaWebFinance\eur($row['amount']))?></td></tr>
<?php endforeach;?>
</tbody></table></div>
<p><strong>Gotovina i čekovi:</strong> <?=h(FakturivaWebFinance\eur($cash))?></p>
<p><strong>Bankovni prijenosi i kartice:</strong> <?=h(FakturivaWebFinance\eur($noncash))?></p>
<p><strong>Ukupno razvrstani primitci:</strong>
<?=h(FakturivaWebFinance\eur($cash+$noncash))?></p>
<?php if(count($rows)>500):?><p>Prikazano prvih 500 uplata. Za puni
zakonski KPR izvezite PDF u Android aplikaciji.</p><?php endif;?>
<h2>Pregled računa</h2>
<div class="finance-table-wrap"><table class="finance-table">
<thead><tr><th>Broj</th><th>Datum</th><th>Kupac</th><th>Status</th><th>Ukupno</th></tr></thead><tbody>
<?php foreach(array_slice($overview['invoices'],0,200) as $invoice):?>
<tr><td><?=h((string)($invoice['number']??'Nacrt'))?></td>
<td><?=h((string)($invoice['issue_date']??''))?></td>
<td><?=h((string)($invoice['client_name']??''))?></td>
<td><?=h((string)($invoice['status']??''))?></td>
<td><?=h(FakturivaWebFinance\eur((int)($invoice['total_cents']??0)))?></td></tr>
<?php endforeach;?>
</tbody></table></div>
<?php if(count($overview['invoices'])>200):?><p>Prikazano prvih 200
računa. Cjelovita arhiva ostaje u šifriranoj kopiji.</p><?php endif;?>
<button class="secondary print-button" type="button" data-print-finance>
<span aria-hidden="true">▤</span> Ispiši ili spremi pregled u PDF</button>
</section>
<?php endif;?>
<nav class="account-links" aria-label="Navigacija">
<a href="dashboard.php">Moj račun</a>
<a href="business.php">Tvrtka, klijenti i naplata</a>
<a href="documents.php">Računi i ponude</a>
<a href="tax-reports.php">KPR i PO-SD</a>
<a href="delete-account.php">Brisanje računa</a>
<a href="privacy.php">Privatnost</a>
</nav>
</main></body></html>
