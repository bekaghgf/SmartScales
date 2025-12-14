package com.example.smartscales.presentation.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.smartscales.R;
import com.example.smartscales.data.models.User;
import com.example.smartscales.data.models.WeightMeasurement;
import com.example.smartscales.presentation.viewmodels.MainViewModel;
import com.example.smartscales.utils.ImageUtils;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final long FACE_RECOGNITION_INTERVAL = 2000; // 2 секунды между распознаваниями

    private MainViewModel viewModel;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private FaceDetector faceDetector;
    private FaceDetectionOverlayView faceOverlay;

    // UI Components
    private PreviewView cameraPreview;
    private TextView tvStatus, tvUserName, tvCurrentWeight, tvChangeYesterday, tvChangeWeek;
    private TextView tvRecognitionStatus, tvConfidence, tvScaleStatus, tvWeightStability;
    private ImageView ivUserAvatar;
    private LineChart weightChart;
    private FloatingActionButton fabAddUser;
    private View viewScaleIndicator;
    private View overlayRecognitionStatus;
    private BottomNavigationView bottomNavigation;

    // Face recognition timing
    private long lastRecognitionTime = 0;
    private boolean isRecognizing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initViewModel();
        setupClickListeners();
        checkPermissions();
        setupBottomNavigation();
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        tvStatus = findViewById(R.id.tvStatus);
        tvUserName = findViewById(R.id.tvUserName);
        tvCurrentWeight = findViewById(R.id.tvCurrentWeight);
        tvChangeYesterday = findViewById(R.id.tvChangeYesterday);
        tvChangeWeek = findViewById(R.id.tvChangeWeek);
        tvScaleStatus = findViewById(R.id.tvScaleStatus);
        tvWeightStability = findViewById(R.id.tvWeightStability);
        tvRecognitionStatus = findViewById(R.id.tvRecognitionStatus);
        tvConfidence = findViewById(R.id.tvConfidence);

        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        weightChart = findViewById(R.id.weightChart);
        faceOverlay = findViewById(R.id.faceOverlay);
        fabAddUser = findViewById(R.id.fabAddUser);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        overlayRecognitionStatus = findViewById(R.id.overlayRecognitionStatus);
        viewScaleIndicator = findViewById(R.id.viewScaleIndicator);

        // Setup chart
        setupChart();

        // Button click listeners
        fabAddUser.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void setupClickListeners() {
        fabAddUser.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // При нажатии на аватар пользователя - показать информацию или меню
        ivUserAvatar.setOnClickListener(v -> {
            if (viewModel.getCurrentUser().getValue() != null) {
                // TODO: Показать меню пользователя
                Toast.makeText(this, "Информация о пользователе", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        viewModel.getStatus().observe(this, status -> {
            if (status != null) {
                tvStatus.setText(status);
            }
        });

        viewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                tvUserName.setText(user.getName());
                ivUserAvatar.setImageResource(R.drawable.ic_user_placeholder);
                // Можно загрузить реальное фото пользователя, если сохранено
            } else {
                tvUserName.setText("Гость");
                ivUserAvatar.setImageResource(R.drawable.ic_user_placeholder);
            }
        });

        viewModel.getCurrentWeight().observe(this, weight -> {
            if (weight != null && weight > 10.0f) { // Игнорируем вес меньше 10 кг
                tvCurrentWeight.setText(String.format(Locale.getDefault(), "%.1f кг", weight));
                tvCurrentWeight.setTextColor(ContextCompat.getColor(this, R.color.primary));
            } else {
                tvCurrentWeight.setText("--.-- кг");
                tvCurrentWeight.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        });

        viewModel.getWeightChangeYesterday().observe(this, change -> {
            updateChangeText(tvChangeYesterday, change);
        });

        viewModel.getWeightChangeWeek().observe(this, change -> {
            updateChangeText(tvChangeWeek, change);
        });

        viewModel.getWeightData().observe(this, measurements -> {
            if (measurements != null && !measurements.isEmpty()) {
                updateWeightChart(measurements);
            } else {
                // Показать пустой график
                weightChart.clear();
                weightChart.invalidate();
            }
        });

        viewModel.getIsRecognizing().observe(this, recognizing -> {
            if (recognizing != null) {
                isRecognizing = recognizing;
                if (recognizing) {
                    overlayRecognitionStatus.setVisibility(View.VISIBLE);
                    tvRecognitionStatus.setText("🔍 Распознавание...");
                    tvConfidence.setText("");
                } else {
                    overlayRecognitionStatus.setVisibility(View.GONE);
                }
            }
        });

        viewModel.getIsScaleConnected().observe(this, connected -> {
            if (connected != null) {
                if (connected) {
                    tvScaleStatus.setText("Весы: подключены");
                    tvScaleStatus.setTextColor(ContextCompat.getColor(this, R.color.success));
                    viewScaleIndicator.setBackgroundResource(R.drawable.circle_green);
                } else {
                    tvScaleStatus.setText("Весы: не подключены");
                    tvScaleStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                    viewScaleIndicator.setBackgroundResource(R.drawable.circle_gray);
                }
            }
        });

        viewModel.getRecognitionConfidence().observe(this, confidence -> {
            if (confidence != null && confidence > 0) {
                tvConfidence.setText(String.format(Locale.getDefault(), "Доверие: %.0f%%", confidence * 100));
            }
        });

        viewModel.getWeightStability().observe(this, stable -> {
            if (stable != null && stable) {
                tvWeightStability.setText("⚡ Стабильный");
                tvWeightStability.setTextColor(ContextCompat.getColor(this, R.color.success));
            } else {
                tvWeightStability.setText("⌛ Измерение...");
                tvWeightStability.setTextColor(ContextCompat.getColor(this, R.color.warning));
            }
        });
    }

    private void updateChangeText(TextView textView, String change) {
        if (change != null && !change.isEmpty() && !change.equals("--")) {
            textView.setText(change);

            try {
                if (change.contains("+")) {
                    textView.setTextColor(ContextCompat.getColor(this, R.color.error)); // Прибавка в весе - красный
                } else if (change.contains("-")) {
                    textView.setTextColor(ContextCompat.getColor(this, R.color.success)); // Потеря веса - зеленый
                } else {
                    textView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                }
            } catch (Exception e) {
                textView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        } else {
            textView.setText("--");
            textView.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void setupChart() {
        weightChart.setBackgroundColor(ContextCompat.getColor(this, R.color.card_background));
        weightChart.getDescription().setEnabled(false);
        weightChart.setTouchEnabled(true);
        weightChart.setDragEnabled(true);
        weightChart.setScaleEnabled(true);
        weightChart.setPinchZoom(true);
        weightChart.setDrawGridBackground(false);
        weightChart.setHighlightPerDragEnabled(true);

        // X Axis
        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(ContextCompat.getColor(this, R.color.divider));
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(7, true);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat format = new SimpleDateFormat("dd.MM", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_YEAR, (int) value - 6);
                return format.format(calendar.getTime());
            }
        });

        // Y Axis
        YAxis leftAxis = weightChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(this, R.color.divider));
        leftAxis.setGranularity(0.1f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f кг", value);
            }
        });

        weightChart.getAxisRight().setEnabled(false);
        weightChart.getLegend().setEnabled(false);

        // Set chart padding
        weightChart.setExtraOffsets(20f, 20f, 20f, 20f);
    }

    private void updateWeightChart(List<WeightMeasurement> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            return;
        }

        List<Entry> entries = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM", Locale.getDefault());

        // Берем последние 7 измерений
        int count = Math.min(7, measurements.size());
        for (int i = 0; i < count; i++) {
            WeightMeasurement measurement = measurements.get(i);
            entries.add(new Entry(count - i - 1, measurement.getWeight()));
        }

        if (entries.isEmpty()) {
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Вес");
        dataSet.setColor(ContextCompat.getColor(this, R.color.primary));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.primary));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(ContextCompat.getColor(this, R.color.text_primary));
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(ContextCompat.getColor(this, R.color.primary_light));
        dataSet.setFillAlpha(100);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        LineData lineData = new LineData(dataSet);
        weightChart.setData(lineData);
        weightChart.invalidate();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        } else {
            initCamera();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (cameraProvider == null && checkPermissions1()) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception e) {
                Log.e(TAG, "Failed to unbind camera", e);
            }
        }
    }
    private boolean checkPermissions1() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCamera();
            } else {
                Toast.makeText(this, "Требуется разрешение на использование камеры",
                        Toast.LENGTH_LONG).show();
                tvStatus.setText("❌ Нет доступа к камере");
            }
        }
    }

    private void initCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();
        initFaceDetector();
        startCamera();
    }

    private void initFaceDetector() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();

        faceDetector = FaceDetection.getClient(options);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Preview
                Preview preview = new Preview.Builder()
                        .build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                // Image Analysis for face detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                // Camera selector - front camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                // Unbind existing use cases
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis
                );

                tvStatus.setText("✅ Камера активирована");

            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed: " + e.getMessage(), e);
                tvStatus.setText("❌ Ошибка камеры: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        long currentTime = System.currentTimeMillis();


        if (isRecognizing || (currentTime - lastRecognitionTime < FACE_RECOGNITION_INTERVAL)) {
            imageProxy.close();
            return;
        }

        try {
            InputImage image = InputImage.fromMediaImage(
                    imageProxy.getImage(),
                    imageProxy.getImageInfo().getRotationDegrees()
            );

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        runOnUiThread(() -> {
                            if (faces != null && !faces.isEmpty()) {
                                faceOverlay.setFaces(faces);
                            } else {
                                faceOverlay.clearFaces();
                            }
                        });

                        if (faces != null && !faces.isEmpty()) {
                            lastRecognitionTime = currentTime;
                            Face face = faces.get(0);

                            Bitmap bitmap = ImageUtils.imageProxyToBitmap(imageProxy);
                            if (bitmap != null) {
                                Bitmap faceBitmap = extractFaceBitmap(bitmap, face);
                                if (faceBitmap != null) {
                                    viewModel.analyzeFaceFrame(faceBitmap);
                                }
                            }
                        } else {
                            runOnUiThread(() -> {
                                viewModel.clearCurrentUser();
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Face detection failed: " + e.getMessage());
                        runOnUiThread(() -> faceOverlay.clearFaces());
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });

        } catch (Exception e) {
            Log.e(TAG, "Image analysis error: " + e.getMessage());
            imageProxy.close();
        }
    }

    private Bitmap extractFaceBitmap(Bitmap originalBitmap, Face face) {
        try {
            android.graphics.Rect bounds = face.getBoundingBox();

            float scale = 1.3f;
            int width = (int) (bounds.width() * scale);
            int height = (int) (bounds.height() * scale);
            int centerX = bounds.centerX();
            int centerY = bounds.centerY();

            int left = Math.max(centerX - width / 2, 0);
            int top = Math.max(centerY - height / 2, 0);
            int right = Math.min(centerX + width / 2, originalBitmap.getWidth());
            int bottom = Math.min(centerY + height / 2, originalBitmap.getHeight());

            if (right - left <= 0 || bottom - top <= 0) {
                return null;
            }

            return Bitmap.createBitmap(originalBitmap, left, top, right - left, bottom - top);

        } catch (Exception e) {
            Log.e(TAG, "Error extracting face bitmap: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (faceDetector != null) {
            faceDetector.close();
        }
        if (viewModel != null) {
            viewModel.cleanup();
        }
    }

    private void setupBottomNavigation() {
        if (bottomNavigation == null) {
            Log.e(TAG, "BottomNavigationView is null!");
            return;
        }

        bottomNavigation.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                // Already on home
                return true;
            } else if (itemId == R.id.nav_users) {
                // TODO: Open users list activity
                Toast.makeText(this, "Список пользователей (в разработке)", Toast.LENGTH_SHORT).show();
                return true;
            } else if (itemId == R.id.nav_history) {
                User currentUser = viewModel.getCurrentUser().getValue();
                if (currentUser != null) {
                    Intent intent = new Intent(MainActivity.this, StatisticsActivity.class);
                    intent.putExtra("USER_ID", currentUser.getId());
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Сначала распознайте пользователя",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (itemId == R.id.nav_settings) {
                // TODO: Open settings
                Toast.makeText(this, "Настройки (в разработке)", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

}