<?php
declare(strict_types=1);
namespace FakturivaWebBusiness;

/** Render a read-only browser-printable document from the authenticated FAK1 snapshot. */
function printField(mixed $value):string {
    return htmlspecialchars(is_scalar($value)?(string)$value:'',
        ENT_QUOTES|ENT_SUBSTITUTE,'UTF-8');
}
function printCents(int $cents):string {
    // intdiv() prevents negating PHP_INT_MIN (whose absolute value cannot
    // be represented as a signed 64-bit integer).
    $whole=abs(intdiv($cents,100));
    $fraction=abs($cents%100);
    $grouped=preg_replace('/\\B(?=(\\d{3})+(?!\\d))/', '.',(string)$whole);
    return ($cents<0?'-':'').$grouped.','.str_pad((string)$fraction,2,'0',STR_PAD_LEFT);
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
/** Safe display of invalid remote fields without casting arbitrary values. */
function printSafeCents(mixed $value):string {
    return is_int($value)?printCents($value):'—';
}
/** Android-compatible, positive HALF_UP arithmetic without intermediate int overflow.
 * This is a read-only verifier, not the narrower web-form input validator.
 */
function printVerifiedLine(array $line):?array {
    $qty=$line['qty_milli']??null;
    $price=$line['unit_cents']??null;
    $savedNet=$line['net_cents']??null;
    $savedTax=$line['tax_cents']??null;
    $rate=$line['tax_rate']??null;
    if(!is_int($qty)||!is_int($price)||!is_int($savedNet)||
       !is_int($savedTax)||!is_int($rate)||
       $qty<1||$qty>1000000000||$price<0||
       $savedNet<0||$savedTax<0||
       !(($rate>=0&&$rate<=100)||($rate>=10001&&$rate<=20000)))
        return null;
    // qty * price / 1000 rounded half-up; split before multiplication.
    $majorPrice=intdiv($price,1000);
    if($majorPrice>intdiv(PHP_INT_MAX,$qty))return null;
    $major=$majorPrice*$qty;
    $minor=intdiv(($price%1000)*$qty+500,1000);
    if($major>PHP_INT_MAX-$minor)return null;
    $net=$major+$minor;
    $bp=$rate>=10000?$rate-10000:$rate*100;
    // net * basisPoints / 10000 rounded half-up.
    $large=intdiv($net,10000);
    if($bp!==0 && $large>intdiv(PHP_INT_MAX,$bp))return null;
    $taxMajor=$large*$bp;
    $taxMinor=intdiv(($net%10000)*$bp+5000,10000);
    if($taxMajor>PHP_INT_MAX-$taxMinor)return null;
    $tax=$taxMajor+$taxMinor;
    if($net!==$savedNet||$tax!==$savedTax||
       $net>PHP_INT_MAX-$tax)return null;
    return [$net,$tax];
}
/** Refuse a normal print action when amounts, parties or lines are incomplete. */
function canPrintDocument(array $doc,array $lines):bool {
    if(!in_array($doc['type']??null,['INVOICE','QUOTE'],true)||
       !is_string($doc['issuer_name']??null)||
       trim($doc['issuer_name'])===''||
       !is_string($doc['client_name']??null)||
       trim($doc['client_name'])===''||
       !is_string($doc['currency_code']??null)||
       !preg_match('/^[A-Z]{3}$/D',$doc['currency_code'])||
       !$lines)return false;
    $net=0;$tax=0;
    foreach($lines as $line){
        if(!is_array($line))return false;
        $verified=printVerifiedLine($line);
        if($verified===null)return false;
        [$lineNet,$lineTax]=$verified;
        if($net>PHP_INT_MAX-$lineNet||$tax>PHP_INT_MAX-$lineTax)
            return false;
        $net+=$lineNet;$tax+=$lineTax;
    }
    if($net>PHP_INT_MAX-$tax)return false;
    return $net===($doc['net_cents']??null)&&
        $tax===($doc['tax_cents']??null)&&
        $net+$tax===($doc['total_cents']??null);
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
    $count=0;
    foreach($lines as $line){
        if(!is_array($line))continue;
        $count++;
        $verified=printVerifiedLine($line);
        $qty=$line['qty_milli']??null;
        $rate=$line['tax_rate']??null;
        $validRate=is_int($rate)&&
            (($rate>=0&&$rate<=100)||($rate>=10001&&$rate<=20000));
        $out.='<tr><td>'.printField($line['name']??'').'</td><td>'.
            (is_int($qty)?printField(printQuantity($qty)):'—').'</td><td>'.
            printField(printSafeCents($line['unit_cents']??null)).'</td><td>'.
            ($validRate?printField(printTax($rate)):'—').'</td><td>'.
            ($verified!==null?printField(printCents($verified[0]+$verified[1])):'—').
            '</td></tr>';
    }
    $out.='</tbody></table></div>';
    if($count===0)$out.='<p class="error-note">Stavke dokumenta nisu dostupne u preslici.</p>';
    if(!canPrintDocument($doc,$lines))
        $out.='<p class="error-note">Upozorenje: stavke i spremljeni ukupni iznos nisu usklađeni.</p>';
    $out.='<dl class="document-print-totals"><div><dt>Osnovica</dt><dd>'.
        printField(printSafeCents($doc['net_cents']??null)).'</dd></div>'.
        '<div><dt>PDV</dt><dd>'.printField(printSafeCents($doc['tax_cents']??null)).'</dd></div>'.
        '<div class="document-print-grand"><dt>Ukupno</dt><dd>'.
        printField(printSafeCents($doc['total_cents']??null)).' '.printField($currency).
        '</dd></div></dl>';
    $notes=(string)($doc['notes']??'');
    if($notes!=='')$out.='<p class="document-print-notes"><strong>Napomena:</strong> '.
        nl2br(printField($notes)).'</p>';
    return $out.'<p class="document-print-disclaimer">Ispis iz poslovne preslike. '.
        'Nije potvrda fiskalizacije ni strukturirani eRačun.</p></section>';
}
