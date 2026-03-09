using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.Web.WebView2.Core;
using System;
using System.Collections.Generic;
using System.Linq;
using Windows.System;

namespace HelixBrowser;

public sealed partial class MainWindow : Window
{
    private readonly List<BrowserTab> _tabs = new();
    private BrowserTab? _activeTab;
    private readonly Prefs _prefs = Prefs.Instance;
    private readonly AdBlockEngine _adBlock = new();

    public MainWindow()
    {
        this.InitializeComponent();
        CreateNewTab("helix://start");
        SetupKeyboardShortcuts();
    }

    // MARK: - Tab Management

    private void CreateNewTab(string url = "helix://start", bool isIncognito = false)
    {
        var tab = new BrowserTab
        {
            Url = url,
            Title = "Tab mới",
            IsIncognito = isIncognito
        };

        var webView = new Microsoft.UI.Xaml.Controls.WebView2();
        webView.DefaultBackgroundColor = Windows.UI.Color.FromArgb(255, 10, 9, 30);
        tab.WebView = webView;
        _tabs.Add(tab);

        InitializeWebView(tab);
        SwitchToTab(tab);
        UpdateTabBar();
    }

    private async void InitializeWebView(BrowserTab tab)
    {
        var webView = tab.WebView!;
        await webView.EnsureCoreWebView2Async();

        var settings = webView.CoreWebView2.Settings;
        settings.AreDefaultScriptDialogsEnabled = true;
        settings.IsScriptEnabled = true;
        settings.AreDevToolsEnabled = true;
        settings.IsStatusBarEnabled = false;

        if (_prefs.IsBlockPopupsEnabled)
            settings.AreDefaultScriptDialogsEnabled = true;

        // Privacy settings
        if (_prefs.IsDoNotTrackEnabled)
            webView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);

        // HTTPS upgrade
        webView.CoreWebView2.NavigationStarting += (s, e) =>
        {
            ProgressBar.Visibility = Visibility.Visible;
            ProgressBar.Value = 20;
            AddressBox.Text = e.Uri;
            UpdateSslIcon(e.Uri);
        };

        webView.CoreWebView2.NavigationCompleted += (s, e) =>
        {
            ProgressBar.Value = 100;
            ProgressBar.Visibility = Visibility.Collapsed;
            tab.Title = webView.CoreWebView2.DocumentTitle;
            tab.Url = webView.CoreWebView2.Source;
            AddressBox.Text = tab.Url;
            UpdateNavigationButtons();
            UpdateTabBar();
        };

        webView.CoreWebView2.NewWindowRequested += (s, e) =>
        {
            e.Handled = true;
            CreateNewTab(e.Uri);
        };

        // Ad blocking
        if (_prefs.IsAdBlockEnabled)
        {
            webView.CoreWebView2.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
            webView.CoreWebView2.WebResourceRequested += (s, e) =>
            {
                if (_adBlock.ShouldBlock(e.Request.Uri))
                {
                    e.Response = webView.CoreWebView2.Environment.CreateWebResourceResponse(null, 403, "Blocked", "");
                }
            };
        }

        // Privacy scripts
        if (_prefs.IsBlockFingerprintingEnabled)
        {
            await webView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(PrivacyScripts.AntiFingerprintingJs);
        }
        if (_prefs.IsBlockTrackersEnabled)
        {
            await webView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(PrivacyScripts.TrackerBlockingJs);
        }

        // Navigate
        if (tab.Url != "helix://start")
        {
            webView.CoreWebView2.Navigate(tab.Url);
        }
        else
        {
            webView.CoreWebView2.NavigateToString(StartPageHtml.Generate());
        }
    }

    private void SwitchToTab(BrowserTab tab)
    {
        _activeTab = tab;
        WebViewContainer.Children.Clear();
        if (tab.WebView != null)
        {
            WebViewContainer.Children.Add(tab.WebView);
        }
        AddressBox.Text = tab.Url == "helix://start" ? "" : tab.Url;
        UpdateNavigationButtons();
        UpdateTabBar();
    }

    private void CloseTab(BrowserTab tab)
    {
        if (_tabs.Count <= 1) return;
        if (tab.IsPinned) return;
        var index = _tabs.IndexOf(tab);
        _tabs.Remove(tab);
        tab.WebView = null;
        if (_activeTab == tab)
        {
            var newIndex = Math.Min(index, _tabs.Count - 1);
            SwitchToTab(_tabs[newIndex]);
        }
        UpdateTabBar();
    }

    private void UpdateTabBar()
    {
        TabBar.Children.Clear();
        foreach (var tab in _tabs)
        {
            var isActive = tab == _activeTab;
            var btn = new Button
            {
                Height = 34,
                MinWidth = 40,
                MaxWidth = 200,
                Padding = new Thickness(8, 0, 8, 0),
                Background = isActive ?
                    new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(20, 255, 255, 255)) :
                    new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(0, 0, 0, 0)),
                BorderThickness = new Thickness(0),
                CornerRadius = new CornerRadius(6),
            };

            var stack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };

            if (tab.IsPinned)
            {
                stack.Children.Add(new FontIcon { Glyph = "\uE718", FontSize = 10, Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(255, 139, 139, 255)) });
            }
            if (tab.IsIncognito)
            {
                stack.Children.Add(new FontIcon { Glyph = "\uE890", FontSize = 10, Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(255, 255, 126, 179)) });
            }

            var title = new TextBlock
            {
                Text = string.IsNullOrEmpty(tab.Title) ? "Tab mới" : tab.Title,
                FontSize = 11,
                Foreground = isActive ?
                    new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(255, 255, 255, 255)) :
                    new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(255, 160, 160, 208)),
                MaxWidth = 120,
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            stack.Children.Add(title);

            if (!tab.IsPinned)
            {
                var closeBtn = new Button
                {
                    Content = "\u00D7",
                    FontSize = 12,
                    Width = 20,
                    Height = 20,
                    Padding = new Thickness(0),
                    Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(0, 0, 0, 0)),
                    BorderThickness = new Thickness(0),
                    Foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(Windows.UI.Color.FromArgb(255, 160, 160, 208))
                };
                var tabToClose = tab;
                closeBtn.Click += (s, e) => CloseTab(tabToClose);
                stack.Children.Add(closeBtn);
            }

            btn.Content = stack;
            var tabToSwitch = tab;
            btn.Click += (s, e) => SwitchToTab(tabToSwitch);
            TabBar.Children.Add(btn);
        }
    }

    // MARK: - Navigation

    private void LoadUrl(string input)
    {
        var formatted = UrlUtils.FormatUrl(input, _prefs.SearchEngine);
        if (_activeTab == null) return;
        _activeTab.Url = formatted;

        if (formatted == "helix://start")
        {
            _activeTab.WebView?.CoreWebView2?.NavigateToString(StartPageHtml.Generate());
        }
        else
        {
            _activeTab.WebView?.CoreWebView2?.Navigate(formatted);
        }
        AddressBox.Text = formatted == "helix://start" ? "" : formatted;
    }

    private void UpdateNavigationButtons()
    {
        BackBtn.IsEnabled = _activeTab?.WebView?.CanGoBack ?? false;
        ForwardBtn.IsEnabled = _activeTab?.WebView?.CanGoForward ?? false;
        BackBtn.Opacity = BackBtn.IsEnabled ? 1.0 : 0.3;
        ForwardBtn.Opacity = ForwardBtn.IsEnabled ? 1.0 : 0.3;
    }

    private void UpdateSslIcon(string url)
    {
        var isSecure = url.StartsWith("https://");
        SslIcon.Glyph = isSecure ? "\uE72E" : "\uE946";
        SslIcon.Foreground = isSecure ?
            (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["HelixGreenBrush"] :
            (Microsoft.UI.Xaml.Media.Brush)Application.Current.Resources["HelixPurpleBrush"];
    }

    // MARK: - Event Handlers

    private void NewTab_Click(object sender, RoutedEventArgs e) => CreateNewTab();
    private void Back_Click(object sender, RoutedEventArgs e) => _activeTab?.WebView?.GoBack();
    private void Forward_Click(object sender, RoutedEventArgs e) => _activeTab?.WebView?.GoForward();
    private void Reload_Click(object sender, RoutedEventArgs e) => _activeTab?.WebView?.Reload();
    private void Home_Click(object sender, RoutedEventArgs e) => LoadUrl(_prefs.Homepage);

    private void AddressBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key == VirtualKey.Enter)
        {
            var text = AddressBox.Text?.Trim();
            if (!string.IsNullOrEmpty(text)) LoadUrl(text);
        }
    }

    private void Bookmark_Click(object sender, RoutedEventArgs e)
    {
        // Toggle bookmark for current page
        if (_activeTab == null) return;
        var db = DatabaseManager.Instance;
        if (db.IsBookmarked(_activeTab.Url))
            db.RemoveBookmark(_activeTab.Url);
        else
            db.AddBookmark(_activeTab.Title, _activeTab.Url);
    }

    private void Menu_Click(object sender, RoutedEventArgs e)
    {
        var menu = new MenuFlyout();
        menu.Items.Add(new MenuFlyoutItem { Text = "Tab mới (Ctrl+T)", Icon = new FontIcon { Glyph = "\uE710" } });
        menu.Items.Add(new MenuFlyoutItem { Text = "Tab ẩn danh mới" });
        menu.Items.Add(new MenuFlyoutSeparator());
        menu.Items.Add(new MenuFlyoutItem { Text = "Lịch sử (Ctrl+H)", Icon = new FontIcon { Glyph = "\uE81C" } });
        menu.Items.Add(new MenuFlyoutItem { Text = "Dấu trang (Ctrl+B)", Icon = new FontIcon { Glyph = "\uE8A4" } });
        menu.Items.Add(new MenuFlyoutItem { Text = "Tìm trên trang (Ctrl+F)", Icon = new FontIcon { Glyph = "\uE721" } });
        menu.Items.Add(new MenuFlyoutSeparator());
        menu.Items.Add(new MenuFlyoutItem { Text = "Cài đặt", Icon = new FontIcon { Glyph = "\uE713" } });

        ((MenuFlyoutItem)menu.Items[0]).Click += (s, _) => CreateNewTab();
        ((MenuFlyoutItem)menu.Items[1]).Click += (s, _) => CreateNewTab(isIncognito: true);
        ((MenuFlyoutItem)menu.Items[5]).Click += (s, _) => { FindBar.Visibility = Visibility.Visible; FindBox.Focus(FocusState.Keyboard); };

        menu.ShowAt(MenuBtn);
    }

    // MARK: - Find in Page

    private async void FindBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        var text = FindBox.Text;
        if (_activeTab?.WebView?.CoreWebView2 != null && !string.IsNullOrEmpty(text))
        {
            await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync($"window.find('{text.Replace("'", "\\'")}')");
        }
    }

    private async void FindNext_Click(object sender, RoutedEventArgs e)
    {
        if (_activeTab?.WebView?.CoreWebView2 != null)
            await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync("window.find()");
    }

    private async void FindPrev_Click(object sender, RoutedEventArgs e)
    {
        if (_activeTab?.WebView?.CoreWebView2 != null)
            await _activeTab.WebView.CoreWebView2.ExecuteScriptAsync("window.find('', false, true)");
    }

    private void FindClose_Click(object sender, RoutedEventArgs e)
    {
        FindBar.Visibility = Visibility.Collapsed;
        FindBox.Text = "";
    }

    // MARK: - Keyboard Shortcuts

    private void SetupKeyboardShortcuts()
    {
        this.Content.KeyDown += (s, e) =>
        {
            var ctrl = Microsoft.UI.Input.InputKeyboardSource.GetKeyStateForCurrentThread(VirtualKey.Control)
                .HasFlag(Windows.UI.Core.CoreVirtualKeyStates.Down);
            if (!ctrl) return;

            switch (e.Key)
            {
                case VirtualKey.T: CreateNewTab(); e.Handled = true; break;
                case VirtualKey.W: if (_activeTab != null) CloseTab(_activeTab); e.Handled = true; break;
                case VirtualKey.L: AddressBox.Focus(FocusState.Keyboard); AddressBox.SelectAll(); e.Handled = true; break;
                case VirtualKey.R: _activeTab?.WebView?.Reload(); e.Handled = true; break;
                case VirtualKey.F: FindBar.Visibility = Visibility.Visible; FindBox.Focus(FocusState.Keyboard); e.Handled = true; break;
                case VirtualKey.H: /* Show history */ e.Handled = true; break;
                case VirtualKey.D: Bookmark_Click(s, e); e.Handled = true; break;
            }
        };
    }
}

// MARK: - Supporting Types

public class BrowserTab
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Url { get; set; } = "helix://start";
    public string Title { get; set; } = "Tab mới";
    public bool IsIncognito { get; set; }
    public bool IsPinned { get; set; }
    public bool IsMuted { get; set; }
    public string? GroupId { get; set; }
    public Microsoft.UI.Xaml.Controls.WebView2? WebView { get; set; }
}
