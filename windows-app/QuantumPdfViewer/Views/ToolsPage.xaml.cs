using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
using System.Threading.Tasks;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace QuantumPdfViewer.Views;

public sealed partial class ToolsPage : Page
{
    public ToolsPage()
    {
        this.InitializeComponent();
    }

    private async void PdfToWord_Click(object sender, RoutedEventArgs e)
    {
        // 1. Pick a file to convert
        var picker = new FileOpenPicker();
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
        InitializeWithWindow.Initialize(picker, hwnd);

        picker.ViewMode = PickerViewMode.Thumbnail;
        picker.SuggestedStartLocation = PickerLocationId.DocumentsLibrary;
        picker.FileTypeFilter.Add(".pdf");

        var file = await picker.PickSingleFileAsync();
        if (file != null)
        {
            // Show loading overlay
            ProgressText.Text = $"Converting {file.Name} to Word...";
            ProgressOverlay.Visibility = Visibility.Visible;

            // Call Rust C-ABI function asynchronously so we don't block the WinUI 3 UI thread
            int status = await Task.Run(() => 
            {
                return Interop.QdcEngine.qdc_convert_to_word(file.Path);
            });

            ProgressOverlay.Visibility = Visibility.Collapsed;

            if (status == 0)
            {
                // Show success dialog
                ContentDialog dialog = new ContentDialog
                {
                    Title = "Conversion Complete",
                    Content = $"Successfully converted {file.Name} using the QDC Rust Engine.",
                    CloseButtonText = "OK",
                    XamlRoot = this.XamlRoot
                };
                await dialog.ShowAsync();
            }
            else
            {
                // Show error dialog
                ContentDialog dialog = new ContentDialog
                {
                    Title = "Conversion Failed",
                    Content = $"The Rust QDC Engine returned error code: {status}",
                    CloseButtonText = "Close",
                    XamlRoot = this.XamlRoot
                };
                await dialog.ShowAsync();
            }
        }
    }
}
