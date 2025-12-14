

package com.example.smartscales.domain.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.smartscales.data.database.AppDatabase;
import com.example.smartscales.data.database.dao.UserDao;
import com.example.smartscales.data.models.User;
import com.example.smartscales.domain.interfaces.FaceRecognitionInterface;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FaceRecognitionService implements FaceRecognitionInterface {
    private static final String TAG = "FaceRecognitionService";

    private boolean isFaceDetectorInitialized = false;
    private CountDownLatch initializationLatch = new CountDownLatch(1);
    private static final float RECOGNITION_THRESHOLD = 0.6f; // Порог распознавания 60%
    private static final float DETECTION_CONFIDENCE = 0.7f; // Минимальная уверенность детекции
    private static final int FACE_EMBEDDING_SIZE = 128; // Размер вектора лица

    private Context context;
    private FaceDetector faceDetector;
    private ExecutorService executorService;
    private UserDao userDao;
    private Map<String, float[]> faceEmbeddingsCache;

    public FaceRecognitionService(Context context) {
        Log.d(TAG, "Конструктор вызван");
        this.context = context.getApplicationContext();
        Log.d(TAG, "Context получен: " + (this.context != null));

        this.context = context.getApplicationContext();
        this.executorService = Executors.newFixedThreadPool(4);
        this.faceEmbeddingsCache = new HashMap<>();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            initializeDatabase();
        }, 1000);
    }



    private void initializeDatabase() {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Начало инициализации БД");
                AppDatabase database = AppDatabase.getInstance(context);
                Log.d(TAG, "AppDatabase получена: " + (database != null));

                assert database != null;
                userDao = database.userDao();
                Log.d(TAG, "UserDao получен: " + (userDao != null));

                loadFaceEmbeddingsFromDatabase();
                Log.i(TAG, "Face Recognition Service initialized with database");

            } catch (Exception e) {
                Log.e(TAG, "Database initialization failed: " + e.getMessage(), e);
                e.printStackTrace();
            }
        });
    }

    private void loadFaceEmbeddingsFromDatabase() {
        try {
            List<User> users = userDao.getAllActiveUsers().getValue();
            if (users != null) {
                for (User user : users) {
                    if (user.getFaceEmbedding() != null) {
                        float[] embedding = byteArrayToFloatArray(user.getFaceEmbedding());
                        faceEmbeddingsCache.put(user.getName(), embedding);
                    }
                }
                Log.i(TAG, "Loaded " + faceEmbeddingsCache.size() + " face embeddings from database");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading face embeddings: " + e.getMessage());
        }
    }

    @Override
    public void initialize(Context context) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Инициализация FaceDetector...");

                FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .setMinFaceSize(0.1f)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                        .build();

                faceDetector = FaceDetection.getClient(options);
                isFaceDetectorInitialized = true;
                initializationLatch.countDown();

                Log.i(TAG, "Face Detector инициализирован");

            } catch (Exception e) {
                Log.e(TAG, "Face detector initialization failed: " + e.getMessage(), e);
                initializationLatch.countDown();
            }
        });
    }

//    @Override
//    public void registerUser(String name, Bitmap faceImage, FaceRegistrationCallback callback) {
//        executorService.execute(() -> {
//            try {
//                Log.d(TAG, "Регистрация пользователя: " + name);
//
//
//                if (faceDetector == null || !isFaceDetectorInitialized) {
//                    Log.d(TAG, "FaceDetector не готов, ждем инициализации...");
//                    try {
//
//                        boolean initialized = initializationLatch.await(5, TimeUnit.SECONDS);
//                        if (!initialized) {
//                            callback.onError("Face detector initialization timeout");
//                            return;
//                        }
//                    } catch (InterruptedException e) {
//                        callback.onError("Face detector wait interrupted");
//                        return;
//                    }
//                }
//
//
//                User existingUser = userDao.getUserByName(name);
//                if (existingUser != null) {
//                    callback.onError("Пользователь уже существует: " + name);
//                    return;
//                }
//
//
//                saveBitmapForDebug(faceImage, "register_" + name + ".jpg");
//                Log.d(TAG, "Размер изображения для регистрации: " +
//                        faceImage.getWidth() + "x" + faceImage.getHeight());
//
//
//                InputImage inputImage = InputImage.fromBitmap(faceImage, 0);
//
//                Log.d(TAG, "Начинаем обработку изображения...");
//                Task<List<Face>> faceTask = faceDetector.process(inputImage);
//
//
//                faceTask.addOnSuccessListener(faces -> {
//                    Log.d(TAG, "Обнаружено лиц: " + (faces != null ? faces.size() : 0));
//
//                    if (faces == null || faces.isEmpty()) {
//                        Log.d(TAG, "Лица не обнаружены на фото");
//                        callback.onError("Лицо не обнаружено на фото. Убедитесь, что лицо хорошо видно");
//                        return;
//                    }
//
//
//                    Face face = faces.get(0);
//                    Log.d(TAG, "Лицо обнаружено: bounds=" + face.getBoundingBox());
//
//
//                    float[] faceEmbedding = generateFaceEmbedding(faceImage, face);
//
//
//                    byte[] embeddingBytes = floatArrayToByteArray(faceEmbedding);
//
//
//                    User user = new User(name, embeddingBytes, 70.0f, 65.0f);
//
//
//                    long userId = userDao.insert(user);
//                    user.setId((int) userId);
//
//                    faceEmbeddingsCache.put(name, faceEmbedding);
//
//                    Log.i(TAG, "Пользователь зарегистрирован с ID: " + userId);
//                    callback.onSuccess(user);
//
//                }).addOnFailureListener(e -> {
//                    Log.e(TAG, "Ошибка обработки лица: " + e.getMessage(), e);
//                    callback.onError("Ошибка обработки лица: " + e.getMessage());
//                });
//
//            } catch (Exception e) {
//                Log.e(TAG, "Ошибка регистрации: " + e.getMessage(), e);
//                callback.onError("Ошибка регистрации: " + e.getMessage());
//            }
//        });
//    }

    @Override
    public void registerUser(String name, Bitmap faceImage, FaceRegistrationCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "=== РЕГИСТРАЦИЯ ПОЛЬЗОВАТЕЛЯ: " + name + " ===");
                saveBitmapForDebug(faceImage, "original_" + name + ".jpg");

                // 2. Проверяем существование пользователя
                if (userDao == null) {
                    Log.e(TAG, "userDao is null!");
                    callback.onError("База данных не готова");
                    return;
                }

                User existingUser = userDao.getUserByName(name);
                if (existingUser != null) {
                    callback.onError("Пользователь уже существует: " + name);
                    return;
                }

                Log.d(TAG, "Размер фото: " + faceImage.getWidth() + "x" + faceImage.getHeight());

                // 3. ВРЕМЕННО: Тут пробно используется ML Kit для тестирования
                // Создаем тестовый эмбеддинг
                float[] embedding = new float[FACE_EMBEDDING_SIZE];
                for (int i = 0; i < embedding.length; i++) {
                    embedding[i] = (float) i / embedding.length;
                }
                normalizeVector(embedding);

                // 4. Конвертируем в byte[]
                byte[] embeddingBytes = floatArrayToByteArray(embedding);

                // 5. Создаем пользователя с реальными весами из UI
                // НУЖНО будет потом использовать реальные веса из параметров метода!
                // Сейчас передаем начальный вес 70.0f и целевой 65.0f - это фиксированные значения!

                User user = new User();
                user.setName(name);
                user.setFaceEmbedding(embeddingBytes);
                user.setInitialWeight(70.0f); // Временное значение
                user.setTargetWeight(65.0f);  // Временное значение
                user.setActive(true);
                user.setCreatedAt(new java.util.Date());

                // 6. Сохраняем в БД
                long id = userDao.insert(user);
                user.setId((int) id);

                // 7. Добавляем в кэш
                faceEmbeddingsCache.put(name, embedding);

                Log.d(TAG, "✅ Пользователь успешно зарегистрирован! ID: " + id);
                callback.onSuccess(user);

            } catch (Exception e) {
                Log.e(TAG, "❌ Ошибка регистрации: " + e.getMessage(), e);
                callback.onError("Исключение: " + e.getMessage());
            }
        });
    }

    @Override
    public void recognizeUser(Bitmap faceImage, FaceRecognitionCallback callback) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "Начало распознавания, размер изображения: " +
                        faceImage.getWidth() + "x" + faceImage.getHeight());

                saveBitmapForDebug(faceImage, "debug_face_input.jpg");

                if (faceDetector == null) {
                    Log.e(TAG, "FaceDetector is null! Инициализируем заново...");
                    initialize(context);
                    Thread.sleep(1000);
                }

                InputImage inputImage = InputImage.fromBitmap(faceImage, 0);
                Log.d(TAG, "InputImage создана, rotation: 0");

                Task<List<Face>> faceTask = faceDetector.process(inputImage);

                faceTask.addOnSuccessListener(faces -> {
                            Log.d(TAG, "Обнаружено лиц: " + (faces != null ? faces.size() : 0));

                            if (faces == null || faces.isEmpty()) {
                                Log.d(TAG, "Лица не обнаружены");
                                callback.onNoFaceDetected();
                                return;
                            }

                            Face firstFace = faces.get(0);
                            Log.d(TAG, "Первое лицо: bounds=" + firstFace.getBoundingBox() +
                                    ", trackingId=" + firstFace.getTrackingId());


                            if (!faceEmbeddingsCache.isEmpty()) {
                                String firstName = faceEmbeddingsCache.keySet().iterator().next();
                                User user = userDao.getUserByName(firstName);
                                if (user != null) {
                                    callback.onUserRecognized(user, 0.8f);
                                } else {
                                    callback.onUnknownFace();
                                }
                            } else {
                                callback.onUnknownFace();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Ошибка обнаружения лиц: " + e.getMessage(), e);
                            callback.onError("Face detection failed: " + e.getMessage());
                        });

            } catch (Exception e) {
                Log.e(TAG, "Recognition error: " + e.getMessage(), e);
                callback.onError("Exception: " + e.getMessage());
            }
        });
    }

    private RecognitionResult findBestMatch(float[] detectedEmbedding) {
        String bestMatchName = null;
        float bestMatchScore = 0;

        for (Map.Entry<String, float[]> entry : faceEmbeddingsCache.entrySet()) {
            float similarity = cosineSimilarity(detectedEmbedding, entry.getValue());

            if (similarity > bestMatchScore) {
                bestMatchScore = similarity;
                bestMatchName = entry.getKey();
            }
        }

        if (bestMatchName != null) {
            return new RecognitionResult(bestMatchName, bestMatchScore);
        }

        return null;
    }

    private Face getBestFace(List<Face> faces) {
        Face bestFace = null;
        float maxArea = 0;

        for (Face face : faces) {
            RectF bounds = new RectF(face.getBoundingBox());
            float area = bounds.width() * bounds.height();

            if (area > maxArea) {
                maxArea = area;
                bestFace = face;
            }
        }

        return bestFace;
    }

    // Упрощенная генерация эмбеддинга (в реальном приложении нужно использовать модель)
    private float[] generateFaceEmbedding(Bitmap faceImage, Face face) {
        float[] embedding = new float[FACE_EMBEDDING_SIZE];

        // Временная реализация - генерируем случайные эмбеддинги
        // В реальном приложении здесь должна быть нейросеть
        for (int i = 0; i < FACE_EMBEDDING_SIZE; i++) {
            embedding[i] = (float) Math.random();
        }

        // Нормализуем вектор
        normalizeVector(embedding);

        return embedding;
    }

    private void normalizeVector(float[] vector) {
        float sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        float norm = (float) Math.sqrt(sum);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    private float cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            return 0;
        }

        float dotProduct = 0;
        float normA = 0;
        float normB = 0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private byte[] floatArrayToByteArray(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    private float[] byteArrayToFloatArray(byte[] bytes) {
        FloatBuffer buffer = ByteBuffer.wrap(bytes).asFloatBuffer();
        float[] floats = new float[buffer.remaining()];
        buffer.get(floats);
        return floats;
    }

    @Override
    public boolean isAvailable() {
        return faceDetector != null && userDao != null;
    }

    @Override
    public void clearCache() {
        faceEmbeddingsCache.clear();
        loadFaceEmbeddingsFromDatabase();
        Log.i(TAG, "Face recognition cache cleared and reloaded");
    }

    public int getRegisteredCount() {
        return faceEmbeddingsCache.size();
    }

    private static class RecognitionResult {
        String userName;
        float confidence;

        RecognitionResult(String userName, float confidence) {
            this.userName = userName;
            this.confidence = confidence;
        }
    }
    private void saveBitmapForDebug(Bitmap bitmap, String filename) {
        try {
            File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), filename);
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            Log.d(TAG, "Изображение сохранено для отладки: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Не удалось сохранить изображение: " + e.getMessage());
        }
    }
}