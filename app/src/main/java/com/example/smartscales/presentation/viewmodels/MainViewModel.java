package com.example.smartscales.presentation.viewmodels;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smartscales.App;
import com.example.smartscales.data.models.User;
import com.example.smartscales.data.models.WeightMeasurement;
import com.example.smartscales.data.repository.UserRepository;
import com.example.smartscales.domain.interfaces.FaceRecognitionInterface;
import com.example.smartscales.domain.interfaces.ScaleInterface;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainViewModel";
    private static final float WEIGHT_STABILITY_THRESHOLD = 0.2f;
    private static final long WEIGHT_STABLE_TIME = 3000;

    private UserRepository userRepository;
    private ScaleInterface scaleService;
    private FaceRecognitionInterface faceRecognition;

    // LiveData
    private MutableLiveData<String> status = new MutableLiveData<>("Инициализация...");
    private MutableLiveData<User> currentUser = new MutableLiveData<>();
    private MutableLiveData<Float> currentWeight = new MutableLiveData<>(0.0f);
    private MutableLiveData<String> weightChangeYesterday = new MutableLiveData<>("--");
    private MutableLiveData<String> weightChangeWeek = new MutableLiveData<>("--");
    private MutableLiveData<Boolean> isRecognizing = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> isScaleConnected = new MutableLiveData<>(false);
    private MutableLiveData<Boolean> weightStability = new MutableLiveData<>(false);
    private MutableLiveData<Float> recognitionConfidence = new MutableLiveData<>(0f);
    private MutableLiveData<List<WeightMeasurement>> weightData = new MutableLiveData<>();

    // Временные данные
    private float lastStableWeight = 0;
    private long lastWeightChangeTime = 0;
    private boolean isWeightStable = false;

    public MainViewModel(@NonNull Application application) {
        super(application);
        initializeComponents(application);
    }

    private void initializeComponents(Application application) {
        userRepository = new UserRepository(application);

        App app = (App) application;
        faceRecognition = app.getFaceRecognition();
        scaleService = app.getScaleService();

        setupScaleListener();
        loadInitialData();

        status.setValue("✅ Система готова. Встаньте на весы.");
    }

    private void setupScaleListener() {
        scaleService.setWeightListener(new ScaleInterface.WeightListener() {
            @Override
            public void onWeightReceived(float weight) {
                long currentTime = System.currentTimeMillis();

                // Проверяем стабильность веса
                if (Math.abs(weight - lastStableWeight) < WEIGHT_STABILITY_THRESHOLD) {
                    if (!isWeightStable) {
                        isWeightStable = true;
                        lastWeightChangeTime = currentTime;
                    } else if (currentTime - lastWeightChangeTime > WEIGHT_STABLE_TIME) {
                        // Вес стабилен более 3 секунд
                        weightStability.postValue(true);

                        // Сохраняем измерение если есть пользователь
                        User user = currentUser.getValue();
                        if (user != null && weight > 10.0f) {
                            saveWeightMeasurement(user.getId(), weight);
                        }
                    }
                } else {
                    isWeightStable = false;
                    lastStableWeight = weight;
                    lastWeightChangeTime = currentTime;
                    weightStability.postValue(false);
                }

                currentWeight.postValue(weight);
            }

            @Override
            public void onConnectionStateChanged(boolean connected) {
                isScaleConnected.postValue(connected);
                if (connected) {
                    status.postValue("✅ Весы подключены");
                } else {
                    status.postValue("⚠️ Весы отключены");
                }
            }

            @Override
            public void onError(String error) {
                status.postValue("❌ Ошибка весов: " + error);
            }
        });

        scaleService.connect();
    }

    private void loadInitialData() {
        // Загружаем данные текущего пользователя если есть
    }

    public void analyzeFaceFrame(Bitmap faceBitmap) {
        if (Boolean.TRUE.equals(isRecognizing.getValue())) {
            return;
        }

        isRecognizing.postValue(true);

        userRepository.recognizeUserFromImage(faceBitmap, faceRecognition,
                new UserRepository.UserRecognitionCallback() {
                    @Override
                    public void onSuccess(User user, float confidence) {
                        isRecognizing.postValue(false);
                        recognitionConfidence.postValue(confidence);

                        // Загружаем полные данные пользователя
                        userRepository.getUserById(user.getId(), new UserRepository.RepositoryCallback<User>() {
                            @Override
                            public void onSuccess(User fullUser) {
                                currentUser.postValue(fullUser);
                                loadUserWeightData(fullUser.getId());
                                updateWeightChanges(fullUser.getId());

                                String message = String.format(Locale.getDefault(),
                                        "✅ %s распознан (%.0f%%)",
                                        fullUser.getName(), confidence * 100);
                                status.postValue(message);
                            }

                            @Override
                            public void onError(Exception error) {
                                // Используем базовые данные
                                currentUser.postValue(user);
                                status.postValue("Пользователь распознан, но данные не загружены");
                            }
                        });
                    }

                    @Override
                    public void onNoFaceDetected() {
                        isRecognizing.postValue(false);
                        status.postValue("❌ Лицо не обнаружено");
                    }

                    @Override
                    public void onUnknownFace() {
                        isRecognizing.postValue(false);
                        currentUser.postValue(null);
                        status.postValue("👤 Неизвестный пользователь");
                    }

                    @Override
                    public void onError(Exception error) {
                        isRecognizing.postValue(false);
                        status.postValue("❌ Ошибка распознавания: " + error.getMessage());
                    }
                });
    }

    private void loadUserWeightData(int userId) {
        userRepository.getLastWeekMeasurements(userId, new UserRepository.RepositoryCallback<List<WeightMeasurement>>() {
            @Override
            public void onSuccess(List<WeightMeasurement> measurements) {
                weightData.postValue(measurements);
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "Error loading weight data: " + error.getMessage());
            }
        });
    }

    private void saveWeightMeasurement(int userId, float weight) {
        WeightMeasurement measurement = new WeightMeasurement(userId, weight);
        measurement.setMeasurementDate(new Date());

        userRepository.insertWeightMeasurement(measurement, new UserRepository.RepositoryCallback<Long>() {
            @Override
            public void onSuccess(Long result) {
                // Обновляем данные после сохранения
                loadUserWeightData(userId);
                updateWeightChanges(userId);

                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                String time = sdf.format(new Date());
                status.postValue("📊 Измерение сохранено (" + time + ")");
            }

            @Override
            public void onError(Exception error) {
                status.postValue("❌ Ошибка сохранения измерения");
            }
        });
    }

    private void updateWeightChanges(int userId) {
        userRepository.getWeightChangeStats(userId, new UserRepository.RepositoryCallback<UserRepository.WeightChangeStats>() {
            @Override
            public void onSuccess(UserRepository.WeightChangeStats stats) {
                String yesterdayText = formatWeightChange(stats.changeYesterday);
                String weekText = formatWeightChange(stats.changeWeek);

                weightChangeYesterday.postValue(yesterdayText);
                weightChangeWeek.postValue(weekText);
            }

            @Override
            public void onError(Exception error) {
                weightChangeYesterday.postValue("--");
                weightChangeWeek.postValue("--");
            }
        });
    }

    private String formatWeightChange(float change) {
        if (Math.abs(change) < 0.01f) {
            return "0.0 кг";
        }

        String sign = change > 0 ? "+" : "";
        return String.format(Locale.getDefault(), "%s%.1f кг", sign, Math.abs(change));
    }

    public void clearCurrentUser() {
        currentUser.postValue(null);
        weightData.postValue(null);
        weightChangeYesterday.postValue("--");
        weightChangeWeek.postValue("--");
        currentWeight.postValue(0f);
    }

    public void cleanup() {
        if (scaleService != null) {
            scaleService.disconnect();
        }
    }

    public LiveData<String> getStatus() { return status; }
    public LiveData<User> getCurrentUser() { return currentUser; }
    public LiveData<Float> getCurrentWeight() { return currentWeight; }
    public LiveData<String> getWeightChangeYesterday() { return weightChangeYesterday; }
    public LiveData<String> getWeightChangeWeek() { return weightChangeWeek; }
    public LiveData<Boolean> getIsRecognizing() { return isRecognizing; }
    public LiveData<Boolean> getIsScaleConnected() { return isScaleConnected; }
    public LiveData<Boolean> getWeightStability() { return weightStability; }
    public LiveData<Float> getRecognitionConfidence() { return recognitionConfidence; }
    public LiveData<List<WeightMeasurement>> getWeightData() { return weightData; }

    private void loadUserData(int userId) {
        // Загружаем последнее измерение
        userRepository.getLastMeasurement(userId,
                new UserRepository.RepositoryCallback<WeightMeasurement>() {
                    @Override
                    public void onSuccess(WeightMeasurement measurement) {
                        if (measurement != null) {
                            currentWeight.postValue(measurement.getWeight());
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Ошибка загрузки измерения: " + error.getMessage());
                    }
                });

        // Загружаем данные для графика
        userRepository.getLastWeekMeasurements(userId,
                new UserRepository.RepositoryCallback<List<WeightMeasurement>>() {
                    @Override
                    public void onSuccess(List<WeightMeasurement> measurements) {
                        weightData.postValue(measurements);
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Ошибка загрузки данных графика: " + error.getMessage());
                    }
                });

        // Загружаем статистику изменений
        userRepository.getWeightChangeStats(userId,
                new UserRepository.RepositoryCallback<UserRepository.WeightChangeStats>() {
                    @Override
                    public void onSuccess(UserRepository.WeightChangeStats stats) {
                        updateWeightChanges(userId);
                    }

                    @Override
                    public void onError(Exception error) {
                        Log.e(TAG, "Ошибка загрузки статистики: " + error.getMessage());
                    }
                });
    }
}