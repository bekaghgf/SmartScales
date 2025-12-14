package com.example.smartscales.domain.interfaces;

public interface ScaleInterface {
    void connect();
    void disconnect();
    boolean isConnected();
    void setWeightListener(WeightListener listener);
    void simulateWeightMeasurement(float weight);

    interface WeightListener {
        void onWeightReceived(float weight);
        void onConnectionStateChanged(boolean connected);
        void onError(String error);
    }
}