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
        self.assertIn('set(a,"qaReferenceCroatian","01".equals(page)||"02".equals(page))',HARNESS)
        self.assertIn('if(qaReferenceCroatian)return "hr"',MAIN)
        self.assertIn('prefs.getString("ui_language","en")',MAIN)
        self.assertNotIn('putString("ui_language"',HARNESS)

    def test_welcome_reference_link_is_functional(self):
        self.assertIn('account.addView(label("Već imate račun? "',MAIN)
        self.assertIn('TextView signIn=label("Prijavite se"',MAIN)
        self.assertIn('account.setOnClickListener(v->openExistingLocalAccess())',MAIN)
        self.assertIn('if(prefs.getBoolean("device_lock",false))securityScreen(true,false)',MAIN)

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

    def test_splash_and_welcome_are_real_native_views_not_static_pngs(self):
        self.assertIn('new SplashBackdrop(this)',MAIN)
        self.assertIn('new WelcomeIllustration(this)',MAIN)
        self.assertIn('label("Računi i ponude, jednostavno."',MAIN)
        self.assertNotIn('BitmapFactory.decode',MAIN)


if __name__=="__main__":
    unittest.main()
