<?php
declare(strict_types=1);
require_once __DIR__.'/lib.php';
FakturivaSelfHost\enforceHttps();
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');
session_name('fakturiva_delete_session');
session_set_cookie_params([
    'lifetime'=>0,'path'=>'/','secure'=>true,
    'httponly'=>true,'samesite'=>'Strict'
]);
session_start();
if(empty($_SESSION['csrf']))$_SESSION['csrf']=bin2hex(random_bytes(24));
$message='';
$complete=false;
if($_SERVER['REQUEST_METHOD']==='POST'){
    try{
        if(!hash_equals((string)$_SESSION['csrf'],(string)($_POST['csrf']??'')))
            throw new RuntimeException('Invalid form request.');
        if(($_POST['confirm']??'')!=='DELETE')
            throw new RuntimeException('Upišite DELETE za potvrdu brisanja.');
        FakturivaSelfHost\deleteAccount(
            (string)($_POST['username']??''),
            (string)($_POST['passphrase']??'')
        );
        $complete=true;
        $_SESSION['csrf']=bin2hex(random_bytes(24));
        $message='Korisnički račun i njegova poslužiteljska pohrana su izbrisani.';
    }catch(Throwable $error){
        $message='Brisanje nije uspjelo. Provjerite podatke i pokušajte ponovno.';
    }
}
function h(string $v):string {
    return htmlspecialchars($v,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8');
}
?><!doctype html>
<html lang="hr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<title>Brisanje računa — Fakturiva</title>
<style>
:root{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;color:#14263f;background:#f5f7fc}
*{box-sizing:border-box}main{max-width:520px;margin:8vh auto;padding:28px;background:#fff;border-radius:22px;box-shadow:0 14px 36px #101c3712}
h1{font-size:27px}p{line-height:1.55;color:#66748d}label{display:block;font-weight:600;margin:16px 0 7px}
input{display:block;width:100%;padding:13px;border-radius:12px;border:1px solid #cbd3e6;font-size:16px}
button{margin-top:22px;padding:15px;width:100%;border:0;border-radius:13px;background:#495fff;color:#fff;font-size:17px;font-weight:700;cursor:pointer}
.notice{padding:13px;background:#e9eeff;border-radius:12px}a{color:#425ae7}
</style>
</head><body><main>
<h1>Brisanje računa Fakturiva</h1>
<p>Ovdje možete trajno izbrisati svoj <strong>poslužiteljski račun</strong>,
spremljenu šifriranu kopiju i sesije na ovom hostingu. Lokalni podaci na Androidu
ne brišu se automatski — njima upravljajte u postavkama aplikacije.
Ako zakon zahtijeva čuvanje nekih poslovnih dokumenata, prije brisanja
spremite vlastitu kopiju.</p>
<?php if($message!==''): ?><p class="notice"><?=h($message)?></p><?php endif; ?>
<?php if(!$complete): ?>
<form method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h((string)$_SESSION['csrf'])?>">
<label for="username">Korisničko ime</label>
<input id="username" name="username" maxlength="80" required autocomplete="username">
<label for="passphrase">Poslužiteljska lozinka (nije lokalni PIN)</label>
<input id="passphrase" type="password" name="passphrase" required autocomplete="current-password">
<label for="confirm">Upišite DELETE za potvrdu</label>
<input id="confirm" name="confirm" required pattern="DELETE">
<button type="submit">Trajno izbriši poslužiteljski račun</button>
</form>
<?php endif; ?>
<p><a href="privacy.php">Pravila privatnosti</a></p>
</main></body></html>
