package com.example.smartscales.domain.services;

import com.example.smartscales.domain.interfaces.ScaleInterface;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class MockScaleService implements ScaleInterface {
    private ScaleInterface.WeightListener weightListener;
    private boolean connected = false;
    private Timer weightSimulationTimer;
    private Random random = new Random();
    private float lastWeight = 75.0f;

    @Override
    public void connect() {
        connected = true;
        if (weightListener != null) {
            weightListener.onConnectionStateChanged(true);
        }
        startWeightSimulation();
    }

    @Override
    public void disconnect() {
        connected = false;
        stopWeightSimulation();
        if (weightListener != null) {
            weightListener.onConnectionStateChanged(false);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void setWeightListener(ScaleInterface.WeightListener listener) {
        this.weightListener = listener;
    }

    @Override
    public void simulateWeightMeasurement(float weight) {
        if (weightListener != null && connected) {
            weightListener.onWeightReceived(weight);
        }
    }

    private void startWeightSimulation() {
        weightSimulationTimer = new Timer();
        weightSimulationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (connected && weightListener != null) {
                    // Генерируем реалистичные изменения веса (±0.3 кг)
                    float variation = (random.nextFloat() - 0.5f) * 0.6f;
                    lastWeight = Math.max(40f, Math.min(150f, lastWeight + variation));
                    weightListener.onWeightReceived(lastWeight);
                }
            }
        }, 3000, 5000); // Первое измерение через 3 сек, потом каждые 5 сек
    }

    private void stopWeightSimulation() {
        if (weightSimulationTimer != null) {
            weightSimulationTimer.cancel();
            weightSimulationTimer = null;
        }
    }
}