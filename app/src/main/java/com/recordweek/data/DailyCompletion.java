package com.recordweek.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "daily_completions",
    foreignKeys = @ForeignKey(
        entity = Task.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index("task_id"),
        // Indice UNICO en (task_id, date): garantiza que solo exista UNA fila por
        // tarea y dia. Asi el insert con OnConflictStrategy.REPLACE actualiza esa
        // fila en vez de crear duplicados cada vez que marcas/desmarcas.
        @Index(value = {"task_id", "date"}, unique = true)
    }
)
public class DailyCompletion {
    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "task_id")
    public int taskId;
    @ColumnInfo(name = "date")
    public String date;
    @ColumnInfo(name = "completed")
    public int completed;

    public DailyCompletion() {}

    public DailyCompletion(int taskId, String date, int completed) {
        this.taskId = taskId;
        this.date = date;
        this.completed = completed;
    }
}
