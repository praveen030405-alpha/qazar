using System;
using System.Runtime.InteropServices;

namespace QuantumPdfViewer.Interop
{
    /// <summary>
    /// P/Invoke bindings for the Rust qdc_win_bridge.dll
    /// </summary>
    internal static class QdcEngine
    {
        private const string DllName = "qdc_engine.dll";

        /// <summary>
        /// Initializes the QDC Engine. Must be called once at application startup.
        /// </summary>
        /// <returns>0 on success, -1 on failure</returns>
        [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
        public static extern int qdc_init();

        /// <summary>
        /// Returns a pointer to a C-string "QDC_OK".
        /// </summary>
        [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
        public static extern IntPtr qdc_health_check();

        /// <summary>
        /// Opens a PDF document.
        /// </summary>
        [DllImport(DllName, CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
        public static extern int qdc_open_document([MarshalAs(UnmanagedType.LPStr)] string inputPath);

        /// <summary>
        /// Renders a page to the pixel buffer.
        /// </summary>
        [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
        public static extern int qdc_render_page(IntPtr pixel_buffer, uint width, uint height, ushort page_index);

        /// <summary>
        /// Converts the given PDF document to a Word (DOCX) document using the QDC Rust Engine.
        /// </summary>
        [DllImport(DllName, CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
        public static extern int qdc_convert_to_word([MarshalAs(UnmanagedType.LPStr)] string inputPath);

        /// <summary>
        /// Request TTS generation for a string of text.
        /// Returns a pointer to WAV byte data.
        /// </summary>
        [DllImport(DllName, CallingConvention = CallingConvention.Cdecl, CharSet = CharSet.Ansi)]
        public static extern IntPtr qdc_tts_synthesize([MarshalAs(UnmanagedType.LPStr)] string text, out uint out_len);

        /// <summary>
        /// Free the allocated TTS byte buffer.
        /// </summary>
        [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
        public static extern void qdc_tts_free_buffer(IntPtr buffer, uint len);

        /// <summary>
        /// Helper to read the health check string safely.
        /// </summary>
        public static string GetHealthStatus()
        {
            IntPtr ptr = qdc_health_check();
            return Marshal.PtrToStringAnsi(ptr) ?? "ERROR";
        }
    }
}
