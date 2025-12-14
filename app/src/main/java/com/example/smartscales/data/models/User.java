package com.example.smartscales.data.models;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.smartscales.data.database.Converters;

import java.util.Date;

@Entity(tableName = "users")
@TypeConverters({Converters.class})
public class User {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public byte[] faceEmbedding;
    public Date createdDate;
    public boolean isActive;
    public float targetWeight;
    public float initialWeight;


    public User() {
        this.createdDate = new Date();
        this.isActive = true;
    }


    @Ignore
    public User(String name, byte[] faceEmbedding, float initialWeight, float targetWeight) {
        this();
        this.name = name;
        this.faceEmbedding = faceEmbedding;
        this.initialWeight = initialWeight;
        this.targetWeight = targetWeight;
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getFaceEmbedding() {
        return faceEmbedding;
    }

    public void setFaceEmbedding(byte[] faceEmbedding) {
        this.faceEmbedding = faceEmbedding;
    }

    public Date getCreatedAt() {
        return createdDate;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdDate = createdAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public float getInitialWeight() {
        return initialWeight;
    }

    public void setInitialWeight(float initialWeight) {
        this.initialWeight = initialWeight;
    }

    public float getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(float targetWeight) {
        this.targetWeight = targetWeight;
    }
}