<?php
declare(strict_types=1);
// Deterministic presentation-only test; never opens an authenticated session.
if(PHP_SAPI!=='cli')exit(2);
require_once __DIR__.'/../../web/ui-shell.php';
$pages=['dashboard','documents','business','finance','tax-reports'];
$routes=['dashboard.php','documents.php','business.php','finance.php','tax-reports.php'];
foreach($pages as $active){
    ob_start();
    fakturivaPortalNavigation($active);
    $markup=(string)ob_get_clean();
    if(substr_count($markup,'<a href=')!==5 ||
       substr_count($markup,'aria-current="page"')!==1 ||
       !str_contains($markup,'href="'.$active.'.php" aria-current="page"'))
        throw new RuntimeException('Navigation lost unique active state: '.$active);
    foreach($routes as $route){
        if(!str_contains($markup,'href="'.$route.'"'))
            throw new RuntimeException('Missing trusted route '.$route);
    }
    if(str_contains($markup,'http://')||str_contains($markup,'https://')||
       !str_contains($markup,'aria-label="Poslovna navigacija"'))
        throw new RuntimeException('Unsafe link or inaccessible nav');
}
foreach($pages as $page){
    $path=__DIR__.'/../../web/'.$page.'.php';
    $contents=file_get_contents($path);
    if(!is_string($contents)||
       !str_contains($contents,"require_once __DIR__.'/ui-shell.php';")||
       !str_contains($contents,"fakturivaPortalNavigation('".$page."')"))
        throw new RuntimeException('Business page did not include shared shell: '.$page);
}
$css=file_get_contents(__DIR__.'/../../web/assets/app.css');
foreach(['--f-app-canvas:#f6f8fc','--f-app-blue:#5566f8',
         '.portal-tabs a[aria-current="page"]','@media(max-width:520px)',
         '.portal-tabs{display:none!important}'] as $selector){
    if(!str_contains((string)$css,$selector))
        throw new RuntimeException('Missing responsive design token '.$selector);
}
echo 'Browser/Windows business navigation routes and parity tokens: OK'.PHP_EOL;
