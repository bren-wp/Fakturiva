<?php
declare(strict_types=1);
require_once __DIR__.'/lib.php';

try{FakturivaSelfHost\enforceHttps();}
catch(Throwable $error){
    http_response_code(403);
    header('Content-Type: text/plain; charset=utf-8');
    exit("Za pristup Fakturivi potrebna je HTTPS veza.");
}
FakturivaSelfHost\ensureStorage();
$sessionDir=FakturivaSelfHost\storage().'/sessions';
if(!is_dir($sessionDir) && !mkdir($sessionDir,0700,true) && !is_dir($sessionDir)){
    http_response_code(500);exit('Session storage unavailable');
}
ini_set('session.use_strict_mode','1');
ini_set('session.use_only_cookies','1');
ini_set('session.cookie_httponly','1');
// Insecure cookies are allowed ONLY on the isolated localhost CLI test server.
// All actual hosting requests require secure cookies over verified HTTPS.
$testHttp=PHP_SAPI==='cli-server' && getenv('FAKTURIVA_TEST_HTTP')==='1' &&
    in_array(($_SERVER['REMOTE_ADDR']??''),['127.0.0.1','::1'],true);
ini_set('session.cookie_secure',$testHttp?'0':'1');
ini_set('session.gc_maxlifetime','1800');
session_save_path($sessionDir);
session_name('FakturivaWeb');
$cookiePath=rtrim(str_replace('\\','/',dirname($_SERVER['SCRIPT_NAME']??'/')),'/');
$cookiePath=($cookiePath===''?'/':$cookiePath.'/');
session_set_cookie_params([
    'lifetime'=>0,'path'=>$cookiePath,'secure'=>!$testHttp,
    'httponly'=>true,'samesite'=>'Strict'
]);
session_start();
header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');
header('Permissions-Policy: camera=(), microphone=(), geolocation=()');
header("Content-Security-Policy: default-src 'none'; style-src 'self'; script-src 'self'; img-src 'self'; font-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'; connect-src 'self'");

function h(string $value):string{
    return htmlspecialchars($value,ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8');
}
function csrf():string{
    if(!isset($_SESSION['csrf']) || !is_string($_SESSION['csrf']))
        $_SESSION['csrf']=bin2hex(random_bytes(32));
    return $_SESSION['csrf'];
}
function checkCsrf():void{
    $token=$_POST['csrf']??null;
    if(!is_string($token)||!hash_equals(csrf(),$token))
        throw new FakturivaSelfHost\HttpFailure(403,'Nevaljana ili istekla potvrda obrasca.');
}
function gotoPage(string $path):never{
    // Only static site-relative destinations. No open redirects.
    if(!in_array($path,['index.php','login.php','register.php','dashboard.php',
                        'link.php','delete-account.php'],true))
        throw new \RuntimeException('Unsafe redirect');
    header('Location: '.$path,true,303);exit;
}
function flash(string $message):void{$_SESSION['flash']=$message;}
function takeFlash():string{
    $message=$_SESSION['flash']??'';
    unset($_SESSION['flash']);
    return is_string($message)?$message:'';
}
function webCurrent():?array{
    $id=$_SESSION['web_id']??null;
    $token=$_SESSION['web_token']??null;
    if(!is_string($id)||!is_string($token))return null;
    if((int)($_SESSION['web_last_seen']??0)<time()-1800){
        unset($_SESSION['web_id'],$_SESSION['web_token']);
        return null;
    }
    try{
        $account=FakturivaSelfHost\webAuthorized($id,$token);
        $_SESSION['web_last_seen']=time();
        return $account;
    }catch(Throwable $error){
        unset($_SESSION['web_id'],$_SESSION['web_token']);
        return null;
    }
}
function requireWeb():array{
    $account=webCurrent();
    if($account===null)gotoPage('login.php');
    return $account;
}
function adoptWebLogin(array $result):void{
    session_regenerate_id(true);
    $_SESSION['web_id']=$result['id'];
    $_SESSION['web_token']=$result['token'];
    $_SESSION['web_last_seen']=time();
    $_SESSION['last_username']=$result['username'];
    $_SESSION['csrf']=bin2hex(random_bytes(32));
}
function logoMarkup():string{
    return '<img class="brand-mark" src="assets/mark.svg" width="72" height="72" alt="Fakturiva">';
}
function footerMarkup():string{
    return '<footer class="auth-footer"><svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="m12 2 8 4v6c0 5-3.4 8.2-8 10-4.6-1.8-8-5-8-10V6z" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="m8.5 12 2.3 2.3 4.6-4.8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg><span>Zaštita vaših poslovnih podataka</span></footer>';
}
