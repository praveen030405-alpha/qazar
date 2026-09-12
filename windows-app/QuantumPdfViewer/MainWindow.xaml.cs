using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
using WinRT.Interop;

namespace QuantumPdfViewer;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        Console.WriteLine("[TRACE] MainWindow Constructor Start");
        try
        {
            this.InitializeComponent();
            Console.WriteLine("[TRACE] MainWindow InitializeComponent Done");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[CRASH] InitializeComponent failed: {ex}");
            System.Diagnostics.Debug.WriteLine($"[CRASH] InitializeComponent failed: {ex}");
            System.IO.File.WriteAllText("C:\\Users\\Praveen Kumar\\Desktop\\qdc_fatal.log", ex.ToString());
            throw;
        }
        
        // Set window title
        this.Title = "Quantum PDF Viewer (QDC Engine)";
        Console.WriteLine("[TRACE] MainWindow Title Set");
        // Wait until visual tree is constructed before navigating!
        this.Activated += MainWindow_Activated;
    }

    private bool _initialized = false;
    private void MainWindow_Activated(object sender, WindowActivatedEventArgs args)
    {
        if (!_initialized)
        {
            _initialized = true;
            // Set default page
            NavView.SelectedItem = NavView.MenuItems[0];
            Console.WriteLine("[TRACE] MainWindow NavView SelectedItem Set in Activated");
        }
    }

    private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        Console.WriteLine("[TRACE] NavView_SelectionChanged Start");
        if (args.SelectedItem is NavigationViewItem item)
        {
            switch (item.Tag)
            {
                case "viewer":
                    Console.WriteLine("[TRACE] Navigating to ViewerPage");
                    RootFrame.Navigate(typeof(Views.ViewerPage));
                    Console.WriteLine("[TRACE] Navigated to ViewerPage");
                    break;
                case "tools":
                    Console.WriteLine("[TRACE] Navigating to ToolsPage");
                    RootFrame.Navigate(typeof(Views.ToolsPage));
                    Console.WriteLine("[TRACE] Navigated to ToolsPage");
                    break;
            }
        }
        Console.WriteLine("[TRACE] NavView_SelectionChanged End");
    }
}
