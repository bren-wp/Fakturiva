<?php
declare(strict_types=1);

// This fixture is fully isolated from installed customer records and secrets.
$fixtureRoot=sys_get_temp_dir().'/fakturiva-storage-test-'.bin2hex(random_bytes(12));
if(!mkdir($fixtureRoot,0700))throw new RuntimeException('Cannot create fixture root');
putenv('FAKTURIVA_STORAGE_ROOT='.$fixtureRoot);
require __DIR__.'/../../web/lib.php';

try {
    $path=FakturivaSelfHost\storage().'/accounts/fixture.json';
    $record=['id'=>'fixture','value'=>'Osjetljivi testni podaci',
             'amount_cents'=>12500,'revision'=>3];
    FakturivaSelfHost\writeRecord($path,$record);
    $stored=file_get_contents($path);
    if($stored===false || str_contains($stored,$record['value']))
        throw new RuntimeException('Encrypted record exposed plaintext');
    if(FakturivaSelfHost\readRecord($path)!==$record)
        throw new RuntimeException('Record did not roundtrip after storage refactor');
    $locked=FakturivaSelfHost\transaction(static function()use($path):array{
        return FakturivaSelfHost\readRecord($path);
    });
    if($locked!==$record)
        throw new RuntimeException('Atomic storage transaction did not retain data');
    $broken=$stored;
    $offset=strpos($broken,'"ciphertext":"');
    if($offset===false)throw new RuntimeException('Missing encrypted payload');
    $offset+=strlen('"ciphertext":"');
    $broken[$offset]=$broken[$offset]==='A'?'B':'A';
    try {
        FakturivaSelfHost\decryptRecord($broken);
        throw new RuntimeException('Tampered storage payload was accepted');
    } catch(RuntimeException $expected) {
        if($expected->getMessage()==='Tampered storage payload was accepted')
            throw $expected;
    } catch(JsonException $expected) {
        // Deliberately malformed ciphertext or envelope must be rejected.
    }
    if(FakturivaSelfHost\readRecord($path)!==$record)
        throw new RuntimeException('Tampering attempt damaged the original record');
    echo "Encrypted storage extraction, atomic write, tamper rejection: OK\n";
} finally {
    // Clean only this test-created sandbox. Never touch normal hosting storage.
    $iterator=new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($fixtureRoot,FilesystemIterator::SKIP_DOTS),
        RecursiveIteratorIterator::CHILD_FIRST);
    foreach($iterator as $entry){
        if($entry->isDir())rmdir($entry->getPathname());
        else unlink($entry->getPathname());
    }
    rmdir($fixtureRoot);
    putenv('FAKTURIVA_STORAGE_ROOT');
}
