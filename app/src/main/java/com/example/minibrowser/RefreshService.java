package com.example.minibrowser;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.webkit.WebView;

public class RefreshService extends Service {

    static final long INTERVAL_MS = 1800L * 1000L;          // 30 دقيقة (1800 ثانية)
    private static final String ACTION = "com.example.minibrowser.REFRESH";

    private WebView web;
    private String url;
    private PowerManager.WakeLock wakeLock;

    private final BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refreshNow(); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(alarmReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(alarmReceiver, f);
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "minibrowser:refresh");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sp = getSharedPreferences("mb", MODE_PRIVATE);
        String u = intent != null ? intent.getStringExtra("url") : null;
        if (u == null) u = sp.getString("url", null);     // إعادة التشغيل بعد قتل النظام للخدمة
        startForegroundCompat(u == null ? "" : u);
        if (u == null) { stopSelf(); return START_NOT_STICKY; }

        sp.edit().putString("url", u).apply();
        url = u;

        web = BrowserHolder.get(this);
        wakeLock.acquire(120_000L);
        web.loadUrl(url);
        scheduleNext();
        return START_STICKY;
    }

    private void refreshNow() {
        wakeLock.acquire(120_000L);                        // يبقي المعالج مستيقظًا أثناء التحميل
        if (web.getUrl() == null) web.loadUrl(url); else web.reload();
        scheduleNext();
    }

    private void scheduleNext() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + INTERVAL_MS, pi);
    }

    private void startForegroundCompat(String u) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                "refresh", "التحديث التلقائي", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "refresh")
                .setContentTitle("متصفح مصغر")
                .setContentText("تحديث كل 30 دقيقة: " + u)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    @Override
    public void onDestroy() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        am.cancel(PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION).setPackage(getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        unregisterReceiver(alarmReceiver);
        if (wakeLock.isHeld()) wakeLock.release();
        BrowserHolder.destroy();
        web = null;
        getSharedPreferences("mb", MODE_PRIVATE).edit().remove("url").apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
