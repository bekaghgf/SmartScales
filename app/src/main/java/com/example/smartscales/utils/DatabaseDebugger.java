package com.example.smartscales.utils;

import android.util.Log;
import com.example.smartscales.data.models.User;
import com.example.smartscales.data.models.WeightMeasurement;
import com.example.smartscales.data.repository.UserRepository;

import java.util.List;

public class DatabaseDebugger {
    private static final String TAG = "DatabaseDebugger";

    private UserRepository userRepository;

    public DatabaseDebugger(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void logAllUsers() {
        new Thread(() -> {
            try {
                List<User> users = userRepository.getAllActiveUsers().getValue();
                if (users != null) {
                    Log.d(TAG, "=== ВСЕ ПОЛЬЗОВАТЕЛИ В БАЗЕ ===");
                    for (User user : users) {
                        Log.d(TAG, "ID: " + user.getId() +
                                ", Имя: " + user.getName() +
                                ", Создан: " + user.getCreatedAt());
                    }
                    Log.d(TAG, "Всего пользователей: " + users.size());
                } else {
                    Log.d(TAG, "Пользователи не найдены или LiveData не инициализирована");
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при чтении пользователей: " + e.getMessage());
            }
        }).start();
    }

    public void logUserMeasurements(int userId) {
        new Thread(() -> {
            try {
                List<WeightMeasurement> measurements =
                        userRepository.getMeasurementsByUser(userId).getValue();
                if (measurements != null) {
                    Log.d(TAG, "=== ИЗМЕРЕНИЯ ДЛЯ ПОЛЬЗОВАТЕЛЯ " + userId + " ===");
                    for (WeightMeasurement measurement : measurements) {
                        Log.d(TAG, "Вес: " + measurement.getWeight() +
                                " кг, Дата: " + measurement.getMeasurementDate());
                    }
                    Log.d(TAG, "Всего измерений: " + measurements.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при чтении измерений: " + e.getMessage());
            }
        }).start();
    }
}