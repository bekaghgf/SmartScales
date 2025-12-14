//package com.example.smartscales.domain.services;
//
//import android.content.Context;
//import android.content.res.AssetFileDescriptor;
//import android.graphics.Bitmap;
//import android.graphics.Matrix;
//import android.graphics.RectF;
//import android.util.Log;
//import androidx.annotation.NonNull;
//import com.google.mlkit.vision.common.InputImage;
//import com.google.mlkit.vision.face.Face;
//import com.google.mlkit.vision.face.FaceDetection;
//import com.google.mlkit.vision.face.FaceDetector;
//import com.google.mlkit.vision.face.FaceDetectorOptions;
//import com.example.smartscales.data.models.User;
//import com.example.smartscales.domain.interfaces.FaceRecognitionInterface;
//import org.tensorflow.lite.Interpreter;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.MappedByteBuffer;
//import java.nio.channels.FileChannel;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.atomic.AtomicInteger;
//
//public class TFLiteFaceRecognition implements FaceRecognitionInterface {
//    private static final String TAG = "TFLiteFaceRecognition";

//    private static final int INPUT_IMAGE_SIZE = 112;
//    private static final int EMBEDDING_SIZE = 512; // MobileFaceNet эмбеддинг размер
//    private static final float RECOGNITION_THRESHOLD = 0.6f;
//    private static final int MAX_REGISTERED_USERS = 20;
//
//    private FaceDetector faceDetector;
//    private Interpreter tfliteInterpreter;
//    private Context context;
//    private ExecutorService executorService;
//    private boolean initialized = false;
//
//    private Map<Integer, float[]> registeredEmbeddings; // userId -> embedding
//    private Map<Integer, String> embeddingFiles; // userId -> путь к файлу
//
//    private AtomicInteger userCounter = new AtomicInteger(1);
//
//    public TFLiteFaceRecognition() {
//        executorService = Executors.newSingleThreadExecutor();
//        registeredEmbeddings = new HashMap<>();
//        embeddingFiles = new HashMap<>();
//    }
//
//    @Override
//    public void initialize(Context context) {
//        this.context = context;
//
//        executorService.execute(() -> {
//            try {
//                initializeFaceDetector();
//
//                if (!loadTFLiteModel()) {
//                    Log.e(TAG, "Failed to load TFLite model, using mock mode");
//                }
//
//                loadRegisteredEmbeddings();
//
//                initialized = true;
//                Log.i(TAG, "Face Recognition initialized successfully");
//
//            } catch (Exception e) {
//                Log.e(TAG, "Initialization failed: " + e.getMessage(), e);
//            }
//        });
//    }
//
//    private void initializeFaceDetector() {
//        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
//                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
//                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//                .setMinFaceSize(0.15f)
//                .build();
//
//        faceDetector = FaceDetection.getClient(options);
//    }
//
//    private boolean loadTFLiteModel() {
//        try {
//            String modelFilename = "mobilefacenet.tflite";
//
//            try {
//                InputStream inputStream = context.getAssets().open(modelFilename);
//                inputStream.close();
//            } catch (IOException e) {
//                Log.e(TAG, "Model file not found: " + modelFilename);
//                return false;
//            }
//
//            MappedByteBuffer modelBuffer = loadModelFile(modelFilename);
//
//            Interpreter.Options options = new Interpreter.Options();
//            options.setNumThreads(4);
//
//            try {
//                tfliteInterpreter = new Interpreter(modelBuffer, options);
//                Log.i(TAG, "TFLite model loaded successfully");
//                return true;
//            } catch (Exception e) {
//                Log.e(TAG, "Error creating TFLite interpreter: " + e.getMessage());
//                return false;
//            }
//
//        } catch (IOException e) {
//            Log.e(TAG, "IO error loading model: " + e.getMessage());
//            return false;
//        }
//    }
//
//    private MappedByteBuffer loadModelFile(String modelFilename) throws IOException {
//        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFilename);
//        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
//        FileChannel fileChannel = inputStream.getChannel();
//        long startOffset = fileDescriptor.getStartOffset();
//        long declaredLength = fileDescriptor.getDeclaredLength();
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
//    }
//
//    @Override
//    public void registerUser(String name, Bitmap faceImage, FaceRegistrationCallback callback) {
//        if (!initialized) {
//            callback.onError("Face recognition service not initialized");
//            return;
//        }
//
//        if (registeredEmbeddings.size() >= MAX_REGISTERED_USERS) {
//            callback.onError("Maximum number of registered users reached (" + MAX_REGISTERED_USERS + ")");
//            return;
//        }
//
//        executorService.execute(() -> {
//            try {
//                FaceDetectionResult detectionResult = detectAndAlignFace(faceImage);
//
//                if (!detectionResult.success) {
//                    callback.onError(detectionResult.errorMessage);
//                    return;
//                }
//
//                float[] embedding = generateEmbedding(detectionResult.alignedFace);
//
//                if (embedding == null) {
//                    callback.onError("Failed to generate face embedding");
//                    return;
//                }
//
//                normalizeEmbedding(embedding);
//
//                int existingUserId = findSimilarFace(embedding);
//                if (existingUserId != -1) {
//                    callback.onError("Similar face already registered with ID: " + existingUserId);
//                    return;
//                }
//
//                int newUserId = userCounter.getAndIncrement();
//
//                saveEmbedding(newUserId, embedding);
//
//                registeredEmbeddings.put(newUserId, embedding);
//
//                User user = new User(name, serializeEmbedding(embedding), 0, 0);
//                user.setId(newUserId);
//
//                Log.i(TAG, "User registered successfully: " + name + " (ID: " + newUserId + ")");
//                callback.onSuccess(user);
//
//            } catch (Exception e) {
//                Log.e(TAG, "Registration failed: " + e.getMessage(), e);
//                callback.onError("Registration error: " + e.getMessage());
//            }
//        });
//    }
//
//    @Override
//    public void recognizeUser(Bitmap faceImage, FaceRecognitionCallback callback) {
//        if (!initialized) {
//            callback.onError("Face recognition service not initialized");
//            return;
//        }
//
//        if (registeredEmbeddings.isEmpty()) {
//            callback.onUnknownFace();
//            return;
//        }
//
//        executorService.execute(() -> {
//            try {
//                FaceDetectionResult detectionResult = detectAndAlignFace(faceImage);
//
//                if (!detectionResult.success) {
//                    if (detectionResult.errorMessage.contains("No face detected")) {
//                        callback.onNoFaceDetected();
//                    } else {
//                        callback.onError(detectionResult.errorMessage);
//                    }
//                    return;
//                }
//
//                float[] embedding = generateEmbedding(detectionResult.alignedFace);
//
//                if (embedding == null) {
//                    callback.onError("Failed to generate face embedding");
//                    return;
//                }
//
//                normalizeEmbedding(embedding);
//
//                RecognitionResult result = findBestMatch(embedding);
//
//                if (result != null && result.similarity >= RECOGNITION_THRESHOLD) {
//                    User user = new User();
//                    user.setId(result.userId);
//                    user.setName("User " + result.userId);
//
//                    callback.onUserRecognized(user, result.similarity);
//                    Log.i(TAG, "User recognized: ID=" + result.userId + ", confidence=" + result.similarity);
//                } else {
//                    callback.onUnknownFace();
//                    Log.i(TAG, "Unknown face detected");
//                }
//
//            } catch (Exception e) {
//                Log.e(TAG, "Recognition failed: " + e.getMessage(), e);
//                callback.onError("Recognition error: " + e.getMessage());
//            }
//        });
//    }
//
//    private FaceDetectionResult detectAndAlignFace(Bitmap original) {
//        FaceDetectionResult result = new FaceDetectionResult();
//
//        try {
//            InputImage image = InputImage.fromBitmap(original, 0);
//
//            List<Face> faces = com.google.android.gms.tasks.Tasks.await(
//                    faceDetector.process(image)
//            );
//
//            if (faces.isEmpty()) {
//                result.success = false;
//                result.errorMessage = "No face detected";
//                return result;
//            }
//
//            if (faces.size() > 1) {
//                result.success = false;
//                result.errorMessage = "Multiple faces detected";
//                return result;
//            }
//
//            Face face = faces.get(0);
//
//            Bitmap alignedFace = alignFace(original, face);
//
//            if (alignedFace == null) {
//                result.success = false;
//                result.errorMessage = "Face alignment failed";
//                return result;
//            }
//
//            result.success = true;
//            result.face = face;
//            result.alignedFace = alignedFace;
//
//        } catch (Exception e) {
//            result.success = false;
//            result.errorMessage = "Face detection error: " + e.getMessage();
//        }
//
//        return result;
//    }
//
//    private Bitmap alignFace(Bitmap original, Face face) {
//        try {
//            RectF bounds = new RectF(face.getBoundingBox());
//
//            float scale = 1.3f;
//            float centerX = bounds.centerX();
//            float centerY = bounds.centerY();
//            float width = bounds.width() * scale;
//            float height = bounds.height() * scale;
//
//            float left = Math.max(centerX - width/2, 0);
//            float top = Math.max(centerY - height/2, 0);
//            float right = Math.min(centerX + width/2, original.getWidth());
//            float bottom = Math.min(centerY + height/2, original.getHeight());
//
//            Bitmap cropped = Bitmap.createBitmap(
//                    original,
//                    (int) left,
//                    (int) top,
//                    (int) (right - left),
//                    (int) (bottom - top)
//            );
//
//            Bitmap scaled = Bitmap.createScaledBitmap(
//                    cropped,
//                    INPUT_IMAGE_SIZE,
//                    INPUT_IMAGE_SIZE,
//                    true
//            );
//
//            Bitmap aligned = rotateFaceIfNeeded(scaled, face);
//
//            cropped.recycle();
//            return aligned != null ? aligned : scaled;
//
//        } catch (Exception e) {
//            Log.e(TAG, "Face alignment error: " + e.getMessage());
//            return null;
//        }
//    }
//
//    private Bitmap rotateFaceIfNeeded(Bitmap faceBitmap, Face face) {
//
//        float roll = face.getHeadEulerAngleZ();
//
//        if (Math.abs(roll) > 10) {
//            Matrix matrix = new Matrix();
//            matrix.postRotate(-roll);
//
//            return Bitmap.createBitmap(
//                    faceBitmap,
//                    0, 0,
//                    faceBitmap.getWidth(),
//                    faceBitmap.getHeight(),
//                    matrix,
//                    true
//            );
//        }
//
//        return faceBitmap;
//    }
//
//    private float[] generateEmbedding(Bitmap faceBitmap) {
//        if (tfliteInterpreter == null) {
//            return generateMockEmbedding();
//        }
//
//        try {
//            ByteBuffer inputBuffer = convertBitmapToByteBuffer(faceBitmap);
//
//            float[][] output = new float[1][EMBEDDING_SIZE];
//            tfliteInterpreter.run(inputBuffer, output);
//
//            return output[0];
//
//        } catch (Exception e) {
//            Log.e(TAG, "Embedding generation error: " + e.getMessage());
//            return generateMockEmbedding();
//        }
//    }
//
//    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
//        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3);
//        inputBuffer.order(ByteOrder.nativeOrder());
//
//        int[] pixels = new int[INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE];
//        bitmap.getPixels(pixels, 0, INPUT_IMAGE_SIZE, 0, 0, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE);
//
//        int pixel = 0;
//        for (int i = 0; i < INPUT_IMAGE_SIZE; i++) {
//            for (int j = 0; j < INPUT_IMAGE_SIZE; j++) {
//                int color = pixels[pixel++];
//
//
//                float r = ((color >> 16) & 0xFF) / 255.0f;
//                float g = ((color >> 8) & 0xFF) / 255.0f;
//                float b = (color & 0xFF) / 255.0f;
//
//               [-1, 1]
//                inputBuffer.putFloat((r - 0.5f) * 2.0f);
//                inputBuffer.putFloat((g - 0.5f) * 2.0f);
//                inputBuffer.putFloat((b - 0.5f) * 2.0f);
//            }
//        }
//
//        return inputBuffer;
//    }
//
//    private float[] generateMockEmbedding() {
//        float[] embedding = new float[EMBEDDING_SIZE];
//        for (int i = 0; i < EMBEDDING_SIZE; i++) {
//            embedding[i] = (float) Math.random() - 0.5f;
//        }
//        normalizeEmbedding(embedding);
//        return embedding;
//    }
//
//    private void normalizeEmbedding(float[] embedding) {
//        float sum = 0.0f;
//        for (float value : embedding) {
//            sum += value * value;
//        }
//
//        float norm = (float) Math.sqrt(sum);
//        if (norm > 0.0f) {
//            for (int i = 0; i < embedding.length; i++) {
//                embedding[i] /= norm;
//            }
//        }
//    }
//
//    private int findSimilarFace(float[] embedding) {
//        for (Map.Entry<Integer, float[]> entry : registeredEmbeddings.entrySet()) {
//            float similarity = cosineSimilarity(embedding, entry.getValue());
//            if (similarity > 0.9f) {
//                return entry.getKey();
//            }
//        }
//        return -1;
//    }
//
//    private RecognitionResult findBestMatch(float[] embedding) {
//        int bestUserId = -1;
//        float bestSimilarity = -1.0f;
//
//        for (Map.Entry<Integer, float[]> entry : registeredEmbeddings.entrySet()) {
//            float similarity = cosineSimilarity(embedding, entry.getValue());
//
//            if (similarity > bestSimilarity) {
//                bestSimilarity = similarity;
//                bestUserId = entry.getKey();
//            }
//        }
//
//        if (bestUserId != -1) {
//            return new RecognitionResult(bestUserId, bestSimilarity);
//        }
//
//        return null;
//    }
//
//    private float cosineSimilarity(float[] a, float[] b) {
//        float dotProduct = 0.0f;
//        float normA = 0.0f;
//        float normB = 0.0f;
//
//        for (int i = 0; i < a.length; i++) {
//            dotProduct += a[i] * b[i];
//            normA += a[i] * a[i];
//            normB += b[i] * b[i];
//        }
//
//        if (normA == 0.0f || normB == 0.0f) {
//            return 0.0f;
//        }
//
//        return dotProduct / (float) Math.sqrt(normA * normB);
//    }
//
//    private void saveEmbedding(int userId, float[] embedding) {
//        try {
//            File embeddingsDir = new File(context.getFilesDir(), "face_embeddings");
//            if (!embeddingsDir.exists()) {
//                embeddingsDir.mkdirs();
//            }
//
//            File embeddingFile = new File(embeddingsDir, "user_" + userId + ".emb");
//
//            try (FileOutputStream fos = new FileOutputStream(embeddingFile)) {
//                for (float value : embedding) {
//                    int intBits = Float.floatToIntBits(value);
//                    fos.write((intBits >> 24) & 0xFF);
//                    fos.write((intBits >> 16) & 0xFF);
//                    fos.write((intBits >> 8) & 0xFF);
//                    fos.write(intBits & 0xFF);
//                }
//            }
//
//            embeddingFiles.put(userId, embeddingFile.getAbsolutePath());
//            Log.d(TAG, "Embedding saved for user " + userId + " at " + embeddingFile.getAbsolutePath());
//
//        } catch (IOException e) {
//            Log.e(TAG, "Failed to save embedding: " + e.getMessage());
//        }
//    }
//
//    private void loadRegisteredEmbeddings() {
//        File embeddingsDir = new File(context.getFilesDir(), "face_embeddings");
//        if (!embeddingsDir.exists() || !embeddingsDir.isDirectory()) {
//            return;
//        }
//
//        File[] files = embeddingsDir.listFiles((dir, name) -> name.endsWith(".emb"));
//
//        if (files != null) {
//            for (File file : files) {
//                try {
//                    String filename = file.getName();
//                    String userIdStr = filename.substring(5, filename.length() - 4); // "user_123.emb"
//                    int userId = Integer.parseInt(userIdStr);
//
//                    float[] embedding = loadEmbeddingFromFile(file);
//
//                    if (embedding != null) {
//                        registeredEmbeddings.put(userId, embedding);
//                        embeddingFiles.put(userId, file.getAbsolutePath());
//
//                        if (userId >= userCounter.get()) {
//                            userCounter.set(userId + 1);
//                        }
//
//                        Log.d(TAG, "Loaded embedding for user " + userId);
//                    }
//
//                } catch (Exception e) {
//                    Log.e(TAG, "Error loading embedding from " + file.getName() + ": " + e.getMessage());
//                }
//            }
//        }
//
//        Log.i(TAG, "Loaded " + registeredEmbeddings.size() + " registered embeddings");
//    }
//
//    private float[] loadEmbeddingFromFile(File file) throws IOException {
//        byte[] bytes = new byte[(int) file.length()];
//
//        try (FileInputStream fis = new FileInputStream(file)) {
//            int bytesRead = fis.read(bytes);
//            if (bytesRead != bytes.length) {
//                throw new IOException("Failed to read complete embedding file");
//            }
//        }
//
//        if (bytes.length % 4 != 0) {
//            throw new IOException("Invalid embedding file size");
//        }
//
//        float[] embedding = new float[bytes.length / 4];
//
//        for (int i = 0; i < embedding.length; i++) {
//            int intBits = ((bytes[i*4] & 0xFF) << 24) |
//                    ((bytes[i*4+1] & 0xFF) << 16) |
//                    ((bytes[i*4+2] & 0xFF) << 8) |
//                    (bytes[i*4+3] & 0xFF);
//            embedding[i] = Float.intBitsToFloat(intBits);
//        }
//
//        return embedding;
//    }
//
//    private String serializeEmbedding(float[] embedding) {
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < Math.min(embedding.length, 10); i++) {
//            sb.append(String.format("%.4f", embedding[i]));
//            if (i < 9) sb.append(",");
//        }
//        return sb.toString();
//    }
//
//    @Override
//    public boolean isAvailable() {
//        return initialized;
//    }
//
//    @Override
//    public void clearCache() {
//        executorService.execute(() -> {
//            registeredEmbeddings.clear();
//            embeddingFiles.clear();
//
//            File embeddingsDir = new File(context.getFilesDir(), "face_embeddings");
//            if (embeddingsDir.exists()) {
//                File[] files = embeddingsDir.listFiles();
//                if (files != null) {
//                    for (File file : files) {
//                        file.delete();
//                    }
//                }
//                embeddingsDir.delete();
//            }
//
//            Log.i(TAG, "Face recognition cache cleared");
//        });
//    }
//
//    private static class FaceDetectionResult {
//        boolean success;
//        String errorMessage;
//        Face face;
//        Bitmap alignedFace;
//    }
//
//    private static class RecognitionResult {
//        int userId;
//        float similarity;
//
//        RecognitionResult(int userId, float similarity) {
//            this.userId = userId;
//            this.similarity = similarity;
//        }
//    }
//
//    public int getRegisteredUsersCount() {
//        return registeredEmbeddings.size();
//    }
//
//    public List<Integer> getRegisteredUserIds() {
//        return new ArrayList<>(registeredEmbeddings.keySet());
//    }
//
//    public void deleteUser(int userId) {
//        executorService.execute(() -> {
//            try {
//                registeredEmbeddings.remove(userId);
//
//
//                String filePath = embeddingFiles.remove(userId);
//                if (filePath != null) {
//                    File file = new File(filePath);
//                    file.delete();
//                }
//
//                Log.i(TAG, "User " + userId + " deleted from face recognition");
//
//            } catch (Exception e) {
//                Log.e(TAG, "Error deleting user " + userId + ": " + e.getMessage());
//            }
//        });
//    }
//}