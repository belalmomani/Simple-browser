package com.example.minibrowser;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

public class MainActivity extends Activity {

    private static final long INTERVAL_MS = 1800L * 1000L;   // 30 دقيقة (1800 ثانية)
    private static final String DEFAULT_URL = "https://dreams.xo.je/";

    private WebView web;
    private EditText input;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            web.reload();                               // إعادة تحميل كاملة للصفحة
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        int p = (int) (8 * getResources().getDisplayMetrics().density);
        bar.setPadding(p, p, p, p);

        Button open = new Button(this);
        open.setText("فتح");

        input = new EditText(this);
        input.setSingleLine(true);
        input.setText(DEFAULT_URL);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        bar.addView(open, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bar.addView(input, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());      // يبقي التنقل داخل التطبيق

        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        open.setOnClickListener(v -> openUrl());
    }

    private void openUrl() {
        String u = input.getText().toString().trim();
        if (u.isEmpty()) return;
        if (!u.matches("(?i)^https?://.*")) u = "https://" + u;
        input.setText(u);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

        web.loadUrl(u);
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, INTERVAL_MS);      // التحديث كل 1800 ثانية
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(refresh);
        web.destroy();
        super.onDestroy();
    }
}
