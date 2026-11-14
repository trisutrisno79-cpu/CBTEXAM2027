package com.smpn1tualang.cbtexam;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ExamActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView txtBattery;
    private ImageView iconBattery, iconNetwork;
    private ToneGenerator toneGen;
    private boolean isAuthorizedExit = false;
    private boolean isDialogShowing = false;
    private int currentZoom = 100;

    private final BroadcastReceiver systemStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    int batteryPct = (int) ((level / (float) scale) * 100);
                    txtBattery.setText(batteryPct + "%");
                    if (batteryPct <= 20) {
                        ImageViewCompat.setImageTintList(iconBattery, ColorStateList.valueOf(Color.parseColor("#FF5252")));
                    } else {
                        ImageViewCompat.setImageTintList(iconBattery, ColorStateList.valueOf(Color.parseColor("#A5D6A7")));
                    }
                }
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                updateNetworkStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Anti Screenshot, Anti Rekam Layar, Fullscreen & Layar Tetap Menyala
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_exam);

        try {
            toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (Exception ignored) {
            toneGen = null;
        }

        maksimalkanVolume();

        webView = findViewById(R.id.webViewExam);
        progressBar = findViewById(R.id.progressBar);
        txtBattery = findViewById(R.id.txtBattery);
        iconBattery = findViewById(R.id.iconBattery);
        iconNetwork = findViewById(R.id.iconNetwork);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        Button btnExit = findViewById(R.id.btnExit);
        FloatingActionButton btnZoomIn = findViewById(R.id.btnZoomIn);
        FloatingActionButton btnZoomOut = findViewById(R.id.btnZoomOut);

        konfigurasiWebView();
        updateNetworkStatus();

        String examUrl = getIntent().getStringExtra("EXAM_URL");
        if (examUrl != null && !examUrl.isEmpty()) {
            webView.loadUrl(examUrl);
        }

        btnRefresh.setOnClickListener(v -> {
            if (webView != null) webView.reload();
        });

        btnExit.setOnClickListener(v -> {
            bunyikanAlarmPeringatan();
            tampilkanDialogKeluar();
        });

        btnZoomIn.setOnClickListener(v -> {
            if (currentZoom < 200 && webView != null) {
                currentZoom += 10;
                webView.getSettings().setTextZoom(currentZoom);
            }
        });

        btnZoomOut.setOnClickListener(v -> {
            if (currentZoom > 60 && webView != null) {
                currentZoom -= 10;
                webView.getSettings().setTextZoom(currentZoom);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                startLockTask();
            } catch (Exception ignored) {
            }
        }
    }

    private void konfigurasiWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // User-Agent khusus sesuai permintaan
        settings.setUserAgentString("10403445@");

        // Anti Copy-Paste
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);
        webView.setHapticFeedbackEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                try {
                    view.evaluateJavascript(
                            "document.documentElement.style.webkitUserSelect='none';" +
                            "document.documentElement.style.userSelect='none';" +
                            "document.addEventListener('copy', function(e){ e.preventDefault(); });",
                            null
                    );
                } catch (Exception ignored) {
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress == 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void maksimalkanVolume() {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void bunyikanAlarmPeringatan() {
        try {
            maksimalkanVolume();
            if (toneGen != null) {
                toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1000);
            }
        } catch (Exception ignored) {
        }
    }

    private void tampilkanDialogKeluar() {
        if (isDialogShowing || isFinishing()) return;
        isDialogShowing = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("IZIN KELUAR UJIAN");
        builder.setMessage("Masukkan password pengawas untuk keluar dari aplikasi:");
        builder.setCancelable(false);

        final EditText inputPass = new EditText(this);
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputPass.setHint("Masukkan Password");
        builder.setView(inputPass);

        builder.setPositiveButton("KELUAR", (dialog, which) -> {
            isDialogShowing = false;
            String pass = inputPass.getText().toString().trim();
            if ("xyz".equals(pass)) {
                isAuthorizedExit = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        stopLockTask();
                    } catch (Exception ignored) {
                    }
                }
                finishAffinity();
            } else {
                bunyikanAlarmPeringatan();
                Toast.makeText(this, "PASSWORD SALAH! Akses keluar ditolak.", Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("LANJUT UJIAN", (dialog, which) -> {
            isDialogShowing = false;
            dialog.dismiss();
        });

        builder.setOnDismissListener(dialog -> isDialogShowing = false);
        builder.show();
    }

    private void updateNetworkStatus() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;
            boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            if (isConnected) {
                iconNetwork.setImageResource(android.R.drawable.presence_online);
                ImageViewCompat.setImageTintList(iconNetwork, ColorStateList.valueOf(Color.parseColor("#69F0AE")));
            } else {
                iconNetwork.setImageResource(android.R.drawable.presence_offline);
                ImageViewCompat.setImageTintList(iconNetwork, ColorStateList.valueOf(Color.parseColor("#FF5252")));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isAuthorizedExit) {
            bunyikanAlarmPeringatan();
            Toast.makeText(this, "Dilarang menekan tombol perangkat saat ujian!", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        bunyikanAlarmPeringatan();
        tampilkanDialogKeluar();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && !isAuthorizedExit && !isDialogShowing) {
            bunyikanAlarmPeringatan();
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(getTaskId(), 0);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!isAuthorizedExit) {
            bunyikanAlarmPeringatan();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        maksimalkanVolume();
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(systemStatusReceiver, filter);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(systemStatusReceiver);
        } catch (Exception ignored) {
        }

        if (!isAuthorizedExit && !isDialogShowing) {
            bunyikanAlarmPeringatan();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isAuthorizedExit && !isFinishing()) {
                    try {
                        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                        if (am != null) {
                            am.moveTaskToFront(getTaskId(), 0);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }, 300);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (toneGen != null) {
            try {
                toneGen.release();
            } catch (Exception ignored) {
            }
            toneGen = null;
        }
    }
}
