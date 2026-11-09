#define MyAppName "Fakturiva"
#define MyAppVersion "1.0.0"
[Setup]
AppId={{319A1CA7-725D-4AD2-8B22-E6BE95205499}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=Brendigo
AppPublisherURL=https://fakturiva.com
DefaultDirName={autopf}\Fakturiva
DefaultGroupName=Fakturiva
DisableProgramGroupPage=yes
OutputDir=build
OutputBaseFilename=Fakturiva-setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
[Files]
Source: "publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{autoprograms}\Fakturiva"; Filename: "{app}\Fakturiva.exe"
Name: "{autodesktop}\Fakturiva"; Filename: "{app}\Fakturiva.exe"; Tasks: desktopicon
[Tasks]
Name: "desktopicon"; Description: "Prečac na radnoj površini"; GroupDescription: "Dodatne mogućnosti:"
[Run]
Filename: "{app}\Fakturiva.exe"; Description: "Pokreni Fakturivu"; Flags: nowait postinstall skipifsilent
