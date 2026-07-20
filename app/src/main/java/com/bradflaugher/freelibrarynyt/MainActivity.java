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
import android.widget.TextView;
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
    private static final String CHECKING_ACCOUNT_STATE = "checking_account";
    private static final String SUBSCRIPTION_CHECK = """
            (function() {
              var accountConfig = window.config;
              if (!accountConfig || typeof accountConfig.LoggedIn !== 'boolean') {
                return 'unknown';
              }
              if (!accountConfig.LoggedIn) return 'logged_out';
              if (window.__libraryNewsAccess) return window.__libraryNewsAccess;

              var pageConfig = window.__preloadedData && window.__preloadedData.config;
              if (!pageConfig || !pageConfig.gqlUrlClient || !pageConfig.gqlRequestHeaders) {
                return 'unknown';
              }

              window.__libraryNewsAccess = 'loading';
              var query = 'query getAccountUserProfileInfo {'
                + ' accountInfo(surfaceCode: "auth-wrapper", prepareToTransaction: false) {'
                + ' subscriptions {'
                + ' subscriptionProducts subscriptionStatus accessTermination'
                + ' } } }';
              var headers = Object.assign(
                {'Content-Type': 'application/json'},
                pageConfig.gqlRequestHeaders
              );
              fetch(pageConfig.gqlUrlClient, {
                method: 'POST',
                credentials: 'include',
                headers: headers,
                body: JSON.stringify({
                  operationName: 'getAccountUserProfileInfo',
                  query: query,
                  variables: {}
                })
              }).then(function(response) {
                if (!response.ok) throw new Error('Account request failed');
                return response.json();
              }).then(function(payload) {
                var account = payload && payload.data && payload.data.accountInfo;
                if (!account || !Array.isArray(account.subscriptions)) {
                  throw new Error('Account data unavailable');
                }
                var now = Date.now();
                var hasNews = account.subscriptions.some(function(subscription) {
                  var products = subscription.subscriptionProducts || [];
                  if (products.indexOf('NEWS') === -1) return false;
                  if (subscription.subscriptionStatus === 'ACTIVE') return true;
                  return subscription.subscriptionStatus === 'SOFT_CANCEL'
                    && (!subscription.accessTermination
                      || Date.parse(subscription.accessTermination) > now);
                });
                window.__libraryNewsAccess = hasNews ? 'subscribed' : 'not_subscribed';
              }).catch(function() {
                window.__libraryNewsAccess = 'error';
              });
              return window.__libraryNewsAccess;
            })()
            """;

    private WebView webView;
    private TextView status;
    private ProgressBar progress;
    private boolean checkingAccount = true;
    private boolean openingNyTimes;
    private int pageGeneration;

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

        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);
        webView = findViewById(R.id.web_view);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new AccountWebViewClient());

        Button libraryButton = findViewById(R.id.open_library);
        Button nytButton = findViewById(R.id.open_nytimes);
        libraryButton.setOnClickListener(view -> openLibrary());
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
            checkingAccount = savedInstanceState.getBoolean(CHECKING_ACCOUNT_STATE, true);
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(CHECKING_ACCOUNT_STATE, checkingAccount);
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        pageGeneration++;
        webView.destroy();
        super.onDestroy();
    }

    private void openLibrary() {
        checkingAccount = false;
        status.setText(R.string.status_library);
        webView.loadUrl(LIBRARY_URL);
    }

    private void openNyTimes() {
        if (openingNyTimes) {
            return;
        }
        openingNyTimes = true;
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

    private void checkSubscription(int generation) {
        if (generation != pageGeneration || openingNyTimes || !isNyTimesUrl(webView.getUrl())) {
            return;
        }

        webView.evaluateJavascript(SUBSCRIPTION_CHECK, result -> {
            if (generation != pageGeneration || openingNyTimes) {
                return;
            }
            switch (result) {
                case "\"subscribed\"" -> {
                    status.setText(R.string.status_subscribed);
                    openNyTimes();
                }
                case "\"not_subscribed\"" -> {
                    if (checkingAccount) {
                        openLibrary();
                    } else {
                        status.setText(R.string.status_activating);
                        webView.postDelayed(() -> checkSubscription(generation), 1000L);
                    }
                }
                case "\"logged_out\"" -> {
                    status.setText(R.string.status_sign_in);
                    webView.postDelayed(() -> checkSubscription(generation), 1000L);
                }
                case "\"error\"" -> status.setText(R.string.status_check_failed);
                default -> webView.postDelayed(() -> checkSubscription(generation), 1000L);
            }
        });
    }

    private static boolean isNyTimesUrl(String url) {
        if (url == null) {
            return false;
        }
        String host = Uri.parse(url).getHost();
        return host != null
                && (host.equals("nytimes.com") || host.endsWith(".nytimes.com"));
    }

    private final class AccountWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            pageGeneration++;
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            if (isNyTimesUrl(url)) {
                int generation = pageGeneration;
                view.postDelayed(() -> checkSubscription(generation), 500L);
            }
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
