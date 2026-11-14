"""Regression guardrails for the server/web/Android/Windows shared-revision path.

These source-level checks complement (but never replace) actual emulator,
PHP HTTP integration and desktop builds on the final exact commit.
"""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
ANDROID=ROOT/"app/src/main/java/com/brendigo/fakturiva"
WEB=ROOT/"web"
MAIN=(ANDROID/"MainActivity.java").read_text(encoding="utf-8")
MIRROR=(ANDROID/"CloudMirror.java").read_text(encoding="utf-8")
NETWORK=(ANDROID/"SelfHostedSyncClient.java").read_text(encoding="utf-8")
BUSINESS=(WEB/"lib_business_sync.php").read_text(encoding="utf-8")
PAYMENTS=(WEB/"lib_payment_sync.php").read_text(encoding="utf-8")
DOCUMENTS=(WEB/"lib_documents_sync.php").read_text(encoding="utf-8")
DOCUMENT_PAGE=(WEB/"documents.php").read_text(encoding="utf-8")
API=(WEB/"api.php").read_text(encoding="utf-8")
LIB=(WEB/"lib.php").read_text(encoding="utf-8")
PAGE=(WEB/"business.php").read_text(encoding="utf-8")


class CrossPlatformSyncChecks(unittest.TestCase):
    def test_login_pull_before_editing_and_never_on_dirty_or_drafts(self):
        self.assertIn("CloudMirror.canRefreshOnLogin(this,verifiedId)",MAIN)
        self.assertIn("!RecoveryVault.hasWorkingDraft(this)",MAIN)
        self.assertIn("CloudMirror.pullNewer(this,verifiedId",MAIN)
        self.assertIn('setup("cloud_refresh",false)',MAIN)
        self.assertIn('if("cloud_refresh".equals(screen) && selfHostedBusy)return;',MAIN)
        self.assertIn('if(!p.getBoolean("dirty",false)',MIRROR)
        self.assertIn('!p.getBoolean("conflict",false)',MIRROR)
        self.assertIn('!p.getBoolean("paused",false)',MIRROR)
        self.assertIn('store.restore(snapshot)',MIRROR)
        self.assertIn('SyncIntegrity.verifyRemote(remote,ciphertext)',MIRROR)
        self.assertIn('if(p.getBoolean("conflict",false)||p.getBoolean("paused",false))',MIRROR)
        self.assertIn('RecoveryVault.hasWorkingDraft(app)',MIRROR)

    def test_revision_probe_only_transfers_backup_when_newer(self):
        self.assertIn('"action","status"',NETWORK)
        self.assertIn("Hosting: Unknown action",NETWORK)
        self.assertIn("SelfHostedSyncClient.revisionStatus(url,token)",MIRROR)
        self.assertIn("if(advertised>known)",MIRROR)
        self.assertIn('new JSONObject().put("action","load")',MIRROR)
        self.assertIn('SyncIntegrity.requireAcknowledgement(',MIRROR)
        self.assertIn("'status'=>FakturivaSelfHost\\backupStatus(authToken())",API)
        self.assertIn("function backupStatus(string $token)",LIB)
        self.assertNotIn("backup_b64",LIB.split(
            "function backupStatus(string $token)",1)[1].split(
            "function loadBackup(string $token)",1)[0])

    def test_web_mutations_are_sealed_atomic_and_identity_isolated(self):
        self.assertIn("\\FakturivaSelfHost\\transaction(",BUSINESS)
        self.assertIn("\\FakturivaSelfHost\\webAuthorized($id,$webToken)",BUSINESS)
        self.assertIn("FakturivaWebFinance\\openSnapshot",BUSINESS)
        self.assertIn("sealSnapshot($root,$passphrase)",BUSINESS)
        self.assertIn("appendPreviousBackup($account,strlen($b64))",BUSINESS)
        self.assertIn("$account['revision']=$expected+1",BUSINESS)
        self.assertIn("'FAK1'.$salt.$nonce.$cipher.$tag",BUSINESS)
        self.assertIn("checkCsrf()",PAGE)
        self.assertIn("requireWeb()",PAGE)
        self.assertIn("autocomplete=\"off\"",PAGE)
        self.assertNotIn("$_SESSION['passphrase']",PAGE)

    def test_web_payment_writes_share_android_revision_and_preserve_invoice_totals(self):
        self.assertIn("require_once __DIR__.'/lib_payment_sync.php'",BUSINESS)
        self.assertIn("return paymentModified($root,$input)",BUSINESS)
        self.assertIn("'payment_add'",PAGE)
        self.assertIn("syncHidden('payment_add',$revision)",PAGE)
        self.assertIn("function payableInvoices(",PAYMENTS)
        self.assertIn("function paymentModified(",PAYMENTS)
        self.assertIn("priceInCents($input['amount']",PAYMENTS)
        self.assertIn("$amount>$available[$id]['outstanding']",PAYMENTS)
        self.assertIn("$document['status']='PAID'",PAYMENTS)
        self.assertIn("in_array($channel,['CASH','BANK','CARD','CHEQUE']",PAYMENTS)
        self.assertNotIn("$_SESSION",PAYMENTS)

    def test_document_crud_and_confirmed_immutability(self):
        self.assertIn("require_once __DIR__.'/lib_documents_sync.php'",BUSINESS)
        self.assertIn("return documentModified($root,$operation,$input)",BUSINESS)
        for operation in ('doc_add','doc_edit','doc_delete','doc_confirm','quote_accept'):
            self.assertIn(operation,DOCUMENTS)
        self.assertIn("if($status!=='DRAFT')",DOCUMENTS)
        self.assertIn("documentIssuer($issuer)",DOCUMENTS)
        self.assertIn("intdiv($qtyMilli*$price+500,1000)",DOCUMENTS)
        self.assertIn("intdiv($net*$bp+5000,10000)",DOCUMENTS)
        self.assertIn("FakturivaWebBusiness\\publish(",DOCUMENT_PAGE)
        self.assertIn("checkCsrf()",DOCUMENT_PAGE)
        self.assertIn("requireWeb()",DOCUMENT_PAGE)
        self.assertIn("sync",BUSINESS)
        self.assertNotIn("$_SESSION['passphrase']",DOCUMENT_PAGE)

    def test_catalog_uses_integer_cents_and_issuer_history_is_preserved(self):
        self.assertIn("function priceInCents(",BUSINESS)
        self.assertIn("'price_cents'=>$price",BUSINESS)
        self.assertIn("'catalog_add'",BUSINESS)
        self.assertIn("'client_add'",BUSINESS)
        self.assertIn("$root['documents']",BUSINESS)  # Validates doc structure.
        self.assertIn("Već izdani dokumenti",PAGE)


if __name__=="__main__":
    unittest.main()
