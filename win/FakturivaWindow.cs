using System;
using System.Drawing;
using System.Windows.Forms;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;

namespace Fakturiva.Win;

/// <summary>
/// WebView2 host for the same HTTPS account as the Fakturiva web portal.
/// Navigation is limited to the canonical trusted Fakturiva origin.
/// </summary>
internal sealed class FakturivaWindow : Form
{
    private const string BaseUrl = "https://fakturiva.eu/";
    private readonly WebView2 browser = new() { Dock = DockStyle.Fill };
    private readonly ToolStrip bar = new() { GripStyle = ToolStripGripStyle.Hidden };
    private readonly ToolStripStatusLabel status = new("Povezivanje s Fakturivom…");
    private readonly StatusStrip foot = new();
    private readonly ToolStripButton back = new("Natrag");
    private readonly ToolStripButton refresh = new("Osvježi");
    private readonly ToolStripButton home = new("Početna");
    private readonly ToolStripButton privacy = new("Privatnost");

    public FakturivaWindow()
    {
        Text = "Fakturiva";
        StartPosition = FormStartPosition.CenterScreen;
        MinimumSize = new Size(360, 560);
        Size = new Size(1100, 800);
        BackColor = Color.White;
        ForeColor = Color.FromArgb(20, 38, 63);
        AutoScaleMode = AutoScaleMode.Dpi;
        back.Enabled = false;
        refresh.Enabled = false;
        home.Enabled = false;
        privacy.Enabled = false;
        back.Click += (_, _) => { if (browser.CoreWebView2 != null && browser.CanGoBack) browser.GoBack(); };
        refresh.Click += (_, _) => browser.CoreWebView2?.Reload();
        home.Click += (_, _) => browser.CoreWebView2?.Navigate(BaseUrl);
        privacy.Click += (_, _) => browser.CoreWebView2?.Navigate(BaseUrl + "politika-privatnosti/");
        bar.Items.Add(back);
        bar.Items.Add(refresh);
        bar.Items.Add(home);
        bar.Items.Add(privacy);
        foot.Items.Add(status);
        Controls.Add(browser);
        Controls.Add(foot);
        Controls.Add(bar);
        bar.Dock = DockStyle.Top;
        Shown += async (_, _) =>
        {
            try
            {
                string folder = System.IO.Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "Fakturiva", "WebView2");
                var env = await CoreWebView2Environment.CreateAsync(null, folder);
                await browser.EnsureCoreWebView2Async(env);
                browser.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
                browser.CoreWebView2.Settings.AreDevToolsEnabled = false;
                browser.CoreWebView2.Settings.IsWebMessageEnabled = false;
                browser.CoreWebView2.Settings.AreHostObjectsAllowed = false;
                browser.CoreWebView2.Settings.IsScriptEnabled = true;
                browser.CoreWebView2.NavigationStarting += (_, e) =>
                {
                    // Only the exact HTTPS Fakturiva hostname can receive cookies.
                    if (!WebViewSecurityPolicy.Allowed(e.Uri)) { e.Cancel = true; status.Text = "Nepoznata adresa blokirana."; }
                };
                browser.CoreWebView2.NewWindowRequested += (_, e) =>
                {
                    e.Handled = true;
                    if (WebViewSecurityPolicy.Allowed(e.Uri)) browser.CoreWebView2.Navigate(e.Uri);
                };
                browser.CoreWebView2.DownloadStarting += (_, e) =>
                {
                    // Never download from a cross-origin URL, even when opened
                    // by a trusted portal page or a server-side redirect.
                    if (!WebViewSecurityPolicy.Allowed(e.DownloadOperation.Uri))
                    {
                        e.Cancel = true;
                        status.Text = "Preuzimanje s nepouzdane adrese blokirano.";
                    }
                };
                browser.CoreWebView2.NavigationCompleted += (_, e) =>
                {
                    back.Enabled = browser.CanGoBack;
                    refresh.Enabled = true;
                    home.Enabled = true;
                    privacy.Enabled = true;
                    status.Text = e.IsSuccess
                        ? "Fakturiva · zaštićena HTTPS veza"
                        : "Nije moguće pristupiti usluzi. Provjerite mrežu.";
                };
                browser.CoreWebView2.Navigate(BaseUrl);
            }
            catch (Exception)
            {
                status.Text = "Za pokretanje potreban je Microsoft Edge WebView2 Runtime.";
                MessageBox.Show("Za prijavu u Fakturivu potreban je WebView2 Runtime i internet veza.",
                    "Fakturiva", MessageBoxButtons.OK, MessageBoxIcon.Information);
            }
        };
    }

}
