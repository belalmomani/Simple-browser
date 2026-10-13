package com.example.minibrowser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String DEFAULT_URL = "https://dreams.xo.je/";
    private EditText input;
    private TextView status;
    private FrameLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = (int) (8 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(p, p + insets.getSystemWindowInsetTop(), p, p + insets.getSystemWindowInsetBottom());
            return insets;
        });

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button open = new Button(this);
        open.setText("فتح");
        input = new EditText(this);
        input.setSingleLine(true);
        input.setText(DEFAULT_URL);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        bar.addView(open, new LinearLayout.LayoutParams(-2, -2));
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        status = new TextView(this);
        status.setTextSize(13);
        Button stop = new Button(this);
        stop.setText("إيقاف");
        row.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(stop, new LinearLayout.LayoutParams(-2, -2));

        container = new FrameLayout(this);

        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        root.addView(container, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        open.setOnClickListener(v -> openUrl());
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, RefreshService.class));
            status.setText("متوقف.");
        });

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void openUrl() {
        String u = input.getText().toString().trim();
        if (u.isEmpty()) return;
        if (!u.matches("(?i)^https?://.*")) u = "https://" + u;
        input.setText(u);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

        BrowserHolder.get(getApplicationContext());
        BrowserHolder.attach(this, container);
        startForegroundService(new Intent(this, RefreshService.class).putExtra("url", u));
        status.setText("يعمل في الخلفية، تحديث كامل كل 30 دقيقة");

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (BrowserHolder.exists()) {
            BrowserHolder.attach(this, container);
            status.setText("يعمل في الخلفية، تحديث كامل كل 30 دقيقة");
        }
    }

    @Override
    protected void onPause() {
        BrowserHolder.detach(getApplicationContext());
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (BrowserHolder.exists()) {
            WebView w = BrowserHolder.get(getApplicationContext());
            if (w.canGoBack()) { w.goBack(); return; }
        }
        super.onBackPressed();
    }
}
