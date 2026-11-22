package com.assamconverter.geocam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * GeoTag Camera — Android app.
 * Opens the GeoTag Camera web app from assamconverter.com in a full-screen WebView and adds what a
 * browser can't: real camera / GPS / microphone permissions, saving straight to the Gallery,
 * sharing files, and the built-in API key that tells the website this is the official app.
 */
public class MainActivity extends Activity {

    static final int REQ_MEDIA = 11, REQ_GEO = 12, REQ_STORAGE = 13, REQ_FILE = 14;

    WebView web;
    ProgressBar bar;
    AppBridge bridge;
    String host;                       // www.assamconverter.com
    volatile String currentHost = "";  // host of the page now shown (read by the JS bridge)

    PermissionRequest pendingMedia;
    GeolocationPermissions.Callback geoCallback;
    String geoOrigin;
    ValueCallback<Uri[]> fileCallback;
    Runnable afterStorage;
    boolean showingOffline = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        host = Uri.parse(BuildConfig.BASE_URL).getHost();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#141210"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#141210"));
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        root.addView(bar, lp);
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " GeoTagCameraApp/" + BuildConfig.VERSION_NAME);
        if (Build.VERSION.SDK_INT < 24) {
            //noinspection deprecation
            s.setGeolocationDatabasePath(getFilesDir().getPath());
        }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        bridge = new AppBridge(this);
        web.addJavascriptInterface(bridge, "GeoTagApp");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        // APK / file download links (e.g. "Download the update") open in the phone's browser
        web.setDownloadListener(new android.webkit.DownloadListener() {
            @Override public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                if (url != null && url.startsWith("https://")) openOutside(Uri.parse(url));
            }
        });

        bridge.cleanOldFiles();
        String start = BuildConfig.BASE_URL + "/geocam/?source=apk";
        Intent in = getIntent();
        if (in != null && in.getData() != null && isOurs(in.getData())) start = in.getData().toString();
        if (saved != null) web.restoreState(saved); else web.loadUrl(start);
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    boolean isOurs(Uri u) {
        if (u == null || u.getHost() == null || !"https".equals(u.getScheme())) return false;
        String h = u.getHost().toLowerCase();
        String bare = host.replaceFirst("^www\\.", "");
        return h.equals(host) || h.equals(bare) || h.equals("www." + bare);
    }

    /* ─────────── pages ─────────── */
    class Client extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return handle(r.getUrl()); }
        @SuppressWarnings("deprecation")
        @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return handle(Uri.parse(url)); }

        boolean handle(Uri u) {
            if (isOurs(u)) return false;                     // our website: stay in the app
            openOutside(u);                                  // Google Maps, WhatsApp, other sites: open outside
            return true;
        }
        @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
            Uri u = Uri.parse(url);
            currentHost = isOurs(u) ? host : (u.getHost() == null ? "" : u.getHost());
            bar.setVisibility(View.VISIBLE);
        }
        @Override public void onPageFinished(WebView v, String url) {
            bar.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
        }
        @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r.isForMainFrame()) offline();
        }
        @SuppressWarnings("deprecation")
        @Override public void onReceivedError(WebView v, int code, String d, String url) {
            if (Build.VERSION.SDK_INT < 23) offline();
        }
    }

    void offline() {
        showingOffline = true;
        currentHost = "";
        web.loadUrl("file:///android_asset/offline.html");
    }

    void reload() {
        runOnUiThread(new Runnable() { public void run() {
            showingOffline = false;
            web.loadUrl(BuildConfig.BASE_URL + "/geocam/?source=apk");
        }});
    }

    void openOutside(Uri u) {
        try {
            Intent i = "intent".equals(u.getScheme()) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, u);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to open this link", Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
    }

    /* ─────────── camera, microphone, location, file picker ─────────── */
    class Chrome extends WebChromeClient {
        @Override public void onProgressChanged(WebView v, int p) { bar.setProgress(p); }

        @Override public void onPermissionRequest(final PermissionRequest req) {
            runOnUiThread(new Runnable() { public void run() {
                if (!isOurs(req.getOrigin())) { req.deny(); return; }
                List<String> need = new ArrayList<>();
                for (String r : req.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && !has(Manifest.permission.CAMERA)) need.add(Manifest.permission.CAMERA);
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && !has(Manifest.permission.RECORD_AUDIO)) need.add(Manifest.permission.RECORD_AUDIO);
                }
                if (need.isEmpty()) { grantMedia(req); return; }
                if (pendingMedia != null) pendingMedia.deny();
                pendingMedia = req;
                ask(need.toArray(new String[0]), REQ_MEDIA);
            }});
        }

        @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
            if (!isOurs(Uri.parse(origin))) { cb.invoke(origin, false, false); return; }
            if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) { cb.invoke(origin, true, false); return; }
            geoOrigin = origin;
            geoCallback = cb;
            ask(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_GEO);
        }

        @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            try { startActivityForResult(Intent.createChooser(i, "Choose a photo"), REQ_FILE); }
            catch (ActivityNotFoundException e) { fileCallback = null; return false; }
            return true;
        }
    }

    void grantMedia(PermissionRequest req) {
        List<String> ok = new ArrayList<>();
        for (String r : req.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && has(Manifest.permission.CAMERA)) ok.add(r);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && has(Manifest.permission.RECORD_AUDIO)) ok.add(r);
        }
        if (ok.isEmpty()) req.deny(); else req.grant(ok.toArray(new String[0]));
    }

    boolean has(String p) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    void ask(String[] perms, int code) {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(perms, code);
    }

    /** Saving to the Gallery on Android 9 and older needs the storage permission first. */
    void withStorage(Runnable r) {
        if (Build.VERSION.SDK_INT >= 29 || has(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { r.run(); return; }
        afterStorage = r;
        ask(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_MEDIA && pendingMedia != null) { grantMedia(pendingMedia); pendingMedia = null; }
        if (code == REQ_GEO && geoCallback != null) {
            boolean ok = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION);
            geoCallback.invoke(geoOrigin, ok, false);
            geoCallback = null;
        }
        if (code == REQ_STORAGE && afterStorage != null) { Runnable r = afterStorage; afterStorage = null; r.run(); }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCallback != null) {
            Uri u = (result == RESULT_OK && data != null) ? data.getData() : null;
            fileCallback.onReceiveValue(u == null ? null : new Uri[]{u});
            fileCallback = null;
        }
    }

    /* ─────────── misc ─────────── */
    void keepAwake(final boolean on) {
        runOnUiThread(new Runnable() { public void run() {
            if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }});
    }

    void js(final String code) {
        runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(code, null); }});
    }

    @Override
    public void onBackPressed() {
        if (showingOffline) { super.onBackPressed(); return; }
        // let the web app close its own screens (log, settings) first
        web.evaluateJavascript("(window.__gtBack&&window.__gtBack())?'1':'0'", new ValueCallback<String>() {
            @Override public void onReceiveValue(String v) {
                if (v != null && v.contains("1")) return;
                if (web.canGoBack()) web.goBack(); else MainActivity.super.onBackPressed();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null && isOurs(intent.getData())) web.loadUrl(intent.getData().toString());
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onPause() { web.onPause(); CookieManager.getInstance().flush(); super.onPause(); }
    @Override protected void onDestroy() { if (web != null) { web.destroy(); } super.onDestroy(); }
}
