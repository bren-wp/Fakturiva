<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
use FakturivaSelfHost\HttpFailure;

$hash=getenv('FAKTURIVA_ADMIN_PASSWORD_HASH');
if(!is_string($hash)||$hash===''){
    http_response_code(503);
    exit('Administracija nije konfigurirana. Postavite FAKTURIVA_ADMIN_PASSWORD_HASH na serveru.');
}
$flash=takeFlash();
$error='';
$active=isset($_SESSION['admin_until']) &&
        is_int($_SESSION['admin_until'])&&$_SESSION['admin_until']>time();
if(!$active)unset($_SESSION['admin_until']);
if(($_SERVER['REQUEST_METHOD']??'GET')==='POST'){
    try{
        checkCsrf();
        $action=(string)($_POST['action']??'');
        if($action==='login'){
            FakturivaSelfHost\transaction(function()use($hash):void{
                $ip=(string)($_SERVER['REMOTE_ADDR']??'');
                FakturivaSelfHost\rateLimit($ip,false);
                if(!password_verify((string)($_POST['admin_password']??''),$hash)){
                    FakturivaSelfHost\rateLimit($ip,true);
                    throw new HttpFailure(401,'Neispravne administratorske vjerodajnice.');
                }
            });
            session_regenerate_id(true);
            $_SESSION['admin_until']=time()+900;
            $_SESSION['csrf']=bin2hex(random_bytes(32));
            header('Location: admin.php',true,303);exit;
        }
        if($action==='logout'){
            unset($_SESSION['admin_until']);
            session_regenerate_id(true);
            header('Location: admin.php',true,303);exit;
        }
        if(!$active)throw new HttpFailure(401,'Administratorska sesija je istekla.');
        if(!in_array($action,['tier','suspend','restore','delete'],true))
            throw new HttpFailure(422,'Nepoznata radnja');
        $id=(string)($_POST['account_id']??'');
        if($action==='delete'){
            // Destructive action requires a fresh admin password and exact text.
            if(($_POST['confirm']??'')!=='IZBRIŠI'||
                !password_verify((string)($_POST['admin_password']??''),$hash))
                throw new HttpFailure(403,'Za brisanje potvrdite administratorsku lozinku.');
        }
        FakturivaSelfHost\adminManageAccount($id,$action,
            (string)($_POST['tier']??''));
        $_SESSION['admin_until']=time()+900;
        flash($action==='delete'?'Korisnički račun je uklonjen.':'Promjena je spremljena.');
        header('Location: admin.php',true,303);exit;
    }catch(HttpFailure $failure){
        http_response_code($failure->statusCode);
        $error=$failure->getMessage();
    }catch(Throwable $failure){
        error_log('Fakturiva administration: '.get_class($failure));
        http_response_code(500);$error='Administratorska radnja nije uspjela.';
    }
}
$active=isset($_SESSION['admin_until']) &&
    is_int($_SESSION['admin_until'])&&$_SESSION['admin_until']>time();
$accounts=$active?FakturivaSelfHost\adminReadAccounts():[];
?><!doctype html><html lang="hr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff"><meta name="robots" content="noindex,nofollow">
<title>Administracija — Fakturiva</title>
<link rel="icon" href="assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="assets/app.css"></head>
<body><main class="account-page">
<?=logoMarkup()?><h1>Administracija korisničkih računa</h1>
<?php if($flash!==''):?><p class="success-note"><?=h($flash)?></p><?php endif;?>
<?php if($error!==''):?><p class="error-note" role="alert"><?=h($error)?></p><?php endif;?>
<?php if(!$active):?>
<section class="panel">
<h2>Prijava administratora</h2>
<p>Administratorska lozinka određuje se zasebno na serveru. Nema tvorničke lozinke.</p>
<form method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="login">
<label for="admin-password">Administratorska lozinka</label>
<input id="admin-password" type="password" name="admin_password"
autocomplete="current-password" required minlength="16">
<button class="primary-action" type="submit">Prijava administratora</button>
</form></section>
<?php else:?>
<p>Ovaj panel prikazuje samo korisnička imena, status i podatke potrebne za
administraciju. Sadržaj šifriranih računa, ponuda, OIB-ova i drugih
poslovnih dokumenata nije dostupan administratoru.</p>
<section class="panel"><h2>Korisnički računi (<?=count($accounts)?>)</h2>
<div class="finance-table-wrap"><table class="finance-table">
<thead><tr><th>Korisnik</th><th>Tarifa</th><th>Status</th><th>Revizija</th>
<th>Pohranjeno</th><th>Upravljanje</th></tr></thead><tbody>
<?php foreach($accounts as $account):?>
<tr>
<td><?=h($account['username'])?>
<small><?=h($account['created_at'])?></small></td>
<td><?=h($account['tier'])?></td>
<td><?=$account['suspended']?'Zabranjen pristup':'Aktivan'?></td>
<td><?=$account['revision']?></td>
<td><?=$account['backup_present']?
h((string)$account['backup_bytes']).' B':'Nema kopije'?></td>
<td>
<form method="post" class="admin-inline">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="account_id" value="<?=h($account['id'])?>">
<input type="hidden" name="action" value="tier">
<label for="tier-<?=h($account['id'])?>">Tarifa</label>
<select id="tier-<?=h($account['id'])?>" name="tier">
<?php foreach(['FREE','PRO','BUSINESS'] as $tier):?>
<option value="<?=$tier?>" <?=$account['tier']===$tier?'selected':''?>><?=$tier?></option>
<?php endforeach;?></select>
<button type="submit">Spremi</button></form>
<form method="post" class="admin-inline">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="account_id" value="<?=h($account['id'])?>">
<input type="hidden" name="action" value="<?=$account['suspended']?'restore':'suspend'?>">
<button type="submit"><?=$account['suspended']?'Omogući':'Zabrani pristup'?></button>
</form>
<details class="admin-delete"><summary>Brisanje računa</summary>
<form method="post" autocomplete="off">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="account_id" value="<?=h($account['id'])?>">
<input type="hidden" name="action" value="delete">
<label>Upišite IZBRIŠI
<input type="text" name="confirm" required autocomplete="off"></label>
<label>Potvrdite administratorsku lozinku
<input type="password" name="admin_password" required autocomplete="off"></label>
<button type="submit" class="danger">Izbriši korisnika</button>
</form></details>
</td></tr>
<?php endforeach;?></tbody></table></div>
</section>
<section class="panel"><h2>Sigurnost i pohrana</h2>
<p>Server se mora zasebno kopirati, zajedno s ključem za šifriranje.
Periodično testirajte obnovu i zaštitite pristup ovom panelu dodatnim
slojem na razini hostinga. Zabranjen pristup nije zamjena za brisanje
računa na zahtjev korisnika.</p></section>
<form method="post">
<input type="hidden" name="csrf" value="<?=h(csrf())?>">
<input type="hidden" name="action" value="logout">
<button type="submit" class="secondary">Odjava administratora</button></form>
<?php endif;?>
</main></body></html>
