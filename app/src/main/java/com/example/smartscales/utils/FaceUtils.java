
package com.example.smartscales.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;

import com.google.mlkit.vision.face.Face;

public class FaceUtils {

    public static Bitmap extractAlignedFace(Bitmap original, Face face, int targetSize) {
        try {
            Rect bounds = face.getBoundingBox();

            int padding = (int) (bounds.width() * 0.2f);
            int left = Math.max(bounds.left - padding, 0);
            int top = Math.max(bounds.top - padding, 0);
            int right = Math.min(bounds.right + padding, original.getWidth());
            int bottom = Math.min(bounds.bottom + padding, original.getHeight());

            Bitmap cropped = Bitmap.createBitmap(original, left, top, right - left, bottom - top);

            if (face.getLeftEyeOpenProbability() != null && face.getRightEyeOpenProbability() != null) {
            }
            Bitmap resized = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true);
            resized = normalizeLighting(resized);

            cropped.recycle();
            return resized;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Bitmap enhanceFaceImage(Bitmap faceImage) {
        try {
            faceImage = adjustContrast(faceImage, 1.2f);

            faceImage = reduceNoise(faceImage);

            faceImage = sharpenImage(faceImage);

            return faceImage;
        } catch (Exception e) {
            return faceImage;
        }
    }

    private static Bitmap adjustContrast(Bitmap src, float contrast) {
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, src.getConfig());

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = src.getPixel(x, y);
                int r = (int) ((((pixel >> 16) & 0xFF) - 128) * contrast + 128);
                int g = (int) ((((pixel >> 8) & 0xFF) - 128) * contrast + 128);
                int b = (int) (((pixel & 0xFF) - 128) * contrast + 128);

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                result.setPixel(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return result;
    }

    private static Bitmap reduceNoise(Bitmap src) {
        return Bitmap.createScaledBitmap(src, src.getWidth(), src.getHeight(), true);
    }

    private static Bitmap sharpenImage(Bitmap src) {
        return src;
    }

    private static Bitmap normalizeLighting(Bitmap src) {
        return src;
    }

    public static boolean isFaceQualityGood(Face face, Bitmap faceImage) {
        if (face == null || faceImage == null) {
            return false;
        }

        Rect bounds = face.getBoundingBox();
        float faceArea = bounds.width() * bounds.height();
        float imageArea = faceImage.getWidth() * faceImage.getHeight();

        if (faceArea / imageArea < 0.05f) {
            return false;
        }

        if (Math.abs(face.getHeadEulerAngleY()) > 30 ||
                Math.abs(face.getHeadEulerAngleZ()) > 30) {
            return false;
        }

        if (face.getLeftEyeOpenProbability() != null &&
                face.getLeftEyeOpenProbability() < 0.3f) {
            return false;
        }

        if (face.getSmilingProbability() != null &&
                face.getSmilingProbability() > 0.8f) {
            return false;
        }

        return true;
    }
}