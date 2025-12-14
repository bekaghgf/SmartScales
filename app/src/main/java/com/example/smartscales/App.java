package com.example.smartscales;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.smartscales.data.database.AppDatabase;
import com.example.smartscales.domain.interfaces.FaceRecognitionInterface;
import com.example.smartscales.domain.interfaces.ScaleInterface;
import com.example.smartscales.domain.services.FaceRecognitionService;
import com.example.smartscales.domain.services.MockScaleService;

public class App extends Application {
    private static App instance;
    private AppDatabase database;
    private FaceRecognitionInterface faceRecognition;
    private ScaleInterface scaleService;
    private SharedPreferences preferences;



    private void initScaleService() {
        scaleService = new MockScaleService();
    }

    public static App getInstance() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public FaceRecognitionInterface getFaceRecognition() {
        return faceRecognition;
    }

    public ScaleInterface getScaleService() {
        return scaleService;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    public Context getAppContext() {
        return getApplicationContext();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        Log.d("App", "Application onCreate started");

        try {
            preferences = PreferenceManager.getDefaultSharedPreferences(this);

            database = AppDatabase.getInstance(this);
            Log.d("App", "Database initialized");

            Thread.sleep(500);

            initFaceRecognition();

            initScaleService();

            Log.d("App", "Все сервисы инициализированы");

        } catch (Exception e) {
            Log.e("App", "Init error: " + e.getMessage(), e);
        }
    }

    private void initFaceRecognition() {
        Log.d("App", "Инициализация FaceRecognitionService...");

        faceRecognition = new FaceRecognitionService(this);

        faceRecognition.initialize(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d("App", "FaceRecognitionService инициализирован после задержки");
        }, 2000);
    }
}