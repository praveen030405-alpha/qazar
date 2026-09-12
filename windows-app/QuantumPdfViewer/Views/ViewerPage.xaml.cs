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
        // Wait for a document to be opened to render.
    }

    private void RenderCurrentPage(ushort pageIndex = 0)
    {
        try
        {
            uint width = 800;
            uint height = 1000;
            
            var bitmap = new Microsoft.UI.Xaml.Media.Imaging.WriteableBitmap((int)width, (int)height);
            byte[] pixelData = new byte[width * height * 4];
            var handle = System.Runtime.InteropServices.GCHandle.Alloc(pixelData, System.Runtime.InteropServices.GCHandleType.Pinned);
            try
            {
                Interop.QdcEngine.qdc_render_page(handle.AddrOfPinnedObject(), width, height, pageIndex);
            }
            finally
            {
                handle.Free();
            }

            using (var stream = System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions.AsStream(bitmap.PixelBuffer))
            {
                stream.Write(pixelData, 0, pixelData.Length);
            }

            this.PdfRenderImage.Source = bitmap;
            System.Diagnostics.Debug.WriteLine($"[QDC Engine] Rendered page {pageIndex} to WriteableBitmap.");
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"[QDC Engine] Failed to render page: {ex.Message}");
        }
    }

    private async void OpenPdfButton_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker();
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.MainWindow);
        InitializeWithWindow.Initialize(picker, hwnd);

        picker.ViewMode = PickerViewMode.Thumbnail;
        picker.SuggestedStartLocation = PickerLocationId.DocumentsLibrary;
        picker.FileTypeFilter.Add(".pdf");

        var file = await picker.PickSingleFileAsync();
        if (file != null)
        {
            DocumentTitleText.Text = file.Name;
            WelcomeOverlay.Visibility = Visibility.Collapsed;
            
            System.Diagnostics.Debug.WriteLine($"Selected PDF: {file.Path}");

            // Open document in Rust Engine
            Interop.QdcEngine.qdc_open_document(file.Path);
            
            // Render first page
            RenderCurrentPage(0);

            // Test Premium TTS Mock
            await SpeakTextAsync($"Opened document: {file.Name}. Converting to docx.");
            
            // Test Conversion
            Interop.QdcEngine.qdc_convert_to_word(file.Path);
        }
    }

    private async System.Threading.Tasks.Task SpeakTextAsync(string text)
    {
        System.Diagnostics.Debug.WriteLine($"[TTS] Synthesizing: {text}");
        
        uint outLen = 0;
        IntPtr ptr = Interop.QdcEngine.qdc_tts_synthesize(text, out outLen);
        
        if (ptr != IntPtr.Zero && outLen > 0)
        {
            byte[] wavData = new byte[outLen];
            Marshal.Copy(ptr, wavData, 0, (int)outLen);
            Interop.QdcEngine.qdc_tts_free_buffer(ptr, outLen);
            
            using (var memoryStream = new System.IO.MemoryStream(wavData))
            {
                var ras = memoryStream.AsRandomAccessStream();
                _mediaPlayer.Source = Windows.Media.Core.MediaSource.CreateFromStream(ras, "audio/wav");
                _mediaPlayer.Play();
            }
        }
        else
        {
            System.Diagnostics.Debug.WriteLine("[TTS] Failed to synthesize audio.");
        }
    }
}
