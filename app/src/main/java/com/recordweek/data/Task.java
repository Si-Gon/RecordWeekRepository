package com.recordweek.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true)
    public int id;
    @ColumnInfo(name = "name")
    public String name;
    @ColumnInfo(name = "color")
    public String color;
    @ColumnInfo(name = "category")
    public String category;
    @ColumnInfo(name = "description")
    public String description;
    @ColumnInfo(name = "days_of_week")
    public String daysOfWeek;
    @ColumnInfo(name = "notification_hour")
    public int notificationHour;
    @ColumnInfo(name = "notification_minute")
    public int notificationMinute;
    @ColumnInfo(name = "is_active")
    public int isActive;

    public Task() {}

    public Task(String name, String color, String category, String description,
                String daysOfWeek, int notificationHour, int notificationMinute, int isActive) {
        this.name = name;
        this.color = color;
        this.category = category;
        this.description = description;
        this.daysOfWeek = daysOfWeek;
        this.notificationHour = notificationHour;
        this.notificationMinute = notificationMinute;
        this.isActive = isActive;
    }
}
