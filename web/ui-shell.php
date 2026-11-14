<?php
declare(strict_types=1);

/**
 * Shared business navigation for mobile web, desktop browsers and the
 * Windows WebView2 shell. Routes are fixed local paths, never user inputs.
 * This is strictly presentation: authorization remains in each page.
 */
function fakturivaPortalNavigation(string $current):void {
    $items=[
        ['dashboard','Početna','dashboard.php'],
        ['documents','Dokumenti','documents.php'],
        ['business','Klijenti','business.php'],
        ['finance','Naplata','finance.php'],
        ['tax-reports','Izvještaji','tax-reports.php']
    ];
    echo '<nav class="portal-tabs" aria-label="Poslovna navigacija">';
    foreach($items as [$id,$label,$href]){
        echo '<a href="'.$href.'"'.
            ($current===$id?' aria-current="page"':'').'>'.$label.'</a>';
    }
    echo '</nav>';
}
