package com.recordweek;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Calendar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.recordweek.data.Task;
import com.recordweek.notification.NotificationScheduler;
import com.recordweek.utils.DateUtils;
import com.recordweek.viewmodel.TaskViewModel;
import org.json.JSONArray;
import org.json.JSONException;

public class AddTaskActivity extends AppCompatActivity {
    public static final String EXTRA_TASK_ID = "task_id";
    private TaskViewModel viewModel;
    private EditText etName, etDescription;
    private Spinner spinnerCategory;
    private View viewCategoryColor;
    private TextView tvSelectedTime;
    private Switch switchActive;
    private Button[] dayButtons = new Button[7];
    private boolean[] selectedDays = new boolean[7];
    private int selectedHour = 8, selectedMinute = 0;
    private int editingTaskId = -1;
    private Task editingTask = null;

    // --- Estado del selector de tipo de tarea ---
    private RadioGroup rgTaskType;
    private View containerWeekdays, containerSpecificDate;
    private TextView tvSelectedDate;
    // Fecha elegida para la tarea PUNTUAL, en formato "yyyy-MM-dd". null = aun no
    // se ha elegido ninguna. Solo se usa cuando el tipo es puntual.
    private String selectedDate = null;

    private static final String[] CATEGORIES = {"Trabajo","Estudio","Gimnasio","Taller Deportivo","Comida & Suplementos","Salud","Tiempo Personal"};
    // Color fijo por categoria, alineado por indice con CATEGORIES (tabla paralela):
    // la categoria en la posicion i siempre usa el color CATEGORY_COLORS[i]. Asi el
    // color deja de elegirse a mano y queda atado semanticamente a la categoria.
    private static final String[] CATEGORY_COLORS = {"RED","BLUE","YELLOW","ORANGE","PURPLE","GREEN","PINK"};

    // Deriva el nombre de color a partir del nombre de categoria. Si por algun motivo
    // la categoria no esta en la lista (dato antiguo o importado raro), cae en PURPLE.
    private static String colorForCategory(String category) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) return CATEGORY_COLORS[i];
        }
        return "PURPLE";
    }

    // Traduce el nombre interno del color ("RED", "BLUE"...) al recurso de color real
    // (R.color.task_red...). Es el mismo mapeo que ya usa la lista de tareas, centralizado
    // aqui para pintar el punto indicador junto al selector de categoria.
    private static int colorResForName(String name) {
        if (name == null) return R.color.task_purple;
        switch (name) {
            case "RED":    return R.color.task_red;
            case "ORANGE": return R.color.task_orange;
            case "YELLOW": return R.color.task_yellow;
            case "GREEN":  return R.color.task_green;
            case "BLUE":   return R.color.task_blue;
            case "PINK":   return R.color.task_pink;
            case "PURPLE":
            default:       return R.color.task_purple;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        editingTaskId = getIntent().getIntExtra(EXTRA_TASK_ID, -1);
        setupToolbar();
        setupFormFields();
        setupTaskTypeSelector();
        setupDaySelector();
        setupDatePicker();
        setupTimePicker();
        setupSaveButton();
        if (editingTaskId != -1) loadTaskForEditing();
    }

    private void setupToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(editingTaskId == -1 ? "Nueva tarea" : "Editar tarea");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupFormFields() {
        etName = findViewById(R.id.et_task_name);
        etDescription = findViewById(R.id.et_task_description);
        tvSelectedTime = findViewById(R.id.tv_selected_time);
        switchActive = findViewById(R.id.switch_active);
        spinnerCategory = findViewById(R.id.spinner_category);
        viewCategoryColor = findViewById(R.id.view_category_color);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CATEGORIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        // Cada vez que cambia la categoria, repintamos el punto con su color asociado.
        // Asi el usuario VE que "Estudio = azul", "Trabajo = rojo", etc., sin elegir nada.
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCategoryColorDot((String) parent.getItemAtPosition(position));
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    // Tinta el punto indicador con el color de la categoria dada. Usamos un solo
    // drawable (bg_category_dot) y le cambiamos el tinte, en vez de tener 7 drawables.
    private void updateCategoryColorDot(String category) {
        int colorRes = colorResForName(colorForCategory(category));
        viewCategoryColor.getBackground().mutate().setTint(getColor(colorRes));
    }

    private void setupDaySelector() {
        int[] ids = {R.id.btn_day_mon,R.id.btn_day_tue,R.id.btn_day_wed,R.id.btn_day_thu,R.id.btn_day_fri,R.id.btn_day_sat,R.id.btn_day_sun};
        for (int i = 0; i < 7; i++) {
            final int index = i;
            dayButtons[i] = findViewById(ids[i]);
            dayButtons[i].setOnClickListener(v -> {
                selectedDays[index] = !selectedDays[index];
                applyDayStyle(index);
            });
        }
    }

    // Centraliza la apariencia de un boton de dia segun este o no seleccionado.
    // La clave del contraste es cambiar TAMBIEN el color del texto, no solo el
    // fondo: si dejaramos el texto casi blanco, sobre el ambar (#E0922F, un tono
    // medio) casi no se distingue. Con texto oscuro sobre ambar, el estado
    // seleccionado se lee como una pastilla solida; el no seleccionado queda
    // oscuro con texto claro. Dos estados visualmente opuestos.
    private void applyDayStyle(int index) {
        boolean sel = selectedDays[index];
        dayButtons[index].setBackgroundResource(sel ? R.drawable.bg_day_selected : R.drawable.bg_day_normal);
        dayButtons[index].setTextColor(getColor(
            sel ? R.color.accent_amber_dark : R.color.text_primary));
    }

    // Conecta el RadioGroup de tipo con los dos bloques. La regla es simple: solo
    // uno de los dos bloques se ve a la vez. Recurrente -> se ven los dias; Puntual
    // -> se ve el selector de fecha. Cambiar de tipo solo alterna visibilidad; NO
    // borra lo ya elegido, para que el usuario pueda ir y volver sin perder datos.
    private void setupTaskTypeSelector() {
        rgTaskType = findViewById(R.id.rg_task_type);
        containerWeekdays = findViewById(R.id.container_weekdays);
        containerSpecificDate = findViewById(R.id.container_specific_date);
        rgTaskType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean oneOff = (checkedId == R.id.rb_oneoff);
            containerWeekdays.setVisibility(oneOff ? View.GONE : View.VISIBLE);
            containerSpecificDate.setVisibility(oneOff ? View.VISIBLE : View.GONE);
        });
    }

    // Al tocar el campo de fecha abrimos el DatePickerDialog. Lo inicializamos en la
    // fecha ya elegida (si la hay) o en hoy. Guardamos la eleccion como "yyyy-MM-dd"
    // (mismo formato que usa toda la app) y mostramos ese texto en el campo.
    // Nota: en el dialogo el mes va de 0..11, por eso sumamos/restamos 1 al formatear.
    private void setupDatePicker() {
        tvSelectedDate = findViewById(R.id.tv_selected_date);
        tvSelectedDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedDate != null) {
                String[] p = selectedDate.split("-");
                cal.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            }
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
                selectedDate = String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
                tvSelectedDate.setText(selectedDate);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            // No dejamos elegir fechas pasadas: una tarea puntual solo tiene sentido
            // de hoy en adelante (una notificacion en el pasado no avisaria nada).
            dialog.getDatePicker().setMinDate(System.currentTimeMillis());
            dialog.show();
        });
    }

    private void setupTimePicker() {
        tvSelectedTime.setText(DateUtils.formatTime(selectedHour, selectedMinute));
        tvSelectedTime.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            selectedHour = h; selectedMinute = m;
            tvSelectedTime.setText(DateUtils.formatTime(h, m));
        }, selectedHour, selectedMinute, true).show());
    }

    private void setupSaveButton() {
        findViewById(R.id.btn_save_task).setOnClickListener(v -> saveTask());
    }

    private void saveTask() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) { etName.setError("El nombre es obligatorio"); etName.requestFocus(); return; }

        boolean oneOff = rgTaskType.getCheckedRadioButtonId() == R.id.rb_oneoff;

        // Cada tipo tiene su propia validacion y su propia forma de rellenar los
        // campos de la Task. Los dos campos (daysOfWeek y specificDate) siempre
        // existen; segun el tipo, uno lleva datos y el otro queda "vacio":
        //   - Recurrente: daysOfWeek = [dias elegidos], specificDate = null.
        //   - Puntual:    daysOfWeek = "[]",           specificDate = "yyyy-MM-dd".
        String daysJson;
        String specificDate;
        if (oneOff) {
            if (selectedDate == null) { Toast.makeText(this, "Elige una fecha", Toast.LENGTH_SHORT).show(); return; }
            daysJson = "[]";
            specificDate = selectedDate;
        } else {
            boolean anyDay = false;
            for (boolean d : selectedDays) if (d) { anyDay = true; break; }
            if (!anyDay) { Toast.makeText(this, "Selecciona al menos un dia", Toast.LENGTH_SHORT).show(); return; }
            JSONArray daysArray = new JSONArray();
            for (int i = 0; i < 7; i++) if (selectedDays[i]) daysArray.put(i + 1);
            daysJson = daysArray.toString();
            specificDate = null;
        }

        String category = (String) spinnerCategory.getSelectedItem();
        String color = colorForCategory(category); // el color ya no se elige a mano
        String description = etDescription.getText().toString().trim();
        int isActive = switchActive.isChecked() ? 1 : 0;
        if (editingTaskId == -1) {
            Task newTask = new Task(name, color, category, description, daysJson, selectedHour, selectedMinute, isActive);
            newTask.specificDate = specificDate;
            viewModel.insertTask(newTask, insertedTask -> runOnUiThread(() -> {
                if (isActive == 1) NotificationScheduler.scheduleTask(this, insertedTask);
                Toast.makeText(this, "Tarea creada", Toast.LENGTH_SHORT).show();
                finish();
            }));
        } else if (editingTask != null) {
            NotificationScheduler.cancelTask(this, editingTask);
            editingTask.name = name; editingTask.color = color; editingTask.category = category;
            editingTask.description = description; editingTask.daysOfWeek = daysJson;
            editingTask.specificDate = specificDate;
            editingTask.notificationHour = selectedHour; editingTask.notificationMinute = selectedMinute;
            editingTask.isActive = isActive;
            viewModel.updateTask(editingTask);
            if (isActive == 1) NotificationScheduler.scheduleTask(this, editingTask);
            Toast.makeText(this, "Tarea actualizada", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadTaskForEditing() {
        viewModel.getTaskById(editingTaskId, task -> {
            if (task == null) return;
            editingTask = task;
            runOnUiThread(() -> {
                etName.setText(task.name);
                etDescription.setText(task.description);
                selectedHour = task.notificationHour; selectedMinute = task.notificationMinute;
                tvSelectedTime.setText(DateUtils.formatTime(selectedHour, selectedMinute));
                switchActive.setChecked(task.isActive == 1);
                for (int i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].equals(task.category)) { spinnerCategory.setSelection(i); break; }
                if (task.isOneOff()) {
                    // Tarea PUNTUAL: marcamos el radio "Fecha puntual" (el listener
                    // ya alterna la visibilidad de los bloques) y reponemos la fecha.
                    rgTaskType.check(R.id.rb_oneoff);
                    selectedDate = task.specificDate;
                    tvSelectedDate.setText(selectedDate);
                } else {
                    // Tarea RECURRENTE: repintamos los dias guardados.
                    try {
                        JSONArray arr = new JSONArray(task.daysOfWeek);
                        for (int i = 0; i < arr.length(); i++) {
                            int day = arr.getInt(i);
                            selectedDays[day - 1] = true;
                            applyDayStyle(day - 1);
                        }
                    } catch (JSONException e) { e.printStackTrace(); }
                }
            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}
