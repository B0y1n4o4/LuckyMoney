package xyz.monkeytong.hongbao.activities;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;
import xyz.monkeytong.hongbao.R;
import xyz.monkeytong.hongbao.utils.DownloadUtil;

/**
 * Created by Zhongyi on 1/19/16.
 * Settings page.
 */
public class WebViewActivity extends Activity {
    private WebView webView;
    private String webViewUrl, webViewTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadUI();

        Bundle bundle = getIntent().getExtras();
        if (bundle != null && !bundle.isEmpty()) {
            webViewTitle = bundle.getString("title");
            webViewUrl = bundle.getString("url");

            final TextView webViewBar = (TextView) findViewById(R.id.webview_bar);
            webViewBar.setText(webViewTitle);

            webView = (WebView) findViewById(R.id.webView);
            webView.getSettings().setBuiltInZoomControls(false);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url.contains("apk")) {
                        Toast.makeText(getApplicationContext(), getString(R.string.download_backend), Toast.LENGTH_SHORT).show();
                        (new DownloadUtil()).enqueue(url, getApplicationContext());
                        return true;
                    } else if (!url.contains("http")) {
                        Toast.makeText(getApplicationContext(), getString(R.string.download_redirect), Toast.LENGTH_LONG).show();
                        webViewBar.setText(getString(R.string.download_hint));
                        return false;
                    } else {
                        view.loadUrl(url);
                        return false;
                    }
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    CookieSyncManager.getInstance().sync();
                }
            });
            webView.loadUrl(webViewUrl);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void loadUI() {
        setContentView(R.layout.activity_webview);

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        Window window = this.getWindow();

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        window.setStatusBarColor(0xffE46C62);
    }

    public void performBack(View view) {
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                    return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }

    public void openLink(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(this.webViewUrl));
        startActivity(intent);
    }
}
