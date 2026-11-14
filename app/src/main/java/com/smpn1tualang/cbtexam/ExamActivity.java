package com.smpn1tualang.cbtexam;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
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
import android.view.Surface;
import android.view.TextureView;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;

public class ExamActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private TextView txtBattery;
    private ImageView iconBattery, iconNetwork;
    private LinearLayout overlayBlocker;
    private MaterialCardView cardFrontCamera;
    private TextureView textureFrontCamera;

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private ToneGenerator toneGen;
    private boolean isAuthorizedExit = false;
    private boolean isDialogShowing = false;
    private int currentZoom = 100;

    private final Handler securityHandler = new Handler(Looper.getMainLooper());
    private final Runnable securityMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAuthorizedExit && !isFinishing()) {
                cekKeamananLayar();
                securityHandler.postDelayed(this, 500);
            }
        }
    };

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
        overlayBlocker = findViewById(R.id.overlayBlocker);
        cardFrontCamera = findViewById(R.id.cardFrontCamera);
        textureFrontCamera = findViewById(R.id.textureFrontCamera);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        Button btnExit = findViewById(R.id.btnExit);
        FloatingActionButton btnZoomIn = findViewById(R.id.btnZoomIn);
        FloatingActionButton btnZoomOut = findViewById(R.id.btnZoomOut);

        konfigurasiWebView();
        updateNetworkStatus();
        siapkanKameraDepan();

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

    // --- KONTROL KAMERA DEPAN PENGAWAS ---
    private void siapkanKameraDepan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cardFrontCamera.setVisibility(View.GONE);
            return;
        }

        textureFrontCamera.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                bukaKameraDepan();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                tutupKameraDepan();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });
    }

    private void bukaKameraDepan() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return;

            String frontCameraId = null;
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    frontCameraId = cameraId;
                    break;
                }
            }

            if (frontCameraId == null) {
                cardFrontCamera.setVisibility(View.GONE);
                return;
            }

            manager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    mulaiPreviewKamera();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    tutupKameraDepan();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    tutupKameraDepan();
                }
            }, securityHandler);
        } catch (Exception e) {
            cardFrontCamera.setVisibility(View.GONE);
        }
    }

    private void mulaiPreviewKamera() {
        try {
            if (cameraDevice == null || !textureFrontCamera.isAvailable()) return;
            SurfaceTexture texture = textureFrontCamera.getSurfaceTexture();
            if (texture == null) return;

            texture.setDefaultBufferSize(320, 240); // Resolusi ringan agar hemat RAM & baterai
            Surface surface = new Surface(texture);

            final CaptureRequest.Builder previewRequestBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) return;
                            cameraCaptureSession = session;
                            try {
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(), null, securityHandler);
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        }
                    }, securityHandler);
        } catch (Exception ignored) {
        }
    }

    private void tutupKameraDepan() {
        try {
            if (cameraCaptureSession != null) {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception ignored) {
        }
    }

    // --- KEAMANAN ANTI FLOATING & ANTI SPLIT SCREEN ---
    private void cekKeamananLayar() {
        boolean isFloatingOrSplit = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInMultiWindowMode() || isInPictureInPictureMode()) {
                isFloatingOrSplit = true;
            }
        }

        if (!hasWindowFocus() && !isDialogShowing) {
            isFloatingOrSplit = true;
        }

        if (isFloatingOrSplit) {
            try {
                sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            } catch (Exception ignored) {
            }

            overlayBlocker.setVisibility(View.VISIBLE);
            bunyikanAlarmPeringatan();

            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(getTaskId(), ActivityManager.MOVE_TASK_WITH_HOME);
                }
            } catch (Exception ignored) {
            }
        } else {
            if (overlayBlocker.getVisibility() == View.VISIBLE) {
                overlayBlocker.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        if (isInMultiWindowMode && !isAuthorizedExit) {
            overlayBlocker.setVisibility(View.VISIBLE);
            bunyikanAlarmPeringatan();
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

        settings.setUserAgentString("10403445@");

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
                toneGen.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 800);
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
                tutupKameraDepan();
                securityHandler.removeCallbacks(securityMonitorRunnable);
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
                iconNetwork.setImageResource(R.drawable.ic_network_online);
            } else {
                iconNetwork.setImageResource(R.drawable.ic_network_offline);
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
        cekKeamananLayar();
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
        updateNetworkStatus();
        securityHandler.post(securityMonitorRunnable);
        if (textureFrontCamera.isAvailable() && cameraDevice == null) {
            bukaKameraDepan();
        }
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
        tutupKameraDepan();
        securityHandler.removeCallbacks(securityMonitorRunnable);
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
                            am.moveTaskToFront(getTaskId(), ActivityManager.MOVE_TASK_WITH_HOME);
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
        tutupKameraDepan();
        securityHandler.removeCallbacks(securityMonitorRunnable);
        if (toneGen != null) {
            try {
                toneGen.release();
            } catch (Exception ignored) {
            }
            toneGen = null;
        }
    }
}
