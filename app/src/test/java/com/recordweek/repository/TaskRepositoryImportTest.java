package com.recordweek.repository;

import com.recordweek.data.DailyCompletion;
import com.recordweek.data.DailyCompletionDao;
import com.recordweek.data.Task;
import com.recordweek.data.TaskDao;

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
public class TaskRepositoryImportTest {

    @Mock
    TaskDao taskDao;

    @Mock
    DailyCompletionDao completionDao;

    private TaskRepository repository;

    @Before
    public void setUp() {
        repository = new TaskRepository(taskDao, completionDao);
        when(taskDao.insert(any(Task.class))).thenReturn(10L, 11L);
    }

    @Test
    public void importDataFromJson_valido() throws Exception {
        String json = "{ \"tasks\": [{\"id\":1,\"name\":\"Gym\",\"color\":\"GREEN\",\"category\":\"Salud\",\"description\":\"\",\"daysOfWeek\":\"[1,3,5]\",\"hour\":9,\"minute\":0,\"active\":1}], \"completions\": [{\"taskId\":1,\"date\":\"2026-07-08\",\"completed\":1}] }";

        CountDownLatch latch = new CountDownLatch(1);
        repository.importDataFromJson(json, (success, message, tasks, comps) -> {
            assertTrue("Import should succeed", success);
            assertEquals(1, tasks);
            assertEquals(1, comps);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
        verify(taskDao).deleteAll();
        verify(taskDao, times(1)).insert(any(Task.class));
        verify(completionDao, times(1)).insert(any(DailyCompletion.class));
    }

    @Test
    public void importDataFromJson_sinTasks_devuelveError() throws Exception {
        String json = "{}";

        CountDownLatch latch = new CountDownLatch(1);
        repository.importDataFromJson(json, (success, message, tasks, comps) -> {
            assertFalse("Import should fail without tasks", success);
            assertEquals(0, tasks);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
        verify(taskDao, never()).deleteAll();
    }

    @Test
    public void importDataFromJson_completacionHuerfana_ignorada() throws Exception {
        String json = "{ \"tasks\": [{\"id\":1,\"name\":\"Gym\",\"color\":\"GREEN\",\"category\":\"Salud\",\"description\":\"\",\"daysOfWeek\":\"[1,3,5]\",\"hour\":9,\"minute\":0,\"active\":1}], \"completions\": [{\"taskId\":999,\"date\":\"2026-07-08\",\"completed\":1}] }";

        when(taskDao.insert(any(Task.class))).thenReturn(10L);

        CountDownLatch latch = new CountDownLatch(1);
        repository.importDataFromJson(json, (success, message, tasks, comps) -> {
            assertTrue("Import should succeed", success);
            assertEquals(1, tasks);
            assertEquals(0, comps); // huerfana ignorada
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
        verify(completionDao, never()).insert(any(DailyCompletion.class));
    }

    @Test
    public void importDataFromJson_mapaIds_traduceTaskId() throws Exception {
        String json = "{ \"tasks\": [{\"id\":5,\"name\":\"Gym\",\"color\":\"GREEN\",\"category\":\"Salud\",\"description\":\"\",\"daysOfWeek\":\"[1,3,5]\",\"hour\":9,\"minute\":0,\"active\":1}], \"completions\": [{\"taskId\":5,\"date\":\"2026-07-08\",\"completed\":1}] }";

        CountDownLatch latch = new CountDownLatch(1);
        repository.importDataFromJson(json, (success, message, tasks, comps) -> {
            assertTrue("Import should succeed", success);
            assertEquals(1, comps);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
        verify(completionDao, times(1)).insert(argThat(dc -> dc.taskId == 10));
    }

    @Test
    public void importDataFromJson_tareaPuntual_conSpecificDate() throws Exception {
        String json = "{\"tasks\":[{\"id\":1,\"name\":\"Cita\",\"color\":\"BLUE\",\"category\":\"Personal\",\"description\":\"\",\"daysOfWeek\":\"[]\",\"specificDate\":\"2026-07-18\",\"hour\":14,\"minute\":30,\"active\":1}],\"completions\":[]}";

        CountDownLatch latch = new CountDownLatch(1);
        repository.importDataFromJson(json, (success, message, tasks, comps) -> {
            assertTrue(success);
            assertEquals(1, tasks);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
        verify(taskDao).insert(argThat(t -> t.specificDate != null && t.specificDate.equals("2026-07-18")));
    }

    @Test
    public void importDataFromJson_backupAntiguoSinSpecificDate() throws Exception {
        String json = "{\"tasks\":[{\"id\":1,\"name\":\"Gym\",\"color\":\"GREEN\",\"category\":\"Salud\",\"description\":\"\",\"daysOfWeek\":\"[1,3,5]\",\"hour\":9,\"minute\":0,\"active\":1}],\"completions\":[]}";

        CountDownLatch latch = new CountDownLatch(1);
        repository.importDataFromJson(json, (success, message, tasks, comps) -> {
            assertTrue(success);
            latch.countDown();
        });

        assertTrue("Callback timed out", latch.await(5, TimeUnit.SECONDS));
        verify(taskDao).insert(argThat(t -> t.specificDate == null));
    }
}
