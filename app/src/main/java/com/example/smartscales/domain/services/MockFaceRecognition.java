//package com.example.smartscales.domain.services;
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.util.Log;
//import com.example.smartscales.data.models.User;
//import com.example.smartscales.domain.interfaces.FaceRecognitionInterface;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Random;
//
//public class MockFaceRecognition implements FaceRecognitionInterface {
//    private static final String TAG = "MockFaceRecognition";
//
//    private Map<Integer, String> registeredUsers = new HashMap<>();
//    private Map<Integer, byte[]> mockEmbeddings = new HashMap<>();
//    private int nextUserId = 1;
//    private Random random = new Random();
//
//    @Override
//    public void initialize(Context context) {
//        Log.i(TAG, "MockFaceRecognition инициализирован");
//    }
//
//    @Override
//    public void registerUser(String name, Bitmap faceImage, FaceRegistrationCallback callback) {
//        if (registeredUsers.size() >= 20) {
//            callback.onError("Достигнут лимит пользователей (20)");
//            return;
//        }
//
//
//        byte[] embedding = generateMockEmbedding();
//        int userId = nextUserId++;
//
//        registeredUsers.put(userId, name);
//        mockEmbeddings.put(userId, embedding);
//
//        User user = new User();
//        user.setId(userId);
//        user.setName(name);
//        user.setFaceEmbedding(embedding);
//
//        Log.i(TAG, "Зарегистрирован пользователь: " + name + " (ID: " + userId + ")");
//        callback.onSuccess(user);
//    }
//
//    @Override
//    public void recognizeUser(Bitmap faceImage, FaceRecognitionCallback callback) {
//        if (registeredUsers.isEmpty()) {
//            callback.onUnknownFace();
//            return;
//        }
//
//
//        if (random.nextFloat() < 0.8f && !registeredUsers.isEmpty()) {
//
//            Integer[] userIds = registeredUsers.keySet().toArray(new Integer[0]);
//            int randomUserId = userIds[random.nextInt(userIds.length)];
//
//            User user = new User();
//            user.setId(randomUserId);
//            user.setName(registeredUsers.get(randomUserId));
//
//            float confidence = 0.7f + random.nextFloat() * 0.3f; // 70-100%
//            callback.onUserRecognized(user, confidence);
//        } else {
//            callback.onUnknownFace();
//        }
//    }
//
//    private byte[] generateMockEmbedding() {
//        byte[] embedding = new byte[512];
//        random.nextBytes(embedding);
//        return embedding;
//    }
//
//    @Override
//    public boolean isAvailable() {
//        return true;
//    }
//
//    @Override
//    public void clearCache() {
//        registeredUsers.clear();
//        mockEmbeddings.clear();
//        nextUserId = 1;
//        Log.i(TAG, "Кэш очищен");
//    }
//
//    public int getRegisteredCount() {
//        return registeredUsers.size();
//    }
//}