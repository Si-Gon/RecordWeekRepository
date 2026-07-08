package com.recordweek.repository;

import com.recordweek.data.AnalyticsData;
import com.recordweek.data.DailyCompletion;
import com.recordweek.data.DailyCompletionDao;
import com.recordweek.data.Task;
import com.recordweek.data.TaskDao;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TaskRepositoryAnalyticsTest {

    @Mock
    TaskDao taskDao;

    @Mock
    DailyCompletionDao completionDao;

    private TaskRepository repository;

    @Before
    public void setUp() {
        repository = new TaskRepository(taskDao, completionDao);
    }

    @Test
    public void loadAnalytics_sinTareas_semanaActual() throws Exception {
        when(taskDao.getAllTasksSync()).thenReturn(Collections.emptyList());
        when(completionDao.getCompletedInRange(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(completionDao.getTotalCompletedAllTime()).thenReturn(0);
        when(completionDao.getDistinctCompletedDatesDesc()).thenReturn(Collections.emptyList());

        CountDownLatch latch = new CountDownLatch(1);
        repository.loadAnalytics(0, data -> {
            assertEquals(0, data.weekScheduled);
            assertEquals(0, data.weekCompleted);
            assertEquals(0, data.weekRatePercent);
            assertEquals(0, data.currentStreak);
            assertEquals(0, data.bestStreak);
            assertEquals(0, data.totalCompletedAllTime);
            assertTrue(data.categoryStats.isEmpty());
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void loadAnalytics_tareaRecurrenteSinCompletar() throws Exception {
        Task task = new Task("Gimnasio", "GREEN", "Salud", "", "[1,2,3,4,5]", 9, 0, 1);
        task.id = 1;
        when(taskDao.getAllTasksSync()).thenReturn(Arrays.asList(task));
        when(completionDao.countCompletedDays(eq(1), anyString(), anyString())).thenReturn(0);
        when(completionDao.getCompletedInRange(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(completionDao.getTotalCompletedAllTime()).thenReturn(0);
        when(completionDao.getDistinctCompletedDatesDesc()).thenReturn(Collections.emptyList());

        CountDownLatch latch = new CountDownLatch(1);
        repository.loadAnalytics(0, data -> {
            assertTrue("Should have some scheduled days", data.weekScheduled > 0);
            assertEquals(0, data.weekCompleted);
            assertEquals(0, data.weekRatePercent);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void loadAnalytics_tareaRecurrenteCompletadaParcial() throws Exception {
        Task task = new Task("Gimnasio", "GREEN", "Salud", "", "[1,2,3]", 9, 0, 1);
        task.id = 1;
        when(taskDao.getAllTasksSync()).thenReturn(Arrays.asList(task));
        when(completionDao.countCompletedDays(eq(1), anyString(), anyString())).thenReturn(1);
        when(completionDao.getCompletedInRange(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(completionDao.getTotalCompletedAllTime()).thenReturn(1);
        when(completionDao.getDistinctCompletedDatesDesc()).thenReturn(Collections.emptyList());

        CountDownLatch latch = new CountDownLatch(1);
        repository.loadAnalytics(0, data -> {
            assertTrue("weekScheduled should be > 0", data.weekScheduled > 0);
            assertTrue("weekCompleted should be <= weekScheduled", data.weekCompleted <= data.weekScheduled);
            assertTrue("weekRatePercent should be >= 0", data.weekRatePercent >= 0);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void loadAnalytics_conOffsetNegativo() throws Exception {
        when(taskDao.getAllTasksSync()).thenReturn(Collections.emptyList());
        when(completionDao.getCompletedInRange(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(completionDao.getTotalCompletedAllTime()).thenReturn(0);
        when(completionDao.getDistinctCompletedDatesDesc()).thenReturn(Collections.emptyList());

        CountDownLatch latch = new CountDownLatch(1);
        repository.loadAnalytics(-1, data -> {
            // Validamos que no explote con offset negativo
            assertEquals(0, data.weekScheduled);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void loadAnalytics_totalCompletedAllTime() throws Exception {
        when(taskDao.getAllTasksSync()).thenReturn(Collections.emptyList());
        when(completionDao.getCompletedInRange(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(completionDao.getTotalCompletedAllTime()).thenReturn(42);
        when(completionDao.getDistinctCompletedDatesDesc()).thenReturn(Collections.emptyList());

        CountDownLatch latch = new CountDownLatch(1);
        repository.loadAnalytics(0, data -> {
            assertEquals(42, data.totalCompletedAllTime);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void loadAnalytics_multipleCategorias() throws Exception {
        Task t1 = new Task("Gimnasio", "GREEN", "Salud", "", "[1,3,5]", 9, 0, 1);
        t1.id = 1;
        Task t2 = new Task("Oficina", "RED", "Trabajo", "", "[2,4]", 10, 0, 1);
        t2.id = 2;
        when(taskDao.getAllTasksSync()).thenReturn(Arrays.asList(t1, t2));
        when(completionDao.countCompletedDays(anyInt(), anyString(), anyString())).thenReturn(0);
        when(completionDao.getCompletedInRange(anyString(), anyString())).thenReturn(Collections.emptyList());
        when(completionDao.getTotalCompletedAllTime()).thenReturn(0);
        when(completionDao.getDistinctCompletedDatesDesc()).thenReturn(Collections.emptyList());

        CountDownLatch latch = new CountDownLatch(1);
        repository.loadAnalytics(0, data -> {
            assertTrue("Should have category entries", data.categoryStats.size() >= 2);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void countScheduledInElapsedWeek_recurrente() {
        Task task = new Task("Test", "GREEN", "Cat", "", "[1,3,5]", 9, 0, 1);
        task.id = 1;
        // Monday=1, Wednesday=3, Friday=5
        String monday = "2026-07-06"; // Monday
        String cutoff = "2026-07-12"; // Sunday
        int count = repository.countScheduledInElapsedWeek(task, monday, cutoff);
        assertEquals(3, count);
    }

    @Test
    public void countScheduledInElapsedWeek_puntualDentro() {
        Task task = new Task("Test", "GREEN", "Cat", "", "[]", 9, 0, 1);
        task.id = 1;
        task.specificDate = "2026-07-08"; // Wednesday (ourDay=3)
        String monday = "2026-07-06"; // Monday
        String cutoff = "2026-07-12"; // Sunday
        int count = repository.countScheduledInElapsedWeek(task, monday, cutoff);
        assertEquals(1, count);
    }

    @Test
    public void countScheduledInElapsedWeek_puntualFuera() {
        Task task = new Task("Test", "GREEN", "Cat", "", "[]", 9, 0, 1);
        task.id = 1;
        task.specificDate = "2026-07-20"; // fuera de la semana
        String monday = "2026-07-06";
        String cutoff = "2026-07-12";
        int count = repository.countScheduledInElapsedWeek(task, monday, cutoff);
        assertEquals(0, count);
    }
}
