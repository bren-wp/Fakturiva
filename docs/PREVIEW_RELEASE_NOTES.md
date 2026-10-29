# Fakturiva v0.9.0-preview.1 — ujednačeni dokumenti, statusi i stabilniji izvoz

**Računi i ponude, jednostavno.** Offline Android aplikacija za rad s računima, ponudama, klijentima, stavkama, naplatom i PDF dokumentima.

## Novo u ovoj razvojnoj verziji

- Ekrani računa i ponuda vizualno su približeni originalnoj referenci: zasebna traka za pretragu s ikonama, nativni gumb filtera, segmentirani filtri, grupirane stavke i značke statusa.
- Iznosi, datumi, broj dokumenta i klijent čitaju se iz lokalne SQLite baze; nisu preslikani testni podaci iz referentnih PNG-ova.
- Statusi računa odražavaju stvarni poslovni događaj: plaćen, neplaćen, djelomično plaćen, pred dospijećem ili dospio.
- Ponude su sada prikazane kao **potvrđene**, a ne kao poslane bez dokaza o stvarnoj dostavi.
- Nativne Android vektorske ikone za uređivanje stavki, povratak i filtriranje uklanjaju razlike u prikazu Unicode znakova na uređajima.
- Početna stranica koristi iste komponente kartica i znački kao popisi dokumenata.
- PDF izvoz pamti odabrani dokument, a mjesečni CSV pamti mjesečni filtar pri ponovnom stvaranju Android aktivnosti.
- Kod šifriranog izvoza i uvoza lozinka se **namjerno ne sprema** u Android Bundle; ako sustav uništi aktivnost, operacija se prekida uz obavijest. Kratka lozinka više ne zatvara obrazac.
- Dodani Android regresijski testovi za datume dospijeća, status plaćanja i quote/invoice životni ciklus.

## Paketi u GitHub izdanju

- `Fakturiva-v0.9.0-preview.1-debug.apk` — APK za testiranje, debug potpis.
- `Fakturiva-v0.9.0-preview.1-DEBUG-SIGNED.aab` — debug-potpisani AAB, nije Google Play produkcijski upload.
- `Fakturiva-v0.9.0-preview.1-UNSIGNED.aab` — nepotpisani release bundle.
- `SHA256SUMS.txt` — SHA-256 kontrolne vrijednosti.

## Važne napomene

Referentni dizajn uključuje vizualizacije i funkcionalnosti koje ne postoje u offline aplikaciji: cloud prijava, strukturirani eRačun i fiskalizacija nisu implementirani. PDF nije fiskalizirani račun ili strukturirani eRačun.

Ovo je **razvojno izdanje**. Potpuna pixel-perfect 1:1 podudarnost svih 20 ekrana, svi uređaji i produkcijska usklađenost nisu potvrđeni. Dizajnerske referentne slike nisu predstavljene kao izvršni ekrani aplikacije.

**Sačuvajte podatke:** debug APK-ovi između izdanja mogu biti potpisani različitim ključevima. Nemojte deinstalirati postojeću aplikaciju radi nadogradnje dok ne izvezete i provjerite šifriranu kopiju izvan uređaja. Lozinku sigurnosne kopije čuvajte odvojeno. Za produkciju je potreban stabilan potpis, fizički testovi i provjera pravnih obveza.

**Fakturiva by Brendigo.**


## Dokazane provjere izdanja

- Android build i lint: **uspješno**.
- Android emulator instrumentacijski testovi, uključujući status uplata i stvarne datume dospijeća: **uspješno**.
- Stvarne Android snimke svih 20 referentnih zaslona na rezoluciji 1179×2556: **snimljene i spremljene u README galeriji**.
- Vizualni audit: prosječna RGB MAE razlika **14,658 → 14,534/255** u odnosu na v0.8. Računi **11,182 → 9,994**, ponude **11,719 → 10,441**.
- Vizualno potvrđena čitljivost iznosa i statusnih oznaka; Androidov fullscreen pomoćni dijalog uklonjen s početnog zaslona.
- **Nije dokazano** 1:1, niti stabilnost na svim fizičkim uređajima. Debug AAB nije produkcijski potpisan.
