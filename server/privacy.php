<?php
declare(strict_types=1);
FakturivaSelfHostNotUsed:
header('Content-Type: text/html; charset=utf-8');
header('Referrer-Policy: no-referrer');
?><!doctype html><html lang="hr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pravila privatnosti — Fakturiva (vlastiti hosting)</title>
<style>body{font:16px/1.6 system-ui,sans-serif;max-width:780px;margin:auto;padding:24px;color:#1d3049}a{color:#435cf8}</style>
</head><body>
<h1>Pravila privatnosti — Fakturiva, vlastiti hosting</h1>
<p>Ovaj poslužitelj je samostalno instalirani modul za opcionalne korisničke
račune Fakturive. Njegov administrator, odnosno vlasnik domene, voditelj
je obrade poslovnih podataka pohranjenih ovdje. <strong>Administrator prije
produkcije mora dopuniti ovu stranicu</strong> svojim pravnim nazivom,
kontaktom, pravnom osnovom obrade, razdobljem čuvanja i svim primateljima.
Ovaj predložak sam po sebi nije dovršena pravna politika privatnosti.</p>
<h2>Podaci i svrha</h2>
<p>Poslužitelj pohranjuje korisničko ime, sažetak poslužiteljske lozinke,
vremenski ograničene sesije i šifrirane sigurnosne kopije računa i ponuda
samo kada korisnik izričito uključi vlastiti hosting. Lokalni PIN ne šalje
se na poslužitelj. Poslužiteljska lozinka drugačija je od kratkog PIN-a.
Podaci su u lokalnim JSON datotekama, dodatno šifriranima AES-256-GCM.
Sadržaj sigurnosne kopije dodatno je šifriran na Android uređaju prije prijenosa.</p>
<h2>Kontrola i brisanje</h2>
<p>Korisnik može preuzeti svoju šifriranu kopiju putem aplikacije.
Za brisanje poslužiteljskog računa i njegovih podataka:
<a href="delete-account.php">obrazac za brisanje računa</a>.
U postavkama Android aplikacije postoji odvojeno brisanje lokalnog profila.
Administrator mora objaviti postupke i ograničenja čuvanja koje zahtijeva zakon.</p>
<h2>Prijenos podataka</h2>
<p>API zahtijeva HTTPS. Poslužitelj ne šalje podatke ugrađenim
oglasnim, analitičkim ili vanjskim računskim servisima. Davatelj
hostinga može imati vlastitu evidenciju pristupa u skladu s ugovorom.
Prilagodite ovaj odjeljak svom hostingu prije objave.</p>
</body></html>
