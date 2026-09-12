using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
using System.Runtime.InteropServices;
using Windows.Storage.Pickers;
using Windows.Media.SpeechSynthesis;
using Windows.Media.Playback;
using WinRT.Interop; // For WindowNative

namespace QuantumPdfViewer.Views;

public sealed partial class ViewerPage : Page
{
    private SpeechSynthesizer _synthesizer;
    private MediaPlayer _mediaPlayer;

    public ViewerPage()
    {
        Console.WriteLine("[TRACE] ViewerPage Constructor Start");
        try
        {
            this.InitializeComponent();
            Console.WriteLine("[TRACE] ViewerPage InitializeComponent Done");
        }
        catch (Exception ex)
        {
            System.IO.File.WriteAllText("C:\\Users\\Praveen Kumar\\Desktop\\viewer_fatal.log", ex.ToString());
            throw;
        }
        
        // Initialize TTS Engine safely
        try
        {
            _synthesizer = new SpeechSynthesizer();
            _mediaPlayer = new MediaPlayer();
            Console.WriteLine("[TRACE] ViewerPage TTS Initialized");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[TTS Init Failed]: {ex.Message}");
            System.Diagnostics.Debug.WriteLine($"[TTS Init Failed]: {ex.Message}");
        }

        // Initialize dummy rendering when the image control loads
        this.PdfRenderImage.Loaded += PdfRenderImage_Loaded;
        Console.WriteLine("[TRACE] ViewerPage Constructor End");
    }

    private void PdfRenderImage_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            uint width = 800;
            uint height = 1000;
            
            // Create a WriteableBitmap matching the desired PDF page resolution
            var bitmap = new Microsoft.UI.Xaml.Media.Imaging.WriteableBitmap((int)width, (int)height);
            
            // Allocate a managed byte array for the raw pixels (BGRA format)
            byte[] pixelData = new byte[width * height * 4];
            
            // Safely pin the managed array so we can pass its raw pointer to Rust
            var handle = System.Runtime.InteropServices.GCHandle.Alloc(pixelData, System.Runtime.InteropServices.GCHandleType.Pinned);
            try
            {
                Interop.QdcEngine.qdc_render_page(handle.AddrOfPinnedObject(), width, height);
            }
            finally
            {
                handle.Free();
            }

            // Write the populated pixel data into the WriteableBitmap's PixelBuffer
            using (var stream = System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions.AsStream(bitmap.PixelBuffer))
            {
                stream.Write(pixelData, 0, pixelData.Length);
            }

            this.PdfRenderImage.Source = bitmap;
            System.Diagnostics.Debug.WriteLine($"[QDC Engine] Rendered page to WriteableBitmap.");
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[QDC Engine] Failed to render page: {ex.Message}");
        }
    }

    private async void OpenPdfButton_Click(object sender, RoutedEventArgs e)
    {
        // 1. Create a native FileOpenPicker
        var picker = new FileOpenPicker();
        
        // 2. Associate the picker with the main window HWND 
        // We get the HWND from the App's main window
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
        InitializeWithWindow.Initialize(picker, hwnd);

        picker.ViewMode = PickerViewMode.Thumbnail;
        picker.SuggestedStartLocation = PickerLocationId.DocumentsLibrary;
        picker.FileTypeFilter.Add(".pdf");

        // 3. Show picker
        var file = await picker.PickSingleFileAsync();
        if (file != null)
        {
            // Update UI
            DocumentTitleText.Text = file.Name;
            WelcomeOverlay.Visibility = Visibility.Collapsed;
            
            // TODO: Pass file path to Rust DLL to begin rendering
            System.Diagnostics.Debug.WriteLine($"Selected PDF: {file.Path}");

            // TEST: Read the title aloud to verify TTS works
            await SpeakTextAsync($"Opened document: {file.Name}");
        }
    }

    private async System.Threading.Tasks.Task SpeakTextAsync(string text)
    {
        var stream = await _synthesizer.SynthesizeTextToStreamAsync(text);
        _mediaPlayer.Source = Windows.Media.Core.MediaSource.CreateFromStream(stream, stream.ContentType);
        _mediaPlayer.Play();
    }
}
