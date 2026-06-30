package com.recordweek;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.recordweek.adapter.TaskAdapter;
import com.recordweek.data.DailyCompletion;
import com.recordweek.data.Task;
import com.recordweek.notification.NotificationScheduler;
import com.recordweek.utils.DateUtils;
import com.recordweek.viewmodel.TaskViewModel;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TaskViewModel viewModel;
    private TaskAdapter adapter;
    private List<Button> dayButtons = new ArrayList<>();
    private int currentSelectedDay;
    private List<Task> allActiveTasks = new ArrayList<>();
    private String todayDate;
    // LiveData de completaciones actualmente observado. Lo guardamos para poder
    // QUITAR su observer antes de suscribir el de otro dia, y evitar acumular
    // observers viejos que repintan el adapter con datos de fechas mezcladas.
    private LiveData<List<DailyCompletion>> currentCompletionsLiveData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Instala la splash del sistema. DEBE ir antes de super.onCreate() y
        // setContentView(): esta llamada intercambia el tema (de splash al normal,
        // via postSplashScreenTheme) en el momento exacto. Si fuera despues, la
        // ventana ya se habria pintado con el tema de splash y veriamos un parpadeo.
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        todayDate = DateUtils.getTodayString();
        currentSelectedDay = DateUtils.getTodayDayOfWeek();
        setupToolbar();
        setupRecyclerView();   // crea 'adapter' antes de que selectDay() lo use
        setupViewModel();      // crea 'viewModel' antes de que selectDay() lo use
        setupDaySelector();    // ahora ya puede llamar a viewModel y adapter sin null
        setupFab();
        cancelWeeklyReset();
    }

    private void setupToolbar() {
        if (getSupportActionBar() == null) return;

        // Titulo con estilos mixtos: "Record" en blanco recto, "Week" en ambar
        // cursiva. La ActionBar normal solo acepta texto plano, asi que usamos un
        // SpannableString: texto al que se le aplican estilos por TRAMOS de
        // caracteres (spans). Indice 0..6 = "Record", 6..10 = "Week".
        String full = "RecordWeek"; // sin espacio: las mayusculas marcan la separacion
        android.text.SpannableString title = new android.text.SpannableString(full);

        int split = "Record".length(); // 6

        // "Record" -> color de texto principal (blanco azulado)
        title.setSpan(
            new android.text.style.ForegroundColorSpan(getColor(R.color.text_primary)),
            0, split, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // "Week" -> color de acento (ambar)
        title.setSpan(
            new android.text.style.ForegroundColorSpan(getColor(R.color.accent_amber)),
            split, full.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        // "Week" -> cursiva (Typeface.ITALIC) para el contraste tipografico
        title.setSpan(
            new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
            split, full.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        getSupportActionBar().setTitle(title);
    }

    private void setupDaySelector() {
        int[] buttonIds = {R.id.btn_mon, R.id.btn_tue, R.id.btn_wed, R.id.btn_thu, R.id.btn_fri, R.id.btn_sat, R.id.btn_sun};
        for (int i = 0; i < buttonIds.length; i++) {
            final int dayNumber = i + 1;
            Button btn = findViewById(buttonIds[i]);
            dayButtons.add(btn);
            btn.setOnClickListener(v -> selectDay(dayNumber));
        }
        selectDay(currentSelectedDay);
    }

    private void selectDay(int day) {
        currentSelectedDay = day;
        viewModel.setSelectedDay(day);
        for (int i = 0; i < dayButtons.size(); i++) {
            boolean isSelected = (i + 1) == day;
            Button dayBtn = dayButtons.get(i);
            dayBtn.setBackgroundResource(isSelected ? R.drawable.bg_day_selected : R.drawable.bg_day_normal);
            dayBtn.setTextColor(getResources().getColor(
                isSelected ? R.color.accent_amber_dark : R.color.text_secondary));
        }
        filterTasksForDay(day);
        observeCompletionsForCurrentDay();
        // Solo el dia de HOY permite marcar/desmarcar. Si el dia seleccionado no es
        // hoy, el adapter bloquea los checkboxes (los muestra apagados y de consulta).
        adapter.setEditable(day == DateUtils.getTodayDayOfWeek());
    }

    private void filterTasksForDay(int day) {
        List<Task> filtered = new ArrayList<>();
        for (Task task : allActiveTasks) {
            if (taskHasDay(task, day)) filtered.add(task);
        }
        adapter.setTasks(filtered);
    }

    private boolean taskHasDay(Task task, int day) {
        if (task.daysOfWeek == null) return false;
        try {
            JSONArray arr = new JSONArray(task.daysOfWeek);
            for (int i = 0; i < arr.length(); i++) if (arr.getInt(i) == day) return true;
        } catch (JSONException e) { e.printStackTrace(); }
        return false;
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recycler_tasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskAdapter(
            new TaskAdapter.OnTaskClickListener() {
                @Override public void onTaskClick(Task task) {}
                @Override public void onTaskLongClick(Task task) { showTaskOptions(task); }
                @Override public void onTaskMenuClick(Task task) { showTaskOptions(task); }
            },
            (task, completed) -> {
                String dateForDay = getDateForSelectedDay();
                viewModel.setCompletion(task.id, dateForDay, completed);
            }
        );
        recyclerView.setAdapter(adapter);
    }

    private String getDateForSelectedDay() {
        int todayDay = DateUtils.getTodayDayOfWeek();
        if (currentSelectedDay == todayDay) return todayDate;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, currentSelectedDay - todayDay);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        viewModel.getActiveTasks().observe(this, tasks -> {
            allActiveTasks = tasks != null ? tasks : new ArrayList<>();
            filterTasksForDay(currentSelectedDay);
        });
        viewModel.setSelectedDay(currentSelectedDay);
        observeCompletionsForCurrentDay();
    }

    private void observeCompletionsForCurrentDay() {
        String dateForDay = getDateForSelectedDay();
        // PASO 1: si ya estabamos observando las completaciones de OTRO dia,
        // quitamos ese observer. Si no lo hicieramos, cada vez que cambias de dia
        // (o entras a la pantalla) se acumula un observer mas. Todos siguen vivos
        // y todos disparan adapter.setCompletions(...) -> el adapter termina
        // recibiendo datos de varias fechas mezcladas y repinta checkboxes que no
        // corresponden al dia actual. Ese era el efecto de "se marcan las demas".
        if (currentCompletionsLiveData != null) {
            currentCompletionsLiveData.removeObservers(this);
        }
        // PASO 2: pedimos el LiveData del dia actual y lo guardamos en el campo,
        // para poder quitarle el observer la proxima vez (ver PASO 1).
        currentCompletionsLiveData = viewModel.getCompletionsByDate(dateForDay);
        // PASO 3: ahora si, un unico observer activo, ligado solo a este dia.
        currentCompletionsLiveData.observe(this, completions -> adapter.setCompletions(completions));
    }

    private void setupFab() {
        FloatingActionButton fab = findViewById(R.id.fab_add_task);
        fab.setOnClickListener(v -> startActivity(new Intent(this, AddTaskActivity.class)));
    }

    private void showTaskOptions(Task task) {
        // Nombre del dia actualmente seleccionado, para que la opcion del medio
        // diga exactamente "Quitar del lunes" en vez de algo generico.
        String dayName = dayNameForNumber(currentSelectedDay);
        String[] options = {"Editar", "Quitar del " + dayName, "Eliminar tarea", "Cancelar"};
        new AlertDialog.Builder(this)
            .setTitle(task.name)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    Intent intent = new Intent(this, AddTaskActivity.class);
                    intent.putExtra(AddTaskActivity.EXTRA_TASK_ID, task.id);
                    startActivity(intent);
                } else if (which == 1) {
                    removeDayFromTask(task, currentSelectedDay);
                } else if (which == 2) {
                    confirmDeleteTask(task);
                }
                // which == 3 -> Cancelar: no hacemos nada.
            }).show();
    }

    // Borrado TOTAL de la tarea (todos los dias + historial). Pide confirmacion
    // porque es irreversible: el ForeignKey CASCADE elimina tambien sus completaciones.
    private void confirmDeleteTask(Task task) {
        new AlertDialog.Builder(this)
            .setTitle("Eliminar tarea")
            .setMessage("Eliminar \"" + task.name + "\" de TODOS los dias? Se borrara todo su historial.")
            .setPositiveButton("Eliminar", (d, w) -> {
                NotificationScheduler.cancelTask(this, task);
                viewModel.deleteTask(task);
            })
            .setNegativeButton("Cancelar", null).show();
    }

    // "Quitar de este dia": NO borra la tarea, solo le quita un dia de su lista
    // daysOfWeek (que es un JSON tipo "[1,2,3,4]"). Recordemos que una tarea es
    // UNA sola entidad con varios dias; quitar un dia = editar esa lista.
    private void removeDayFromTask(Task task, int day) {
        List<Integer> remaining = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(task.daysOfWeek);
            for (int i = 0; i < arr.length(); i++) {
                int d = arr.getInt(i);
                if (d != day) remaining.add(d); // conservamos todos MENOS el dia a quitar
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return; // si el JSON esta corrupto, no tocamos nada
        }

        // Caso especial: era el unico dia. La tarea quedaria sin ningun dia (fantasma).
        // Preguntamos si prefiere eliminarla por completo.
        if (remaining.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("Ultimo dia")
                .setMessage("\"" + task.name + "\" solo estaba en este dia. Si lo quitas, se quedara sin dias.\n\nEliminar la tarea por completo?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    NotificationScheduler.cancelTask(this, task);
                    viewModel.deleteTask(task);
                })
                .setNegativeButton("Cancelar", null).show();
            return;
        }

        // Caso normal: reconstruimos el JSON con los dias restantes y actualizamos.
        // Primero cancelamos TODAS las alarmas de la tarea (sus request codes dependen
        // de los dias viejos) y luego reprogramamos con los dias nuevos. Asi no queda
        // una notificacion huerfana del dia que acabamos de quitar.
        NotificationScheduler.cancelTask(this, task);
        JSONArray newDays = new JSONArray();
        for (int d : remaining) newDays.put(d);
        task.daysOfWeek = newDays.toString();
        viewModel.updateTask(task);
        if (task.isActive == 1) NotificationScheduler.scheduleTask(this, task);
    }

    private String dayNameForNumber(int day) {
        switch (day) {
            case 1: return "lunes";
            case 2: return "martes";
            case 3: return "miercoles";
            case 4: return "jueves";
            case 5: return "viernes";
            case 6: return "sabado";
            case 7: return "domingo";
            default: return "dia";
        }
    }

    // Antes esto programaba un borrado semanal automatico. Ahora las rutinas son
    // permanentes, asi que en lugar de programarlo, CANCELAMOS cualquier trabajo
    // "weekly_reset" que haya quedado encolado en ejecuciones anteriores de la app.
    // WorkManager guarda los trabajos en su propia base de datos interna, por eso
    // no basta con borrar el codigo: hay que pedirle explicitamente que lo cancele.
    private void cancelWeeklyReset() {
        WorkManager.getInstance(this).cancelUniqueWork("weekly_reset");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_analytics) {
            startActivity(new Intent(this, AnalyticsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
