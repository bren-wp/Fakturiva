<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
if(webCurrent()!==null)gotoPage('dashboard.php');
$error=takeFlash();
?><!doctype html>
<html lang="hr"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff"><meta name="robots" content="noindex,nofollow">
<title>Odabir računa — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
</head><body>
<main class="auth-page auth-register">
<header class="auth-head"><?=logoMarkup()?>
<h1>Prijava PIN-om</h1><p class="subtitle">Odaberite korisnički račun na ovom hostingu.</p></header>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<form class="auth-form" action="auth.php" method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="identify">
<label class="input-label" for="username">Korisničko ime</label>
<div class="field-wrap">
<input id="username" name="username" type="text" minlength="3" maxlength="80" autocomplete="username" autocapitalize="none" spellcheck="false" required placeholder="ime@tvrtka.hr">
</div>
<button class="primary-action" type="submit">Nastavi na prijavu PIN-om</button>
</form>
<nav class="auth-switch"><span>Nemate račun?</span><a href="register.php">Registrirajte se</a></nav>
<p class="form-prompt">Račun već koristite u Android aplikaciji?
<a href="link.php">Povežite Android račun</a>.</p>
<?=footerMarkup()?>
</main></body></html>
