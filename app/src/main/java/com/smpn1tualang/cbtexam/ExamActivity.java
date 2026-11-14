package com.smpn1tualang.cbtexam;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ExamActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView txtBattery, txtNetwork;
    private boolean isAuthorizedExit = false;
    private int currentZoom = 100;

    // Receiver untuk memantau persentase Baterai dan status Jaringan secara realtime
    private final BroadcastReceiver systemStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int batteryPct = (int) ((level / (float) scale) * 100);
                txtBattery.setText("BAT: " + batteryPct + "%");
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                updateNetworkStatus();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. ANTI SCREENSHOT & ANTI REKAM LAYAR + FULLSCREEN
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_exam);

        // 2. FULL-KAN VOLUME SUARA HP
        maksimalkanVolume();

        // Inisialisasi UI
        webView = findViewById(R.id.webViewExam);
        progressBar = findViewById(R.id.progressBar);
        txtBattery = findViewById(R.id.txtBattery);
        txtNetwork = findViewById(R.id.txtNetwork);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        Button btnExit = findViewById(R.id.btnExit);
        FloatingActionButton btnZoomIn = findViewById(R.id.btnZoomIn);
        FloatingActionButton btnZoomOut = findViewById(R.id.btnZoomOut);

        // Setup WebView & User-Agent
        konfigurasiWebView();

        String examUrl = getIntent().getStringExtra("EXAM_URL");
        if (examUrl != null) {
            webView.loadUrl(examUrl);
        }

        // Tombol Refresh
        btnRefresh.setOnClickListener(v -> webView.reload());

        // Tombol Keluar (Bunyikan suara & minta password: xyz)
        btnExit.setOnClickListener(v -> {
            maksimalkanVolume();
            bunyikanAlarmPeringatan();
            tampilkanDialogKeluar();
        });

        // Tombol Zoom In & Zoom Out
        btnZoomIn.setOnClickListener(v -> {
            if (currentZoom < 200) {
                currentZoom += 10;
                webView.getSettings().setTextZoom(currentZoom);
            }
        });

        btnZoomOut.setOnClickListener(v -> {
            if (currentZoom > 60) {
                currentZoom -= 10;
                webView.getSettings().setTextZoom(currentZoom);
            }
        });

        // Aktifkan Mode Kiosk / Pinning (Anti Floating & Anti Keluar)
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
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false); // Sembunyikan tombol zoom bawaan, pakai tombol custom

        // AKTIFKAN USER-AGENT KHUSUS: 10403445@
        settings.setUserAgentString("10403445@");

        // ANTI COPY-PASTE (Blokir seleksi teks & klik tahan)
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);
        webView.setHapticFeedbackEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Injeksi CSS & JS tambahan untuk memastikan teks tidak bisa di-blok/copy
                view.evaluateJavascript(
                        "document.documentElement.style.webkitUserSelect='none';" +
                        "document.documentElement.style.userSelect='none';" +
                        "document.addEventListener('copy', function(e){ e.preventDefault(); });",
                        null
                );
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
                int maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING);

                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0);
                audioManager.setStreamVolume(AudioManager.STREAM_RING, maxRing, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void bunyikanAlarmPeringatan() {
        try {
            maksimalkanVolume();
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200);
        } catch (Exception ignored) {
        }
    }

    private void tampilkanDialogKeluar() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("IZIN KELUAR UJIAN");
        builder.setMessage("Masukkan password pengawas untuk keluar dari aplikasi:");
        builder.setCancelable(false);

        final EditText inputPass = new EditText(this);
        inputPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        inputPass.setHint("Masukkan Password");
        builder.setView(inputPass);

        builder.setPositiveButton("KELUAR", (dialog, which) -> {
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
                System.exit(0);
            } else {
                bunyikanAlarmPeringatan();
                Toast.makeText(this, "PASSWORD SALAH! Akses keluar ditolak.", Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("LANJUT UJIAN", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void updateNetworkStatus() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm != null ? cm.getActiveNetworkInfo() : null;
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        if (isConnected) {
            txtNetwork.setText("NET: ONLINE");
            txtNetwork.setTextColor(Color.parseColor("#A5D6A7"));
        } else {
            txtNetwork.setText("NET: OFFLINE");
            txtNetwork.setTextColor(Color.parseColor("#FFCDD2"));
        }
    }

    // DETEKSI JIKA SISWA MENEKAN TOMBOL HP (BACK, VOLUME, POWER, RECENT)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isAuthorizedExit) {
            bunyikanAlarmPeringatan();
            Toast.makeText(this, "Dilarang menekan tombol perangkat saat ujian!", Toast.LENGTH_SHORT).show();
            return true; // Blokir fungsi tombol
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // Bunyikan suara & minta password jika tombol Back ditekan
        bunyikanAlarmPeringatan();
        tampilkanDialogKeluar();
    }

    // DETEKSI ANTI FLOATING APPS & NOTIFIKASI PANEL
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && !isAuthorizedExit) {
            // Terpanggil jika siswa menarik status bar atau membuka floating app
            bunyikanAlarmPeringatan();
            // Paksa tarik kembali aplikasi ke depan
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.moveTaskToFront(getTaskId(), 0);
            }
        }
    }

    // DETEKSI JIKA SISWA MENEKAN TOMBOL HOME / PINDAH APLIKASI
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
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(systemStatusReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(systemStatusReceiver);
        } catch (Exception ignored) {
        }
        if (!isAuthorizedExit) {
            bunyikanAlarmPeringatan();
            // Kembalikan layar ujian ke depan secara paksa
            Intent intent = new Intent(this, ExamActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        }
    }
}
