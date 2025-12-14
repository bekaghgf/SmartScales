package com.example.smartscales.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {

    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            Log.d("ImageUtils", "Конвертация ImageProxy в Bitmap");

            Bitmap bitmap = null;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                bitmap = imageProxy.toBitmap();
            }

            if (bitmap == null) {
                ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
                if (planes != null && planes.length > 0) {
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);

                    int width = imageProxy.getWidth();
                    int height = imageProxy.getHeight();
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int pixel = data[y * width + x] & 0xFF;
                            bitmap.setPixel(x, y, Color.rgb(pixel, pixel, pixel));
                        }
                    }
                }
            }

            if (bitmap != null) {
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                bitmap.recycle();
                return rotated;
            }

            return bitmap;

        } catch (Exception e) {
            Log.e("ImageUtils", "Error converting image: " + e.getMessage(), e);
            return null;
        }
    }

    public static Bitmap cropAndResizeFace(Bitmap original, android.graphics.Rect faceRect, int targetSize) {
        try {
            int padding = (int) (faceRect.width() * 0.2f);
            int left = Math.max(faceRect.left - padding, 0);
            int top = Math.max(faceRect.top - padding, 0);
            int right = Math.min(faceRect.right + padding, original.getWidth());
            int bottom = Math.min(faceRect.bottom + padding, original.getHeight());

            Bitmap cropped = Bitmap.createBitmap(original, left, top, right - left, bottom - top);
            Bitmap resized = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true);

            cropped.recycle();
            return resized;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}