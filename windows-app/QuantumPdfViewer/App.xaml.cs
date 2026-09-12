using Windows.ApplicationModel;
using Windows.ApplicationModel.Activation;
using Windows.Foundation;
using Windows.Foundation.Collections;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Navigation;
using Microsoft.UI.Xaml.Shapes;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace QuantumPdfViewer;

/// <summary>
/// Provides application-specific behavior to supplement the default Application class.
/// </summary>
public sealed partial class App : Application
{
    public static MainWindow MainWindow { get; private set; }
    
    /// <summary>
    /// Initializes the singleton application object.  This is the first line of authored code
    /// executed, and as such is the logical equivalent of main() or WinMain().
    /// </summary>
    public App()
    {
        try
        {
            this.InitializeComponent();
        }
        catch (System.Exception ex)
        {
            System.IO.File.WriteAllText("C:\\Users\\Praveen Kumar\\Desktop\\app_fatal.log", ex.ToString());
            throw;
        }
        
        // Initialize the Rust backend engine via P/Invoke
        try 
        {
            int status = Interop.QdcEngine.qdc_init();
            string health = Interop.QdcEngine.GetHealthStatus();
            System.Diagnostics.Debug.WriteLine($"[QDC Engine] Init status: {status}, Health: {health}");
        }
        catch (System.Exception ex)
        {
            System.IO.File.WriteAllText("C:\\Users\\Praveen Kumar\\Desktop\\engine_fatal.log", ex.ToString());
            System.Diagnostics.Debug.WriteLine($"[QDC Engine] Failed to load qdc_engine.dll: {ex.Message}");
        }
    }

    /// <summary>
    /// Invoked when the application is launched.
    /// </summary>
    /// <param name="args">Details about the launch request and process.</param>
    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        this.UnhandledException += (sender, e) =>
        {
            try
            {
                System.IO.File.WriteAllText("C:\\Users\\Praveen Kumar\\Desktop\\qdc_crash.log", e.Exception.ToString() + "\n" + e.Message);
            }
            catch {}
            e.Handled = true;
        };

        try
        {
            MainWindow = new MainWindow();
            MainWindow.Activate();
        }
        catch (System.Exception ex)
        {
            System.IO.File.WriteAllText("C:\\Users\\Praveen Kumar\\Desktop\\launch_fatal.log", ex.ToString());
            throw;
        }
    }
}
