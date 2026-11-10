<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
if(webCurrent()!==null)gotoPage('dashboard.php');
$error=takeFlash();
$remembered=$_COOKIE['FakturivaRemember']??'';
if(!is_string($remembered)||strlen($remembered)>80)$remembered='';
?><!doctype html>
<html lang="hr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff"><meta name="robots" content="noindex,nofollow">
<title>Prijava — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
<script src="assets/auth.js" defer></script></head>
<body><main class="auth-page auth-reference-login">
<header class="auth-head">
<?=logoMarkup()?>
<h1>Dobro došli natrag</h1>
<p class="subtitle">Prijavite se i nastavite s poslovanjem.</p>
</header>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<form class="auth-form" method="post" action="auth.php" data-auth-form>
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="login-password">
<label class="input-label" for="email">E-adresa</label>
<div class="field-wrap">
<input id="email" name="username" type="text" minlength="3" maxlength="80"
 autocomplete="username" autocapitalize="none" spellcheck="false" required
 placeholder="ime@tvrtka.hr" value="<?=h($remembered)?>">
<svg class="field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
<rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="1.7"/>
<path d="m4 7 8 6 8-6" stroke="currentColor" stroke-width="1.7"/>
</svg></div>
<label class="input-label password-label" for="password">Lozinka</label>
<div class="field-wrap">
<input id="password" name="passphrase" type="password" minlength="12" maxlength="256"
 autocomplete="current-password" required aria-label="Lozinka">
<svg class="field-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
<rect x="4" y="10" width="16" height="12" rx="2" stroke="currentColor" stroke-width="1.7"/>
<path d="M8 10V7a4 4 0 0 1 8 0v3" stroke="currentColor" stroke-width="1.7"/>
</svg></div>
<div class="login-actions">
<label class="remember"><input type="checkbox" name="remember" value="yes"
 <?=$remembered!==''?'checked':''?>> Zapamti me</label>
<a href="password-help.php">Zaboravili ste lozinku?</a>
</div>
<button class="primary-action" type="submit">
<svg viewBox="0 0 28 28" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><path d="m10 5 9 9-9 9"/></svg>
Prijavi se</button>
</form>
<nav class="auth-switch"><span>Nemate račun?</span>
<a href="register.php">Registrirajte se</a></nav>
<?=footerMarkup()?>
</main></body></html>
