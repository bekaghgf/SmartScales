package com.example.smartscales.presentation.viewmodels;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.smartscales.App;
import com.example.smartscales.data.models.User;
import com.example.smartscales.data.repository.UserRepository;
import com.example.smartscales.domain.interfaces.FaceRecognitionInterface;

public class RegisterViewModel extends AndroidViewModel {
    private static final String TAG = "RegisterViewModel";

    private UserRepository userRepository;
    private FaceRecognitionInterface faceRecognitionService;

    private MutableLiveData<Boolean> registrationSuccess = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<Boolean> registrationProgress = new MutableLiveData<>(false);

    public RegisterViewModel(@NonNull Application application) {
        super(application);
        userRepository = new UserRepository(application);
        App app = (App) application;
        faceRecognitionService = app.getFaceRecognition();
    }

    public void registerUser(String name, Bitmap faceImage, float initialWeight, float targetWeight) {
        Log.d(TAG, "Начало регистрации пользователя: " + name);

        if (name == null || name.trim().isEmpty()) {
            errorMessage.postValue("Введите имя пользователя");
            return;
        }

        if (faceImage == null) {
            errorMessage.postValue("Сделайте фотографию для регистрации");
            return;
        }

        registrationProgress.postValue(true);

        userRepository.addUserWithFace(name, faceImage, initialWeight, targetWeight,
                faceRecognitionService,
                new UserRepository.UserRegistrationCallback() {
                    @Override
                    public void onSuccess(User user) {
                        registrationProgress.postValue(false);
                        registrationSuccess.postValue(true);
                        Log.d(TAG, "Пользователь успешно зарегистрирован: " + name);
                    }

                    @Override
                    public void onError(Exception error) {
                        registrationProgress.postValue(false);
                        errorMessage.postValue("Ошибка регистрации: " + error.getMessage());
                        Log.e(TAG, "Не удалось зарегистрировать пользователя: " + name, error);
                    }
                });
    }

    public MutableLiveData<Boolean> getRegistrationSuccess() {
        return registrationSuccess;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public MutableLiveData<Boolean> getRegistrationProgress() {
        return registrationProgress;
    }
}