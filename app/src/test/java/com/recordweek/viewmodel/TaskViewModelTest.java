package com.recordweek.viewmodel;

import android.app.Application;

import com.recordweek.data.AnalyticsData;
import com.recordweek.data.DailyCompletion;
import com.recordweek.data.MonthData;
import com.recordweek.data.Task;
import com.recordweek.repository.TaskRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TaskViewModelTest {

    @Mock
    Application application;

    @Mock
    TaskRepository repository;

    private TaskViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new TaskViewModel(application, repository);
    }

    @Test
    public void loadAnalytics_emiteDatos() throws Exception {
        AnalyticsData data = new AnalyticsData();
        data.weekScheduled = 5;
        data.weekCompleted = 3;
        data.weekRatePercent = 60;

        doAnswer(invocation -> {
            TaskRepository.OnAnalyticsLoadedCallback cb = invocation.getArgument(1);
            cb.onLoaded(data);
            return null;
        }).when(repository).loadAnalytics(eq(0), any());

        viewModel.loadAnalytics(0);

        // Pequeña pausa para que postValue se ejecute
        Thread.sleep(100);
        AnalyticsData result = viewModel.getAnalyticsData().getValue();
        assertEquals(60, result.weekRatePercent);
        assertEquals(5, result.weekScheduled);
    }

    @Test
    public void loadAnalytics_loadingStates() throws Exception {
        doAnswer(invocation -> {
            TaskRepository.OnAnalyticsLoadedCallback cb = invocation.getArgument(1);
            cb.onLoaded(new AnalyticsData());
            return null;
        }).when(repository).loadAnalytics(anyInt(), any());

        assertEquals(false, viewModel.getIsLoading().getValue());

        viewModel.loadAnalytics(0);
        assertEquals(true, viewModel.getIsLoading().getValue());

        Thread.sleep(100);
        assertEquals(false, viewModel.getIsLoading().getValue());
    }

    @Test
    public void loadAnalytics_conOffset_delega() throws Exception {
        doAnswer(invocation -> {
            TaskRepository.OnAnalyticsLoadedCallback cb = invocation.getArgument(1);
            cb.onLoaded(new AnalyticsData());
            return null;
        }).when(repository).loadAnalytics(anyInt(), any());

        viewModel.loadAnalytics(-2);
        verify(repository).loadAnalytics(eq(-2), any());
    }

    @Test
    public void loadMonth_delega() {
        viewModel.loadMonth(2026, 6, data -> {});
        verify(repository).loadMonthData(eq(2026), eq(6), any());
    }

    @Test
    public void setCompletion_creaDailyCompletion() {
        viewModel.setCompletion(12, "2026-07-18", true);
        verify(repository).upsertCompletion(argThat(dc ->
                dc.taskId == 12 &&
                dc.date.equals("2026-07-18") &&
                dc.completed == 1
        ));
    }

    @Test
    public void setCompletion_false() {
        viewModel.setCompletion(12, "2026-07-18", false);
        verify(repository).upsertCompletion(argThat(dc -> dc.completed == 0));
    }

    @Test
    public void setSelectedDay_actualizaLiveData() {
        viewModel.setSelectedDay(3);
        assertEquals(Integer.valueOf(3), viewModel.getSelectedDay().getValue());
    }

    @Test
    public void deleteAllCompletions_delega() {
        viewModel.deleteAllCompletions();
        verify(repository).deleteAllCompletions();
    }

    @Test
    public void exportData_delega() {
        viewModel.exportData(json -> {});
        verify(repository).exportDataAsJson(any());
    }

    @Test
    public void importData_delega() {
        viewModel.importData("{}", (s, m, t, c) -> {});
        verify(repository).importDataFromJson(eq("{}"), any());
    }
}
