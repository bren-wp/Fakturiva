"""Regression checks for the two supplied 1179 x 2556 entry references.

The screenshot harness may display Croatian reference content, but the
production application must retain its selected language and a native UI.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
MAIN=(ROOT/"app/src/main/java/com/brendigo/fakturiva/MainActivity.java").read_text(encoding="utf-8")
HARNESS=(ROOT/"app/src/debug/java/com/brendigo/fakturiva/ScreenshotHarness.java").read_text(encoding="utf-8")
FOREGROUND=(ROOT/"app/src/main/res/drawable/brand_fg.xml").read_text(encoding="utf-8")
ADAPTIVE=(ROOT/"app/src/main/res/drawable/brand_adaptive_bg.xml").read_text(encoding="utf-8")


class EntryReferenceTest(unittest.TestCase):
    def test_debug_reference_language_is_not_persisted(self):
        self.assertIn('set(a,"qaReferenceCroatian",true)',HARNESS)
        self.assertIn('if(qaReferenceCroatian)return "hr"',MAIN)
        self.assertIn('prefs.getString("ui_language",preferred)',MAIN)
        self.assertNotIn('putString("ui_language"',HARNESS)

    def test_welcome_reference_link_is_functional(self):
        self.assertIn('account.addView(label("Već imate račun? "',MAIN)
        self.assertIn('TextView signIn=label("Prijavite se"',MAIN)
        self.assertIn('account.setOnClickListener(v->openExistingLocalAccess())',MAIN)
        # Remote email/password login is the default entry path; verified
        # local PIN remains an offline fallback for existing profiles.
        self.assertIn('if(LocalAccounts.hasAccounts(this))loginServerScreen()', MAIN)
        self.assertIn('AccountBridge.restoreLogin(', MAIN)
        self.assertIn('AccountBridge.register(', MAIN)
        self.assertIn('secondary(body,"Prijava lokalnim PIN-om (bez interneta)"', MAIN)
        self.assertIn('call(a,"loginServerScreen")', HARNESS)
        self.assertIn('String id=LocalAccounts.verify(this,loginUsername,digits)', MAIN)
        self.assertIn('openLocalSession(id)', MAIN)
        self.assertIn('if(prefs.getBoolean("device_lock",false))', MAIN)
        self.assertIn('securityScreen(true,false)', MAIN)

    def test_server_first_auth_binds_only_acknowledged_snapshots(self):
        bridge=(ROOT/"app/src/main/java/com/brendigo/fakturiva/AccountBridge.java").read_text(encoding="utf-8")
        mirror=(ROOT/"app/src/main/java/com/brendigo/fakturiva/CloudMirror.java").read_text(encoding="utf-8")
        self.assertIn('https://fakturiva.eu/',bridge)
        self.assertIn('SelfHostedSyncClient.request(url, auth, null)',bridge)
        self.assertIn('SecureBackup.decrypt(sealed, secret)',bridge)
        self.assertIn('LocalAccounts.register(app, username, digits)',bridge)
        self.assertIn('CloudMirror.connectAcknowledged(app, id, url, username',bridge)
        self.assertIn('.putBoolean("dirty", false)',mirror)
        self.assertIn('.putInt("revision", revision)',mirror)
        self.assertIn('if (!sha256(sealed).equals(remote.getString("sha256")))',bridge)
        self.assertIn('ensureNewLocalProfile(app, username)',bridge)

    def test_reference_login_has_real_device_credential_then_pin_flow(self):
        self.assertIn('action("◎  Prijava biometrijom",BLUE,false,',MAIN)
        self.assertIn('this::deviceVerifiedLocalAccess',MAIN)
        self.assertIn('confirmDeviceCredential(4)',MAIN)
        self.assertIn('completedAction==4',MAIN)
        self.assertIn('loginPinScreen()',MAIN)
        self.assertIn('String id=LocalAccounts.verify(this,loginUsername,digits)',MAIN)

    def test_dark_reference_background_and_eracun_surface_match_palette(self):
        self.assertIn('dark?0xff0d1728:BG',MAIN)
        self.assertIn('dark?0xff253451:0xffedf0ff',MAIN)
        self.assertIn('dark?0xff263853:0xfff1f5ff',MAIN)
        self.assertIn('"eracun".equals(screen)',MAIN)

    def test_android_email_registration_requires_matching_password_before_pin(self):
        registration=MAIN.split("private void registrationScreen(){",1)[1].split(
            "private void localBrand(){",1)[0]
        self.assertIn('place(body,label("E-adresa"',registration)
        self.assertIn('place(body,label("Lozinka"',registration)
        self.assertIn('place(body,label("Ponovite lozinku"',registration)
        self.assertIn('firstPassword.equals(confirm.getText().toString())',registration)
        self.assertIn('AlertDialog dialog=new AlertDialog.Builder(this)',registration)
        self.assertIn('AccountBridge.register(this,username,secret,digits',registration)
        self.assertIn('restoreDialog.setOnShowListener',MAIN)
        self.assertIn('localPin.setError("PIN mora imati 4–6 znamenki.")',MAIN)
        # PIN belongs exclusively to the modal, not the initial form.
        self.assertLess(registration.index('place(body,label("Lozinka"'),
                        registration.index('EditText pin=field('))
        self.assertIn('https://fakturiva.eu/uvjeti-koristenja/',registration)

    def test_failed_first_cloud_upload_never_leaves_an_unlinked_local_profile(self):
        bridge=(ROOT/"app/src/main/java/com/brendigo/fakturiva/AccountBridge.java").read_text(
            encoding="utf-8")
        self.assertIn('staged = new Store(app, stagedId)',bridge)
        self.assertIn('app.deleteDatabase(Store.databaseName(stagedId))',bridge)
        self.assertIn('if (remoteRevision != 1)',bridge)
        self.assertLess(bridge.index('JSONObject ack = SelfHostedSyncClient.request('),
                        bridge.index('id = LocalAccounts.register(app, username, digits)'))
        self.assertLess(bridge.index('ensureNewLocalProfile(app, username);\n                id ='),
                        bridge.index('CloudMirror.connectAcknowledged(app, id'))

    def test_cross_device_manual_restore_is_fail_closed_and_recovery_backed(self):
        mirror=(ROOT/"app/src/main/java/com/brendigo/fakturiva/CloudMirror.java").read_text(
            encoding="utf-8")
        self.assertIn('public static void pullNewer(Context ctx,String id,PullCallback callback)',mirror)
        self.assertIn('if(p.getBoolean("dirty",false))',mirror)
        self.assertIn('if(!sha(ciphertext).equals(remote.getString("sha256")))',mirror)
        self.assertIn('SecureBackup.decrypt(ciphertext,pass)',mirror)
        self.assertIn('store.restore(snapshot)',mirror)
        self.assertIn('.putBoolean("dirty",false)',mirror)
        self.assertIn('.putInt("revision",actual)',mirror)
        self.assertIn('new AlertDialog.Builder(this)',MAIN)
        self.assertIn('CloudMirror.pullNewer(this,profile,(error,updated)->{',MAIN)
        self.assertIn('setCancelable(false).create()',MAIN)
        self.assertIn('Preuzmi novije podatke s weba',MAIN)

    def test_reference_eracun_vector_and_quote_conversion_are_real_actions(self):
        icon=(ROOT/"app/src/main/res/drawable/ic_eracun_exchange.xml").read_text(
            encoding="utf-8")
        self.assertIn('android:strokeWidth="2.0"',icon)
        self.assertIn('R.drawable.ic_eracun_exchange',MAIN)
        self.assertIn('place(body,action("Pretvori u račun"',MAIN)
        self.assertIn('long invoiceId=db.quoteToInvoice(id)',MAIN)
        self.assertIn('db.acceptQuote(id)',MAIN)

    def test_adaptive_launcher_uses_full_bleed_gradient_and_brand_glyph(self):
        self.assertIn('android:scaleX="1.35"',FOREGROUND)
        self.assertIn('android:translateX="9.9"',FOREGROUND)
        self.assertIn('#FFFFFF',FOREGROUND)
        self.assertIn('#2FD2C5',FOREGROUND)
        self.assertIn('#164472',ADAPTIVE)
        self.assertIn('#0A2038',ADAPTIVE)
        for path in ("ic_launcher.xml","ic_launcher_round.xml"):
            launcher=(ROOT/"app/src/main/res/mipmap-anydpi-v26"/path).read_text(encoding="utf-8")
            self.assertIn('@drawable/brand_adaptive_bg',launcher)
            self.assertIn('@drawable/brand_fg',launcher)

    def test_owner_requested_splash_duration_is_four_to_five_seconds(self):
        self.assertIn('SPLASH_DURATION_MS=4500',MAIN)
        self.assertIn('frame.postDelayed(this::finishSplash,SPLASH_DURATION_MS)',MAIN)
        self.assertNotIn('frame.setOnClickListener(v->finishSplash())',MAIN)
        self.assertIn('if("splash".equals(screen)){finish();return;}',MAIN)

    def test_splash_and_welcome_are_real_native_views_not_static_pngs(self):
        self.assertIn('new SplashBackdrop(this)',MAIN)
        self.assertIn('new WelcomeIllustration(this)',MAIN)
        self.assertIn('label("Računi i ponude, jednostavno."',MAIN)
        self.assertNotIn('BitmapFactory.decode',MAIN)


if __name__=="__main__":
    unittest.main()
