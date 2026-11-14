<?php
declare(strict_types=1);
namespace FakturivaWebBusiness;

/** Render a read-only browser-printable document from the authenticated FAK1 snapshot. */
function printField(mixed $value):string {
    return htmlspecialchars(is_scalar($value)?(string)$value:'',
        ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8');
}
function printCents(int $cents):string {
    $negative=$cents<0;
    $absolute=$negative?-$cents:$cents;
    $whole=preg_replace('/\\B(?=(\\d{3})+(?!\\d))/', '.',(string)intdiv($absolute,100));
    return ($negative?'-':'').$whole.','.str_pad((string)($absolute%100),2,'0',STR_PAD_LEFT);
}
function printQuantity(int $milli):string {
    $whole=intdiv($milli,1000);
    $fraction=$milli%1000;
    return $fraction===0?(string)$whole:
        $whole.','.rtrim(str_pad((string)$fraction,3,'0',STR_PAD_LEFT),'0');
}
function printTax(int $code):string {
    $basisPoints=$code>=10000?$code-10000:$code*100;
    return intdiv($basisPoints,100).
        ($basisPoints%100===0?'':','.rtrim(str_pad((string)($basisPoints%100),2,'0',STR_PAD_LEFT),'0')).'%';
}
function printDocumentPreview(array $doc,array $lines):string {
    $type=($doc['type']??null)==='QUOTE'?'Ponuda':'Račun';
    $draft=($doc['status']??null)==='DRAFT';
    $id=(int)($doc['id']??0);
    $number=$draft?'Nacrt #'.$id:(string)($doc['number']??('Dokument #'.$id));
    $currency=(string)($doc['currency_code']??'EUR');
    $out='<section class="document-print-sheet" aria-label="'.printField($type.' '.$number).'">';
    $out.='<header class="document-print-header"><div><p>FAKTURIVA · BRENDIGO</p>'.
        '<h2>'.printField($type).'</h2><strong>'.printField($number).'</strong></div>'.
        '<div class="document-print-status">'.printField((string)($doc['status']??'')).'</div></header>';
    if($draft)$out.='<p class="document-print-draft">NACRT — nije izdan dokument</p>';
    $out.='<div class="document-print-parties"><div><h3>Izdavatelj</h3><strong>'.
        printField($doc['issuer_name']??'').'</strong><p>'.
        printField($doc['issuer_address']??'').'</p><p>OIB: '.
        printField($doc['issuer_oib']??'').'</p></div><div><h3>Primatelj</h3><strong>'.
        printField($doc['client_name']??'').'</strong><p>'.
        printField($doc['client_address']??'').'</p><p>OIB: '.
        printField($doc['client_oib']??'').'</p></div></div>';
    $out.='<dl class="document-print-dates"><div><dt>Datum izdavanja</dt><dd>'.
        printField($doc['issue_date']??'').'</dd></div><div><dt>Dospijeće</dt><dd>'.
        printField($doc['due_date']??'').'</dd></div><div><dt>Datum isporuke</dt><dd>'.
        printField($doc['supply_date']??'').'</dd></div></dl>';
    $out.='<div class="finance-table-wrap"><table class="finance-table document-print-table">'.
        '<thead><tr><th>Stavka</th><th>Količina</th><th>Cijena</th>'.
        '<th>PDV</th><th>Ukupno</th></tr></thead><tbody>';
    $sumNet=0;$sumTax=0;$count=0;
    foreach($lines as $line){
        if(!is_array($line)||!is_int($line['qty_milli']??null)||
           !is_int($line['unit_cents']??null)||
           !is_int($line['net_cents']??null)||
           !is_int($line['tax_cents']??null)||
           !is_int($line['tax_rate']??null))continue;
        $count++;
        $sumNet+=$line['net_cents'];$sumTax+=$line['tax_cents'];
        $out.='<tr><td>'.printField($line['name']??'').'</td><td>'.
            printField(printQuantity($line['qty_milli'])).'</td><td>'.
            printField(printCents($line['unit_cents'])).'</td><td>'.
            printField(printTax($line['tax_rate'])).'</td><td>'.
            printField(printCents($line['net_cents']+$line['tax_cents'])).'</td></tr>';
    }
    $out.='</tbody></table></div>';
    if($count===0)$out.='<p class="error-note">Stavke dokumenta nisu dostupne u preslici.</p>';
    if($count>0 && ($sumNet!==($doc['net_cents']??null)||
        $sumTax!==($doc['tax_cents']??null)||
        $sumNet+$sumTax!==($doc['total_cents']??null)))
        $out.='<p class="error-note">Upozorenje: stavke i spremljeni ukupni iznos nisu usklađeni.</p>';
    $out.='<dl class="document-print-totals"><div><dt>Osnovica</dt><dd>'.
        printField(printCents((int)($doc['net_cents']??0))).'</dd></div>'.
        '<div><dt>PDV</dt><dd>'.printField(printCents((int)($doc['tax_cents']??0))).'</dd></div>'.
        '<div class="document-print-grand"><dt>Ukupno</dt><dd>'.
        printField(printCents((int)($doc['total_cents']??0))).' '.printField($currency).
        '</dd></div></dl>';
    $notes=(string)($doc['notes']??'');
    if($notes!=='')$out.='<p class="document-print-notes"><strong>Napomena:</strong> '.
        nl2br(printField($notes)).'</p>';
    return $out.'<p class="document-print-disclaimer">Ispis iz poslovne preslike. '.
        'Nije potvrda fiskalizacije ni strukturirani eRačun.</p></section>';
}
