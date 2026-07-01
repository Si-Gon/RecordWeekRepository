package com.recordweek.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// ============================================================
//  Datos que necesita la VISTA MENSUAL, calculados de una sola
//  pasada en segundo plano (igual que AnalyticsData). La Activity
//  solo lee este objeto ya listo y pinta; no toca la BD.
// ============================================================
public class MonthData {

    // Tareas ACTIVAS (recurrentes). La agenda de cada dia se arma filtrando
    // estas por dia de semana. No guardamos instancias por fecha porque el
    // modelo es semanal: una tarea "existe" en varios dias de la semana.
    public List<Task> activeTasks = new ArrayList<>();

    // Claves "taskId|yyyy-MM-dd" de las completaciones MARCADAS dentro del mes.
    // Usamos un Set para poder preguntar en O(1) "se completo la tarea X el dia Y?"
    // al pintar cada tarjeta de la agenda (nodo lleno = hecha, hueco = pendiente).
    public Set<String> completedKeys = new HashSet<>();

    // Resumen del mes HASTA HOY: cuantas tareas cuadraban (denominador),
    // cuantas se cumplieron (numerador) y el porcentaje redondeado. Se muestra
    // en el subtitulo "Este mes · NN% cumplido".
    public int monthScheduled = 0;
    public int monthCompleted = 0;
    public int monthRatePercent = 0;

    // Helper para construir la clave del Set en un solo sitio y no arriesgarnos
    // a formatearla distinto en el repositorio y en la Activity.
    public static String key(int taskId, String date) {
        return taskId + "|" + date;
    }
}
