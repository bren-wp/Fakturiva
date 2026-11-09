<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
if(webCurrent()!==null)gotoPage('dashboard.php');
$error=takeFlash();
?><!doctype html>
<html lang="hr"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="color-scheme" content="light">
<meta name="theme-color" content="#ffffff">
<meta name="robots" content="noindex,nofollow">
<title>Registracija — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
<script src="assets/auth.js" defer></script>
</head><body>
<main class="auth-page auth-register">
<header class="auth-head">
<?=logoMarkup()?>
<h1>Registracija</h1>
<p class="subtitle">Kreirajte novi račun i započnite s poslovanjem.</p>
</header>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<form class="auth-form" method="post" action="auth.php" autocomplete="off" data-auth-form>
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="register">
<label class="input-label" for="username">E-adresa</label>
<div class="field-wrap">
<input id="username" name="username" required minlength="3" maxlength="80"
 type="email" inputmode="email" autocapitalize="none" autocomplete="email" spellcheck="false"
 placeholder="ime@tvrtka.hr">
<svg class="field-icon" viewBox="0 0 24 24" aria-hidden="true">
<circle cx="12" cy="7.5" r="4" fill="none" stroke="currentColor" stroke-width="1.7"/>
<path d="M3 21v-1.5c0-4 3.6-6.5 9-6.5s9 2.5 9 6.5V21H3Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>
</svg>
</div>
<label class="input-label" for="register-password">Lozinka</label>
<div class="field-wrap">
<input id="register-password" name="passphrase" required type="password"
 minlength="12" maxlength="256" autocomplete="new-password"
 placeholder="Najmanje 12 znakova" aria-describedby="password-help">
</div>
<p class="help-text" id="password-help">Snažna lozinka za web i Android omogućuje pristup
podacima nakon promjene uređaja. Pohranite je na sigurnom mjestu; Android dodatno koristi lokalni PIN.</p>
<button class="primary-action" type="submit">
<svg viewBox="0 0 28 28" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
<circle cx="11" cy="8" r="4.5"/><path d="M3 25v-2c0-5 3.3-8 8-8s8 3 8 8v2H3ZM22 9h6M25 6v6"/>
</svg>Registracija
</button>
</form>
<nav class="auth-switch" aria-label="Prijava">
<span>Već imate račun?</span><a href="login.php">Prijavite se</a>
</nav>
<?=footerMarkup()?>
</main>
</body></html>
