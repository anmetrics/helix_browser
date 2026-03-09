using Microsoft.UI.Xaml;

namespace HelixBrowser;

public partial class App : Application
{
    public App()
    {
        this.InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        m_window = new MainWindow();
        m_window.Title = "Helix Browser";
        m_window.Activate();
    }

    private Window? m_window;
}
