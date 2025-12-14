
package com.example.smartscales.presentation.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.smartscales.R;
import com.example.smartscales.presentation.viewmodels.RegisterViewModel;
import com.example.smartscales.utils.CameraHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private CameraHelper cameraHelper;
    private RegisterViewModel viewModel;

    private PreviewView previewView;
    private ImageView ivPreview;
    private MaterialButton btnTakePhoto, btnSave, btnRetake;
    private TextInputEditText etUserName, etInitialWeight, etTargetWeight;
    private TextInputLayout tilUserName, tilInitialWeight, tilTargetWeight;
    private TextView tvInstruction;
    private LinearProgressIndicator progressBar;
    private View cardPreview, cardPhotoPreview;

    private Bitmap capturedPhoto;
    private boolean isPhotoTaken = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        initViewModel();
        setupClickListeners();

        if (!checkPermissions()) {
            requestPermissions();
        } else {
            initCamera();
        }
    }

    private void initViews() {
        previewView = findViewById(R.id.previewView);
        ivPreview = findViewById(R.id.ivPreview);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnSave = findViewById(R.id.btnSave);
        btnRetake = findViewById(R.id.btnRetake);
        etUserName = findViewById(R.id.etUserName);
        etInitialWeight = findViewById(R.id.etInitialWeight);
        etTargetWeight = findViewById(R.id.etTargetWeight);
        tilUserName = findViewById(R.id.tilUserName);
        tilInitialWeight = findViewById(R.id.tilInitialWeight);
        tilTargetWeight = findViewById(R.id.tilTargetWeight);
        tvInstruction = findViewById(R.id.tvInstruction);
        progressBar = findViewById(R.id.progressBar);
        cardPreview = findViewById(R.id.cardPreview);
        cardPhotoPreview = findViewById(R.id.cardPhotoPreview);

        // Initially hide save button until photo is taken
        btnSave.setEnabled(false);
        btnRetake.setVisibility(View.GONE);
        cardPhotoPreview.setVisibility(View.GONE);
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);

        // Observe ViewModel
        viewModel.getRegistrationProgress().observe(this, inProgress -> {
            if (inProgress != null) {
                progressBar.setVisibility(inProgress ? View.VISIBLE : View.GONE);
                btnSave.setEnabled(!inProgress);
                btnRetake.setEnabled(!inProgress);
            }
        });

        viewModel.getRegistrationSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(this, "✅ Пользователь успешно зарегистрирован",
                        Toast.LENGTH_LONG).show();

                // Return to main activity
                new android.os.Handler().postDelayed(() -> {
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }, 1500);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, "❌ " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupClickListeners() {
        btnTakePhoto.setOnClickListener(v -> takePhoto());
        btnSave.setOnClickListener(v -> saveUser());
        btnRetake.setOnClickListener(v -> retakePhoto());
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        String[] permissions = {Manifest.permission.CAMERA};
        requestPermissions(permissions, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera();
            } else {
                Toast.makeText(this, "Требуется разрешение на использование камеры",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initCamera() {
        try {
            cameraHelper = new CameraHelper(this, previewView);
            cameraHelper.setCaptureCallback(new CameraHelper.CameraCaptureCallback() {
                @Override
                public void onPhotoCaptured(Bitmap bitmap) {
                    runOnUiThread(() -> handlePhotoCaptured(bitmap));
                }

                @Override
                public void onError(Exception exception) {
                    runOnUiThread(() -> {
                        Toast.makeText(RegisterActivity.this,
                                "Ошибка камеры: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        btnTakePhoto.setEnabled(true);
                        btnTakePhoto.setText("📸 Сделать фото");
                    });
                }
            });

            cameraHelper.startCamera(this);
            tvInstruction.setText("Расположите лицо в рамке и нажмите 'Сделать фото'");

        } catch (Exception e) {
            Toast.makeText(this, "Не удалось запустить камеру: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handlePhotoCaptured(Bitmap bitmap) {

        runOnUiThread(() -> {
            Log.d("RegisterActivity", "Photo captured: " +
                    (bitmap != null) + ", size: " +
                    (bitmap != null ? bitmap.getWidth() + "x" + bitmap.getHeight() : "null"));

            if (bitmap == null) {
                Toast.makeText(this, "Bitmap is null!", Toast.LENGTH_LONG).show();
                return;
            }

            capturedPhoto = bitmap;
            ivPreview.setImageBitmap(capturedPhoto);

            // Show photo preview, hide camera preview
            cardPreview.setVisibility(View.GONE);
//            cardPhotoPreview.setVisibility(View.VISIBLE);

            btnTakePhoto.setVisibility(View.GONE);
            btnRetake.setVisibility(View.VISIBLE);
            btnSave.setEnabled(true);

            tvInstruction.setText("✅ Фото сделано! Заполните данные");
            isPhotoTaken = true;
        });
    }

    private void takePhoto() {
        if (cameraHelper == null) {
            Toast.makeText(this, "Камера не готова", Toast.LENGTH_SHORT).show();
            return;
        }

        tvInstruction.setText("📸 Съемка...");
        btnTakePhoto.setEnabled(false);
        btnTakePhoto.setText("Съемка...");
        cameraHelper.capturePhoto();
    }

    private void retakePhoto() {
        capturedPhoto = null;

        // Show camera preview again
        cardPreview.setVisibility(View.VISIBLE);
        cardPhotoPreview.setVisibility(View.GONE);

        btnTakePhoto.setVisibility(View.VISIBLE);
        btnRetake.setVisibility(View.GONE);
        btnSave.setEnabled(false);

        btnTakePhoto.setEnabled(true);
        btnTakePhoto.setText("📸 Сделать фото");
        tvInstruction.setText("Расположите лицо в рамке и нажмите 'Сделать фото'");
        isPhotoTaken = false;
    }

    private void saveUser() {
        // Validate inputs
        if (!validateInputs()) {
            return;
        }

        if (!isPhotoTaken || capturedPhoto == null) {
            Toast.makeText(this, "Сначала сделайте фотографию", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get user data
        String userName = etUserName.getText().toString().trim();
        float initialWeight = parseFloat(etInitialWeight.getText().toString());
        float targetWeight = parseFloat(etTargetWeight.getText().toString());

        Log.d("RegisterActivity", "Сохранение пользователя: " + userName);
        Log.d("RegisterActivity", "Размер фото: " +
                capturedPhoto.getWidth() + "x" + capturedPhoto.getHeight());
        Log.d("RegisterActivity", "Вес: " + initialWeight + " -> " + targetWeight);

        // Call ViewModel to register user
        viewModel.registerUser(userName, capturedPhoto, initialWeight, targetWeight);
    }

    private boolean validateInputs() {
        boolean isValid = true;

        // Validate name
        if (TextUtils.isEmpty(etUserName.getText())) {
            tilUserName.setError("Введите имя пользователя");
            isValid = false;
        } else {
            tilUserName.setError(null);
        }

        // Validate initial weight
        String initialWeightStr = etInitialWeight.getText().toString();
        if (TextUtils.isEmpty(initialWeightStr)) {
            tilInitialWeight.setError("Введите начальный вес");
            isValid = false;
        } else {
            try {
                float weight = Float.parseFloat(initialWeightStr);
                if (weight < 20 || weight > 300) {
                    tilInitialWeight.setError("Вес должен быть от 20 до 300 кг");
                    isValid = false;
                } else {
                    tilInitialWeight.setError(null);
                }
            } catch (NumberFormatException e) {
                tilInitialWeight.setError("Введите корректное число");
                isValid = false;
            }
        }

        // Validate target weight
        String targetWeightStr = etTargetWeight.getText().toString();
        if (TextUtils.isEmpty(targetWeightStr)) {
            tilTargetWeight.setError("Введите целевой вес");
            isValid = false;
        } else {
            try {
                float weight = Float.parseFloat(targetWeightStr);
                if (weight < 20 || weight > 300) {
                    tilTargetWeight.setError("Вес должен быть от 20 до 300 кг");
                    isValid = false;
                } else {
                    tilTargetWeight.setError(null);
                }
            } catch (NumberFormatException e) {
                tilTargetWeight.setError("Введите корректное число");
                isValid = false;
            }
        }

        return isValid;
    }

    private float parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraHelper != null) {
            cameraHelper.stopCamera();
        }
        if (capturedPhoto != null && !capturedPhoto.isRecycled()) {
            capturedPhoto.recycle();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraHelper != null) {
            cameraHelper.stopCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkPermissions() && cameraHelper != null && !cameraHelper.isCameraRunning()) {
            initCamera();
        }
    }
}