<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
if(($_SERVER['REQUEST_METHOD']??'')!=='POST'){
    header('Allow: POST');http_response_code(405);exit('Method not allowed');
}
if((int)($_SERVER['CONTENT_LENGTH']??0)>4096){
    http_response_code(413);exit('Request too large');
}
$mode=(string)($_POST['action']??'');
$redirect=match($mode){
    'register'=>'register.php','login'=>'login.php',
    'link'=>'link.php','set-sync'=>'dashboard.php','identify'=>'identify.php',
    'logout'=>'login.php',default=>'login.php'
};
$username='';
try{
    checkCsrf();
    $username=trim((string)($_POST['username']??''));
    $pin=(string)($_POST['pin']??'');
    $passphrase=(string)($_POST['passphrase']??'');
    $ip=(string)($_SERVER['REMOTE_ADDR']??'');
    if($mode==='identify'){
        FakturivaSelfHost\normalizeName($username);
        $_SESSION['last_username']=$username;
        gotoPage('login.php');
    }
    if($mode==='register'){
        $result=FakturivaSelfHost\webRegister($username,$pin,$ip,$passphrase);
        adoptWebLogin($result);
        flash('Račun je uspješno registriran.');
        gotoPage('dashboard.php');
    }
    if($mode==='login'){
        $result=FakturivaSelfHost\webLogin($username,$pin,$ip);
        adoptWebLogin($result);
        gotoPage('dashboard.php');
    }
    if($mode==='link'){
        $newPin=(string)($_POST['new_pin']??'');
        $result=FakturivaSelfHost\webLinkAndroidAccount(
            $username,$passphrase,$newPin,$ip);
        adoptWebLogin($result);
        flash('Web PIN je postavljen za postojeći račun.');
        gotoPage('dashboard.php');
    }
    if($mode==='set-sync'){
        $account=requireWeb();
        $webId=(string)$_SESSION['web_id'];
        $webToken=(string)$_SESSION['web_token'];
        FakturivaSelfHost\webChangeAndroidPassphrase(
            $webId,$webToken,$pin,$passphrase);
        flash('Lozinka za Android sinkronizaciju je ažurirana.');
        gotoPage('dashboard.php');
    }
    if($mode==='logout'){
        if(webCurrent()!==null){
            FakturivaSelfHost\webLogout(
                (string)$_SESSION['web_id'],(string)$_SESSION['web_token']);
        }
        unset($_SESSION['web_id'],$_SESSION['web_token'],$_SESSION['web_last_seen']);
        session_regenerate_id(true);
        $_SESSION['csrf']=bin2hex(random_bytes(32));
        flash('Odjavljeni ste.');
        gotoPage('login.php');
    }
    throw new FakturivaSelfHost\HttpFailure(400,'Nepoznata radnja');
}catch(FakturivaSelfHost\HttpFailure $error){
    flash($error->getMessage());
}catch(Throwable $error){
    error_log('Fakturiva web auth: '.get_class($error));
    flash('Zahtjev nije uspio. Pokušajte ponovno.');
}
if($mode==='login'&&$username!==''&&strlen($username)<81)
    $_SESSION['last_username']=$username;
gotoPage($redirect);
