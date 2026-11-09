# Fakturiva Windows (početna implementacija)

Windows klijent trenutno koristi WebView2 za HTTPS korisnički panel
`https://fakturiva.com`, a ne odvojenu bazu niti zasebnu prijavu.
Ovo **nije** 1:1 port svih 20 Android ekrana; njihova izrada i
poređenje screenshotova s referencama ostaju zasebna obveza.

Za razvojni build koristite .NET 10 SDK na Windowsu i
Microsoft.Web.WebView2 1.0.4314.35.

```powershell
dotnet publish win/Fakturiva.Win.csproj -c Release -r win-x64 --self-contained true -o win/publish
```

`win/publish/Fakturiva.exe` isporučuje se uz pripadajuće
datoteke u mapi `publish`; izdvojeni `Fakturiva.exe` ne treba
distribuirati bez pripadajućih runtime paketa ako ih objavljena
verzija zahtijeva. Za instalaciju:

```powershell
iscc win/installer.iss
```

Windows WebView2 Runtime mora biti instaliran na ciljanom računalu.
U Windows korisničkom profilu ostaju nužni kolačići i WebView2 cache;
poslovni podatci ostaju vezani uz poslužiteljski račun.
Mreža i dostupnost objavljene domene potrebni su za rad.
Ne promovirati kao dovršeni Windows proizvod do stvarnog
QA procesa, testa instalera, provjere offline slučajeva i
izdanih sigurnosnih pravila.
