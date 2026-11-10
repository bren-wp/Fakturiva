<?php
declare(strict_types=1);
require_once __DIR__.'/lib.php';

use function FakturivaSelfHost\enforceHttps;
use function FakturivaSelfHost\fail;

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('X-Frame-Options: DENY');

try {
    enforceHttps();
    if($_SERVER['REQUEST_METHOD']==='GET' &&
        (($_GET['action']??'')==='health')) {
        echo json_encode(['ok'=>true,'service'=>'fakturiva-self-host',
            'version'=>1],JSON_THROW_ON_ERROR);
        exit;
    }
    if($_SERVER['REQUEST_METHOD']!=='POST') {
        header('Allow: POST, GET');
        fail(405,'Method not allowed');
    }
    $type=strtolower(trim(explode(';',$_SERVER['CONTENT_TYPE']??'')[0]));
    if($type!=='application/json')fail(415,'JSON request required');
    if((int)($_SERVER['CONTENT_LENGTH']??0)>36000000)
        fail(413,'Request too large');
    $raw=file_get_contents('php://input',false,null,0,36000001);
    if($raw===false||strlen($raw)>36000000)fail(413,'Request too large');
    $payload=json_decode($raw,true,32,JSON_THROW_ON_ERROR);
    if(!is_array($payload))fail(400,'Invalid JSON body');
    $action=$payload['action']??'';
    $username=(string)($payload['username']??'');
    $password=(string)($payload['passphrase']??'');
    $address=(string)($_SERVER['REMOTE_ADDR']??'');
    $response=match($action) {
        'register'=>registerEmailAccount($username,$password,$address),
        'login'=>FakturivaSelfHost\signIn($username,$password,$address),
        'load'=>FakturivaSelfHost\loadBackup(authToken()),
        'save'=>FakturivaSelfHost\saveBackup(
            authToken(),(string)($payload['backup_b64']??''),
            requireRevision($payload['expected_revision']??null)),
        'restore_revision'=>FakturivaSelfHost\restoreEncryptedRevision(
            authToken(),requireRevision($payload['revision']??null),
            requireRevision($payload['expected_revision']??null)),
        'logout'=>FakturivaSelfHost\signOut(authToken()),
        'delete'=>deleteAuthenticated($password),
        default=>throw new FakturivaSelfHost\HttpFailure(404,'Unknown action')
    };
    echo json_encode(['ok'=>true,'data'=>$response],
        JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);
} catch(FakturivaSelfHost\HttpFailure $e) {
    http_response_code($e->statusCode);
    echo json_encode(['ok'=>false,'error'=>$e->getMessage()],
        JSON_THROW_ON_ERROR|JSON_UNESCAPED_UNICODE);
} catch(\JsonException $e) {
    http_response_code(400);
    echo '{"ok":false,"error":"Invalid JSON"}';
} catch(\Throwable $e) {
    error_log('Fakturiva self-host failure: '.get_class($e));
    http_response_code(500);
    echo '{"ok":false,"error":"Internal server error"}';
}

function requireRevision(mixed $value): int {
    if(!is_int($value)||$value<0||$value>2147483646)
        throw new FakturivaSelfHost\HttpFailure(422,'Valid expected_revision required');
    return $value;
}
function authToken(): string {
    $header=$_SERVER['HTTP_AUTHORIZATION']??
        $_SERVER['REDIRECT_HTTP_AUTHORIZATION']??'';
    if(!is_string($header)||!preg_match('/^Bearer (.+)$/iD',$header,$m))
        throw new FakturivaSelfHost\HttpFailure(401,'Bearer session required');
    return $m[1];
}
function deleteAuthenticated(string $passphrase): array {
    FakturivaSelfHost\deleteAuthorized(authToken(),$passphrase);
    return ['deleted'=>true];
}

/** New public API identities must be email addresses. Existing legacy usernames
 * remain valid for login and account recovery, never for a new registration. */
function registerEmailAccount(string $username,string $password,string $address): array {
    if ($username!==trim($username) || strlen($username)>80 ||
        filter_var($username,FILTER_VALIDATE_EMAIL)===false ||
        !preg_match('/^[A-Za-z0-9][A-Za-z0-9._+@-]{2,79}$/D',$username)) {
        fail(422,'Unesite valjanu e-adresu.');
    }
    return FakturivaSelfHost\signUp($username,$password,$address);
}
