package com.recordweek.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface DailyCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DailyCompletion completion);
    @Query("SELECT COUNT(*) FROM daily_completions WHERE task_id = :taskId AND date >= :startDate AND date <= :endDate AND completed = 1")
    int countCompletedDays(int taskId, String startDate, String endDate);
    @Query("DELETE FROM daily_completions")
    void deleteAll();
    @Query("SELECT * FROM daily_completions WHERE date = :date")
    LiveData<List<DailyCompletion>> getCompletionsByDate(String date);

    // --- Queries para Analytics (lectura directa del historial) ---

    // Trae TODAS las completaciones marcadas (completed = 1) dentro de un rango de
    // fechas, de forma sincrona (List, no LiveData) porque Analytics las procesa
    // de una sola vez en un hilo de fondo. Con esto agrupamos por dia y por
    // categoria en Java, cruzando task_id con la lista de tareas.
    @Query("SELECT * FROM daily_completions WHERE date >= :startDate AND date <= :endDate AND completed = 1")
    List<DailyCompletion> getCompletedInRange(String startDate, String endDate);

    // Contador historico total: cuantas veces se ha completado CUALQUIER tarea en
    // toda la vida de la app. Es la metrica "X tareas completadas desde siempre".
    @Query("SELECT COUNT(*) FROM daily_completions WHERE completed = 1")
    int getTotalCompletedAllTime();

    // Fechas DISTINTAS en las que se completo al menos una tarea, de la mas
    // reciente a la mas antigua. Sirve para calcular la racha: recorremos esta
    // lista hacia atras contando dias consecutivos. DISTINCT evita contar el
    // mismo dia dos veces si ese dia se completaron varias tareas.
    @Query("SELECT DISTINCT date FROM daily_completions WHERE completed = 1 ORDER BY date DESC")
    List<String> getDistinctCompletedDatesDesc();

    // Todas las completaciones, de forma sincrona. Usado para exportar a JSON.
    @Query("SELECT * FROM daily_completions ORDER BY date ASC")
    List<DailyCompletion> getAllSync();
}
