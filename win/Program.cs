using System;
using System.Windows.Forms;

namespace Fakturiva.Win;

/// <summary>Windows application entry point; no business state is kept here.</summary>
internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new FakturivaWindow());
    }
}
