package com.recordweek;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.recordweek.data.AnalyticsData;
import com.recordweek.utils.DateUtils;
import com.recordweek.viewmodel.TaskViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// ============================================================
//  VISTA ANALISIS como Fragment (antes AnalyticsActivity).
//  Cambios por ser Fragment: arranque en onViewCreated, widgets con
//  root.findViewById, observe con getViewLifecycleOwner(). La logica de metricas
//  y del grafico es identica a la Activity.
// ============================================================
public class AnalyticsFragment extends Fragment {
    private TaskViewModel viewModel;
    private View root;
    private BarChart barChart;
    private TextView tvWeekRange, tvWeekRate, tvWeekDetail, tvStreak, tvBestStreak, tvTotalCompleted, tvNoCategories;
    private TextView btnWeekPrev, btnWeekNext;
    private LinearLayout containerCategories;

    // Semana mostrada: 0 = actual, -1 = pasada, -2 = antepasada... Nunca positivo
    // (no miramos el futuro). Es el "estado" de la pantalla de Analisis.
    private int currentWeekOffset = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analytics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        barChart = root.findViewById(R.id.bar_chart);
        tvWeekRange = root.findViewById(R.id.tv_week_range);
        tvWeekRate = root.findViewById(R.id.tv_week_rate);
        tvWeekDetail = root.findViewById(R.id.tv_week_detail);
        tvStreak = root.findViewById(R.id.tv_streak);
        tvBestStreak = root.findViewById(R.id.tv_best_streak);
        tvTotalCompleted = root.findViewById(R.id.tv_total_completed);
        tvNoCategories = root.findViewById(R.id.tv_no_categories);
        containerCategories = root.findViewById(R.id.container_categories);
        btnWeekPrev = root.findViewById(R.id.btn_week_prev);
        btnWeekNext = root.findViewById(R.id.btn_week_next);

        setupWeekNavigation();
        setupBarChart();
        observeAnalytics();
        showWeek(0); // arranca en la semana actual
    }

    // Conecta las flechas. ‹ retrocede una semana; › avanza, pero nunca pasa de 0.
    private void setupWeekNavigation() {
        btnWeekPrev.setOnClickListener(v -> showWeek(currentWeekOffset - 1));
        btnWeekNext.setOnClickListener(v -> {
            if (currentWeekOffset < 0) showWeek(currentWeekOffset + 1);
        });
    }

    // Cambia a la semana indicada: actualiza el estado, el texto del rango, el
    // aspecto de la flecha "siguiente" (atenuada si ya estamos en la actual) y
    // pide al ViewModel recalcular las metricas de esa semana.
    private void showWeek(int weekOffset) {
        if (weekOffset > 0) weekOffset = 0; // seguro: nunca futuro
        currentWeekOffset = weekOffset;

        String prefix = weekOffset == 0 ? "Esta semana" :
                        (weekOffset == -1 ? "Semana pasada" : "Hace " + (-weekOffset) + " semanas");
        tvWeekRange.setText(prefix + "  ·  " + DateUtils.getWeekRangeLabel(weekOffset));

        // La flecha "siguiente" no tiene sentido en la semana actual: la atenuamos
        // y dejamos de responder (el propio listener ya bloquea offset > 0).
        boolean canGoNext = weekOffset < 0;
        btnWeekNext.setEnabled(canGoNext);
        btnWeekNext.setAlpha(canGoNext ? 1.0f : 0.3f);

        viewModel.loadAnalytics(weekOffset);
    }

    private void setupBarChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setScaleEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setBackgroundColor(Color.TRANSPARENT);
        barChart.getLegend().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#5B6B8C"));
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(
            new String[]{"L", "M", "X", "J", "V", "S", "D"}));

        barChart.getAxisLeft().setTextColor(Color.parseColor("#5B6B8C"));
        barChart.getAxisLeft().setAxisMinimum(0f);
        barChart.getAxisLeft().setGranularity(1f);
        barChart.getAxisLeft().setGridColor(Color.parseColor("#161E30"));
        barChart.getAxisRight().setEnabled(false);
    }

    private void observeAnalytics() {
        viewModel.getAnalyticsData().observe(getViewLifecycleOwner(), data -> {
            if (data == null) return;
            bindSummary(data);
            updateBarChart(data);
            updateCategories(data);
        });
    }

    private void bindSummary(AnalyticsData data) {
        tvWeekRate.setText(data.weekRatePercent + "%");
        tvWeekDetail.setText(data.weekCompleted + " de " + data.weekScheduled + " tareas");
        tvStreak.setText(data.currentStreak + (data.currentStreak == 1 ? " dia" : " dias"));
        tvBestStreak.setText("Record: " + data.bestStreak + (data.bestStreak == 1 ? " dia" : " dias"));
        tvTotalCompleted.setText(String.valueOf(data.totalCompletedAllTime));
    }

    private void updateBarChart(AnalyticsData data) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            entries.add(new BarEntry(i, data.completionsPerWeekday[i]));
        }
        BarDataSet dataSet = new BarDataSet(entries, "Actividad");
        dataSet.setColor(Color.parseColor("#E0922F"));
        dataSet.setValueTextColor(Color.parseColor("#EAF0FA"));
        dataSet.setValueTextSize(10f);
        // Mostrar el valor como entero (sin decimales).
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return String.valueOf((int) barEntry.getY());
            }
        });
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);
        barChart.setData(barData);
        barChart.setFitBars(true);
        barChart.invalidate();
    }

    private void updateCategories(AnalyticsData data) {
        containerCategories.removeAllViews();
        if (data.categoryStats.isEmpty()) {
            tvNoCategories.setVisibility(View.VISIBLE);
            return;
        }
        tvNoCategories.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Map.Entry<String, int[]> entry : data.categoryStats.entrySet()) {
            int scheduled = entry.getValue()[0];
            int completed = entry.getValue()[1];
            int percent = scheduled > 0 ? Math.round(completed * 100f / scheduled) : 0;

            View row = inflater.inflate(R.layout.item_category_stat, containerCategories, false);
            TextView name = row.findViewById(R.id.tv_cat_name);
            TextView pct = row.findViewById(R.id.tv_cat_percent);
            ProgressBar bar = row.findViewById(R.id.progress_cat);
            name.setText(entry.getKey());
            pct.setText(percent + "%");
            bar.setProgress(percent);
            containerCategories.addView(row);
        }
    }
}
