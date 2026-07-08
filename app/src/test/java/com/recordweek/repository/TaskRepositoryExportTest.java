package com.recordweek.repository;

import com.recordweek.data.DailyCompletion;
import com.recordweek.data.DailyCompletionDao;
import com.recordweek.data.Task;
import com.recordweek.data.TaskDao;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TaskRepositoryExportTest {

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
    public void exportDataAsJson_incluyeTasksYCompletions() throws Exception {
        Task task = new Task("Gimnasio", "GREEN", "Salud", "Rutina A", "[1,3,5]", 9, 0, 1);
        task.id = 1;
        when(taskDao.getAllTasksSync()).thenReturn(Arrays.asList(task));

        DailyCompletion dc = new DailyCompletion(1, "2026-07-08", 1);
        when(completionDao.getAllSync()).thenReturn(Arrays.asList(dc));

        CountDownLatch latch = new CountDownLatch(1);
        repository.exportDataAsJson(json -> {
            try {
                JSONObject root = new JSONObject(json);
                assertTrue(root.has("tasks"));
                assertTrue(root.has("completions"));
                assertTrue(root.has("exportedAt"));

                JSONArray tasksArr = root.getJSONArray("tasks");
                assertEquals(1, tasksArr.length());
                assertEquals("Gimnasio", tasksArr.getJSONObject(0).getString("name"));

                JSONArray compArr = root.getJSONArray("completions");
                assertEquals(1, compArr.length());
                assertEquals(1, compArr.getJSONObject(0).getInt("taskId"));
            } catch (Exception e) {
                fail("JSON parse error: " + e.getMessage());
            }
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void exportDataAsJson_tareaPuntual_incluyeSpecificDate() throws Exception {
        Task task = new Task("Cita", "BLUE", "Personal", "", "[]", 14, 30, 1);
        task.id = 2;
        task.specificDate = "2026-07-18";
        when(taskDao.getAllTasksSync()).thenReturn(Arrays.asList(task));
        when(completionDao.getAllSync()).thenReturn(Collections.emptyList());

        CountDownLatch latch = new CountDownLatch(1);
        repository.exportDataAsJson(json -> {
            try {
                JSONObject root = new JSONObject(json);
                JSONArray tasksArr = root.getJSONArray("tasks");
                JSONObject taskObj = tasksArr.getJSONObject(0);
                assertTrue(taskObj.has("specificDate"));
                assertEquals("2026-07-18", taskObj.getString("specificDate"));
            } catch (Exception e) {
                fail("JSON parse error: " + e.getMessage());
            }
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void exportDataAsJson_errorHandler() throws Exception {
        when(taskDao.getAllTasksSync()).thenThrow(new RuntimeException("DB error"));

        CountDownLatch latch = new CountDownLatch(1);
        repository.exportDataAsJson(json -> {
            assertTrue(json.contains("error"));
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
    }
}
