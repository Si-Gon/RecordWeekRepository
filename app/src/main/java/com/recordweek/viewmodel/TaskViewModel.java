package com.recordweek.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.recordweek.data.AnalyticsData;
import com.recordweek.data.DailyCompletion;
import com.recordweek.data.Task;
import com.recordweek.repository.TaskRepository;
import java.util.List;

public class TaskViewModel extends AndroidViewModel {
    private final TaskRepository repository;
    private final LiveData<List<Task>> allTasks;
    private final MutableLiveData<Integer> selectedDay = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<AnalyticsData> analyticsData = new MutableLiveData<>();

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new TaskRepository(application);
        allTasks = repository.getAllTasks();
    }

    // Constructor package-private para tests — inyecta Repository mockeado
    // El Application es null solo en tests; getApplication() no se usa en esos paths.
    TaskViewModel(@NonNull Application application, TaskRepository repository) {
        super(application);
        this.repository = repository;
        this.allTasks = repository.getAllTasks();
    }

    public LiveData<List<Task>> getAllTasks() { return allTasks; }
    public LiveData<List<Task>> getActiveTasks() { return repository.getActiveTasks(); }
    public void insertTask(Task task, TaskRepository.OnTaskInsertedCallback callback) { repository.insertTask(task, callback); }
    public void updateTask(Task task) { repository.updateTask(task); }
    public void deleteTask(Task task) { repository.deleteTask(task); }
    public void getTaskById(int id, TaskRepository.OnTaskLoadedCallback callback) { repository.getTaskById(id, callback); }

    public void setCompletion(int taskId, String date, boolean completed) {
        DailyCompletion dc = new DailyCompletion();
        dc.taskId = taskId;
        dc.date = date;
        dc.completed = completed ? 1 : 0;
        repository.upsertCompletion(dc);
    }

    public LiveData<DailyCompletion> getCompletionLive(int taskId, String date) { return repository.getCompletionLive(taskId, date); }
    public LiveData<List<DailyCompletion>> getCompletionsByDate(String date) { return repository.getCompletionsByDate(date); }

    // Analytics nuevo: dispara el calculo en el repository (hilo de fondo) y publica
    // el resultado en un LiveData que la Activity observa. postValue porque venimos
    // de un hilo que NO es el principal.
    public LiveData<AnalyticsData> getAnalyticsData() { return analyticsData; }
    public void loadAnalytics() { loadAnalytics(0); }

    // weekOffset: 0 = semana actual, -1 = pasada, -2 = antepasada...
    public void loadAnalytics(int weekOffset) {
        isLoading.setValue(true);
        repository.loadAnalytics(weekOffset, data -> {
            analyticsData.postValue(data);
            isLoading.postValue(false);
        });
    }

    // Vista mensual: pide al repository (hilo de fondo) preparar los datos de un
    // mes (year, monthZeroBased) y devuelve el MonthData por callback. La Activity
    // es responsable de saltar al hilo principal (runOnUiThread) para pintar.
    public void loadMonth(int year, int monthZeroBased, TaskRepository.OnMonthLoadedCallback callback) {
        repository.loadMonthData(year, monthZeroBased, callback);
    }

    // Borra TODO el historial de completaciones (no las tareas). Usado por la
    // opcion "borrar datos" en Configuracion, siempre con confirmacion previa.
    public void deleteAllCompletions() { repository.deleteAllCompletions(); }

    // Exporta tareas + completaciones como texto JSON (para Compartir).
    public void exportData(TaskRepository.OnExportReadyCallback callback) {
        repository.exportDataAsJson(callback);
    }

    // Importa desde el texto JSON de un respaldo (estrategia reemplazar todo).
    public void importData(String json, TaskRepository.OnImportFinishedCallback callback) {
        repository.importDataFromJson(json, callback);
    }

    public MutableLiveData<Integer> getSelectedDay() { return selectedDay; }
    public void setSelectedDay(int day) { selectedDay.setValue(day); }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
}
