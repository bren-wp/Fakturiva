<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
$account=requireWeb();
$flash=takeFlash();
$canSync=is_string($account['password_hash']??null) &&
          (string)$account['password_hash']!=='';
$usesLegacyPin=isset($account['web_pin_hash']);
?><!doctype html>
<html lang="hr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff"><meta name="robots" content="noindex,nofollow">
<title>Moj račun — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
<script src="assets/auth.js" defer></script>
</head><body>
<main class="account-page">
<?=logoMarkup()?>
<h1>Moj korisnički račun</h1>
<p>Prijavljeni ste kao <strong><?=h((string)$account['username'])?></strong>.
Na ovoj domeni možete upravljati svojom web prijavom i dozvolom za
Android sinkronizaciju. Poslovni podaci ostaju odvojeni po korisničkim računima.</p>
<?php if($flash!==''):?><p class="success-note" role="status"><?=h($flash)?></p><?php endif;?>
<section class="panel">
<h2>Pohrana na hostingu</h2>
<p>Vaš poslužiteljski račun ima zaseban šifrirani JSON zapis.</p>
<p>Sigurnosna kopija: <strong><?=empty($account['backup'])?'Nema pohranjene kopije':'Pohranjena šifrirana kopija'?></strong></p>
<p>Revizija podataka: <strong><?= (int)($account['revision']??0)?></strong></p>
<p>Posljednja potvrđena pohrana:
<strong><?=h((string)($account['saved_at']??'Još nema potvrđenog prijenosa'))?></strong></p>
<p>Zadržane verzije:
<strong><?=count(is_array($account['backup_history']??null)?$account['backup_history']:[]) + (empty($account['backup'])?0:1)?></strong></p>
<p>Android sinkronizacija: <strong><?=$canSync?'Postavljena zasebna lozinka':'Nije aktivirana'?></strong></p>
<small>Poslužitelj ne prikazuje sadržaj šifrirane preslike. Izvoz/uvoz pokrećete u Android aplikaciji.</small>
</section>
<section class="panel">
<h2>Računi, naplate i Knjiga prometa</h2>
<p>Pregledajte poslovne podatke iz svoje posljednje šifrirane sigurnosne kopije.
Za otključavanje je potrebna zasebna lozinka za poslovne podatke.</p>
<a class="primary-action" href="finance.php">Otvori poslovni pregled</a>
<p><a href="tax-reports.php">Knjiga prometa i PO-SD (Hrvatska)</a></p>
</section>
<section class="panel">
<h2>Lozinka za Android sinkronizaciju</h2>
<p>Snažna lozinka služi za prijavu i zaštitu poslovnih
podatke. Promjena lozinke uz postojeće šifrirane dokumente nije dostupna
dok siguran postupak ponovnog šifriranja ne bude implementiran.</p>
<?php if(!empty($account['backup'])):?>
<p class="error-note" role="status">Promjena lozinke privremeno je blokirana
kako postojeći računi ne bi postali nedostupni.</p>
<?php else:?>
<form method="post" action="auth.php" data-auth-form autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="set-sync">
<?php if($usesLegacyPin):?>
<label for="current-pin">Potvrdite svoj postojeći web PIN</label>
<input id="current-pin" name="pin" type="password" inputmode="numeric"
pattern="[0-9]{4,6}" maxlength="6" minlength="4" autocomplete="off"
data-numeric-pin required>
<?php else:?>
<label for="current-password">Trenutačna lozinka</label>
<input id="current-password" name="pin" type="password"
minlength="12" maxlength="256" autocomplete="current-password" required>
<?php endif;?>
<label for="sync-password">Nova zasebna lozinka hostinga</label>
<input id="sync-password" name="passphrase" type="password"
minlength="12" maxlength="256" autocomplete="new-password" required>
<button class="primary-action" type="submit">Spremi lozinku</button>
</form>
<?php endif;?>
</section>
<nav class="account-links" aria-label="Korisnički račun">
<form action="auth.php" method="post">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="logout">
<button type="submit">Odjava</button>
</form>
<a href="delete-account.php">Izbriši račun</a>
<a href="privacy.php">Privatnost</a>
</nav>
</main></body></html>
