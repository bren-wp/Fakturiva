<?php
declare(strict_types=1);
require __DIR__.'/bootstrap.php';
if(webCurrent()!==null)gotoPage('dashboard.php');
gotoPage('login.php');
