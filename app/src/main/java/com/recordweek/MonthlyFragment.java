package com.recordweek;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.recordweek.data.MonthData;
import com.recordweek.data.Task;
import com.recordweek.utils.CardStyle;
import com.recordweek.utils.DateUtils;
import com.recordweek.viewmodel.TaskViewModel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// ============================================================
//  VISTA MENSUAL (solo consulta) como Fragment (antes MonthlyActivity).
//  - Calendario compacto como INDICE (semana empieza lunes) con puntitos de
//    categoria por dia.
//  - Al tocar un dia, se muestra su AGENDA por hora, de SOLO LECTURA.
//
//  Cambios por ser Fragment (misma logica de dibujo que la Activity):
//   - Arranque en onViewCreated (no onCreate); widgets con root.findViewById.
//   - "this" (contexto) -> requireContext(); getResources() sigue funcionando en
//     Fragment (delega en la Activity).
//   - El calculo pesado vuelve por callback desde un hilo de fondo; como puede
//     llegar cuando ya cambiaste de pestana, protegemos con getActivity()!=null e
//     isAdded()/getView()!=null antes de tocar vistas.
// ============================================================
public class MonthlyFragment extends Fragment {

    private TaskViewModel viewModel;
    private View root;

    // Vistas del layout
    private TextView tvMonthLabel, tvMonthSummary, tvDayHead, tvDayCount, tvAgendaEmpty;
    private TextView btnMonthPrev, btnMonthNext;
    private LinearLayout containerCalendar, containerAgenda;

    // Estado de la pantalla
    private int currentYear;
    private int currentMonth;   // 0 = Enero .. 11 = Diciembre (como Calendar)
    private int selectedDay;    // dia del mes actualmente elegido (1..31)
    private MonthData monthData; // datos ya calculados del mes mostrado

    private static final String[] MONTH_NAMES = {
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };
    private static final String[] WEEKDAY_NAMES = { // indice 1..7 (0 sin usar)
        "", "Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado", "Domingo"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_monthly, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        tvMonthLabel = root.findViewById(R.id.tv_month_label);
        tvMonthSummary = root.findViewById(R.id.tv_month_summary);
        tvDayHead = root.findViewById(R.id.tv_day_head);
        tvDayCount = root.findViewById(R.id.tv_day_count);
        tvAgendaEmpty = root.findViewById(R.id.tv_agenda_empty);
        btnMonthPrev = root.findViewById(R.id.btn_month_prev);
        btnMonthNext = root.findViewById(R.id.btn_month_next);
        containerCalendar = root.findViewById(R.id.container_calendar);
        containerAgenda = root.findViewById(R.id.container_agenda);

        // Arrancamos en el mes de HOY, con hoy como dia seleccionado.
        Calendar today = Calendar.getInstance();
        currentYear = today.get(Calendar.YEAR);
        currentMonth = today.get(Calendar.MONTH);
        selectedDay = today.get(Calendar.DAY_OF_MONTH);

        btnMonthPrev.setOnClickListener(v -> changeMonth(-1));
        btnMonthNext.setOnClickListener(v -> changeMonth(+1));

        loadMonth();
    }

    // Avanza o retrocede 'delta' meses. Al cambiar de mes elegimos por defecto:
    //   - si el nuevo mes es el mes real de hoy -> el dia de hoy;
    //   - si no -> el dia 1.
    private void changeMonth(int delta) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(currentYear, currentMonth, 1);
        cal.add(Calendar.MONTH, delta);
        currentYear = cal.get(Calendar.YEAR);
        currentMonth = cal.get(Calendar.MONTH);

        Calendar today = Calendar.getInstance();
        boolean isRealMonth = (currentYear == today.get(Calendar.YEAR)
                && currentMonth == today.get(Calendar.MONTH));
        selectedDay = isRealMonth ? today.get(Calendar.DAY_OF_MONTH) : 1;

        loadMonth();
    }

    // Pide al ViewModel los datos del mes (hilo de fondo) y, cuando vuelven,
    // salta al hilo principal para pintar todo. El label del mes lo ponemos ya.
    private void loadMonth() {
        tvMonthLabel.setText(MONTH_NAMES[currentMonth] + " " + currentYear);
        viewModel.loadMonth(currentYear, currentMonth, data -> {
            // El callback llega en hilo de fondo y puede llegar tarde (ya cambiaste
            // de pestana). Si la Activity ya no existe, ni intentamos.
            Activity act = getActivity();
            if (act == null) return;
            act.runOnUiThread(() -> {
                // Segundo cinturon de seguridad: si el Fragment ya no esta pegado o
                // su vista se destruyo, no tocamos widgets (evita crash por vistas nulas).
                if (!isAdded() || getView() == null) return;
                monthData = data;
                tvMonthSummary.setText("Este mes · " + data.monthRatePercent + "% cumplido");
                renderCalendar();
                renderAgenda();
            });
        });
    }

    // ============================================================
    //  CALENDARIO (indice). Construimos filas de 7 celdas. La primera semana
    //  puede empezar con huecos si el dia 1 no cae en lunes.
    // ============================================================
    private void renderCalendar() {
        containerCalendar.removeAllViews();
        if (monthData == null) return;

        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(currentYear, currentMonth, 1);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        // Dia de semana del 1 en nuestra convencion (Lunes=1..Domingo=7).
        int firstOurDay = DateUtils.calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK));
        int leadingBlanks = firstOurDay - 1; // huecos antes del dia 1

        Calendar today = Calendar.getInstance();
        boolean isRealMonth = (currentYear == today.get(Calendar.YEAR)
                && currentMonth == today.get(Calendar.MONTH));
        int todayDay = today.get(Calendar.DAY_OF_MONTH);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int totalCells = leadingBlanks + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7.0);

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int c = 0; c < 7; c++) {
                int cellIndex = r * 7 + c;
                int dayNumber = cellIndex - leadingBlanks + 1;

                // Parametros de columna: ancho 0 + peso 1 -> 7 columnas iguales.
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

                if (dayNumber < 1 || dayNumber > daysInMonth) {
                    // Hueco: una celda vacia que solo ocupa su columna.
                    View blank = new View(requireContext());
                    blank.setLayoutParams(lp);
                    row.addView(blank);
                    continue;
                }

                View cell = inflater.inflate(R.layout.item_month_day, row, false);
                cell.setLayoutParams(lp);
                bindDayCell(cell, dayNumber, isRealMonth && dayNumber == todayDay);
                row.addView(cell);
            }
            containerCalendar.addView(row);
        }
    }

    // Pinta una celda de dia: numero (con resalte de hoy/seleccionado), puntitos
    // de categoria y el click que cambia el dia seleccionado.
    private void bindDayCell(View cell, int dayNumber, boolean isToday) {
        TextView number = cell.findViewById(R.id.tv_day_number);
        LinearLayout dotsRow = cell.findViewById(R.id.dots_row);
        number.setText(String.valueOf(dayNumber));

        boolean isSelected = (dayNumber == selectedDay);
        if (isSelected) {
            // Seleccionado: circulo ambar relleno + texto oscuro (mismo criterio
            // que el selector de dias de la pantalla principal).
            number.setBackgroundResource(R.drawable.bg_day_selected);
            number.setTextColor(requireContext().getColor(R.color.accent_amber_dark));
        } else {
            number.setBackground(null);
            // Hoy (si no esta seleccionado): texto ambar para ubicarlo de un vistazo.
            number.setTextColor(requireContext().getColor(isToday ? R.color.accent_amber : R.color.text_primary));
        }

        // Puntitos: colores distintos de las categorias con tarea ese dia. Pasamos
        // tambien la FECHA exacta porque las tareas puntuales solo "ocurren" ese dia.
        int ourDay = ourDayOfMonthDay(dayNumber);
        String dateStr = dateOfMonthDay(dayNumber);
        renderDots(dotsRow, dateStr, ourDay);

        cell.setOnClickListener(v -> {
            selectedDay = dayNumber;
            renderCalendar(); // repinta resaltes
            renderAgenda();   // muestra la agenda del nuevo dia
        });
    }

    // Añade hasta 4 puntitos (circulos de 5dp) con los colores de las categorias
    // que tienen alguna tarea ese dia de semana. LinkedHashSet -> colores unicos
    // conservando el orden de aparicion.
    private void renderDots(LinearLayout dotsRow, String dateStr, int ourDay) {
        dotsRow.removeAllViews();
        if (monthData == null) return;

        Set<Integer> colors = new LinkedHashSet<>();
        for (Task t : monthData.activeTasks) {
            if (t.occursOn(dateStr, ourDay)) colors.add(CardStyle.taskColor(t.color));
        }

        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(5 * density);
        int margin = Math.round(1.5f * density);
        int shown = 0;
        for (int color : colors) {
            if (shown >= 4) break; // no saturar la celda
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable circle =
                new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            circle.setColor(color);
            dot.setBackground(circle);
            dotsRow.addView(dot);
            shown++;
        }
    }

    // ============================================================
    //  AGENDA del dia seleccionado. Tareas de ese dia ordenadas por hora de
    //  notificacion, cada una con su tarjeta de solo lectura.
    // ============================================================
    private void renderAgenda() {
        containerAgenda.removeAllViews();
        if (monthData == null) return;

        // Fecha exacta del dia elegido (yyyy-MM-dd) y su dia de semana.
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(currentYear, currentMonth, selectedDay);
        String dateStr = DateUtils.calendarToString(cal);
        int ourDay = DateUtils.calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK));

        // Cabecera del dia: "Miercoles 18 de junio".
        tvDayHead.setText(WEEKDAY_NAMES[ourDay] + " " + selectedDay + " de "
            + MONTH_NAMES[currentMonth].toLowerCase(Locale.getDefault()));

        // Tareas de ese dia (recurrentes por dia de semana + puntuales por fecha),
        // ordenadas por hora:minuto.
        List<Task> dayTasks = new ArrayList<>();
        for (Task t : monthData.activeTasks) {
            if (t.occursOn(dateStr, ourDay)) dayTasks.add(t);
        }
        Collections.sort(dayTasks, (a, b) -> {
            int am = a.notificationHour * 60 + a.notificationMinute;
            int bm = b.notificationHour * 60 + b.notificationMinute;
            return Integer.compare(am, bm);
        });

        if (dayTasks.isEmpty()) {
            tvDayCount.setText("0/0 hechas");
            tvAgendaEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvAgendaEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int done = 0;
        for (Task t : dayTasks) {
            boolean isDone = monthData.completedKeys.contains(MonthData.key(t.id, dateStr));
            if (isDone) done++;
            View card = inflater.inflate(R.layout.item_agenda_task, containerAgenda, false);
            bindAgendaCard(card, t, isDone);
            containerAgenda.addView(card);
        }
        tvDayCount.setText(done + "/" + dayTasks.size() + " hechas");
    }

    // Pinta una tarjeta de la agenda (solo lectura): hora, nodo del riel, nombre,
    // categoria y estado. El fondo de la tarjeta se dibuja en codigo (mismo estilo
    // que la lista diaria) porque el color depende de la categoria.
    private void bindAgendaCard(View card, Task task, boolean isDone) {
        TextView hour = card.findViewById(R.id.tv_agenda_hour);
        TextView name = card.findViewById(R.id.tv_agenda_name);
        TextView category = card.findViewById(R.id.tv_agenda_category);
        TextView status = card.findViewById(R.id.tv_agenda_status);
        View node = card.findViewById(R.id.agenda_node);
        LinearLayout body = card.findViewById(R.id.agenda_card);

        int accent = CardStyle.taskColor(task.color);
        hour.setText(DateUtils.formatTime(task.notificationHour, task.notificationMinute));
        name.setText(task.name);
        category.setText(task.category);
        body.setBackground(CardStyle.cardBackground(requireContext(), accent));

        // Estado de SOLO LECTURA: hecha (ambar, con check) o pendiente (gris).
        if (isDone) {
            status.setText("\u2713 hecha");
            status.setTextColor(requireContext().getColor(R.color.accent_amber));
        } else {
            status.setText("pendiente");
            status.setTextColor(requireContext().getColor(R.color.text_secondary));
        }

        // Nodo del riel: relleno ambar si esta hecha, hueco (borde ambar sobre el
        // fondo) si esta pendiente. Se dibuja en codigo para no crear dos drawables.
        node.setBackground(buildNode(isDone));
    }

    // ============================================================
    //  Helpers de dibujo y de datos (reflejan los de TaskAdapter para mantener
    //  identico el estilo de tarjeta y el mapeo de colores por categoria).
    // ============================================================

    // Circulo del nodo del riel. Hecha: relleno ambar. Pendiente: fondo del lienzo
    // (bg_dark) con un aro ambar, para que se vea "hueco".
    private android.graphics.drawable.Drawable buildNode(boolean isDone) {
        float density = getResources().getDisplayMetrics().density;
        android.graphics.drawable.GradientDrawable circle =
            new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        int amber = requireContext().getColor(R.color.accent_amber);
        if (isDone) {
            circle.setColor(amber);
        } else {
            circle.setColor(requireContext().getColor(R.color.bg_dark));
            circle.setStroke(Math.round(2 * density), amber);
        }
        return circle;
    }

    // El fondo de la tarjeta y el mapeo de color por categoria ahora viven en
    // CardStyle (utils), compartidos con TaskAdapter. Ver CardStyle.cardBackground
    // y CardStyle.taskColor.

    // La decision de "¿esta tarea ocurre este dia?" vive en Task.occursOn(), que
    // unifica recurrentes (por dia de semana) y puntuales (por fecha exacta).

    // Dia de semana (Lunes=1..Domingo=7) del dia 'dayNumber' del mes mostrado.
    private int ourDayOfMonthDay(int dayNumber) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(currentYear, currentMonth, dayNumber);
        return DateUtils.calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK));
    }

    // Fecha "yyyy-MM-dd" del dia 'dayNumber' del mes mostrado. La necesita occursOn()
    // para comparar contra la fecha de las tareas puntuales.
    private String dateOfMonthDay(int dayNumber) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(currentYear, currentMonth, dayNumber);
        return DateUtils.calendarToString(cal);
    }
}
