
package com.example.smartscales.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraHelper {
    private static final String TAG = "CameraHelper";
    private static final int IMAGE_QUALITY = 90;

    private Context context;
    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private CameraCaptureCallback captureCallback;
    private boolean isCameraRunning = false;

    public CameraHelper(Context context, PreviewView previewView) {
        this.context = context.getApplicationContext();
        this.previewView = previewView;
        this.cameraExecutor = Executors.newSingleThreadExecutor();
    }

    public void startCamera(LifecycleOwner lifecycleOwner) {
        if (isCameraRunning) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(lifecycleOwner);
                isCameraRunning = true;
            } catch (Exception e) {
                if (captureCallback != null) {
                    captureCallback.onError(e);
                }
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases(LifecycleOwner lifecycleOwner) {
        if (cameraProvider == null) {
            return;
        }

        try {
            cameraProvider.unbindAll();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build();

            Preview preview = new Preview.Builder()
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build();

            cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
            );

        } catch (Exception e) {
            if (captureCallback != null) {
                captureCallback.onError(e);
            }
        }
    }

    public void capturePhoto() {
        if (imageCapture == null || captureCallback == null) {
            if (captureCallback != null) {
                captureCallback.onError(new Exception("Camera not ready"));
            }
            return;
        }

        File photoFile = createImageFile();
        if (photoFile == null) {
            captureCallback.onError(new Exception("Cannot create image file"));
            return;
        }

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);

                        if (bitmap != null) {
                            bitmap = rotateAndMirrorForFrontCamera(bitmap);
                            captureCallback.onPhotoCaptured(bitmap);
                        } else {
                            captureCallback.onError(new Exception("Failed to decode image file"));
                        }

                        photoFile.delete();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        captureCallback.onError(exception);
                    }
                }
        );
    }

    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "FACE_" + timeStamp;

            File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (storageDir == null) {
                storageDir = context.getFilesDir();
            }

            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            return null;
        }
    }

    private Bitmap rotateAndMirrorForFrontCamera(Bitmap bitmap) {
        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true
            );

            bitmap.recycle();
            return rotatedBitmap;

        } catch (Exception e) {
            Log.e(TAG, "Ошибка обработки фото: " + e.getMessage());
            return bitmap;
        }
    }

    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            if (planes == null || planes.length < 3) {
                return null;
            }

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    imageProxy.getWidth(),
                    imageProxy.getHeight(),
                    null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                    IMAGE_QUALITY,
                    out
            );

            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        } catch (Exception e) {
            return null;
        }
    }

    public void stopCamera() {
        if (cameraProvider != null) {
            try {
                cameraProvider.unbindAll();
            } catch (Exception ignored) {}
            cameraProvider = null;
        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }

        isCameraRunning = false;
    }

    public boolean isCameraRunning() {
        return isCameraRunning;
    }

    public void setCaptureCallback(CameraCaptureCallback callback) {
        this.captureCallback = callback;
    }

    public static boolean checkCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    public interface CameraCaptureCallback {
        void onPhotoCaptured(Bitmap bitmap);
        void onError(Exception exception);
    }


    public static Bitmap captureFrame(ImageProxy imageProxy) {
        try {
            Bitmap bitmap = imageProxy.toBitmap();

            if (bitmap != null) {
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                matrix.postScale(-1, 1, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f); // Зеркало

                Bitmap result = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                return result;
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "captureFrame error: " + e.getMessage());
            return null;
        }
    }
}