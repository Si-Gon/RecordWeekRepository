package com.recordweek.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "weekly_analysis",
    foreignKeys = @ForeignKey(
        entity = Task.class,
        parentColumns = "id",
        childColumns = "task_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("task_id")}
)
public class WeeklyAnalysis {
    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "task_id")
    public int taskId;
    @ColumnInfo(name = "week_number")
    public int weekNumber;
    @ColumnInfo(name = "year")
    public int year;
    @ColumnInfo(name = "days_scheduled")
    public int daysScheduled;
    @ColumnInfo(name = "days_completed")
    public int daysCompleted;

    public WeeklyAnalysis() {}

    public WeeklyAnalysis(int taskId, int weekNumber, int year, int daysScheduled, int daysCompleted) {
        this.taskId = taskId;
        this.weekNumber = weekNumber;
        this.year = year;
        this.daysScheduled = daysScheduled;
        this.daysCompleted = daysCompleted;
    }
}
