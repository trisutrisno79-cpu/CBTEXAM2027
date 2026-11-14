package com.smpn1tualang.cbtexam;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private int pendingAction = 0; // 1 = Scan QR, 2 = Input Link

    private final ActivityResultLauncher<ScanOptions> qrLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    bukaHalamanUjian(result.getContents());
                } else {
                    Toast.makeText(this, "Scan dibatalkan", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    if (pendingAction == 1) {
                        mulaiScanQr();
                    } else if (pendingAction == 2) {
                        tampilkanDialogInputLink();
                    }
                } else {
                    Toast.makeText(this, "Izin kamera wajib diaktifkan untuk pengawasan ujian!", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        ImageView imgLogo = findViewById(R.id.imgLogo);
        try {
            Glide.with(this)
                    .load("https://smpn1tualang.sch.id/images/aplikasi/logo.png")
                    .placeholder(android.R.drawable.sym_def_app_icon)
                    .error(android.R.drawable.sym_def_app_icon)
                    .into(imgLogo);
        } catch (Exception ignored) {
        }

        MaterialCardView btnScanQr = findViewById(R.id.btnScanQr);
        MaterialCardView btnInputLink = findViewById(R.id.btnInputLink);
        MaterialCardView btnBantuan = findViewById(R.id.btnBantuan);

        // 1. Menu Scan QR Code
        btnScanQr.setOnClickListener(v -> {
            pendingAction = 1;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                mulaiScanQr();
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        // 2. Menu Masukkan Link Ujian (Juga pastikan izin kamera aktif)
        btnInputLink.setOnClickListener(v -> {
            pendingAction = 2;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                tampilkanDialogInputLink();
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        // 3. Menu Bantuan
        btnBantuan.setOnClickListener(v -> tampilkanDialogBantuan());
    }

    private void mulaiScanQr() {
        try {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Arahkan kamera ke QR Code Ujian dari Guru Pengawas");
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);
            options.setCaptureActivity(QrScannerActivity.class);
            qrLauncher.launch(options);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka kamera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void tampilkanDialogInputLink() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Masukkan Link Ujian");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 12);

        final EditText inputUrl = new EditText(this);
        inputUrl.setHint("https://...");
        inputUrl.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputUrl.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        inputUrl.setSaveEnabled(false);
        inputUrl.setText("");

        layout.addView(inputUrl);
        builder.setView(layout);

        builder.setPositiveButton("MASUK UJIAN", (dialog, which) -> {
            String url = inputUrl.getText().toString().trim();
            inputUrl.setText("");
            if (!url.isEmpty()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url;
                }
                bukaHalamanUjian(url);
            } else {
                Toast.makeText(this, "Link ujian tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("BATAL", (dialog, which) -> {
            inputUrl.setText("");
            dialog.cancel();
        });

        builder.show();
    }

    private void tampilkanDialogBantuan() {
        String infoPembuat = "CBT EXAM BROWSER Dibuat Untuk Mempermudah Pelaksanakan Sistem Ujian Yang Transparan dan Jujur.\n\n" +
                "Aplikasi ini dibuat oleh :\n" +
                "TRI SUTRISNO, S.Kom\n" +
                "Guru SMP Negeri 1 Tualang\n" +
                "trisutrisno79@guru.smp.belajar.id\n" +
                "+6281276534734";

        new AlertDialog.Builder(this)
                .setTitle("Tentang Aplikasi & Bantuan")
                .setMessage(infoPembuat)
                .setPositiveButton("TUTUP", null)
                .show();
    }

    private void bukaHalamanUjian(String url) {
        Intent intent = new Intent(MainActivity.this, ExamActivity.class);
        intent.putExtra("EXAM_URL", url);
        startActivity(intent);
    }
}
