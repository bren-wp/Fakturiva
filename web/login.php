<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
if(webCurrent()!==null)gotoPage('dashboard.php');
if(!isset($_SESSION['last_username'])||!is_string($_SESSION['last_username'])||
   $_SESSION['last_username']==='')gotoPage('identify.php');
$username=(string)$_SESSION['last_username'];
$error=takeFlash();
?><!doctype html>
<html lang="hr"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff">
<meta name="robots" content="noindex,nofollow">
<title>Prijava PIN-om — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
<script src="assets/auth.js" defer></script>
</head><body>
<main class="auth-page auth-login">
<header class="auth-head">
<?=logoMarkup()?>
<h1>Prijava PIN-om</h1>
<p class="subtitle">Unesite svoj sigurnosni PIN za pristup računu.</p>
</header>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<form class="auth-form" method="post" action="auth.php" autocomplete="off" data-auth-form>
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="login">
<input type="hidden" name="username" value="<?=h($username)?>">
<label class="pin-label" for="login-pin">PIN</label>
<div class="pin-wrap">
<input class="pin-entry" id="login-pin" type="password" inputmode="none"
 name="pin" minlength="4" maxlength="6" pattern="[0-9]{4,6}" required
 aria-label="Sigurnosni PIN" autocomplete="off" data-numeric-pin data-keypad-input>
<svg class="pin-lock" viewBox="0 0 24 27" fill="none" aria-hidden="true">
<rect x="3.5" y="12" width="17" height="13" rx="2" stroke="currentColor" stroke-width="2"/>
<path d="M7.7 12V8.2a4.3 4.3 0 0 1 8.6 0V12" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>
</svg>
</div>
<div class="pin-grid" role="group" aria-label="Numerička tipkovnica PIN-a" data-keypad>
<?php foreach([1,2,3,4,5,6,7,8,9] as $digit): ?>
<button class="pin-key" type="button" data-digit="<?=$digit?>" aria-label="Znamenka <?=$digit?>"><?=$digit?></button>
<?php endforeach; ?>
<button class="pin-key is-delete" type="button" data-backspace aria-label="Obriši posljednju znamenku">
<svg viewBox="0 0 35 27" aria-hidden="true">
<path d="M12 3H31a3 3 0 0 1 3 3v15a3 3 0 0 1-3 3H12L2 13.5 12 3Z"/>
<path d="m18 10 8 8m0-8-8 8"/>
</svg>
</button>
<button class="pin-key" type="button" data-digit="0" aria-label="Znamenka 0">0</button>
<span class="pin-key is-spacer" aria-hidden="true"></span>
</div>
<button class="primary-action" type="submit" data-login-submit>
<svg viewBox="0 0 28 28" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"><path d="m10 5 9 9-9 9"/></svg>
Prijavi se
</button>
</form>
<?=footerMarkup()?>
<div class="detail-entry"><a href="identify.php" aria-label="Odaberi drugi korisnički račun">Promijeni korisnika</a></div>
</main>
</body></html>
