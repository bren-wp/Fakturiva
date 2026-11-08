package com.brendigo.fakturiva;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Gradually migrate all user-visible text to reviewed, complete locale catalogs. */
public final class UiStrings {
    public static final String DEFAULT="en";
    // Requested rollout languages; only reviewed languages may be enabled.
    private static final String[][] TARGETS={
        {"en","English"},
        {"hr","Hrvatski"},
        {"de","Deutsch"},
        {"fr","Français"},
        {"es","Español"},
        {"it","Italiano"},
        {"pt","Português"},
        {"nl","Nederlands"},
        {"pl","Polski"},
        {"cs","Čeština"},
        {"sk","Slovenčina"},
        {"sl","Slovenščina"},
        {"hu","Magyar"},
        {"ro","Română"},
        {"bg","Български"},
        {"sr","Српски"},
        {"bs","Bosanski"},
        {"uk","Українська"},
        {"ru","Русский"},
        {"tr","Türkçe"},
        {"el","Ελληνικά"},
        {"sv","Svenska"},
        {"da","Dansk"},
        {"nb","Norsk"},
        {"fi","Suomi"},
        {"et","Eesti"},
        {"lv","Latviešu"},
        {"lt","Lietuvių"},
        {"ar","العربية"},
        {"ja","日本語"},
        {"zh","中文"}
    };
    private static final Map<String,String> EN=new HashMap<>();
    private static final Map<String,String> HR=new HashMap<>();
    static {
        EN.put("Početna","Home");
        EN.put("Računi","Invoices");
        EN.put("Ponude","Quotes");
        EN.put("Klijenti","Clients");
        EN.put("Više","More");
        EN.put("Postavke","Settings");
        EN.put("Profil tvrtke","Company profile");
        EN.put("Izvještaji","Reports");
        EN.put("Naplata","Payments");
        EN.put("Artikli i usluge","Products and services");
        EN.put("Novi račun","New invoice");
        EN.put("Nova ponuda","New quote");
        EN.put("Novi klijent","New client");
        EN.put("Brze radnje","Quick actions");
        EN.put("Nedavni dokumenti","Recent documents");
        EN.put("Ovaj mjesec","This month");
        EN.put("Na ovaj mjesec","Current month");
        EN.put("Natrag","Back");
        EN.put("Zatvori","Close");
        EN.put("U redu","OK");
        EN.put("Odustani","Cancel");
        EN.put("Spremi","Save");
        EN.put("Ukloni","Remove");
        EN.put("Potvrdi","Confirm");
        EN.put("Pregled","Preview");
        EN.put("Podaci","Details");
        EN.put("Stavke","Items");
        EN.put("Dodaj novi račun","Add invoice");
        EN.put("Dodaj novu ponudu","Add quote");
        EN.put("Osnovni podaci","Basic details");
        EN.put("Datum izdavanja","Issue date");
        EN.put("Datum dospijeća","Due date");
        EN.put("Datum ponude","Quote date");
        EN.put("Vrijedi do","Valid until");
        EN.put("Klijent","Client");
        EN.put("Porezni broj (neobavezno)","Tax ID (optional)");
        EN.put("E-adresa","Email");
        EN.put("Telefon","Phone");
        EN.put("Adresa","Address");
        EN.put("Naziv izdavatelja","Legal business name");
        EN.put("Poštanski broj","Postal code");
        EN.put("Grad / mjesto","City");
        EN.put("Država","Country");
        EN.put("Web stranica","Website");
        EN.put("Naziv banke","Bank name");
        EN.put("Način plaćanja","Payment method");
        EN.put("Referenca plaćanja","Payment reference");
        EN.put("Porezna ili poslovna napomena","Tax or legal note");
        EN.put("Podaci o tvrtki","Company details");
        EN.put("Podaci za plaćanje","Payment details");
        EN.put("Dodatni poslovni podaci","Additional company details");
        EN.put("Upravljajte svojom aplikacijom","Manage your app");
        EN.put("Postavke aplikacije","Application settings");
        EN.put("Tvrtka i poslovni podaci","Company and business details");
        EN.put("Naziv, adresa i kontakt podaci","Name, address and contact details");
        EN.put("Numeracija dokumenata","Document numbering");
        EN.put("Pregled dodijeljenih brojeva računa i ponuda","Review invoice and quote numbers");
        EN.put("Porezi i stope","Taxes and rates");
        EN.put("Porezni status i napomene u profilu tvrtke","Tax status and notes in company profile");
        EN.put("Načini plaćanja","Payment methods");
        EN.put("IBAN, banka i uvjeti plaćanja","IBAN, bank and payment terms");
        EN.put("Podaci na dokumentima","Document information");
        EN.put("Profil izdavatelja za nove PDF dokumente","Issuer profile for new PDFs");
        EN.put("Pomoć i upute","Help and guidance");
        EN.put("Računi, ponude i načini rada","Invoices, quotes and workflows");
        EN.put("Sigurnost","Security");
        EN.put("PIN i privatnost","PIN and privacy");
        EN.put("Zaštita uređajem aktivna","Device lock enabled");
        EN.put("Izvoz i sigurnosne kopije","Export and backups");
        EN.put("Izvoz i obnova podataka","Export and restore data");
        EN.put("Izgled aplikacije","Appearance");
        EN.put("Svijetla tema aktivna","Light theme enabled");
        EN.put("Tamna tema aktivna","Dark theme enabled");
        EN.put("POSLOVNI ALATI","BUSINESS TOOLS");
        EN.put("Katalog proizvoda i cijene","Products and prices catalog");
        EN.put("Analitika računa i naplate","Invoice and payment analytics");
        EN.put("Računi i ponude, jednostavno.","Invoices and quotes, made simple.");
        EN.put("Vaše poslovanje. Jednostavnije.","Business, made simpler.");
        EN.put("SVE NA JEDNOM MJESTU","ALL IN ONE PLACE");
        EN.put("Manje papirologije.\nViše vremena za posao.","Less paperwork.\nMore time for business.");
        EN.put("Izradite profesionalne račune i ponude, pratite naplatu i organizirajte klijente.","Create professional invoices and quotes, track payments and manage clients.");
        EN.put("›  Započni","›  Get started");
        EN.put("Računi i ponude, jednostavno i pregledno.","Invoices and quotes, clear and simple.");
        EN.put("Otključajte Fakturivu","Unlock Fakturiva");
        EN.put("Zaštitite svoje poslovanje","Protect your business");
        EN.put("SIGURNOST APLIKACIJE","APP SECURITY");
        EN.put("Zaštita pri ponovnom otvaranju","Protection when reopening");
        EN.put("Vaša poslovna dokumentacija","Your business documents");
        EN.put("Zaštitu možete promijeniti u Postavkama.","You can change protection in Settings.");
        EN.put("Otključaj uređajem","Unlock with device");
        EN.put("Isključi zaštitu","Disable protection");
        EN.put("Uključi zaštitu uređajem","Enable device protection");
        EN.put("Postavi zaštitu uređaja","Set up device protection");
        EN.put("Nastavi bez dodatne zaštite","Continue without extra protection");
        EN.put("Dobro jutro","Good morning");
        EN.put("Dobar dan","Good afternoon");
        EN.put("Dobra večer","Good evening");
        EN.put("Evo pregleda vašeg poslovanja.","Here's your business overview.");
        EN.put("Naplaćeno ovaj mjesec","Received this month");
        EN.put("Prema evidentiranim uplatama","From recorded payments");
        EN.put("Prikaži sve  ›","View all  ›");
        EN.put("Još nema računa. Dodajte prvi dokument.","No invoices yet. Create your first.");
        EN.put("Trenutačno nema nenaplaćenih potvrđenih računa.","No outstanding issued invoices.");
        EN.put("Pregledajte i pretražite račune","Review and search invoices");
        EN.put("Svi vaši izdani računi","All your invoices");
        EN.put("Profesionalne ponude na jednom mjestu","Professional quotes in one place");
        EN.put("Pretraži dokumente","Search documents");
        EN.put("Pretraži račune...","Search invoices...");
        EN.put("Pretraži ponude...","Search quotes...");
        EN.put("Plaćeni","Paid");
        EN.put("Neplaćeni","Unpaid");
        EN.put("Skice","Drafts");
        EN.put("Potvrđene","Confirmed");
        EN.put("Svi","All");
        EN.put("Aktivne ponude","Active quotes");
        EN.put("Vrijednost ponuda","Quote value");
        EN.put("Neplaćeno  ","Unpaid  ");
        EN.put("Nema dokumenata za odabranu pretragu.","No documents match your search.");
        EN.put("Stavke računa","Invoice items");
        EN.put("Stavke ponude","Quote items");
        EN.put("Pregled računa","Invoice preview");
        EN.put("Pregled ponude","Quote preview");
        EN.put("Nova stavka","New item");
        EN.put("Uredi stavku","Edit item");
        EN.put("Naziv artikla ili usluge","Product or service name");
        EN.put("Količina","Quantity");
        EN.put("Jedinična cijena bez PDV-a","Unit price before tax");
        EN.put("Stopa PDV-a","Tax rate");
        EN.put("Jedinična cijena, EUR","Unit price");
        EN.put("Dodaj stavku dokumenta","Add item");
        EN.put("Dodajte barem jednu stavku","Add at least one item");
        EN.put("Sažetak računa","Invoice summary");
        EN.put("Sažetak ponude","Quote summary");
        EN.put("Osnovica","Subtotal");
        EN.put("PDV","Tax");
        EN.put("UKUPNO","TOTAL");
        EN.put("Pregled dokumenta","Document preview");
        EN.put("Provjera i potvrda","Review and confirm");
        EN.put("Spremi nacrt","Save draft");
        EN.put("Potvrdi dokument","Confirm document");
        EN.put("Potvrdi račun","Confirm invoice");
        EN.put("Potvrdi ponudu","Confirm quote");
        EN.put("Uredi nacrt","Edit draft");
        EN.put("Pregled potvrđenog dokumenta","Confirmed document");
        EN.put("Broj nakon potvrde","Number assigned on confirmation");
        EN.put("Preostalo","Remaining");
        EN.put("Preostalo za naplatu: ","Outstanding: ");
        EN.put("Evidentiraj uplatu","Record payment");
        EN.put("Preuzmi PDF","Save PDF");
        EN.put("Pošalji","Send");
        EN.put("Podijeli ponudu","Share quote");
        EN.put("Provjerite sigurnosnu kopiju","Check backup");
        EN.put("Sigurnosna kopija","Backup");
        EN.put("Automatska zaštita","Automatic protection");
        EN.put("Spremi promjene","Save changes");
        EN.put("Promjene vrijede samo za nove dokumente. Potvrđeni računi i ponude čuvaju izvorne podatke izdavatelja.","Changes apply to new documents only. Issued documents keep their original issuer details.");
        EN.put("Porezni identifikacijski broj (ako postoji)","Tax ID (if applicable)");
        EN.put("Porezni broj","Tax ID");
        EN.put("Porezni broj klijenta","Client tax ID");
        EN.put("Trgovačko ime","Trading name");
        EN.put("Pomoć","Help");
        EN.put("Dobro došli u Fakturivu","Welcome to Fakturiva");
        EN.put("Kako započeti","Getting started");
        EN.put("Računi, ponude i osnovne radnje","Invoices, quotes and basics");
        EN.put("Izradite račun","Create invoice");
        EN.put("Pripremite ponudu","Create quote");
        EN.put("Pregledajte i uredite klijente","View and edit clients");
        EN.put("Izvezite ili vratite podatke","Export or restore data");
        EN.put("Evidencija uplata i potraživanja","Payments and receivables");
        EN.put("Ukupno naplaćeno","Total received");
        EN.put("Nedavne uplate","Recent payments");
        EN.put("Neplaćeni računi","Unpaid invoices");
        EN.put("Za naplatu","Outstanding");
        EN.put("Sve uplate","All payments");
        EN.put("Nema evidentiranih uplata.","No payments recorded.");
        EN.put("Prikaz naplate","Payment view");
        EN.put("Filtriraj uplate","Filter payments");
        EN.put("Statistika i analiza poslovanja","Business statistics and analysis");
        EN.put("Ukupno fakturirano","Total invoiced");
        EN.put("Pregled naplate","Payments overview");
        EN.put("Naplaćeno u mjesecu","Received this month");
        EN.put("Nenaplaćeno iz izdanih računa","Outstanding on issued invoices");
        EN.put("Pregled svih uplata","View all payments");
        EN.put("Odaberi mjesec izvještaja: ","Select report month: ");
        EN.put("Potvrda dokumenta","Confirm document");
        HR.put("Company and legal identity","Tvrtka i pravni podaci");
        HR.put("Payment and bank details","Plaćanje i banka");
        HR.put("Additional invoice details","Dodatni podaci na računu");
        HR.put("Legal business name","Službeni naziv poslovnog subjekta");
        HR.put("Tax ID (optional)","Porezni broj (neobavezno)");
        HR.put("VAT ID (optional)","PDV identifikacijski broj (neobavezno)");
        HR.put("Street address","Adresa");
        HR.put("Postal code","Poštanski broj");
        HR.put("City","Grad");
        HR.put("Country","Država");
        HR.put("Email","E-adresa");
        HR.put("Phone","Telefon");
        HR.put("Website","Web stranica");
        HR.put("IBAN (optional)","IBAN (neobavezno)");
        HR.put("Bank name","Naziv banke");
        HR.put("Register reference (optional)","Registracijska oznaka (neobavezno)");
        HR.put("Business unit","Poslovna jedinica");
        HR.put("Operator reference","Oznaka operatera");
        HR.put("Payment method","Način plaćanja");
        HR.put("Payment reference","Poziv na broj / referenca");
        HR.put("Tax or legal note","Porezna ili zakonska napomena");
        HR.put("Legal form (Ltd, Inc, LLC, GmbH, d.o.o., sole trader...)","Pravni oblik (d.o.o., obrt, Ltd, Inc, GmbH...)");
        HR.put("Trading name (optional)","Trgovački naziv (neobavezno)");
        HR.put("Company registration number","Registracijski broj tvrtke");
        HR.put("Registering authority","Registracijsko tijelo");
        HR.put("Tax registration details","Podaci porezne registracije");
        HR.put("Additional address line","Dodatna adresa");
        HR.put("State / province / region","Regija / pokrajina / savezna država");
        HR.put("Payment terms","Uvjeti plaćanja");
        HR.put("Invoice footer note","Napomena u podnožju računa");
        HR.put("Company details saved","Podaci tvrtke su spremljeni");
        HR.put("Invoice and quote issuer","Izdavatelj računa i ponuda");
        HR.put("Company details","Podaci o tvrtki");
        HR.put("Unit price before tax","Jedinična cijena bez poreza");
        HR.put("Contact person","Kontakt osoba");
        HR.put("Bank account number (non-IBAN)","Broj bankovnog računa (izvan IBAN sustava)");
        HR.put("Bank routing number / ABA","Bankovni routing/ABA broj");
        HR.put("Sort code / branch code","Kod banke / poslovnice");
        HR.put("Bank address","Adresa banke");
        HR.put("Purchase order / invoice reference","Referenca narudžbe / računa");
        HR.put("Language","Jezik");
        HR.put("Default currency","Zadana valuta");
        HR.put("Cancel","Odustani");
        HR.put("Applies to new invoices and quotes. Issued documents retain their original currency.","Primjenjuje se na nove račune i ponude. Već potvrđeni dokumenti zadržavaju izvornu valutu.");
        HR.put("Unit price","Jedinična cijena");
        HR.put("Company and business details","Podaci o poslovnom subjektu");
    }
    private UiStrings(){}
    /**
     * The current review UI exposes English and Croatian. Other requested
     * languages must NOT be advertised before the full phrase inventory,
     * plural rules, dialogs and PDF templates have passed translation QA.
     */
    public static String[] readyCodes(){return new String[]{"en","hr"};}
    public static String[] readyNames(){return new String[]{"English","Hrvatski"};}
    public static String[][] planned(){return TARGETS.clone();}
    public static String normalized(String code){
        return "hr".equals(code)?"hr":"en";
    }
    public static String translate(String source,String language){
        if(source==null)return null;
        String found="hr".equals(normalized(language))?HR.get(source):EN.get(source);
        return found==null?source:found;
    }
}
