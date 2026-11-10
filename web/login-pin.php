<?php
declare(strict_types=1);
// Retired legacy endpoint. Never display or accept alternate PIN credentials.
// Preserve existing bookmarks without exposing the historical login interface.
require __DIR__.'/bootstrap.php';
gotoPage('login.php');
