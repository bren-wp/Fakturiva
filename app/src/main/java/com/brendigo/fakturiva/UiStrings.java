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
        EN.put("Odaberite USB pisač","Choose USB printer");
        EN.put("Potvrdite USB ispis","Confirm USB printing");
        EN.put("USB termalni pisači","USB receipt printers");
        EN.put("Nije pronađen priključen USB Printer Class pisač. Provjerite OTG kabel, podršku za ESC/POS i USB dozvolu.","No USB Printer Class device found. Check the OTG adapter, ESC/POS support and USB permission.");
        EN.put("Podaci dokumenta šalju se isključivo na odabrani USB pisač. Android će zatražiti dozvolu. Provjerite model i papir.","Document data will only be sent to the selected USB printer. Android will ask for access. Verify the model and paper.");
        EN.put("Slanje na USB pisač…","Sending receipt to USB printer…");
        EN.put("Podaci su poslani. Provjerite ispis na papiru.","Print data sent. Verify the paper output.");
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
        EN.put("Već imate račun? ","Already use Fakturiva? ");
        EN.put("Prijavite se","Continue");
        EN.put("Već imate račun? Prijavite se","Already use Fakturiva? Continue");
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
        HR.put("LAN receipt printer","Mrežni termalni pisač");
        HR.put("Configure a local network printer","Postavite pisač na lokalnoj mreži");
        HR.put("About and support","O aplikaciji i podrška");
        HR.put("A3 · Android printer","A3 · Android pisač");
        HR.put("A6 · Android printer","A6 · Android pisač");
        HR.put("80 mm · Bluetooth ESC/POS (512 dots)","80 mm · Bluetooth ESC/POS (512 točaka)");
        HR.put("58 mm · LAN ESC/POS","58 mm · Mrežni ESC/POS");
        HR.put("80 mm · LAN ESC/POS (512 dots)","80 mm · Mrežni ESC/POS (512 točaka)");
        HR.put("80 mm · LAN ESC/POS (576 dots)","80 mm · Mrežni ESC/POS (576 točaka)");
        HR.put("Only trusted private-network ESC/POS printers with a raw TCP port are supported.","Podržani su ESC/POS pisači na pouzdanoj lokalnoj mreži s izravnim TCP priključkom.");
        HR.put("Printer IPv4","IPv4 adresa pisača");
        HR.put("TCP port","TCP priključak");
        HR.put("Save","Spremi");
        HR.put("Print on LAN ESC/POS","Ispiši na mrežnom ESC/POS pisaču");
        HR.put("Enter a valid TCP port (1024–65535).","Unesite ispravan TCP priključak (1024–65535).");
        HR.put("Sending receipt to LAN printer…","Slanje računa na mrežni pisač…");
        HR.put("Powered by Brendigo","Powered by Brendigo");
        HR.put("Version ","Verzija ");
        HR.put("Every generated PDF invoice and thermal receipt has a small Powered by Brendigo attribution at the bottom.","Svaki izrađeni PDF račun i termalni ispis ima sitnu oznaku Powered by Brendigo pri dnu.");
        HR.put("To request removal of this attribution, contact our support team at info@plati.com.hr.","Za zahtjev za uklanjanje ove oznake obratite se podršci na info@plati.com.hr.");
        HR.put("This app creates ordinary invoices and quotes. Printing does not process card payments or provide fiscalization.","Aplikacija izrađuje obične račune i ponude. Ispis ne obrađuje kartična plaćanja niti provodi fiskalizaciju.");
        HR.put("No email app is installed. Please write to info@plati.com.hr.","Nije pronađena aplikacija za e-poštu. Pošaljite poruku na info@plati.com.hr.");
        HR.put("Printers and paper","Pisači i formati papira");
        HR.put("80 mm · USB ESC/POS (512 dots)","80 mm · USB ESC/POS (512 točaka)");
        HR.put("80 mm · USB ESC/POS (576 dots)","80 mm · USB ESC/POS (576 točaka)");
        HR.put("The selected USB device has no printer-class bulk OUT endpoint.","Odabrani USB uređaj nema odgovarajući izlaz pisača.");
        HR.put("USB host service is unavailable.","USB host usluga nije dostupna.");
        HR.put("USB printer access was denied or the printer was removed.","Dozvola za USB pisač je odbijena ili je pisač uklonjen.");
        HR.put("Could not request permission for the selected USB printer.","Nije moguće zatražiti dozvolu za odabrani USB pisač.");
        HR.put("USB print interrupted. Check for a partial receipt before retrying.","USB ispis je prekinut. Prije ponavljanja provjerite je li ispis djelomično završen.");
        HR.put("A4 · Android printer","A4 · Android pisač");
        HR.put("US Letter · Android printer","US Letter · Android pisač");
        HR.put("A5 · Android printer","A5 · Android pisač");
        HR.put("US Legal · Android printer","US Legal · Android pisač");
        HR.put("58 mm · Bluetooth ESC/POS","58 mm · Bluetooth ESC/POS");
        HR.put("80 mm · Bluetooth ESC/POS","80 mm · Bluetooth ESC/POS");
        HR.put("Print invoice or quote","Ispiši račun ili ponudu");
        HR.put("Print / POS printer","Ispis / POS pisač");
        HR.put("Print invoice / quote","Ispis računa / ponude");
        HR.put("Print","Ispiši");
        HR.put("Pair Bluetooth printer","Upari Bluetooth pisač");
        HR.put("Bluetooth is unavailable on this device.","Bluetooth nije dostupan na ovom uređaju.");
        HR.put("Bluetooth is disabled","Bluetooth je isključen");
        HR.put("Enable Bluetooth and pair an ESC/POS printer in Android settings.","Uključite Bluetooth i uparite ESC/POS pisač u postavkama Androida.");
        HR.put("Bluetooth settings","Postavke Bluetootha");
        HR.put("No paired Bluetooth printers","Nema uparenih Bluetooth pisača");
        HR.put("First pair a compatible Bluetooth Classic ESC/POS receipt printer in Android settings.","Najprije uparite kompatibilan Bluetooth Classic ESC/POS pisač u postavkama Androida.");
        HR.put("Choose ESC/POS printer","Odaberite ESC/POS pisač");
        HR.put("Paired Bluetooth device","Upareni Bluetooth uređaj");
        HR.put("Sending receipt to printer…","Slanje računa na pisač…");
        HR.put("Print data sent. Check the printer for completion.","Podaci su poslani. Provjerite je li pisač završio ispis.");
        HR.put("Bluetooth permission is missing.","Nedostaje dopuštenje za Bluetooth.");
        HR.put("Nearby devices permission is required for Bluetooth printing.","Za Bluetooth ispis potrebno je dopuštenje za uređaje u blizini.");
        HR.put("Printer connection or printing failed. Check the paper before retrying.","Spajanje ili ispis nisu uspjeli. Prije ponovnog pokušaja provjerite papir i moguće djelomičan ispis.");
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
