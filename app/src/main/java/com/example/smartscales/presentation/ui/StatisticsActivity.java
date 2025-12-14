
package com.example.smartscales.presentation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartscales.R;
import com.example.smartscales.data.models.User;
import com.example.smartscales.presentation.viewmodels.StatisticsViewModel;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class StatisticsActivity extends AppCompatActivity {

    private StatisticsViewModel viewModel;
    private int userId = -1;

    // UI Components
    private TextView tvTotalMeasurements, tvPeriod, tvAverageWeight;
    private TextView tvInitialWeight, tvCurrentWeight, tvTotalChange, tvUserName;
    private LineChart weightChart;
    private RecyclerView rvDailyStats;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        // Get userId from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("USER_ID")) {
            userId = intent.getIntExtra("USER_ID", -1);
        }

        initViews();
        initViewModel();

        if (userId == -1) {
            Toast.makeText(this, "Ошибка: не указан пользователь", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        tvTotalMeasurements = findViewById(R.id.tvTotalMeasurements);
        tvPeriod = findViewById(R.id.tvPeriod);
        tvAverageWeight = findViewById(R.id.tvAverageWeight);
        tvInitialWeight = findViewById(R.id.tvInitialWeight);
        tvCurrentWeight = findViewById(R.id.tvCurrentWeight);
        tvTotalChange = findViewById(R.id.tvTotalChange);
        tvUserName = findViewById(R.id.tvUserName);
        weightChart = findViewById(R.id.weightChart);
        rvDailyStats = findViewById(R.id.rvDailyStats);

        // Setup RecyclerView
        rvDailyStats.setLayoutManager(new LinearLayoutManager(this));
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(StatisticsViewModel.class);

        // Set current user ID
        viewModel.setCurrentUserId(userId);

        // Observe data from ViewModel
        viewModel.getUser().observe(this, user -> {
            if (user != null) {
                tvUserName.setText(user.getName());
                tvInitialWeight.setText(String.format(Locale.getDefault(), "%.1f кг", user.getInitialWeight()));
            }
        });

        viewModel.getTotalMeasurements().observe(this, count -> {
            if (count != null) {
                tvTotalMeasurements.setText(String.valueOf(count));
            }
        });

        viewModel.getAverageWeight().observe(this, avg -> {
            if (avg != null) {
                tvAverageWeight.setText(String.format(Locale.getDefault(), "%.1f кг", avg));
            }
        });

        viewModel.getCurrentWeight().observe(this, weight -> {
            if (weight != null) {
                tvCurrentWeight.setText(String.format(Locale.getDefault(), "%.1f кг", weight));

                // Update total change
                Float initialWeight = viewModel.getInitialWeight().getValue();
                if (initialWeight != null) {
                    float change = weight - initialWeight;
                    String sign = change > 0 ? "+" : "";
                    tvTotalChange.setText(String.format(Locale.getDefault(), "%s%.1f кг", sign, change));

                    // Set color based on change
                    if (change > 0) {
                        tvTotalChange.setTextColor(getResources().getColor(R.color.error));
                    } else if (change < 0) {
                        tvTotalChange.setTextColor(getResources().getColor(R.color.success));
                    } else {
                        tvTotalChange.setTextColor(getResources().getColor(R.color.text_secondary));
                    }
                }
            }
        });

        viewModel.getWeightData().observe(this, measurements -> {
            if (measurements != null && !measurements.isEmpty()) {
                updateWeightChart(measurements);
            } else {
                Toast.makeText(this, "Нет данных для отображения", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getDailyStats().observe(this, dailyStats -> {
            // TODO: Setup adapter for daily stats
        });
    }

    private void setupChart() {
        weightChart.setBackgroundColor(getResources().getColor(R.color.card_background));
        weightChart.getDescription().setEnabled(false);
        weightChart.setTouchEnabled(true);
        weightChart.setDragEnabled(true);
        weightChart.setScaleEnabled(true);
        weightChart.setPinchZoom(true);
        weightChart.setDrawGridBackground(false);
        weightChart.setHighlightPerDragEnabled(true);

        // X Axis
        XAxis xAxis = weightChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(getResources().getColor(R.color.divider));
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(7, true);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat format = new SimpleDateFormat("dd.MM", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_YEAR, (int) value - 6);
                return format.format(calendar.getTime());
            }
        });

        // Y Axis
        YAxis leftAxis = weightChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(getResources().getColor(R.color.divider));
        leftAxis.setGranularity(0.1f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f кг", value);
            }
        });

        weightChart.getAxisRight().setEnabled(false);
        weightChart.getLegend().setEnabled(false);
        weightChart.setExtraOffsets(20f, 20f, 20f, 20f);
    }

    private void updateWeightChart(List<com.example.smartscales.data.models.WeightMeasurement> measurements) {
        List<Entry> entries = new ArrayList<>();

        // Take last 7 measurements
        int count = Math.min(7, measurements.size());
        for (int i = 0; i < count; i++) {
            com.example.smartscales.data.models.WeightMeasurement measurement = measurements.get(i);
            entries.add(new Entry(count - i - 1, measurement.getWeight()));
        }

        if (entries.isEmpty()) {
            return;
        }

        LineDataSet dataSet = new LineDataSet(entries, "Вес");
        dataSet.setColor(getResources().getColor(R.color.primary));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(getResources().getColor(R.color.primary));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(getResources().getColor(R.color.text_primary));
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(getResources().getColor(R.color.primary_light));
        dataSet.setFillAlpha(100);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setCubicIntensity(0.2f);

        LineData lineData = new LineData(dataSet);
        weightChart.setData(lineData);
        weightChart.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (viewModel != null) {
            viewModel.cleanup();
        }
    }
}