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
    'register'=>'register.php','login-password'=>'login.php',
    'set-sync'=>'dashboard.php','logout'=>'login.php',default=>'login.php'
};
$username='';
try{
    checkCsrf();
    $username=trim((string)($_POST['username']??''));
    $currentPassword=(string)($_POST['current_password']??'');
    $passphrase=(string)($_POST['passphrase']??'');
    $ip=(string)($_SERVER['REMOTE_ADDR']??'');
    if($mode==='register'){
        // Only new browser registrations require an actual email address.
        // Legacy username-based accounts remain able to sign in.
        if(filter_var($username,FILTER_VALIDATE_EMAIL)===false)
            throw new FakturivaSelfHost\HttpFailure(422,'Unesite ispravnu e-adresu.');
        $confirm=(string)($_POST['confirm_passphrase']??'');
        if($confirm==='' || !hash_equals($passphrase,$confirm))
            throw new FakturivaSelfHost\HttpFailure(422,
                'Lozinke se ne podudaraju.');
        $result=FakturivaSelfHost\webRegister($username,"",$ip,$passphrase);
        adoptWebLogin($result);
        flash('Račun je uspješno registriran.');
        gotoPage('dashboard.php');
    }
    if($mode==='login-password'){
        $result=FakturivaSelfHost\webLoginPassword($username,$passphrase,$ip);
        adoptWebLogin($result);
        // Remember only the username after an explicit opt-in, never a secret.
        // Host-only first-party functional cookie; no analytics or third party.
        $testHttp=PHP_SAPI==='cli-server' &&
            getenv('FAKTURIVA_TEST_HTTP')==='1' &&
            in_array($ip,['127.0.0.1','::1'],true);
        $remember=($_POST['remember']??'')==='yes';
        setcookie('FakturivaRemember',$remember?$username:'',[
            'expires'=>$remember?time()+60*86400:time()-3600,
            'path'=>'/','secure'=>!$testHttp,'httponly'=>true,'samesite'=>'Lax']);
        gotoPage('dashboard.php');
    }
    if($mode==='set-sync'){
        $account=requireWeb();
        $webId=(string)$_SESSION['web_id'];
        $webToken=(string)$_SESSION['web_token'];
        FakturivaSelfHost\webChangeAndroidPassphrase(
            $webId,$webToken,$currentPassword,$passphrase);
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
if($mode==='login-password'&&$username!==''&&strlen($username)<81)
    $_SESSION['last_username']=$username;
gotoPage($redirect);
