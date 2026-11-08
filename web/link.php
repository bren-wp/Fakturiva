<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
if(webCurrent()!==null)gotoPage('dashboard.php');
$error=takeFlash();
?><!doctype html>
<html lang="hr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff"><meta name="robots" content="noindex,nofollow">
<title>Povezivanje Android računa — Fakturiva</title>
<link rel="stylesheet" href="assets/app.css"><link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<script src="assets/auth.js" defer></script>
</head><body>
<main class="account-page">
<?=logoMarkup()?>
<h1>Povežite postojeći račun</h1>
<p>Ako ste korisnički račun već stvorili u Android Fakturivi i
uključili vlastiti hosting, ovdje možete postaviti web PIN.
Za potvrdu je potrebna vaša <strong>postojeća lozinka hostinga</strong>.
Lokalni Android PIN ne prenosi se na web.</p>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<section class="panel">
<form method="post" action="auth.php" data-auth-form autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="link">
<label for="username">Korisničko ime</label>
<input id="username" name="username" type="text" autocomplete="username" minlength="3" maxlength="80" required placeholder="ime@tvrtka.hr">
<label for="passphrase">Postojeća lozinka hostinga (najmanje 12 znakova)</label>
<input id="passphrase" name="passphrase" type="password" autocomplete="current-password" required minlength="12" maxlength="256">
<label for="new-pin">Novi web PIN (4–6 znamenki)</label>
<input id="new-pin" name="new_pin" type="password" inputmode="numeric" pattern="[0-9]{4,6}" minlength="4" maxlength="6" data-numeric-pin autocomplete="new-password" required>
<button class="primary-action" type="submit">Postavi PIN i poveži račun</button>
</form>
</section>
<div class="account-links"><a href="identify.php">Prijava</a><a href="register.php">Nova registracija</a><a href="privacy.php">Privatnost</a></div>
</main></body></html>
