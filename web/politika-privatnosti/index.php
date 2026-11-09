<?php
declare(strict_types=1);
/** Public, review-required legal text for fakturiva.eu. */
header('Content-Type: text/html; charset=utf-8');
header('Cache-Control: public, max-age=3600');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');
?><!doctype html><html lang="hr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#ffffff">
<title>Politika privatnosti — Fakturiva</title>
<link rel="canonical" href="https://fakturiva.eu/politika-privatnosti">
<link rel="icon" href="../assets/mark.svg" type="image/svg+xml">
<link rel="stylesheet" href="../assets/app.css"></head>
<body><main class="account-page legal-page">
<img class="brand-mark" src="../assets/mark.svg" alt="Fakturiva" width="72" height="72">
<h1>Politika privatnosti Fakturive</h1>
<p><strong>Datum nacrta: 9. studenoga 2026.</strong>
Ovaj tekst opisuje predviđeni model obrade. Prije javne objave vlasnik usluge
mora provjeriti stvarnog voditelja obrade, njegovu pravnu adresu, kontakt,
podizvršitelje, lokacije hostinga i rokove čuvanja te zatražiti završnu
pravnu provjeru. Naziv „Brendigo” sam po sebi ne određuje pravni subjekt.</p>
<h2>1. Voditelj obrade i kontakt</h2>
<p>Za upravljanje korisničkim računima Fakturive predviđen je pružatelj
usluge Fakturiva / Brendigo. <strong>Točan registrirani naziv, pravni oblik,
adresa sjedišta i porezni identifikator moraju biti upisani prije
produkcijskog puštanja.</strong> Kontakt za privatnost:
<a href="mailto:info@fakturiva.eu">info@fakturiva.eu</a>.
Za računovodstvene podatke svojih kupaca i zaposlenika korisnik koji
vodi poslovanje u pravilu je samostalan voditelj obrade, a pružatelj
hostinga odnosno Fakturiva (ovisno o stvarnom ugovornom modelu)
može biti izvršitelj obrade. Ako Fakturiva obrađuje podatke po
nalozima poslovnog korisnika, potreban je odgovarajući ugovor o
obradi podataka prema članku 28. GDPR-a.</p>
<h2>2. Koje podatke obrađujemo</h2>
<p>Prilikom registracije: korisničko ime ili e-adresa, provjera pristupa
(kontrolirani hash web PIN-a), vrijeme stvaranja računa, status pristupa,
oznake dodijeljene tarife i metapodatci sesije. Poslužitelj za Android
API prihvaća zasebnu jaku lozinku i čuva njezin zaštićeni hash.
Poslužiteljski tokeni služe isključivo za autentifikaciju.</p>
<p>Podatci iz poslovnog rada mogu uključivati naziv pravnog subjekta,
OIB i PDV identifikatore, adrese, podatke klijenata, zaposlenika ili
operatera, brojeve računa, artikle, cijene, plaćanja, KPR, PO-SD,
logotipe i predloške. Sadržaj Android sigurnosnih kopija šifrira se
na uređaju prije HTTPS prijenosa i na serveru čuva kao šifrirani zapis.
Ako korisnik otključava poslovni pregled u web-pregledniku, zasebna
lozinka se obrađuje tijekom pojedinačnog zahtjeva radi dešifriranja
njegovih podataka; ne sprema se u web sesiju.</p>
<p>Za zaštitu od napada i zlouporabe mogu se obrađivati IP adresa,
podatci o pokušajima prijave, vremenske oznake te nužni sigurnosni
zapisi poslužitelja. Konkretne logove hostinga i rokove brisanja
treba utvrditi s odabranim pružateljem hostinga.</p>
<h2>3. Svrhe i pravne osnove</h2>
<p>Račun i prijava, sigurnosne kopije, sinkronizacija, izrada izvještaja
i podrška: izvršavanje ugovora i predugovorne radnje, čl. 6. st. 1.
točka (b) GDPR-a; mjere sigurnosti, otkrivanje zlouporabe i zaštita
infrastrukture: legitimni interes iz čl. 6. st. 1. točka (f), uz
procjenu ravnoteže interesa. Čuvanje isprava kad je za uslugu primjenjiva
konkretna pravna obveza: čl. 6. st. 1. točka (c). Posebne svrhe koje
se ne temelje na navedenome zahtijevaju zasebnu pravnu osnovu.
Ne uključujemo profiliranje radi oglašavanja ili prodaju podataka.</p>
<h2>4. Pohrana i sigurnost</h2>
<p>Server koristi JSON datoteke pod <code>storage/data</code> s
odvojenim identitetima i ovlastima korisnika. Poslužiteljski
zapisi šifrirani su AES-256-GCM, a sadržaj Android sigurnosne kopije
dodatno je šifriran korisnikovom jakom lozinkom. Prijenos je
predviđen putem HTTPS-a. Kratki PIN nije enkripcijski ključ za
poslovni sadržaj. Na Androidu može postojati privremena lokalna
radna kopija radi odziva i rada bez mreže. Ne tvrdi se da su podatci
sigurni samo zbog jednog šifriranog zapisa: potrebne su zasebne
sigurnosne kopije servera, kontrola pristupa, nadzor promjena,
provjera obnove i plan oporavka.</p>
<h2>5. Pristup administratora</h2>
<p>Administrator usluge može vidjeti korisničko ime, datum registracije,
status, tarifu, broj revizije i metapodatke o pohrani te, uz odgovarajuću
ovlast, privremeno onemogućiti pristup ili obrisati račun. Administratorski
panel ne daje ključ za dešifriranje privatnog sadržaja korisničke kopije.
Pristup na serveru mora biti ograničen, bilježen i periodično revidiran.</p>
<h2>6. Primatelji, izvršitelji i međunarodni prijenos</h2>
<p>Podatke mogu tehnički obrađivati ovlašteni zaposlenici,
pružatelj infrastrukture/hostinga i nužni komunikacijski pružatelji
za korisnički odabrane funkcije. Nije zamišljeno automatsko slanje
oglasnim ili analitičkim mrežama. Identitet stvarnog hostera i
drugih izvršitelja, države pohrane, njihove ugovore i eventualne
prijenose izvan EGP-a potrebno je konkretizirati prije aktivacije.
Za prijenose izvan EGP-a potrebno je primijeniti odgovarajući mehanizam
prijenosa, kada je to potrebno, i obavijestiti korisnike.</p>
<h2>7. Rokovi čuvanja</h2>
<p>Korisnički račun i povezani zapisi čuvaju se dok je račun aktivan
i koliko je opravdano radi pružanja usluge ili zakonskih obveza.
Po brisanju računa treba ukloniti primarne podatke i konfigurirati
brisanje ili isteknuće rezervnih kopija unutar zasebno utvrđenog
roka. Rokovi čuvanja računa i poslovnih isprava razlikuju se prema
državi, vrsti isprave i obvezama poslovnog korisnika; oni se
ne određuju proizvoljno u aplikaciji. Prije produkcije objavit će
se mjerljivi rokovi za korisničke zapise, sigurnosne logove i
rezervne kopije. Za legitimne obveze zakonskog arhiviranja
moguća je ograničena daljnja pohrana uz obrazloženje.</p>
<h2>8. Prava ispitanika</h2>
<p>Imate pravo na informacije, pristup, ispravak, brisanje, ograničenje,
prenosivost i prigovor u opsegu propisanom GDPR-om, kao i pravo
povući privolu gdje je ona pravna osnova. Za zahtjeve kontaktirajte
<a href="mailto:info@fakturiva.eu">info@fakturiva.eu</a>.
Radi zaštite drugih korisnika možemo zatražiti razumnu provjeru
identiteta. Zahtjeve razmatramo u zakonskim rokovima.
Imate pravo podnijeti pritužbu nadzornom tijelu, u Hrvatskoj
Agenciji za zaštitu osobnih podataka (AZOP), kada je nadležna.</p>
<h2>9. Brisanje računa</h2>
<p>Postupak brisanja računa dostupan je na
<a href="../delete-account.php">javnom obrascu za brisanje računa</a>
i iz postavki aplikacije. Privremena blokada ne zamjenjuje brisanje.
Brisanje cloud računa i sadržaja koji nije obvezno zadržati
mora obuhvatiti i rezervne kopije prema deklariranom rasporedu,
uz provjeru upute o rokovima arhiviranja zakonskih isprava.</p>
<h2>10. Kolačići i tehnologije praćenja</h2>
<p>Web koristi vlastiti strogo nužni kolačić za autentifikaciju,
postavljen s parametrima Secure, HttpOnly i SameSite=Strict.
Nema ugrađenih kolačića za oglase ni praćenje analitike.
Ako se ubuduće dodaju neobvezni alati, korisnici će biti
pravodobno obaviješteni i gdje je potrebno zatražit će se
prethodni pristanak.</p>
<h2>11. Zaštita maloljetnika</h2>
<p>Usluga je namijenjena poslovnim korisnicima i nije namjerno
usmjerena na djecu. Ako se utvrdi prikupljanje podataka bez
odgovarajuće osnove, aktivira se postupak za njihov pregled
i, gdje je nužno, brisanje.</p>
<h2>12. Promjene pravila</h2>
<p>Ažuriranja politike objavit će se na ovoj adresi uz označeni datum
primjene. O značajnim promjenama obrade korisnike ćemo obavijestiti
na odgovarajući način. Povijesna inačica mora biti dostupna
kako bi korisnik mogao utvrditi koja su pravila vrijedila.</p>
<p><small>Temelj: Uredba (EU) 2016/679, članci 5., 6., 12.–22.,
28. i 32., i pravila Google Playa o korisničkim podatcima.</small></p>
<nav class="account-links"><a href="../login.php">Prijava</a>
<a href="../uvjeti-koristenja/">Uvjeti korištenja</a>
<a href="../delete-account.php">Brisanje računa</a></nav>
</main></body></html>
