package com.example.smartscales.domain.interfaces;

import android.content.Context;
import android.graphics.Bitmap;
import com.example.smartscales.data.models.User;

public interface FaceRecognitionInterface {

    /**
     * Инициализация сервиса распознавания
     */
    void initialize(Context context);

    /**
     * Асинхронная регистрация нового пользователя
     */
    void registerUser(String name, Bitmap faceImage, FaceRegistrationCallback callback);

    /**
     * Асинхронное распознавание пользователя
     */
    void recognizeUser(Bitmap faceImage, FaceRecognitionCallback callback);

    /**
     * Проверяет, доступен ли сервис распознавания
     */
    boolean isAvailable();

    /**
     * Очищает кэш распознавания
     */
    void clearCache();

    /**
     * Колбэк для регистрации пользователя
     */
    interface FaceRegistrationCallback {
        void onSuccess(User user);
        void onError(String error);
    }

    /**
     * Колбэк для распознавания пользователя
     */
    interface FaceRecognitionCallback {
        void onUserRecognized(User user, float confidence);
        void onNoFaceDetected();
        void onUnknownFace();
        void onError(String error);
    }
}