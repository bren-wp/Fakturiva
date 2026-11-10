<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
$account=webCurrent();
$message='';
$complete=false;
if(($_SERVER['REQUEST_METHOD']??'')==='POST'){
    try {
        checkCsrf();
        if(($_POST['confirm']??'')!=='DELETE')
            throw new FakturivaSelfHost\HttpFailure(422,'Unesite riječ DELETE za potvrdu.');
        if($account!==null){
            FakturivaSelfHost\webDeleteAccount((string)$_SESSION['web_id'],
                (string)$_SESSION['web_token'],(string)($_POST['current_password']??''));
        }else{
            FakturivaSelfHost\deleteAccount((string)($_POST['username']??''),
                (string)($_POST['passphrase']??''));
        }
        unset($_SESSION['web_id'],$_SESSION['web_token'],
              $_SESSION['web_last_seen'],$_SESSION['last_username']);
        session_regenerate_id(true);
        $_SESSION['csrf']=bin2hex(random_bytes(32));
        $complete=true;
        $message='Korisnički račun i pripadajuća poslužiteljska pohrana trajno su uklonjeni.';
    } catch(Throwable $error) {
        error_log('Fakturiva account deletion attempt: '.get_class($error));
        $message='Brisanje nije uspjelo. Provjerite podatke i pokušajte ponovno.';
    }
}
?><!doctype html><html lang="hr">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1,viewport-fit=cover">
<meta name="robots" content="noindex,nofollow"><meta name="theme-color" content="#ffffff">
<title>Brisanje računa — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css">
<script src="assets/auth.js" defer></script>
</head><body>
<main class="account-page">
<?=logoMarkup()?>
<h1>Brisanje korisničkog računa</h1>
<p>Ovaj obrazac trajno briše <strong>račun na ovoj domeni</strong>,
njegove poslužiteljske sesije i šifriranu sigurnosnu kopiju.
Podaci u Android aplikaciji na vašem uređaju ne brišu se automatski.</p>
<p>Prije brisanja preuzmite kopiju dokumenata koje prema primjenjivim
zakonima morate čuvati.</p>
<?php if($message!==''):?><p class="<?=$complete?'success-note':'error-note'?>" role="status"><?=h($message)?></p><?php endif;?>
<?php if(!$complete):?>
<section class="panel">
<form action="delete-account.php" method="post" autocomplete="off" data-auth-form>
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<?php if($account!==null):?>
<p>Prijavljeni korisnik: <strong><?=h((string)$account['username'])?></strong></p>
<label for="current-password">Lozinka računa za potvrdu brisanja</label>
<input type="password" id="current-password" name="current_password" minlength="12" maxlength="256" required autocomplete="current-password">
<?php else:?>
<p>Ako nemate otvorenu web sesiju, možete potvrditi brisanje
svojim korisničkim imenom i <strong>zasebnom lozinkom hostinga</strong>.</p>
<label for="username">Korisničko ime</label>
<input id="username" name="username" required minlength="3" maxlength="80" autocomplete="username">
<label for="passphrase">Lozinka računa</label>
<input type="password" id="passphrase" name="passphrase" required autocomplete="current-password">
<?php endif;?>
<label for="confirm">Upišite DELETE za potvrdu</label>
<input id="confirm" name="confirm" required pattern="DELETE">
<button class="danger" type="submit">Trajno izbriši račun i njegove podatke</button>
</form>
</section>
<?php endif;?>
<nav class="account-links" aria-label="Povratak">
<a href="<?= $account!==null&&!$complete?'dashboard.php':'login.php' ?>">Natrag</a>
<a href="privacy.php">Pravila privatnosti</a>
</nav>
</main></body></html>
