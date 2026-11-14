using Fakturiva.Win;

string[] trusted = {
    "https://fakturiva.eu/",
    "https://FAKTURIVA.EU/login.php",
    "https://fakturiva.eu:443/documents.php?mode=show"
};
string?[] rejected = {
    null, "", "http://fakturiva.eu/", "file:///C:/Windows/system.ini",
    "javascript:alert(1)", "data:text/html,unsafe",
    "https://fakturiva.eu.evil.test/", "https://notfakturiva.eu/",
    "https://fakturiva.eu@evil.test/", "https://user@fakturiva.eu/",
    "https://fakturiva.eu:8443/", "https://localhost/",
    "https://127.0.0.1/"
};
foreach (var url in trusted)
    if (!WebViewSecurityPolicy.Allowed(url))
        throw new Exception("Expected safe Fakturiva navigation: " + url);
foreach (var url in rejected)
    if (WebViewSecurityPolicy.Allowed(url))
        throw new Exception("Unsafe navigation was accepted: " + url);
Console.WriteLine($"Windows WebView2 origin policy: {trusted.Length} allowed, {rejected.Length} denied");
