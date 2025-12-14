package com.example.smartscales.data.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.smartscales.data.models.User;

import java.util.List;

@Dao
public interface UserDao {

    @Insert
    long insert(User user);

    @Update
    void update(User user);

    @Delete
    void delete(User user);



    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY name")
    LiveData<List<User>> getAllActiveUsers();

    @Query("SELECT * FROM users WHERE id = :userId")
    User getUserById(int userId);

    @Query("SELECT * FROM users WHERE name = :name LIMIT 1")
    User getUserByName(String name);

    @Query("UPDATE users SET isActive = 0 WHERE id = :userId")
    void deactivateUser(int userId);

    @Query("SELECT COUNT(*) FROM users WHERE isActive = 1")
    int getActiveUsersCount();

    @Query("SELECT * FROM users WHERE name LIKE :name LIMIT 1")
    User findUserByName(String name);

    @Query("SELECT * FROM users WHERE id = :id")
    LiveData<User> getUserByIdLive(int id);



}