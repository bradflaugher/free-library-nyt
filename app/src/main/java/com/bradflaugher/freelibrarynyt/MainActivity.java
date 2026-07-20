package com.bradflaugher.freelibrarynyt;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.view.WindowInsets;
import android.window.OnBackInvokedDispatcher;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public final class MainActivity extends Activity {
    private static final String LIBRARY_URL =
            "https://libwww.freelibrary.org/elecres/NYTimesRemote.cfm";
    private static final String ACCOUNT_URL =
            "https://myaccount.nytimes.com/seg/subscription";
    private static final String NYTIMES_PACKAGE = "com.nytimes.android";

    private WebView webView;
    private ProgressBar progress;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        int horizontalPadding = root.getPaddingLeft();
        int verticalPadding = root.getPaddingTop();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(
                    horizontalPadding + bars.left,
                    verticalPadding + bars.top,
                    horizontalPadding + bars.right,
                    verticalPadding + bars.bottom);
            return insets;
        });

        progress = findViewById(R.id.progress);
        webView = findViewById(R.id.web_view);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new AccountWebViewClient());

        Button libraryButton = findViewById(R.id.open_library);
        Button refreshButton = findViewById(R.id.refresh);
        Button nytButton = findViewById(R.id.open_nytimes);
        libraryButton.setOnClickListener(view -> openLibrary());
        refreshButton.setOnClickListener(view -> webView.reload());
        nytButton.setOnClickListener(view -> openNyTimes());

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                });

        if (savedInstanceState == null) {
            webView.loadUrl(ACCOUNT_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }

    private void openLibrary() {
        webView.loadUrl(LIBRARY_URL);
    }

    private void openNyTimes() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(NYTIMES_PACKAGE);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launchIntent);
            finish();
            return;
        }

        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + NYTIMES_PACKAGE)));
            finish();
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + NYTIMES_PACKAGE)));
            finish();
        }
    }

    private final class AccountWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            if ("https".equalsIgnoreCase(scheme)) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(MainActivity.this, R.string.cannot_open_link, Toast.LENGTH_LONG)
                        .show();
            }
            return true;
        }
    }
}
