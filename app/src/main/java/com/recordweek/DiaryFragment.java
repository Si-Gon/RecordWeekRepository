package com.recordweek;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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

// ============================================================
//  DiaryFragment: la vista DIARIO como Fragment (antes era MainActivity).
//
//  ¿Que cambia respecto a la Activity? La logica es la MISMA; solo cambia "donde"
//  se engancha:
//   - En una Activity el punto de arranque es onCreate() y el contexto es "this".
//   - En un Fragment el arranque es onCreateView() (inflar la vista) +
//     onViewCreated() (ya con la vista lista para buscar widgets). Como un Fragment
//     NO es un Context, usamos requireContext()/requireActivity() donde antes iba
//     "this", y getViewLifecycleOwner() para observar LiveData (asi el observer se
//     suelta solo cuando la vista del Fragment se destruye al cambiar de pestana).
//
//  Lo que ANTES vivia en MainActivity y AHORA no esta aqui:
//   - La splash y el titulo "RecordWeek": los pone el host (MainActivity) una vez.
//   - cancelWeeklyReset(): tarea de arranque unica; tambien vive en el host.
// ============================================================
public class DiaryFragment extends Fragment {

    private TaskViewModel viewModel;
    private TaskAdapter adapter;
    private final List<Button> dayButtons = new ArrayList<>();
    private int currentSelectedDay;
    private List<Task> allActiveTasks = new ArrayList<>();
    private String todayDate;
    // LiveData de completaciones actualmente observado. Lo guardamos para poder
    // QUITAR su observer antes de suscribir el de otro dia, y evitar acumular
    // observers viejos que repintan el adapter con datos de fechas mezcladas.
    private LiveData<List<DailyCompletion>> currentCompletionsLiveData;
    // La vista raiz del Fragment. La guardamos para hacer findViewById sobre ella
    // (un Fragment no tiene findViewById propio como la Activity).
    private View root;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Solo inflamos y devolvemos la vista. La configuracion (buscar widgets,
        // enganchar listeners) va en onViewCreated, cuando la vista ya existe.
        return inflater.inflate(R.layout.fragment_diary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        todayDate = DateUtils.getTodayString();
        currentSelectedDay = DateUtils.getTodayDayOfWeek();
        setupRecyclerView();   // crea 'adapter' antes de que selectDay() lo use
        setupViewModel();      // crea 'viewModel' antes de que selectDay() lo use
        setupDaySelector();    // ahora ya puede llamar a viewModel y adapter sin null
        setupFab();
    }

    private void setupDaySelector() {
        int[] buttonIds = {R.id.btn_mon, R.id.btn_tue, R.id.btn_wed, R.id.btn_thu, R.id.btn_fri, R.id.btn_sat, R.id.btn_sun};
        for (int i = 0; i < buttonIds.length; i++) {
            final int dayNumber = i + 1;
            Button btn = root.findViewById(buttonIds[i]);
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
            dayBtn.setTextColor(requireContext().getColor(
                isSelected ? R.color.accent_amber_dark : R.color.text_secondary));
        }
        filterTasksForDay(day);
        observeCompletionsForCurrentDay();
        // Solo el dia de HOY permite marcar/desmarcar. Si el dia seleccionado no es
        // hoy, el adapter bloquea los checkboxes (los muestra apagados y de consulta).
        adapter.setEditable(day == DateUtils.getTodayDayOfWeek());
    }

    private void filterTasksForDay(int day) {
        // Necesitamos la FECHA real de ese dia (no solo el dia de semana), porque una
        // tarea puntual solo "ocurre" si su fecha coincide exactamente. occursOn()
        // resuelve ambos casos: recurrente (por dia de semana) y puntual (por fecha).
        String date = getDateForDay(day);
        List<Task> filtered = new ArrayList<>();
        for (Task task : allActiveTasks) {
            if (task.occursOn(date, day)) filtered.add(task);
        }
        adapter.setTasks(filtered);
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = root.findViewById(R.id.recycler_tasks);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
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
        return getDateForDay(currentSelectedDay);
    }

    // Fecha real ("yyyy-MM-dd") del dia de semana dado DENTRO de la semana actual.
    // Diario siempre muestra la semana en curso, asi que "dia 3" = el miercoles de
    // esta semana. Se calcula desplazando desde hoy la diferencia de dias.
    private String getDateForDay(int day) {
        int todayDay = DateUtils.getTodayDayOfWeek();
        if (day == todayDay) return todayDate;
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, day - todayDay);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.getTime());
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        viewModel.getActiveTasks().observe(getViewLifecycleOwner(), tasks -> {
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
            currentCompletionsLiveData.removeObservers(getViewLifecycleOwner());
        }
        // PASO 2: pedimos el LiveData del dia actual y lo guardamos en el campo,
        // para poder quitarle el observer la proxima vez (ver PASO 1).
        currentCompletionsLiveData = viewModel.getCompletionsByDate(dateForDay);
        // PASO 3: ahora si, un unico observer activo, ligado solo a este dia.
        currentCompletionsLiveData.observe(getViewLifecycleOwner(),
            completions -> adapter.setCompletions(completions));
    }

    private void setupFab() {
        FloatingActionButton fab = root.findViewById(R.id.fab_add_task);
        fab.setOnClickListener(v -> startActivity(new Intent(requireContext(), AddTaskActivity.class)));
    }

    private void showTaskOptions(Task task) {
        // "Quitar del <dia>" solo tiene sentido en tareas RECURRENTES: quita un dia de
        // su lista. Una tarea PUNTUAL no tiene lista de dias (ocurre una sola fecha),
        // asi que para ella ofrecemos solo Editar / Eliminar / Cancelar.
        if (task.isOneOff()) {
            String[] options = {"Editar", "Eliminar tarea", "Cancelar"};
            new AlertDialog.Builder(requireContext())
                .setTitle(task.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openTaskForEditing(task);
                    } else if (which == 1) {
                        confirmDeleteTask(task);
                    }
                    // which == 2 -> Cancelar.
                }).show();
            return;
        }

        // Nombre del dia actualmente seleccionado, para que la opcion del medio
        // diga exactamente "Quitar del lunes" en vez de algo generico.
        String dayName = dayNameForNumber(currentSelectedDay);
        String[] options = {"Editar", "Quitar del " + dayName, "Eliminar tarea", "Cancelar"};
        new AlertDialog.Builder(requireContext())
            .setTitle(task.name)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    openTaskForEditing(task);
                } else if (which == 1) {
                    removeDayFromTask(task, currentSelectedDay);
                } else if (which == 2) {
                    confirmDeleteTask(task);
                }
                // which == 3 -> Cancelar: no hacemos nada.
            }).show();
    }

    private void openTaskForEditing(Task task) {
        Intent intent = new Intent(requireContext(), AddTaskActivity.class);
        intent.putExtra(AddTaskActivity.EXTRA_TASK_ID, task.id);
        startActivity(intent);
    }

    // Borrado TOTAL de la tarea (todos los dias + historial). Pide confirmacion
    // porque es irreversible: el ForeignKey CASCADE elimina tambien sus completaciones.
    private void confirmDeleteTask(Task task) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Eliminar tarea")
            .setMessage("Eliminar \"" + task.name + "\" de TODOS los dias? Se borrara todo su historial.")
            .setPositiveButton("Eliminar", (d, w) -> {
                NotificationScheduler.cancelTask(requireContext(), task);
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
            new AlertDialog.Builder(requireContext())
                .setTitle("Ultimo dia")
                .setMessage("\"" + task.name + "\" solo estaba en este dia. Si lo quitas, se quedara sin dias.\n\nEliminar la tarea por completo?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    NotificationScheduler.cancelTask(requireContext(), task);
                    viewModel.deleteTask(task);
                })
                .setNegativeButton("Cancelar", null).show();
            return;
        }

        // Caso normal: reconstruimos el JSON con los dias restantes y actualizamos.
        // Primero cancelamos TODAS las alarmas de la tarea (sus request codes dependen
        // de los dias viejos) y luego reprogramamos con los dias nuevos. Asi no queda
        // una notificacion huerfana del dia que acabamos de quitar.
        NotificationScheduler.cancelTask(requireContext(), task);
        JSONArray newDays = new JSONArray();
        for (int d : remaining) newDays.put(d);
        task.daysOfWeek = newDays.toString();
        viewModel.updateTask(task);
        if (task.isActive == 1) NotificationScheduler.scheduleTask(requireContext(), task);
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
}
