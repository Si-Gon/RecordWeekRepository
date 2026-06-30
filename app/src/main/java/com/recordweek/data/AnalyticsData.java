package com.recordweek.data;

import java.util.LinkedHashMap;
import java.util.Map;

// POJO contenedor con TODAS las metricas de Analytics ya calculadas.
// El Repository lo construye en un hilo de fondo y la Activity solo lo pinta.
// La idea: un unico objeto coherente en vez de muchos callbacks sueltos.
public class AnalyticsData {

    // --- Cumplimiento semanal (el "punto general") ---
    public int weekScheduled;   // tareas que tocaban de lunes a HOY
    public int weekCompleted;   // de esas, cuantas se marcaron
    public int weekRatePercent; // weekCompleted * 100 / weekScheduled (0 si no tocaba nada)

    // --- Racha ---
    public int currentStreak;   // dias consecutivos (hasta hoy o ayer) con actividad
    public int bestStreak;      // mejor racha historica

    // --- Total historico ---
    public int totalCompletedAllTime;

    // --- Actividad por dia de la semana actual (indice 0 = Lunes ... 6 = Domingo) ---
    // Guarda cuantas tareas se completaron cada dia. Los dias futuros quedan en 0.
    public int[] completionsPerWeekday = new int[7];

    // --- Cumplimiento por categoria ---
    // Clave: nombre de categoria. Valor: [tocaban, completadas].
    // LinkedHashMap para conservar un orden estable al pintar las barras.
    public Map<String, int[]> categoryStats = new LinkedHashMap<>();

    public boolean isEmpty() {
        return totalCompletedAllTime == 0 && weekScheduled == 0;
    }
}
