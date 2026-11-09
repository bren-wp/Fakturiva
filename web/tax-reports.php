<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
require_once __DIR__.'/lib_tax.php';
$account=requireWeb();
$error='';$book=null;$report=null;
$mode=(string)($_POST['report']??'kpr');
$from=(string)($_POST['from']??date('Y-01-01'));
$to=(string)($_POST['to']??date('Y-m-d'));
$year=(int)($_POST['year']??date('Y'));
$activeFrom=(string)($_POST['active_from']??($year.'-01-01'));
$activeTo=(string)($_POST['active_to']??($year.'-12-31'));
$unit=(string)($_POST['unit']??'PP1');
if(($_SERVER['REQUEST_METHOD']??'GET')==='POST'){
    try{
        checkCsrf();
        if((int)($_SERVER['CONTENT_LENGTH']??0)>5000)
            throw new FakturivaSelfHost\HttpFailure(413,'Zahtjev je prevelik');
        $mode=in_array($mode,['kpr','posd'],true)?$mode:'kpr';
        $pass=(string)($_POST['passphrase']??'');
        $snapshot=FakturivaWebFinance\openSnapshot($account,$pass);
        unset($pass);
        if($mode==='kpr'){
            if(strlen($unit)>60||!preg_match('/^[A-Za-z0-9._-]{1,60}$/D',$unit))
                throw new FakturivaSelfHost\HttpFailure(422,'Odaberite poslovni prostor.');
            $book=FakturivaWebTax\book($snapshot,$from,$to,$unit);
        }else{
            $report=FakturivaWebTax\posd($snapshot,$year,$activeFrom,$activeTo,
                (string)($_POST['paid_tax']??'0,00'));
            if(($_POST['operation']??'')==='working_xml'){
                $bytes=FakturivaWebTax\workingXml($report);
                header('Content-Type: application/xml; charset=utf-8');
                header('Content-Disposition: attachment; filename="Fakturiva-PO-SD-RADNI-'.$year.'.xml"');
                header('Cache-Control: no-store');
                echo $bytes;exit;
            }
        }
        unset($snapshot);
    }catch(Throwable $errorObject){
        if($errorObject instanceof FakturivaSelfHost\HttpFailure){
            http_response_code($errorObject->statusCode);
            $error=$errorObject->getMessage();
        }else{
            error_log('Fakturiva tax preview: '.get_class($errorObject));
            http_response_code(500);
            $error='Izvještaj nije moguće izraditi iz postojećih poslovnih podataka.';
        }
    }
}
$money=static fn(int $amount):string=>FakturivaWebFinance\eur($amount);
?><!doctype html><html lang="hr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow">
<title>Knjiga prometa i PO-SD — Fakturiva</title>
<link rel="stylesheet" href="assets/app.css"><link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<script src="assets/finance.js" defer></script>
</head><body><main class="account-page finance-page">
<?=logoMarkup()?>
<h1>Knjiga prometa i PO-SD</h1>
<p>Podatci se čitaju iz vaše šifrirane kopije na serveru nakon što
potvrdite zasebnu lozinku. Obračun koristi evidentirane uplate prema
datumu stvarne naplate, ne samo izdane račune. Prikaz vrijedi za
odgovarajuće hrvatske porezne obveznike za 2025. i 2026. godinu.</p>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<section class="panel">
<h2>Izrada poreznog izvještaja</h2>
<form method="post" action="tax-reports.php" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<label for="report-kind">Vrsta izvještaja</label>
<select id="report-kind" name="report">
<option value="kpr" <?=$mode==='kpr'?'selected':''?>>Knjiga prometa (KPR)</option>
<option value="posd" <?=$mode==='posd'?'selected':''?>>PO-SD (paušalni obrt)</option>
</select>
<label for="passphrase">Lozinka poslovnih podataka</label>
<input id="passphrase" type="password" name="passphrase" required minlength="12"
maxlength="256" autocomplete="off">
<div class="finance-dates">
<div><label for="from">KPR od</label><input type="date" id="from" name="from"
value="<?=h($from)?>" required></div>
<div><label for="to">KPR do</label><input type="date" id="to" name="to"
value="<?=h($to)?>" required></div>
</div>
<label for="unit">Poslovni prostor za KPR</label>
<input id="unit" name="unit" type="text" maxlength="60" value="<?=h($unit)?>">
<p>Za više poslovnih prostora izvještaje vodite odvojeno.
PO-SD obuhvaća sve prostore relevantne djelatnosti.</p>
<label for="year">PO-SD porezna godina</label>
<select id="year" name="year">
<?php foreach([2025,2026] as $allowedYear):?>
<option value="<?=$allowedYear?>" <?=$year===$allowedYear?'selected':''?>>
<?=$allowedYear?></option><?php endforeach;?></select>
<div class="finance-dates"><div>
<label for="active-from">Djelatnost od</label>
<input id="active-from" type="date" name="active_from" value="<?=h($activeFrom)?>">
</div><div><label for="active-to">Djelatnost do</label>
<input id="active-to" type="date" name="active_to" value="<?=h($activeTo)?>">
</div></div>
<label for="paid-tax">Već uplaćeni porez za PO-SD (EUR)</label>
<input id="paid-tax" name="paid_tax" value="0,00">
<div class="tax-actions">
<button class="primary-action" name="operation" value="view" type="submit">
Izračunaj izvještaj</button>
<button class="secondary" name="operation" value="working_xml" type="submit">
Preuzmi radni PO-SD XML</button>
</div>
</form>
<p><small>Radni PO-SD XML nije validirani dokument za izravnu predaju u ePoreznu.
Potvrda službenog XSD-a i testni uvoz ostaju uvjet za predajni XML.</small></p>
</section>
<?php if($book!==null||$report!==null):?>
<section class="panel print-report">
<h2><?=$book!==null?'Knjiga prometa':'PO-SD — godišnji pregled'?></h2>
<?php $data=$book??$report['book'];?>
<p>Razdoblje: <?=h($data['from'])?> – <?=h($data['to'])?>.</p>
<p>Obveznik: <?=h((string)($data['business']['name']??''))?> ·
OIB: <?=h((string)($data['business']['oib']??''))?></p>
<div class="finance-table-wrap"><table class="finance-table">
<thead><tr><th>Datum</th><th>Broj računa</th>
<th>Opis</th><th>Gotovina</th><th>Bezgotovinski</th><th>Ukupno</th></tr></thead>
<tbody><?php foreach($data['rows'] as $entry):?>
<tr><td><?=h($entry['date'])?></td><td><?=h($entry['number'])?></td>
<td><?=h($entry['description'])?></td>
<td><?=h($money($entry['cash']))?></td>
<td><?=h($money($entry['noncash']))?></td>
<td><?=h($money($entry['total']))?></td></tr>
<?php endforeach;?>
<tr><th colspan="3">Ukupno</th>
<th><?=h($money($data['cash']))?></th>
<th><?=h($money($data['noncash']))?></th>
<th><?=h($money($data['total']))?></th></tr>
</tbody></table></div>
<?php if($report!==null):?>
<h2>PO-SD — obračun</h2>
<div class="finance-kpis">
<div><small>Broj mjeseci</small><strong><?=$report['months']?></strong></div>
<div><small>Porezni razred</small><strong><?=$report['bracket']?></strong></div>
<div><small>Paušalni dohodak</small><strong><?=h($money($report['income']))?></strong></div>
<div><small>Porezna obveza</small><strong><?=h($money($report['tax']))?></strong></div>
</div>
<p>Preračunati godišnji primitci:
<strong><?=h($money($report['annualized']))?></strong>.
Uplaćeni porez:
<strong><?=h($money($report['paid']))?></strong>.
Razlika za uplatu / povrat:
<strong><?=h($money($report['difference']))?></strong>.</p>
<?php endif;?>
<p>Provjerite podatke prema poslovnim knjigama, bankovnim izvodima,
blagajni i važećem poreznom rješenju. Dokument ne potvrđuje
predaju Poreznoj upravi niti sam izvršava poreznu prijavu.</p>
<button class="secondary print-button" type="button" data-print-finance>
Ispiši ili spremi kao PDF</button>
</section><?php endif;?>
<nav class="account-links">
<a href="dashboard.php">Moj račun</a>
<a href="finance.php">Poslovni pregled</a>
<a href="politika-privatnosti/">Privatnost</a>
</nav>
</main></body></html>
