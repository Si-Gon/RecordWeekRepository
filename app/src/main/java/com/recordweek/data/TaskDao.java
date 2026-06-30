package com.recordweek.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TaskDao {
    @Insert
    long insert(Task task);
    @Update
    void update(Task task);
    @Delete
    void delete(Task task);
    @Query("SELECT * FROM tasks ORDER BY name ASC")
    LiveData<List<Task>> getAllTasks();
    @Query("SELECT * FROM tasks WHERE is_active = 1 ORDER BY notification_hour ASC, notification_minute ASC")
    LiveData<List<Task>> getActiveTasks();
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    Task getTaskById(int taskId);
    @Query("SELECT * FROM tasks")
    List<Task> getAllTasksSync();
    @Query("SELECT * FROM tasks WHERE is_active = 1")
    List<Task> getActiveTasksSync();

    // Borra TODAS las tareas. Usado por la importacion con estrategia "reemplazar
    // todo": vaciamos primero y luego cargamos las del archivo. Por el ForeignKey
    // CASCADE de daily_completions, al borrar las tareas se borran tambien sus
    // completaciones automaticamente.
    @Query("DELETE FROM tasks")
    void deleteAll();
}
