package com.example.smartscales.data.repository;

import android.app.Application;
import android.graphics.Bitmap;
import androidx.lifecycle.LiveData;
import com.example.smartscales.data.database.AppDatabase;
import com.example.smartscales.data.database.dao.UserDao;
import com.example.smartscales.data.database.dao.WeightMeasurementDao;
import com.example.smartscales.data.models.User;
import com.example.smartscales.data.models.WeightMeasurement;
import com.example.smartscales.domain.interfaces.FaceRecognitionInterface;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRepository {
    private UserDao userDao;
    private WeightMeasurementDao weightMeasurementDao;
    private ExecutorService executorService;

    public UserRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        this.userDao = database.userDao();
        this.weightMeasurementDao = database.weightMeasurementDao();
        this.executorService = Executors.newFixedThreadPool(2);
    }


    public void insertUser(User user, RepositoryCallback<Long> callback) {
        executorService.execute(() -> {
            try {
                long id = userDao.insert(user);
                user.setId((int) id);
                callback.onSuccess(id);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void updateUser(User user, RepositoryCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                userDao.update(user);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void deleteUser(User user, RepositoryCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                userDao.delete(user);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getUserById(int userId, RepositoryCallback<User> callback) {
        executorService.execute(() -> {
            try {
                User user = userDao.getUserById(userId);
                callback.onSuccess(user);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public LiveData<List<User>> getAllActiveUsers() {
        return userDao.getAllActiveUsers();
    }

    public void deactivateUser(int userId, RepositoryCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                userDao.deactivateUser(userId);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }



    public void insertWeightMeasurement(WeightMeasurement measurement, RepositoryCallback<Long> callback) {
        executorService.execute(() -> {
            try {
                long id = weightMeasurementDao.insert(measurement);
                measurement.setId((int) id); // Устанавливаем ID объекту
                callback.onSuccess(id);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public LiveData<List<WeightMeasurement>> getMeasurementsByUser(int userId) {
        return weightMeasurementDao.getMeasurementsByUser(userId);
    }

    public void getLastMeasurement(int userId, RepositoryCallback<WeightMeasurement> callback) {
        executorService.execute(() -> {
            try {
                WeightMeasurement measurement = weightMeasurementDao.getLastMeasurement(userId);
                callback.onSuccess(measurement);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getTodayMeasurement(int userId, RepositoryCallback<WeightMeasurement> callback) {
        executorService.execute(() -> {
            try {
                WeightMeasurement measurement = weightMeasurementDao.getTodayMeasurement(userId);
                callback.onSuccess(measurement);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getLastWeekMeasurements(int userId, RepositoryCallback<List<WeightMeasurement>> callback) {
        executorService.execute(() -> {
            try {
                List<WeightMeasurement> measurements = weightMeasurementDao.getLastWeekMeasurementsSync(userId);
                callback.onSuccess(measurements);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ========== Статистика ==========

    public void getAverageWeight(int userId, RepositoryCallback<Double> callback) {
        executorService.execute(() -> {
            try {
                Double average = weightMeasurementDao.getAverageWeight(userId);
                callback.onSuccess(average != null ? average : 0.0);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void getWeightStats(int userId, RepositoryCallback<WeightStats> callback) {
        executorService.execute(() -> {
            try {
                Double average = weightMeasurementDao.getAverageWeight(userId);
                Float min = weightMeasurementDao.getMinWeight(userId);
                Float max = weightMeasurementDao.getMaxWeight(userId);
                Integer count = weightMeasurementDao.getMeasurementCount(userId);

                WeightStats stats = new WeightStats(
                        average != null ? average : 0.0,
                        min != null ? min : 0f,
                        max != null ? max : 0f,
                        count != null ? count : 0
                );

                callback.onSuccess(stats);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ========== Face Recognition methods ==========

    public void addUserWithFace(String name, Bitmap faceImage, float initialWeight, float targetWeight,
                                FaceRecognitionInterface faceRecognition,
                                UserRegistrationCallback callback) {
        executorService.execute(() -> {
            try {
                faceRecognition.registerUser(name, faceImage,
                        new FaceRecognitionInterface.FaceRegistrationCallback() {
                            @Override
                            public void onSuccess(User user) {
                                // Уже получили пользователя с ID от FaceRecognitionService

                                // Устанавливаем вес из параметров (не фиксированные 70.0/65.0!)
                                user.setInitialWeight(initialWeight);
                                user.setTargetWeight(targetWeight);

                                // Просто обновляем с новыми весами
                                updateUser(user, new RepositoryCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        callback.onSuccess(user);
                                    }

                                    @Override
                                    public void onError(Exception error) {
                                        callback.onError(error);
                                    }
                                });
                            }

                            @Override
                            public void onError(String error) {
                                callback.onError(new Exception(error));
                            }
                        });
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void recognizeUserFromImage(Bitmap faceImage,
                                       FaceRecognitionInterface faceRecognition,
                                       UserRecognitionCallback callback) {
        executorService.execute(() -> {
            try {
                faceRecognition.recognizeUser(faceImage,
                        new FaceRecognitionInterface.FaceRecognitionCallback() {
                            @Override
                            public void onUserRecognized(User user, float confidence) {
                                callback.onSuccess(user, confidence);
                            }

                            @Override
                            public void onNoFaceDetected() {
                                callback.onNoFaceDetected();
                            }

                            @Override
                            public void onUnknownFace() {
                                callback.onUnknownFace();
                            }

                            @Override
                            public void onError(String error) {
                                callback.onError(new Exception(error));
                            }
                        });
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }



    public void getWeightChangeStats(int userId, RepositoryCallback<WeightChangeStats> callback) {
        executorService.execute(() -> {
            try {
                WeightMeasurement today = weightMeasurementDao.getTodayMeasurement(userId);

                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_YEAR, -1);
                Date yesterday = calendar.getTime();

                List<WeightMeasurement> yesterdayMeasurements =
                        weightMeasurementDao.getMeasurementsInRange(userId,
                                yesterday, new Date(yesterday.getTime() + 86400000));

                WeightMeasurement yesterdayMeasurement = yesterdayMeasurements.isEmpty() ?
                        null : yesterdayMeasurements.get(0);

                // Получаем последние 7 дней
                calendar.add(Calendar.DAY_OF_YEAR, -6);
                Date weekAgo = calendar.getTime();
                List<WeightMeasurement> weekMeasurements =
                        weightMeasurementDao.getMeasurementsInRange(userId, weekAgo, new Date());

                // Рассчитываем изменения
                float changeYesterday = 0;
                float changeWeek = 0;

                if (today != null && yesterdayMeasurement != null) {
                    changeYesterday = today.getWeight() - yesterdayMeasurement.getWeight();
                }

                if (today != null && weekMeasurements.size() >= 2) {
                    WeightMeasurement firstOfWeek = weekMeasurements.get(weekMeasurements.size() - 1);
                    changeWeek = today.getWeight() - firstOfWeek.getWeight();
                }

                WeightChangeStats stats = new WeightChangeStats(changeYesterday, changeWeek);
                callback.onSuccess(stats);

            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    public void cleanupOldData(RepositoryCallback<Void> callback) {
        executorService.execute(() -> {
            try {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MONTH, -6); // Удаляем данные старше 6 месяцев
                weightMeasurementDao.deleteOldMeasurements(calendar.getTime());
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    // ========== Вспомогательные классы ==========

    public static class WeightStats {
        public final double average;
        public final float min;
        public final float max;
        public final int count;

        public WeightStats(double average, float min, float max, int count) {
            this.average = average;
            this.min = min;
            this.max = max;
            this.count = count;
        }
    }

    public static class WeightChangeStats {
        public final float changeYesterday;
        public final float changeWeek;

        public WeightChangeStats(float changeYesterday, float changeWeek) {
            this.changeYesterday = changeYesterday;
            this.changeWeek = changeWeek;
        }
    }



    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    public interface UserRegistrationCallback {
        void onSuccess(User user);
        void onError(Exception error);
    }

    public interface UserRecognitionCallback {
        void onSuccess(User user, float confidence);
        void onNoFaceDetected();
        void onUnknownFace();
        void onError(Exception error);
    }
}