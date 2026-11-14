<?php
declare(strict_types=1);
namespace FakturivaWebBusiness;

use FakturivaSelfHost\HttpFailure;

/**
 * Web document writes use the exact schema-8 Android SQLite backup rows.
 * A document is mutable only as DRAFT; issued numbers and frozen snapshots
 * must never be changed by CRUD operations.
 */
function documentId(array $input,string $key='id'):int {
    $id=$input[$key]??null;
    if(!is_int($id)||$id<1||$id>2147483646)
        throw new HttpFailure(422,'Neispravan identifikator dokumenta.');
    return $id;
}
function documentDate(array $input,string $key):string {
    $date=requiredField($input,$key,10,true);
    if(!preg_match('/^\d{4}-\d{2}-\d{2}$/D',$date)||
       !checkdate((int)substr($date,5,2),(int)substr($date,8,2),
           (int)substr($date,0,4)))
        throw new HttpFailure(422,'Nevaljan datum: '.$key);
    return $date;
}
function documentCompany(array $root):array {
    $values=[];
    foreach($root['company'] as $row){
        if(!is_array($row)||!is_string($row['key']??null)||
           !is_string($row['value']??null))
            throw new HttpFailure(422,'Neispravni podatci izdavatelja.');
        $values[$row['key']]=$row['value'];
    }
    return $values;
}
function issuerKeys():array{
    return ['name','address','oib','email','iban','tax_note','phone','vat_id',
        'postal_code','city','country','website','bank_name','swift','registry',
        'business_unit','operator','payment_method','payment_reference',
        'legal_form','trading_name','company_number','register_authority',
        'tax_registration','address_2','region','payment_terms','footer_note',
        'contact_person','bank_account_number','bank_routing_number',
        'bank_sort_code','bank_address','invoice_reference','invoice_theme',
        'invoice_accent','logo_sha256'];
}
function documentIssuer(array $company):array {
    $result=[];
    foreach(issuerKeys() as $key){
        $value=$company[$key]??'';
        if(!is_string($value)||strlen($value)>20000)
            throw new HttpFailure(422,'Neispravni podatci izdavatelja.');
        $result['issuer_'.$key]=$value;
    }
    return $result;
}
function documentCurrency(array $company):string {
    $currency=strtoupper(trim((string)($company['currency']??'EUR')));
    if($currency==='')$currency='EUR';
    if(!in_array($currency,['EUR','USD','GBP','CHF','CAD','AUD','NZD',
        'SGD','HKD','CNY','INR','BRL','MXN','ZAR','PLN','CZK','SEK',
        'NOK','DKK','RON','HUF','BGN','TRY','ILS','THB','MYR',
        'PHP','TWD'],true))
        throw new HttpFailure(422,'Nepodržana valuta poslovnog računa.');
    return $currency;
}
/** Match Android Money.parseRate: whole percent or 0.01%-resolution code. */
function documentTaxRate(mixed $raw):int {
    if(!is_string($raw)||strlen($raw)>10)
        throw new HttpFailure(422,'Neispravna stopa PDV-a.');
    $raw=trim(str_replace('%','',$raw));
    if(preg_match('/^([0-9]{1,3})(?:[.,]([0-9]{1,2}))?$/D',$raw,$parts)){
        $whole=(int)$parts[1];
        $fraction=(int)str_pad($parts[2]??'0',2,'0');
        if($whole>100||($whole===100&&$fraction>0))
            throw new HttpFailure(422,'PDV mora biti u rasponu od 0 do 100 %.');
        return $fraction===0?$whole:10000+$whole*100+$fraction;
    }
    // Backward compatibility for existing schema-8 extended tax codes.
    if(preg_match('/^[0-9]{5}$/D',$raw) &&
       (int)$raw>=10001&&(int)$raw<=20000)
        return (int)$raw;
    throw new HttpFailure(422,'Neispravna stopa PDV-a.');
}
/** Match Android Money.subtotal/Money.tax: positive half-up integer rounding. */
function documentLines(array $input):array {
    $source=$input['lines']??null;
    if(!is_array($source)||!array_is_list($source)||count($source)>40)
        throw new HttpFailure(422,'Račun mora sadržavati najviše 40 stavki.');
    $rows=[];$netSum=0;$taxSum=0;
    foreach($source as $raw){
        if(!is_array($raw))throw new HttpFailure(422,'Neispravna stavka.');
        $name=requiredField($raw,'name',200);
        if($name===''){
            if(trim((string)($raw['qty']??''))!=='' ||
               trim((string)($raw['price']??''))!=='')
                throw new HttpFailure(422,'Nedostaje naziv stavke.');
            continue;
        }
        $quantity=requiredField($raw,'qty',24,true);
        if(!preg_match('/^([0-9]{1,5})(?:[.,]([0-9]{1,3}))?$/D',$quantity,$match))
            throw new HttpFailure(422,'Neispravna količina.');
        $qtyMilli=(int)$match[1]*1000+
            (int)str_pad($match[2]??'0',3,'0');
        $price=priceInCents($raw['price']??null);
        if($qtyMilli<1||$qtyMilli>10000000||$price>1000000000)
            throw new HttpFailure(422,'Prevelika količina ili jedinična cijena.');
        $rate=documentTaxRate($raw['tax_rate']??null);
        $bp=$rate>=10000?$rate-10000:$rate*100;
        $net=intdiv($qtyMilli*$price+500,1000);
        $tax=intdiv($net*$bp+5000,10000);
        $netSum+=$net;$taxSum+=$tax;
        if($netSum>2000000000000||$taxSum>2000000000000)
            throw new HttpFailure(422,'Iznos dokumenta premašuje dopušteni limit.');
        $rows[]=['name'=>$name,'qty_milli'=>$qtyMilli,'unit_cents'=>$price,
            'tax_rate'=>$rate,'net_cents'=>$net,'tax_cents'=>$tax];
    }
    if(count($rows)<1)
        throw new HttpFailure(422,'Dodajte barem jednu valjanu stavku.');
    return [$rows,$netSum,$taxSum,$netSum+$taxSum];
}
function nextSnapshotId(array $rows):int {
    $max=0;$seen=[];
    foreach($rows as $row){
        $id=$row['id']??null;
        if(!is_array($row)||!is_int($id)||$id<1||
           $id>2147483646||isset($seen[$id]))
            throw new HttpFailure(422,'Neispravni ili duplicirani ID-ovi.');
        $seen[$id]=true;$max=max($max,$id);
    }
    if($max>=2147483646)throw new HttpFailure(413,'Dosegnut je ID limit.');
    return $max+1;
}
function clientSnapshot(array $root,int $clientId):array {
    $found=null;
    foreach($root['clients'] as $client){
        if(!is_array($client)||!is_int($client['id']??null))
            throw new HttpFailure(422,'Neispravan popis klijenata.');
        if($client['id']===$clientId){
            if($found!==null)throw new HttpFailure(422,'Duplicirani klijent.');
            $found=$client;
        }
    }
    if($found===null)throw new HttpFailure(404,'Klijent nije pronađen.');
    $data=['client_id'=>$clientId];
    foreach(['name','address','oib','email','phone','vat_id'] as $key){
        $value=$found[$key]??'';
        if(!is_string($value)||strlen($value)>20000)
            throw new HttpFailure(422,'Neispravna polja klijenta.');
        $data['client_'.$key]=$value;
    }
    return $data;
}
function existingDocumentIndex(array $root,int $id):int {
    foreach($root['documents'] as $i=>$doc){
        if(is_array($doc)&&($doc['id']??null)===$id)return $i;
    }
    throw new HttpFailure(404,'Dokument nije pronađen.');
}
function documentModified(array $root,string $operation,array $input):array {
    foreach(['clients','documents','lines','payments','sequences'] as $table){
        if(!is_array($root[$table]??null)||!array_is_list($root[$table])||
           count($root[$table])>100000)
            throw new HttpFailure(422,'Neispravna poslovna baza: '.$table);
    }
    // Refuse mutations of malformed or ambiguous backups before rewriting them.
    $docIds=[];$lineIds=[];
    foreach($root['documents'] as $entry){
        $key=$entry['id']??null;
        if(!is_array($entry)||!is_int($key)||$key<1||
           $key>2147483646||isset($docIds[$key]))
            throw new HttpFailure(422,'Duplicirani ili nevaljani dokumenti.');
        $docIds[$key]=true;
    }
    foreach($root['lines'] as $entry){
        $key=$entry['id']??null;
        $documentId=$entry['document_id']??null;
        if(!is_array($entry)||!is_int($key)||$key<1||
           $key>2147483646||isset($lineIds[$key])||
           !is_int($documentId)||!isset($docIds[$documentId]))
            throw new HttpFailure(422,'Nevaljani ID-ovi ili veze stavki.');
        $lineIds[$key]=true;
    }
    if(in_array($operation,['doc_add','doc_edit'],true)){
        $type=requiredField($input,'type',8,true);
        if(!in_array($type,['INVOICE','QUOTE'],true))
            throw new HttpFailure(422,'Neispravna vrsta dokumenta.');
        $clientId=documentId($input,'client_id');
        $dates=['issue_date'=>documentDate($input,'issue_date'),
            'due_date'=>documentDate($input,'due_date'),
            'supply_date'=>documentDate($input,'supply_date')];
        if($dates['due_date']<$dates['issue_date'])
            throw new HttpFailure(422,'Dospijeće ne smije biti prije izdavanja.');
        $notes=requiredField($input,'notes',4000);
        $title=$type==='QUOTE'?requiredField($input,'quote_title',200):'';
        [$lines,$net,$tax,$total]=documentLines($input);
        $client=clientSnapshot($root,$clientId);
        if($operation==='doc_add'){
            if(count($root['documents'])>=100000)
                throw new HttpFailure(413,'Dosegnut je limit dokumenata.');
            $id=nextSnapshotId($root['documents']);
            $company=documentCompany($root);
            $doc=['id'=>$id,'type'=>$type,'number'=>null,
                'status'=>'DRAFT','created_at'=>(int)round(microtime(true)*1000),
                'notes'=>$notes,'quote_title'=>$title,'net_cents'=>$net,
                'tax_cents'=>$tax,'total_cents'=>$total,
                'currency_code'=>documentCurrency($company)]
                +$client+$dates+documentIssuer($company);
            $root['documents'][]=$doc;
        }else{
            $id=documentId($input);
            $index=existingDocumentIndex($root,$id);
            $existing=$root['documents'][$index];
            if(($existing['status']??null)!=='DRAFT' ||
               ($existing['type']??null)!==$type)
                throw new HttpFailure(409,
                    'Uređivati se mogu samo nacrti iste vrste dokumenta.');
            foreach($client+$dates+[
                'notes'=>$notes,'quote_title'=>$title,'net_cents'=>$net,
                'tax_cents'=>$tax,'total_cents'=>$total] as $key=>$value)
                $existing[$key]=$value;
            $root['documents'][$index]=$existing;
            $root['lines']=array_values(array_filter($root['lines'],
                static fn($row)=>($row['document_id']??null)!==$id));
        }
        // Android Store.restore rejects snapshots with more than 100,000 rows.
        // For edits the replaced draft lines have already been removed above.
        if(count($root['lines'])+count($lines)>100000)
            throw new HttpFailure(413,'Dosegnut je limit stavki dokumenata.');
        $lineId=nextSnapshotId($root['lines']);
        if($lineId+count($lines)-1>2147483646)
            throw new HttpFailure(413,'Dosegnut je limit ID-ova stavki.');
        foreach($lines as $line){
            $root['lines'][]=['id'=>$lineId++,'document_id'=>$id]+$line;
            if($lineId>2147483647)
                throw new HttpFailure(413,'Previše stavki dokumenata.');
        }
        return $root;
    }
    $id=documentId($input);
    $index=existingDocumentIndex($root,$id);
    $document=$root['documents'][$index];
    $status=$document['status']??'';
    $type=$document['type']??'';
    if(!in_array($type,['INVOICE','QUOTE'],true))
        throw new HttpFailure(422,'Neispravna vrsta dokumenta.');
    if($operation==='doc_delete'){
        if($status!=='DRAFT')
            throw new HttpFailure(409,'Potvrđeni dokument ne smije se obrisati.');
        foreach($root['payments'] as $p)
            if(($p['document_id']??null)===$id)
                throw new HttpFailure(409,'Dokument s uplatama ne smije se brisati.');
        $root['documents']=array_values(array_filter($root['documents'],
            static fn($row)=>($row['id']??null)!==$id));
        $root['lines']=array_values(array_filter($root['lines'],
            static fn($row)=>($row['document_id']??null)!==$id));
        return $root;
    }
    if($operation==='doc_confirm'){
        if($status!=='DRAFT')throw new HttpFailure(409,'Dokument je već potvrđen.');
        $issuer=documentCompany($root);
        if(trim((string)($issuer['name']??''))==='' ||
           ($type==='INVOICE'&&trim((string)($issuer['address']??''))===''))
            throw new HttpFailure(422,'Prije potvrde unesite naziv i adresu izdavatelja.');
        $found=0;$net=0;$tax=0;
        foreach($root['lines'] as $line){
            if(($line['document_id']??null)!==$id)continue;
            $found++;
            if(!is_int($line['net_cents']??null)||!is_int($line['tax_cents']??null))
                throw new HttpFailure(422,'Neispravan iznos stavke.');
            $net+=$line['net_cents'];$tax+=$line['tax_cents'];
        }
        if($found===0||$net!==($document['net_cents']??null)||
           $tax!==($document['tax_cents']??null)||
           $net+$tax!==($document['total_cents']??null))
            throw new HttpFailure(422,'Stavke i ukupan iznos ne odgovaraju.');
        $year=(int)substr((string)($document['issue_date']??''),0,4);
        if($year<2000||$year>2100)
            throw new HttpFailure(422,'Godina izdavanja nije podržana.');
        $last=0;$sequenceIndex=null;
        foreach($root['sequences'] as $i=>$row){
            if(($row['type']??null)===$type&&($row['year']??null)===$year){
                if($sequenceIndex!==null||!is_int($row['last_num']??null))
                    throw new HttpFailure(422,'Neispravan brojač računa.');
                $sequenceIndex=$i;$last=$row['last_num'];
            }
        }
        if($last<0||$last>=2147483646)
            throw new HttpFailure(413,'Neispravan brojač dokumenta.');
        $num=$last+1;
        $prefix=$type==='INVOICE'?'R':'P';
        $number=$prefix.'-'.$year.'-'.str_pad((string)$num,4,'0',STR_PAD_LEFT);
        foreach($root['documents'] as $row)
            if(($row['number']??null)===$number)
                throw new HttpFailure(409,'Brojač dokumenata nije usklađen.');
        if($sequenceIndex===null)$root['sequences'][]=[
            'type'=>$type,'year'=>$year,'last_num'=>$num];
        else $root['sequences'][$sequenceIndex]['last_num']=$num;
        $document['number']=$number;
        $document['status']=$type==='INVOICE'?'ISSUED':'SENT';
        foreach(documentIssuer($issuer) as $key=>$value)$document[$key]=$value;
        $root['documents'][$index]=$document;
        return $root;
    }
    if($operation==='quote_convert'){
        if($type!=='QUOTE'||!in_array($status,['SENT','ACCEPTED'],true))
            throw new HttpFailure(409,'Samo potvrđena ponuda može postati nacrt računa.');
        $number=$document['number']??null;
        if(!is_string($number)||$number==='')
            throw new HttpFailure(422,'Ponuda nema potvrđen broj.');
        $company=documentCompany($root);
        $client=clientSnapshot($root,(int)($document['client_id']??0));
        if(count($root['documents'])>=100000)
            throw new HttpFailure(413,'Dosegnut je limit dokumenata.');
        $newId=nextSnapshotId($root['documents']);
        $newLines=[];$net=0;$tax=0;
        foreach($root['lines'] as $line){
            if(($line['document_id']??null)!==$id)continue;
            if(!is_int($line['net_cents']??null)||!is_int($line['tax_cents']??null)||
               $line['net_cents']<0||$line['tax_cents']<0)
                throw new HttpFailure(422,'Nevaljani iznosi izvorne ponude.');
            $net+=$line['net_cents'];$tax+=$line['tax_cents'];
            $newLines[]=$line;
        }
        if(!$newLines||$net!==($document['net_cents']??null)||
           $tax!==($document['tax_cents']??null)||
           $net+$tax!==($document['total_cents']??null))
            throw new HttpFailure(422,'Ponuda i njezine stavke nisu usklađene.');
        if(count($root['lines'])+count($newLines)>100000)
            throw new HttpFailure(413,'Dosegnut je limit stavki dokumenata.');
        $new=$document;
        $new['id']=$newId;$new['type']='INVOICE';$new['number']=null;
        $new['status']='DRAFT';$new['created_at']=(int)round(microtime(true)*1000);
        $new['notes']='Prema ponudi '.$number;
        $new['quote_title']='';
        $new['currency_code']=documentCurrency(
            ['currency'=>$document['currency_code']??'EUR']);
        foreach($client+documentIssuer($company) as $key=>$value)$new[$key]=$value;
        $root['documents'][]=$new;
        $nextId=nextSnapshotId($root['lines']);
        if($nextId+count($newLines)-1>2147483646)
            throw new HttpFailure(413,'Dosegnut je limit ID-ova stavki.');
        foreach($newLines as $line){
            if($nextId>2147483646)throw new HttpFailure(413,'Previše stavki.');
            $line['id']=$nextId++;$line['document_id']=$newId;
            $root['lines'][]=$line;
        }
        return $root;
    }
    if($operation==='quote_accept'){
        if($type!=='QUOTE'||$status!=='SENT')
            throw new HttpFailure(409,'Prihvatiti se može samo potvrđena ponuda.');
        $root['documents'][$index]['status']='ACCEPTED';
        return $root;
    }
    throw new HttpFailure(422,'Nepoznata radnja nad dokumentom.');
}
