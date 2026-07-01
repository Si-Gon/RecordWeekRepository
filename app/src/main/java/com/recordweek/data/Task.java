package com.recordweek.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import org.json.JSONArray;

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

    // Fecha de una tarea PUNTUAL (formato "yyyy-MM-dd). Es ANULABLE a proposito:
    //  - null / vacio  -> tarea RECURRENTE: ocurre cada semana en los dias de daysOfWeek.
    //  - "2026-07-18"  -> tarea PUNTUAL: ocurre UNA sola vez, ese dia exacto.
    // Se añadio en la version 4 de la BD; las tareas viejas quedan con null (=recurrentes).
    @ColumnInfo(name = "specific_date")
    public String specificDate;

    public Task() {}

    // Constructor de tareas RECURRENTES (specificDate queda null por defecto). Se
    // mantiene igual para no tocar la importacion ni el codigo que ya lo usa.
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

    // ¿Es una tarea puntual (ocurre una sola vez en una fecha) en vez de recurrente?
    public boolean isOneOff() {
        return specificDate != null && !specificDate.isEmpty();
    }

    // UNICO punto que decide si esta tarea "ocurre" en un dia dado. Antes esta
    // logica estaba copiada en varias pantallas; al centralizarla aqui, todas las
    // vistas (Diario, Mensual, Analisis) tratan igual a recurrentes y puntuales.
    //   date   -> la fecha de ese dia en formato "yyyy-MM-dd"
    //   ourDay -> su dia de semana ya calculado (Lunes=1 .. Domingo=7)
    // Regla:
    //   - Puntual: ocurre solo si la fecha coincide EXACTAMENTE con specificDate.
    //   - Recurrente: ocurre si su lista daysOfWeek incluye ese dia de semana.
    public boolean occursOn(String date, int ourDay) {
        if (isOneOff()) {
            return specificDate.equals(date);
        }
        if (daysOfWeek == null || daysOfWeek.isEmpty()) return false;
        try {
            JSONArray arr = new JSONArray(daysOfWeek);
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getInt(i) == ourDay) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
