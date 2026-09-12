using System;
using System.Runtime.InteropServices;
using WinRT;

namespace QuantumPdfViewer.Interop
{
    [ComImport]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    [Guid("63AAD0B8-7C24-40FF-85A8-640D944CC325")]
    internal interface ISwapChainPanelNative
    {
        void SetSwapChain(IntPtr swapChain);
    }
}
