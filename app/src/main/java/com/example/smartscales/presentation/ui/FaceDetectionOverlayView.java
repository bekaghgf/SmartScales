package com.example.smartscales.presentation.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import java.util.List;

public class FaceDetectionOverlayView extends View {
    private Paint facePaint;
    private Paint landmarkPaint;
    private List<Face> faces;

    public FaceDetectionOverlayView(Context context) {
        super(context);
        init();
    }

    public FaceDetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        facePaint = new Paint();
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(4f);

        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(2f);
    }

    public void setFaces(List<Face> faces) {
        this.faces = faces;
        invalidate();
    }

    public void clearFaces() {
        this.faces = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (faces != null) {
            for (Face face : faces) {
                // Draw face bounding box
                RectF bounds = new RectF(face.getBoundingBox());
                canvas.drawRect(bounds, facePaint);

                // Draw face contours if available
                drawFaceContours(canvas, face);

                // Draw additional info
                drawFaceInfo(canvas, face, bounds);
            }
        }
    }

    private void drawFaceContours(Canvas canvas, Face face) {
        // Draw face contours
        for (FaceContour contour : face.getAllContours()) {
            for (android.graphics.PointF point : contour.getPoints()) {
                canvas.drawCircle(point.x, point.y, 2f, landmarkPaint);
            }
        }
    }

    private void drawFaceInfo(Canvas canvas, Face face, RectF bounds) {
        Paint textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(24f);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        // Draw confidence if available
        if (face.getTrackingId() != null) {
            String text = "ID: " + face.getTrackingId();
            canvas.drawText(text, bounds.left, bounds.top - 10, textPaint);
        }

        // Draw smile probability
        if (face.getSmilingProbability() != null) {
            String smileText = String.format("Улыбка: %.0f%%", face.getSmilingProbability() * 100);
            canvas.drawText(smileText, bounds.left, bounds.bottom + 30, textPaint);
        }
    }
}