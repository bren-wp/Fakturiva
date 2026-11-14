<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli')exit(2);
require_once __DIR__.'/../../web/lib_document_print.php';

use function FakturivaWebBusiness\printCents;
use function FakturivaWebBusiness\printQuantity;
use function FakturivaWebBusiness\printTax;
use function FakturivaWebBusiness\printDocumentPreview;
use function FakturivaWebBusiness\canPrintDocument;

function printCheck(bool $ok,string $label):void {
    if(!$ok)throw new RuntimeException($label);
}
printCheck(printCents(123456789)==='1.234.567,89','Cent formatting lost precision');
printCheck(printCents(0)==='0,00','Zero formatting failed');
printCheck(printQuantity(1250)==='1,25','Milli-quantity formatting failed');
printCheck(printTax(11250)==='12,5%','Fractional tax code formatted incorrectly');

$doc=['id'=>4,'type'=>'INVOICE','status'=>'ISSUED','number'=>'R-2026-4',
    'currency_code'=>'EUR',
    'issuer_name'=>'<script>alert(1)</script>','issuer_address'=>'Synthetic street',
    'client_name'=>'<img src=x onerror=alert(1)>',
    'net_cents'=>1000,'tax_cents'=>250,'total_cents'=>1250,
    'notes'=>'Test & only','issue_date'=>'2026-11-14',
    'due_date'=>'2026-11-30','supply_date'=>'2026-11-14'];
$lines=[['id'=>2,'name'=>'Service <unsafe>','qty_milli'=>1000,
    'unit_cents'=>1000,'net_cents'=>1000,'tax_cents'=>250,'tax_rate'=>25]];
printCheck(canPrintDocument($doc,$lines),'Valid invoice cannot be printed');
$html=printDocumentPreview($doc,$lines);
foreach(['&lt;script&gt;','&lt;img src=x onerror=alert(1)&gt;',
         'Service &lt;unsafe&gt;','12,50 EUR','R-2026-4',
         'Nije potvrda fiskalizacije'] as $expected)
    printCheck(str_contains($html,$expected),'Missing escaped document detail');
foreach(['<script>','<img src=x','Service <unsafe>'] as $unsafe)
    printCheck(!str_contains($html,$unsafe),'HTML injection not escaped');
printCheck(!str_contains($html,'nisu usklađeni'),'Valid document flagged inconsistent');
$doc['status']='DRAFT';
$draft=printDocumentPreview($doc,$lines);
printCheck(str_contains($draft,'NACRT'),'Draft is missing non-issued warning');
$doc['total_cents']=99999;
printCheck(!canPrintDocument($doc,$lines),'Inconsistent totals were printable');
$invalid=printDocumentPreview($doc,$lines);
printCheck(str_contains($invalid,'nisu usklađeni'),
    'Inconsistent document totals were silently printed');
echo 'Read-only document print: integer sums, escaped fields and draft labeling OK'.PHP_EOL;
