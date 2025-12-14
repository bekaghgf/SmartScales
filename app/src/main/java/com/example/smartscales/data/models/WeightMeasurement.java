package com.example.smartscales.data.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "weight_measurements")
public class WeightMeasurement {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;
    public float weight;
    public Date measurementDate;
    public String notes;

    public WeightMeasurement() {
        this.measurementDate = new Date();
    }


    @Ignore
    public WeightMeasurement(int userId, float weight) {
        this();
        this.userId = userId;
        this.weight = weight;
        this.notes = "";
    }


    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public float getWeight() {
        return weight;
    }

    public Date getMeasurementDate() {
        return measurementDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setWeight(float weight) {
        this.weight = weight;
    }

    public void setMeasurementDate(Date measurementDate) {
        this.measurementDate = measurementDate;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}