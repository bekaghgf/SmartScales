package com.example.smartscales.data.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Delete;
import com.example.smartscales.data.models.WeightMeasurement;
import java.util.Date;
import java.util.List;

@Dao
public interface WeightMeasurementDao {
    @Insert
    long insert(WeightMeasurement measurement);

    // Основные запросы
    @Query("SELECT * FROM weight_measurements WHERE userId = :userId ORDER BY measurementDate DESC")
    LiveData<List<WeightMeasurement>> getMeasurementsByUser(int userId);

    @Query("SELECT * FROM weight_measurements WHERE userId = :userId AND " +
            "measurementDate >= date('now', '-7 days') ORDER BY measurementDate DESC")
    LiveData<List<WeightMeasurement>> getLastWeekMeasurements(int userId);

    // Синхронные методы для репозитория
    @Query("SELECT * FROM weight_measurements WHERE userId = :userId ORDER BY measurementDate DESC LIMIT 1")
    WeightMeasurement getLastMeasurement(int userId);

    @Query("SELECT * FROM weight_measurements WHERE userId = :userId AND " +
            "date(measurementDate/1000, 'unixepoch', 'localtime') = date('now', 'localtime') " +
            "ORDER BY measurementDate DESC LIMIT 1")
    WeightMeasurement getTodayMeasurement(int userId);

    @Query("SELECT * FROM weight_measurements WHERE userId = :userId AND " +
            "measurementDate >= date('now', '-7 days') ORDER BY measurementDate DESC")
    List<WeightMeasurement> getLastWeekMeasurementsSync(int userId);

    // Статистика
    @Query("SELECT AVG(weight) FROM weight_measurements WHERE userId = :userId")
    Double getAverageWeight(int userId);

    @Query("SELECT COUNT(*) FROM weight_measurements WHERE userId = :userId")
    Integer getMeasurementCount(int userId);

    @Query("SELECT MIN(weight) FROM weight_measurements WHERE userId = :userId")
    Float getMinWeight(int userId);

    @Query("SELECT MAX(weight) FROM weight_measurements WHERE userId = :userId")
    Float getMaxWeight(int userId);

    // Удаление старых записей
    @Query("DELETE FROM weight_measurements WHERE measurementDate < :date")
    void deleteOldMeasurements(Date date);

    // Для графиков
    @Query("SELECT * FROM weight_measurements WHERE userId = :userId AND " +
            "measurementDate >= :startDate AND measurementDate <= :endDate " +
            "ORDER BY measurementDate")
    List<WeightMeasurement> getMeasurementsInRange(int userId, Date startDate, Date endDate);

}