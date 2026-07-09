package com.recordweek.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.recordweek.data.AnalyticsData;
import com.recordweek.data.AppDatabase;
import com.recordweek.data.DailyCompletion;
import com.recordweek.data.DailyCompletionDao;
import com.recordweek.data.MonthData;
import com.recordweek.data.Task;
import com.recordweek.data.TaskDao;
import com.recordweek.utils.DateUtils;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskRepository {
    private final TaskDao taskDao;
    private final DailyCompletionDao completionDao;
    private final ExecutorService executor;
    private final LiveData<List<Task>> allTasks;
    private final LiveData<List<Task>> activeTasks;

    public TaskRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.taskDao = db.taskDao();
        this.completionDao = db.dailyCompletionDao();
        this.executor = Executors.newFixedThreadPool(4);
        this.allTasks = this.taskDao.getAllTasks();
        this.activeTasks = this.taskDao.getActiveTasks();
    }

    // Constructor package-private para tests — inyecta DAOs directamente sin abrir BD
    TaskRepository(TaskDao taskDao, DailyCompletionDao completionDao) {
        this.taskDao = taskDao;
        this.completionDao = completionDao;
        this.executor = Executors.newSingleThreadExecutor();
        this.allTasks = this.taskDao.getAllTasks();
        this.activeTasks = this.taskDao.getActiveTasks();
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

    public void upsertCompletion(DailyCompletion completion) {
        executor.execute(() -> completionDao.insert(completion));
    }

    public LiveData<List<DailyCompletion>> getCompletionsByDate(String date) {
        return completionDao.getCompletionsByDate(date);
    }

    public void deleteAllCompletions() { executor.execute(completionDao::deleteAll); }

    // ============================================================
    //  ANALYTICS: calcula todas las metricas de una sola pasada
    //  leyendo directamente del historial de completaciones.
    //  Se ejecuta en un hilo de fondo (executor) porque toca la BD.
    // ============================================================
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

            // Todas las tareas (no solo activas): una tarea pudo desactivarse pero
            // sus completaciones siguen siendo parte del historial.
            List<Task> tasks = taskDao.getAllTasksSync();

            // --- 1) Cumplimiento semanal + por categoria ---
            // Para cada tarea contamos cuantos de sus dias programados caen dentro
            // del corte (denominador) y cuantos se completaron (numerador).
            for (Task task : tasks) {
                int scheduledSoFar = countScheduledInElapsedWeek(task, monday, cutoffDate);
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

    // ============================================================
    //  VISTA MENSUAL: prepara de una pasada todo lo que la pantalla
    //  necesita para un mes (year, monthZeroBased: 0=Enero..11=Diciembre).
    //  Corre en el executor (hilo de fondo) porque toca la BD; el
    //  resultado (MonthData ya calculado) vuelve por callback.
    // ============================================================
    public void loadMonthData(int year, int monthZeroBased, OnMonthLoadedCallback callback) {
        executor.execute(() -> {
            MonthData data = new MonthData();

            // 1) Tareas activas: la agenda de cada dia se arma filtrando estas con
            //    Task.occursOn (recurrentes por dia de semana, puntuales por fecha).
            data.activeTasks = taskDao.getActiveTasksSync();

            // 2) Rango de fechas del mes: primer dia (1) y ultimo (28/29/30/31).
            //    getActualMaximum nos da el ultimo dia real segun el mes/anio.
            Calendar cal = Calendar.getInstance();
            cal.clear();
            cal.set(year, monthZeroBased, 1);
            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

            String firstDay = DateUtils.calendarToString(cal);
            cal.set(Calendar.DAY_OF_MONTH, daysInMonth);
            String lastDay = DateUtils.calendarToString(cal);

            // 3) Completaciones marcadas del mes -> Set de claves "taskId|fecha".
            //    Reutilizamos getCompletedInRange (ya usado por Analytics).
            for (DailyCompletion dc : completionDao.getCompletedInRange(firstDay, lastDay)) {
                data.completedKeys.add(MonthData.key(dc.taskId, dc.date));
            }

            // 4) Resumen del mes hasta HOY. Recorremos dia a dia; para cada dia
            //    contamos las tareas activas programadas ese dia de semana
            //    (denominador) y cuantas estan en el Set de completadas (numerador).
            //    Los dias futuros no cuentan aun (no son exigibles todavia).
            String todayStr = DateUtils.getTodayString();
            for (int d = 1; d <= daysInMonth; d++) {
                cal.set(Calendar.DAY_OF_MONTH, d);
                String dateStr = DateUtils.calendarToString(cal);
                if (dateStr.compareTo(todayStr) > 0) break; // formato yyyy-MM-dd ordena como texto
                int ourDay = DateUtils.dateStringToOurDay(dateStr); // Lunes=1..Domingo=7
                for (Task t : data.activeTasks) {
                    if (t.occursOn(dateStr, ourDay)) {
                        data.monthScheduled++;
                        if (data.completedKeys.contains(MonthData.key(t.id, dateStr))) {
                            data.monthCompleted++;
                        }
                    }
                }
            }
            data.monthRatePercent = data.monthScheduled > 0
                ? Math.round(data.monthCompleted * 100f / data.monthScheduled) : 0;

            if (callback != null) callback.onLoaded(data);
        });
    }

    // Cuenta cuantos "dias exigibles" tuvo la tarea en la parte YA transcurrida de la
    // semana (de 'monday' hasta 'cutoffDate' inclusive). Es el denominador del
    // cumplimiento semanal. Recorremos dia a dia y delegamos en Task.occursOn, que
    // unifica los dos tipos de tarea:
    //   - Recurrente: suma 1 por cada dia de semana suyo que ya paso esta semana.
    //   - Puntual: suma 1 solo si su fecha exacta cae en ese tramo transcurrido.
    // Al iterar por FECHAS reales (no por numeros de dia sueltos) el mismo bucle
    // sirve para ambos, y ya no hace falta parsear el JSON aqui.
    int countScheduledInElapsedWeek(Task task, String monday, String cutoffDate) {
        Calendar cal = DateUtils.stringToCalendar(monday);
        if (cal == null) return 0;
        int count = 0;
        for (int i = 0; i < 7; i++) { // como mucho, los 7 dias de la semana
            String dateStr = DateUtils.calendarToString(cal);
            if (dateStr.compareTo(cutoffDate) > 0) break; // ya pasamos el corte
            int ourDay = DateUtils.calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK));
            if (task.occursOn(dateStr, ourDay)) count++;
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return count;
    }

    // Calcula racha actual y mejor racha a partir de las fechas (desc) con actividad.
    // Racha actual: dias consecutivos terminando en hoy o ayer (si aun no marcas hoy,
    // la racha de ayer sigue "viva"). Mejor racha: el tramo consecutivo mas largo.
    static void computeStreaks(List<String> datesDesc, AnalyticsData data) {
        if (datesDesc == null || datesDesc.isEmpty()) {
            data.currentStreak = 0;
            data.bestStreak = 0;
            return;
        }
        Set<String> dateSet = new HashSet<>(datesDesc);

        // Racha actual: empezamos en hoy; si hoy no hay, probamos ayer.
        int current = 0;
        Calendar cursor = Calendar.getInstance();
        String todayStr = DateUtils.calendarToString(cursor);
        if (!dateSet.contains(todayStr)) {
            cursor.add(Calendar.DAY_OF_YEAR, -1); // si hoy no, arrancamos desde ayer
        }
        while (dateSet.contains(DateUtils.calendarToString(cursor))) {
            current++;
            cursor.add(Calendar.DAY_OF_YEAR, -1);
        }
        data.currentStreak = current;

        // Mejor racha: recorremos todas las fechas distintas ordenadas y medimos
        // el tramo consecutivo mas largo. datesDesc viene de mas reciente a mas
        // antigua; comparamos cada fecha con la anterior para ver si son contiguas.
        int best = 1, run = 1;
        for (int i = 1; i < datesDesc.size(); i++) {
            Calendar prev = DateUtils.stringToCalendar(datesDesc.get(i - 1));
            Calendar curr = DateUtils.stringToCalendar(datesDesc.get(i));
            // Fecha ilegible (dato corrupto): cortamos la racha y seguimos.
            if (prev == null || curr == null) { run = 1; continue; }
            prev.add(Calendar.DAY_OF_YEAR, -1); // la anterior menos un dia
            if (sameDay(prev, curr)) {
                run++;
            } else {
                run = 1;
            }
            if (run > best) best = run;
        }
        data.bestStreak = Math.max(best, current);
    }

    // ponytail: static y package-private (no privadas) para poder probarlas desde
    // StreakTest sin construir el Repository entero (que abriria la BD). No usan
    // estado de instancia, asi que static es correcto ademas de testeable.
    static boolean sameDay(Calendar a, Calendar b) {
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
                    // specificDate solo tiene valor en tareas puntuales; en las
                    // recurrentes es null y JSONObject.put lo omite (queda ausente),
                    // por eso al importar usamos optString(...,null) mas abajo.
                    o.put("specificDate", t.specificDate);
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
                    // Recuperamos la fecha puntual. Si el respaldo es antiguo (no tiene
                    // el campo) o la tarea es recurrente, optString devuelve null y la
                    // tarea queda como recurrente, que es justo lo que corresponde.
                    t.specificDate = o.optString("specificDate", null);
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
    public interface OnAnalyticsLoadedCallback { void onLoaded(AnalyticsData data); }
    public interface OnMonthLoadedCallback { void onLoaded(MonthData data); }
}
