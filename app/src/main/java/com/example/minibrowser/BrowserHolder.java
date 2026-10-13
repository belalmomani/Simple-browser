package com.example.minibrowser;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** WebView واحد مشترك: تملكه الخدمة، وتعرضه الواجهة عندما تكون ظاهرة. */
final class BrowserHolder {
    private static MutableContextWrapper ctx;
    private static WebView web;
    private static Context appCtx;
    private static FrameLayout overlay;

    static WebView get(Context app) {
        if (web == null) {
            appCtx = app.getApplicationContext();
            ctx = new MutableContextWrapper(appCtx);
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
        moveToOverlay();
    }

    /** نافذة 1x1 شفافة تبقي الصفحة «ظاهرة» للنظام فلا يخنق المتصفح مؤقتاتها. تحتاج إذن العرض فوق التطبيقات. */
    private static void moveToOverlay() {
        if (!Settings.canDrawOverlays(appCtx)) return;
        try {
            WindowManager wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
            if (overlay == null) {
                overlay = new FrameLayout(appCtx);
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.TOP | Gravity.START;
                wm.addView(overlay, lp);
            }
            overlay.addView(web, new ViewGroup.LayoutParams(1, 1));
        } catch (Exception ignored) { }
    }

    static void destroy() {
        if (web == null) return;
        removeFromParent();
        if (overlay != null) {
            try { ((WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE)).removeView(overlay); }
            catch (Exception ignored) { }
            overlay = null;
        }
        web.destroy();
        web = null;
        ctx = null;
    }

    private static void removeFromParent() {
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
    }
}
