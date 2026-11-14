package com.smpn1tualang.cbtexam;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Fullscreen portrait
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        ImageView imgLogo = findViewById(R.id.imgLogo);
        Glide.with(this)
                .load("https://smpn1tualang.sch.id/images/aplikasi/logo.png")
                .placeholder(R.drawable.logo_sekolah)
                .error(R.drawable.logo_sekolah)
                .into(imgLogo);

        MaterialCardView btnScanQr = findViewById(R.id.btnScanQr);
        MaterialCardView btnInputLink = findViewById(R.id.btnInputLink);
        MaterialCardView btnBantuan = findViewById(R.id.btnBantuan);

        // 1. Menu Scan QR Code Ujian
        btnScanQr.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setPrompt("Arahkan kamera ke QR Code Ujian dari Guru Pengawas");
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);
            options.setCaptureActivity(QrScannerActivity.class);
            qrLauncher.launch(options);
        });

        // 2. Menu Masukkan Link Ujian (Hapus history ketikan / tanpa autofill)
        btnInputLink.setOnClickListener(v -> tampilkanDialogInputLink());

        // 3. Menu Bantuan
        btnBantuan.setOnClickListener(v -> tampilkanDialogBantuan());
    }

    private void tampilkanDialogInputLink() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Masukkan Link Ujian");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 12);

        final EditText inputUrl = new EditText(this);
        inputUrl.setHint("https://...");
        // Nonaktifkan riwayat ketikan, saran keyboard, dan autofill
        inputUrl.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        inputUrl.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        inputUrl.setSaveEnabled(false);
        inputUrl.setText(""); // Pastikan selalu kosong saat dibuka

        layout.addView(inputUrl);
        builder.setView(layout);

        builder.setPositiveButton("MASUK UJIAN", (dialog, which) -> {
            String url = inputUrl.getText().toString().trim();
            inputUrl.setText(""); // Hapus ketikan segera setelah disubmit
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
        String infoPembuat = "CBT EXAM BROWSER dibuat untuk tujuan mempermudah dalam melaksanakan sistem ujian yang transparan dan jujur.\n\n" +
                "Aplikasi ini dibuat oleh :\n" +
                "TRI SUTRISNO, S.Kom\n" +
                "Guru SMP Negeri 1 Tualang\n" +
                "trisutrisno79@guru.smp.belajar.id\n" +
                "+6281276534734";

        new AlertDialog.Builder(this)
                .setTitle("Tentang Aplikasi & Bantuan")
                .setIcon(R.drawable.logo_sekolah)
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
