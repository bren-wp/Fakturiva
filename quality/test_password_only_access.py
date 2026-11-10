"""Regression guards for the public password-only account flow.

Legacy verifier data may exist in encrypted storage for migration, but must
never expose a standalone PIN login/registration control or handler.
"""
from pathlib import Path
import re
import unittest

ROOT=Path(__file__).resolve().parents[1]
MAIN=(ROOT/"app/src/main/java/com/brendigo/fakturiva/MainActivity.java").read_text(encoding="utf-8")
BRIDGE=(ROOT/"app/src/main/java/com/brendigo/fakturiva/AccountBridge.java").read_text(encoding="utf-8")
LOCAL=(ROOT/"app/src/main/java/com/brendigo/fakturiva/LocalAccounts.java").read_text(encoding="utf-8")
WEB=(ROOT/"web/lib.php").read_text(encoding="utf-8")
AUTH=(ROOT/"web/auth.php").read_text(encoding="utf-8")


class PasswordOnlyAccessTest(unittest.TestCase):
    def test_app_does_not_offer_pin_or_alternative_login(self):
        for obsolete in ("loginPinScreen", "updatePinDots", "enteredLocalPin",
                         "Prijava PIN-om", "Prijava biometrijom", "Lokalni PIN",
                         "PIN za potvrdu brisanja"):
            self.assertNotIn(obsolete, MAIN)
        self.assertIn("AccountBridge.verifyCredentials(this,name,secret", MAIN)
        self.assertIn("AccountBridge.restoreLogin(this,name,secret", MAIN)
        self.assertIn("AccountBridge.register(this,username,secret", MAIN)
        self.assertIn("LocalAccounts.verify(this,email,localSecret)", MAIN)

    def test_legacy_local_profile_migrates_only_after_web_auth(self):
        self.assertIn("verifiedId = LocalAccounts.rekeyAfterVerifiedServerLogin(", BRIDGE)
        self.assertLess(BRIDGE.index("token = SelfHostedSyncClient.request(url, auth, null)"),
                        BRIDGE.index("rekeyAfterVerifiedServerLogin("))
        self.assertIn('credential_version",2', LOCAL)
        self.assertIn('entry.optInt("credential_version",1) != 2', LOCAL)
        self.assertIn('String rekeyAfterVerifiedServerLogin(', LOCAL)

    def test_web_login_and_sensitive_operations_are_password_only(self):
        for obsolete in ("function webLogin(", "function webLinkAndroidAccount(",
                         "function requireSensitiveWebPin(", "function pinMatches(",
                         "function checkWebPin(", "function pinHash("):
            self.assertNotIn(obsolete, WEB)
        for obsolete in ("'login'=>'login-pin.php'", "'link'=>'link.php'",
                         "'identify'=>'identify.php'", "webLogin($username",
                         "webLinkAndroidAccount("):
            self.assertNotIn(obsolete, AUTH)
        self.assertIn('webLoginPassword($username,$passphrase,$ip)', AUTH)
        self.assertIn('webChangeAndroidPassphrase(', AUTH)
        self.assertIn('web_pin_hash', WEB)  # removed only after verified password login

    def test_no_public_web_numeric_input(self):
        for filename in ("web/login.php","web/register.php","web/dashboard.php",
                         "web/delete-account.php","web/assets/auth.js"):
            source=(ROOT/filename).read_text(encoding="utf-8")
            self.assertNotIn('data-numeric-pin',source,filename)
            self.assertNotIn('data-keypad',source,filename)
            self.assertNotIn('pattern="[0-9]{4,6}"',source,filename)
            self.assertNotIn('Prijava PIN-om',source,filename)
        for filename in ("web/login-pin.php","web/identify.php","web/link.php"):
            source=(ROOT/filename).read_text(encoding="utf-8")
            self.assertIn("gotoPage('login.php')",source,filename)

    def test_android_manifest_targets_modern_sdk_and_disables_cleartext(self):
        manifest=(ROOT/"app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        build=(ROOT/"app/build.gradle").read_text(encoding="utf-8")
        self.assertIn('usesCleartextTraffic="false"',manifest)
        self.assertIn('allowBackup="false"',manifest)
        self.assertRegex(build,r"targetSdk\s+36\b")
        self.assertRegex(build,r"minSdk\s+24\b")


if __name__=="__main__":
    unittest.main()
