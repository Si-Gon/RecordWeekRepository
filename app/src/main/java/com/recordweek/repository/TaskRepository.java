package com.recordweek.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.recordweek.data.AnalyticsData;
import com.recordweek.data.AppDatabase;
import com.recordweek.data.DailyCompletion;
import com.recordweek.data.DailyCompletionDao;
import com.recordweek.data.Task;
import com.recordweek.data.TaskDao;
import com.recordweek.data.WeeklyAnalysis;
import com.recordweek.data.WeeklyAnalysisDao;
import com.recordweek.utils.DateUtils;
import org.json.JSONArray;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskRepository {
    private final TaskDao taskDao;
    private final DailyCompletionDao completionDao;
    private final WeeklyAnalysisDao analysisDao;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final LiveData<List<Task>> allTasks;
    private final LiveData<List<Task>> activeTasks;

    public TaskRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        taskDao = db.taskDao();
        completionDao = db.dailyCompletionDao();
        analysisDao = db.weeklyAnalysisDao();
        allTasks = taskDao.getAllTasks();
        activeTasks = taskDao.getActiveTasks();
    }

    public LiveData<List<Task>> getAllTasks() { return allTasks; }
    public LiveData<List<Task>> getActiveTasks() { return activeTasks; }

    public void insertTask(Task task, OnTaskInsertedCallback callback) {
        executor.execute(() -> {
            long newId = taskDao.insert(task);
            task.id = (int) newId;
            if (callback != null) callback.onInserted(task);
        });
    }

    public void updateTask(Task task) { executor.execute(() -> taskDao.update(task)); }
    public void deleteTask(Task task) { executor.execute(() -> taskDao.delete(task)); }

    public void getTaskById(int taskId, OnTaskLoadedCallback callback) {
        executor.execute(() -> {
            Task task = taskDao.getTaskById(taskId);
            if (callback != null) callback.onLoaded(task);
        });
    }

    public List<Task> getActiveTasksSync() { return taskDao.getActiveTasksSync(); }

    public void upsertCompletion(DailyCompletion completion) {
        executor.execute(() -> completionDao.insert(completion));
    }

    public LiveData<DailyCompletion> getCompletionLive(int taskId, String date) {
        return completionDao.getCompletionLive(taskId, date);
    }

    public LiveData<List<DailyCompletion>> getCompletionsByDate(String date) {
        return completionDao.getCompletionsByDate(date);
    }

    public void countCompletedDays(int taskId, String startDate, String endDate, OnCountCallback callback) {
        executor.execute(() -> {
            int count = completionDao.countCompletedDays(taskId, startDate, endDate);
            if (callback != null) callback.onCount(count);
        });
    }

    public void deleteAllCompletions() { executor.execute(completionDao::deleteAll); }

    public void insertAnalysis(WeeklyAnalysis analysis) {
        executor.execute(() -> {
            int exists = analysisDao.existsEntry(analysis.taskId, analysis.weekNumber, analysis.year);
            if (exists == 0) analysisDao.insert(analysis);
        });
    }

    public void getAnalysisByYear(int year, OnAnalysisLoadedCallback callback) {
        executor.execute(() -> {
            List<WeeklyAnalysis> list = analysisDao.getByYearForAnalysis(year);
            if (callback != null) callback.onLoaded(list);
        });
    }

    public void getAnalysisByTask(int taskId, OnAnalysisLoadedCallback callback) {
        executor.execute(() -> {
            List<WeeklyAnalysis> list = analysisDao.getByTaskDescending(taskId);
            if (callback != null) callback.onLoaded(list);
        });
    }

    // ============================================================
    //  ANALYTICS: calcula todas las metricas de una sola pasada
    //  leyendo directamente del historial de completaciones.
    //  Se ejecuta en un hilo de fondo (executor) porque toca la BD.
    // ============================================================
    // Compatibilidad: la llamada sin offset equivale a "semana actual" (offset 0).
    public void loadAnalytics(OnAnalyticsLoadedCallback callback) {
        loadAnalytics(0, callback);
    }

    public void loadAnalytics(int weekOffset, OnAnalyticsLoadedCallback callback) {
        executor.execute(() -> {
            AnalyticsData data = new AnalyticsData();

            String monday = DateUtils.getMondayForOffset(weekOffset);
            String sunday = DateUtils.getSundayForOffset(weekOffset);

            // Punto de corte para contar dias "exigibles":
            //  - Semana actual (offset 0): hasta HOY (los dias futuros no cuentan aun).
            //  - Semana pasada (offset < 0): la semana ya termino, asi que cuentan los
            //    7 dias; el corte es el domingo de esa semana y el dia tope es 7.
            boolean isCurrentWeek = (weekOffset == 0);
            String cutoffDate = isCurrentWeek ? DateUtils.getTodayString() : sunday;
            int cutoffDay = isCurrentWeek ? DateUtils.getTodayDayOfWeek() : 7; // Lunes=1..Domingo=7

            // Todas las tareas (no solo activas): una tarea pudo desactivarse pero
            // sus completaciones siguen siendo parte del historial.
            List<Task> tasks = taskDao.getAllTasksSync();

            // --- 1) Cumplimiento semanal + por categoria ---
            // Para cada tarea contamos cuantos de sus dias programados caen dentro
            // del corte (denominador) y cuantos se completaron (numerador).
            for (Task task : tasks) {
                int scheduledSoFar = countScheduledDaysUpTo(task.daysOfWeek, cutoffDay);
                if (scheduledSoFar == 0) continue; // esta tarea no tocaba en ese tramo
                int completed = completionDao.countCompletedDays(task.id, monday, cutoffDate);
                if (completed > scheduledSoFar) completed = scheduledSoFar; // tope de seguridad

                data.weekScheduled += scheduledSoFar;
                data.weekCompleted += completed;

                String cat = (task.category == null || task.category.isEmpty()) ? "Sin categoria" : task.category;
                int[] stats = data.categoryStats.containsKey(cat) ? data.categoryStats.get(cat) : new int[]{0, 0};
                stats[0] += scheduledSoFar;
                stats[1] += completed;
                data.categoryStats.put(cat, stats);
            }
            data.weekRatePercent = data.weekScheduled > 0
                ? Math.round(data.weekCompleted * 100f / data.weekScheduled) : 0;

            // --- 2) Actividad por dia de la semana elegida ---
            // Recorremos las completaciones de esa semana y, segun la fecha de
            // cada una, sumamos 1 al contador del dia correspondiente.
            List<DailyCompletion> weekCompletions = completionDao.getCompletedInRange(monday, sunday);
            for (DailyCompletion dc : weekCompletions) {
                int ourDay = DateUtils.dateStringToOurDay(dc.date); // 1..7
                if (ourDay >= 1 && ourDay <= 7) {
                    data.completionsPerWeekday[ourDay - 1]++;
                }
            }

            // --- 3) Total historico (global, no depende de la semana mostrada) ---
            data.totalCompletedAllTime = completionDao.getTotalCompletedAllTime();

            // --- 4) Rachas (actual y mejor; tambien globales) ---
            List<String> dates = completionDao.getDistinctCompletedDatesDesc();
            computeStreaks(dates, data);

            if (callback != null) callback.onLoaded(data);
        });
    }

    // Cuenta cuantos dias programados de la tarea (su JSON daysOfWeek) son <= hoy.
    // Asi el denominador del cumplimiento solo incluye dias ya pasados esta semana.
    private int countScheduledDaysUpTo(String daysOfWeekJson, int todayDay) {
        if (daysOfWeekJson == null || daysOfWeekJson.isEmpty()) return 0;
        try {
            JSONArray arr = new JSONArray(daysOfWeekJson);
            int count = 0;
            for (int i = 0; i < arr.length(); i++) {
                int d = arr.getInt(i);
                if (d >= 1 && d <= todayDay) count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    // Calcula racha actual y mejor racha a partir de las fechas (desc) con actividad.
    // Racha actual: dias consecutivos terminando en hoy o ayer (si aun no marcas hoy,
    // la racha de ayer sigue "viva"). Mejor racha: el tramo consecutivo mas largo.
    private void computeStreaks(List<String> datesDesc, AnalyticsData data) {
        if (datesDesc == null || datesDesc.isEmpty()) {
            data.currentStreak = 0;
            data.bestStreak = 0;
            return;
        }
        Set<String> dateSet = new HashSet<>(datesDesc);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        // Racha actual: empezamos en hoy; si hoy no hay, probamos ayer.
        int current = 0;
        Calendar cursor = Calendar.getInstance();
        String todayStr = fmt.format(cursor.getTime());
        if (!dateSet.contains(todayStr)) {
            cursor.add(Calendar.DAY_OF_YEAR, -1); // si hoy no, arrancamos desde ayer
        }
        while (dateSet.contains(fmt.format(cursor.getTime()))) {
            current++;
            cursor.add(Calendar.DAY_OF_YEAR, -1);
        }
        data.currentStreak = current;

        // Mejor racha: recorremos todas las fechas distintas ordenadas y medimos
        // el tramo consecutivo mas largo. datesDesc viene de mas reciente a mas
        // antigua; comparamos cada fecha con la anterior para ver si son contiguas.
        int best = 1, run = 1;
        try {
            for (int i = 1; i < datesDesc.size(); i++) {
                Calendar prev = Calendar.getInstance();
                prev.setTime(fmt.parse(datesDesc.get(i - 1)));
                Calendar curr = Calendar.getInstance();
                curr.setTime(fmt.parse(datesDesc.get(i)));
                prev.add(Calendar.DAY_OF_YEAR, -1); // la anterior menos un dia
                if (sameDay(prev, curr)) {
                    run++;
                } else {
                    run = 1;
                }
                if (run > best) best = run;
            }
        } catch (Exception e) {
            best = Math.max(best, current);
        }
        data.bestStreak = Math.max(best, current);
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // Construye un JSON con tareas + completaciones para exportar/compartir.
    // Se hace en hilo de fondo porque lee la BD; el resultado (texto) vuelve por callback.
    public void exportDataAsJson(OnExportReadyCallback callback) {
        executor.execute(() -> {
            String json;
            try {
                org.json.JSONObject root = new org.json.JSONObject();
                root.put("exportedAt", DateUtils.getTodayString());

                org.json.JSONArray tasksArr = new org.json.JSONArray();
                for (Task t : taskDao.getAllTasksSync()) {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("id", t.id);
                    o.put("name", t.name);
                    o.put("category", t.category);
                    o.put("color", t.color);
                    o.put("description", t.description);
                    o.put("daysOfWeek", t.daysOfWeek);
                    o.put("hour", t.notificationHour);
                    o.put("minute", t.notificationMinute);
                    o.put("active", t.isActive);
                    tasksArr.put(o);
                }
                root.put("tasks", tasksArr);

                org.json.JSONArray compArr = new org.json.JSONArray();
                for (DailyCompletion c : completionDao.getAllSync()) {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("taskId", c.taskId);
                    o.put("date", c.date);
                    o.put("completed", c.completed);
                    compArr.put(o);
                }
                root.put("completions", compArr);

                json = root.toString(2);
            } catch (Exception e) {
                json = "{\"error\":\"No se pudo exportar\"}";
            }
            if (callback != null) callback.onReady(json);
        });
    }

    // ============================================================
    //  IMPORTAR: estrategia "reemplazar todo". Borra los datos
    //  actuales y carga los del JSON. Lo critico es el MAPA de IDs:
    //  cada tarea recibe un id NUEVO al insertarse, y traducimos el
    //  task_id de cada completacion del id viejo al nuevo, para no
    //  romper la relacion tarea <-> historial.
    // ============================================================
    public void importDataFromJson(String json, OnImportFinishedCallback callback) {
        executor.execute(() -> {
            try {
                org.json.JSONObject root = new org.json.JSONObject(json);

                // 1) Validacion minima: debe traer al menos el arreglo "tasks".
                if (!root.has("tasks")) {
                    if (callback != null) callback.onFinished(false, "El archivo no tiene el formato esperado", 0, 0);
                    return;
                }

                org.json.JSONArray tasksArr = root.getJSONArray("tasks");
                org.json.JSONArray compArr = root.has("completions")
                    ? root.getJSONArray("completions") : new org.json.JSONArray();

                // 2) Reemplazar todo: vaciamos las tareas (CASCADE borra completaciones).
                taskDao.deleteAll();

                // 3) Insertar tareas y construir el mapa idViejo -> idNuevo.
                Map<Integer, Integer> idMap = new HashMap<>();
                int importedTasks = 0;
                for (int i = 0; i < tasksArr.length(); i++) {
                    org.json.JSONObject o = tasksArr.getJSONObject(i);
                    int oldId = o.optInt("id", -1);
                    Task t = new Task(
                        o.optString("name", ""),
                        o.optString("color", "PURPLE"),
                        o.optString("category", ""),
                        o.optString("description", ""),
                        o.optString("daysOfWeek", "[]"),
                        o.optInt("hour", 8),
                        o.optInt("minute", 0),
                        o.optInt("active", 1)
                    );
                    long newId = taskDao.insert(t); // Room asigna el id autoincrementado
                    if (oldId != -1) idMap.put(oldId, (int) newId);
                    importedTasks++;
                }

                // 4) Insertar completaciones traduciendo el task_id con el mapa.
                //    Si una completacion apunta a un id que no esta en el mapa
                //    (dato huerfano), la saltamos en vez de romper.
                int importedCompletions = 0;
                for (int i = 0; i < compArr.length(); i++) {
                    org.json.JSONObject o = compArr.getJSONObject(i);
                    int oldTaskId = o.optInt("taskId", -1);
                    Integer newTaskId = idMap.get(oldTaskId);
                    if (newTaskId == null) continue; // huerfana: no tiene tarea valida
                    DailyCompletion dc = new DailyCompletion(
                        newTaskId,
                        o.optString("date", ""),
                        o.optInt("completed", 0)
                    );
                    completionDao.insert(dc);
                    importedCompletions++;
                }

                if (callback != null) callback.onFinished(true, "Importacion completa", importedTasks, importedCompletions);
            } catch (Exception e) {
                if (callback != null) callback.onFinished(false, "Error al leer el archivo: " + e.getMessage(), 0, 0);
            }
        });
    }

    public interface OnImportFinishedCallback {
        void onFinished(boolean success, String message, int tasksImported, int completionsImported);
    }

    public interface OnExportReadyCallback { void onReady(String json); }
    public interface OnTaskInsertedCallback { void onInserted(Task task); }
    public interface OnTaskLoadedCallback { void onLoaded(Task task); }
    public interface OnCountCallback { void onCount(int count); }
    public interface OnAnalysisLoadedCallback { void onLoaded(List<WeeklyAnalysis> analysisList); }
    public interface OnAnalyticsLoadedCallback { void onLoaded(AnalyticsData data); }
}
