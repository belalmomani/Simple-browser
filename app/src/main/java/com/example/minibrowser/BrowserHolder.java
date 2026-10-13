package com.example.minibrowser;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** WebView واحد مشترك: تملكه الخدمة، وتعرضه الواجهة عندما تكون ظاهرة. */
final class BrowserHolder {
    private static MutableContextWrapper ctx;
    private static WebView web;

    static WebView get(Context app) {
        if (web == null) {
            ctx = new MutableContextWrapper(app.getApplicationContext());
            web = new WebView(ctx);
            web.getSettings().setJavaScriptEnabled(true);
            web.getSettings().setDomStorageEnabled(true);
            web.setWebViewClient(new WebViewClient());
        }
        return web;
    }

    static boolean exists() { return web != null; }

    static void attach(Context activity, ViewGroup parent) {
        if (web == null) return;
        ctx.setBaseContext(activity);
        removeFromParent();
        parent.addView(web, new ViewGroup.LayoutParams(-1, -1));
    }

    static void detach(Context app) {
        if (web == null) return;
        removeFromParent();
        ctx.setBaseContext(app.getApplicationContext());
    }

    static void destroy() {
        if (web == null) return;
        removeFromParent();
        web.destroy();
        web = null;
        ctx = null;
    }

    private static void removeFromParent() {
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
    }
}
