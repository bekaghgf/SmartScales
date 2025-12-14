
package com.example.smartscales.presentation.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smartscales.data.models.User;
import com.example.smartscales.data.models.WeightMeasurement;
import com.example.smartscales.data.repository.UserRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatisticsViewModel extends AndroidViewModel {

    private UserRepository userRepository;
    private int currentUserId = -1;

    // LiveData
    private MutableLiveData<User> user = new MutableLiveData<>();
    private MutableLiveData<Integer> totalMeasurements = new MutableLiveData<>(0);
    private MutableLiveData<Float> averageWeight = new MutableLiveData<>(0f);
    private MutableLiveData<Float> initialWeight = new MutableLiveData<>(0f);
    private MutableLiveData<Float> currentWeight = new MutableLiveData<>(0f);
    private MutableLiveData<List<WeightMeasurement>> weightData = new MutableLiveData<>();
    private MutableLiveData<List<DailyStat>> dailyStats = new MutableLiveData<>();

    public StatisticsViewModel(@NonNull Application application) {
        super(application);
        userRepository = new UserRepository(application);
    }

    public void setCurrentUserId(int userId) {
        this.currentUserId = userId;
        loadStatistics();
    }

    public void loadStatistics() {
        if (currentUserId == -1) {
            Log.e("StatisticsViewModel", "User ID not set");
            return;
        }

        // Load user data
        userRepository.getUserById(currentUserId, new UserRepository.RepositoryCallback<User>() {
            @Override
            public void onSuccess(User userData) {
                user.postValue(userData);
                initialWeight.postValue(userData.getInitialWeight());

                // Load measurements
                userRepository.getLastMeasurement(currentUserId,
                        new UserRepository.RepositoryCallback<WeightMeasurement>() {
                            @Override
                            public void onSuccess(WeightMeasurement measurement) {
                                if (measurement != null) {
                                    currentWeight.postValue(measurement.getWeight());
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                Log.e("StatisticsViewModel", "Error loading last measurement", error);
                            }
                        });
            }

            @Override
            public void onError(Exception error) {
                Log.e("StatisticsViewModel", "Error loading user", error);
            }
        });

        // Load all measurements for statistics
        userRepository.getMeasurementsByUser(currentUserId).observeForever(measurements -> {
            if (measurements != null) {
                totalMeasurements.postValue(measurements.size());
                weightData.postValue(measurements);

                // Calculate average weight
                if (!measurements.isEmpty()) {
                    float sum = 0;
                    for (WeightMeasurement m : measurements) {
                        sum += m.getWeight();
                    }
                    averageWeight.postValue(sum / measurements.size());
                }

                // Create daily stats
                createDailyStats(measurements);
            }
        });
    }

    private void createDailyStats(List<WeightMeasurement> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            dailyStats.postValue(new ArrayList<>());
            return;
        }

        List<DailyStat> stats = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM", Locale.getDefault());

        // Group by days (last 7 days)
        Calendar calendar = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            calendar.add(Calendar.DAY_OF_YEAR, -i);
            Date date = calendar.getTime();
            String dateStr = dateFormat.format(date);

            // Find measurement for this date
            WeightMeasurement dayMeasurement = null;
            for (WeightMeasurement m : measurements) {
                if (isSameDay(m.getMeasurementDate(), date)) {
                    dayMeasurement = m;
                    break;
                }
            }

            if (dayMeasurement != null) {
                DailyStat stat = new DailyStat(
                        dateStr,
                        dayMeasurement.getWeight(),
                        0 // Simplified change calculation
                );
                stats.add(stat);
            }
        }

        dailyStats.postValue(stats);
    }

    private boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) return false;

        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(date1);
        cal2.setTime(date2);

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }

    // Getters for LiveData
    public LiveData<User> getUser() { return user; }
    public LiveData<Integer> getTotalMeasurements() { return totalMeasurements; }
    public LiveData<Float> getAverageWeight() { return averageWeight; }
    public LiveData<Float> getInitialWeight() { return initialWeight; }
    public LiveData<Float> getCurrentWeight() { return currentWeight; }
    public LiveData<List<WeightMeasurement>> getWeightData() { return weightData; }
    public LiveData<List<DailyStat>> getDailyStats() { return dailyStats; }

    public void cleanup() {

    }

    // Helper class for daily statistics
    public static class DailyStat {
        public String date;
        public float weight;
        public float change;

        public DailyStat(String date, float weight, float change) {
            this.date = date;
            this.weight = weight;
            this.change = change;
        }
    }
}