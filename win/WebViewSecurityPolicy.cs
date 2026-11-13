using System;

namespace Fakturiva.Win;

/// <summary>Exact-origin HTTPS navigation policy for WebView2.</summary>
internal static class WebViewSecurityPolicy
{
    internal static bool Allowed(string? target)
    {
        return Uri.TryCreate(target, UriKind.Absolute, out var uri)
            && uri.Scheme == Uri.UriSchemeHttps
            && uri.Host.Equals("fakturiva.eu", StringComparison.OrdinalIgnoreCase)
            && uri.Port == 443
            && string.IsNullOrEmpty(uri.UserInfo);
    }
}
