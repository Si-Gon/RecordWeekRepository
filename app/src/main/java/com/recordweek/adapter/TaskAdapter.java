package com.recordweek.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.recordweek.R;
import com.recordweek.data.DailyCompletion;
import com.recordweek.data.Task;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {
    private List<Task> taskList = new ArrayList<>();
    private Map<Integer, DailyCompletion> completionsMap = new HashMap<>();
    private OnTaskClickListener listener;
    private OnCompletionChangedListener completionListener;
    // Solo se pueden marcar/desmarcar tareas del dia de HOY. Cuando el usuario mira
    // otro dia (pasado o futuro) ponemos editable=false: el checkbox sigue mostrando
    // si se completo (consultar el historial), pero se ve apagado y no deja cambiarlo.
    private boolean editable = true;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
        void onTaskLongClick(Task task);
        // Nuevo: el usuario pulso el boton de tres puntos de esta tarea.
        // La Activity decide que menu mostrar (Editar / Quitar de este dia / Eliminar).
        void onTaskMenuClick(Task task);
    }
    public interface OnCompletionChangedListener {
        void onCompletionChanged(Task task, boolean completed);
    }

    public TaskAdapter(OnTaskClickListener listener, OnCompletionChangedListener completionListener) {
        this.listener = listener;
        this.completionListener = completionListener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(taskList.get(position), completionsMap.get(taskList.get(position).id));
    }

    @Override
    public int getItemCount() { return taskList.size(); }

    public void setTasks(List<Task> tasks) {
        this.taskList = tasks != null ? tasks : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setCompletions(List<DailyCompletion> completions) {
        completionsMap.clear();
        if (completions != null) for (DailyCompletion dc : completions) completionsMap.put(dc.taskId, dc);
        notifyDataSetChanged();
    }

    // La Activity llama esto al cambiar de dia: true solo si el dia mostrado es hoy.
    // Si cambia respecto al valor anterior, repintamos para aplicar el bloqueo.
    public void setEditable(boolean editable) {
        if (this.editable != editable) {
            this.editable = editable;
            notifyDataSetChanged();
        }
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final View layoutSubtitle;
        private final TextView taskName, taskCategory, taskTime, taskDescription;
        private final CheckBox checkBoxCompleted;
        private final android.widget.ImageButton btnMenu;
        private boolean isExpanded = false;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutSubtitle = itemView.findViewById(R.id.layout_subtitle);
            taskName = itemView.findViewById(R.id.tv_task_name);
            taskCategory = itemView.findViewById(R.id.tv_task_category);
            taskTime = itemView.findViewById(R.id.tv_task_time);
            taskDescription = itemView.findViewById(R.id.tv_task_description);
            checkBoxCompleted = itemView.findViewById(R.id.checkbox_completed);
            btnMenu = itemView.findViewById(R.id.btn_task_menu);
        }

        public void bind(Task task, DailyCompletion completion) {
            isExpanded = false;
            taskName.setText(task.name);
            taskCategory.setText(task.category);
            taskTime.setText(String.format("%02d:%02d", task.notificationHour, task.notificationMinute));
            // Fondo de la tarjeta (tarjeta redondeada + degradado + acento) segun categoria.
            itemView.setBackground(buildCardBackground(resolveTaskColor(task.color)));
            applyExpandedState(task);
            // Estado real de la marca para ESTE dia (lo usamos tambien para revertir
            // si el usuario toca un checkbox bloqueado).
            final boolean isChecked = completion != null && completion.completed == 1;
            // Apariencia: si no es hoy, lo mostramos "apagado" (semitransparente)
            // para comunicar visualmente que es solo de consulta.
            checkBoxCompleted.setAlpha(editable ? 1.0f : 0.4f);
            // Pintamos el estado y enganchamos el listener correcto. Lo hacemos en un
            // metodo aparte porque necesitamos volver a llamarlo al revertir (ver abajo)
            // y asi no duplicamos el patron quitar-listener / setChecked / poner-listener.
            bindCheckbox(task, isChecked);
            itemView.setOnClickListener(v -> {
                isExpanded = !isExpanded;
                applyExpandedState(task);
                if (listener != null) listener.onTaskClick(task);
            });
            itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onTaskLongClick(task);
                return true;
            });
            btnMenu.setOnClickListener(v -> {
                if (listener != null) listener.onTaskMenuClick(task);
            });
        }

        // Pinta el checkbox con su estado real y le pone el listener adecuado.
        // Patron clave (por el reciclaje del RecyclerView): SIEMPRE quitar el listener
        // antes de setChecked, para que ese cambio programatico no dispare el callback.
        private void bindCheckbox(Task task, boolean isChecked) {
            checkBoxCompleted.setOnCheckedChangeListener(null);
            checkBoxCompleted.setChecked(isChecked);
            checkBoxCompleted.setOnCheckedChangeListener((buttonView, checked) -> {
                if (editable) {
                    // Dia de hoy: guardamos el cambio normalmente.
                    if (completionListener != null) completionListener.onCompletionChanged(task, checked);
                } else {
                    // Dia bloqueado: revertimos al estado real y avisamos. Reusar
                    // bindCheckbox aqui reaplica el patron quitar/poner listener, asi
                    // la reversion no vuelve a disparar este callback (sin recursion).
                    bindCheckbox(task, isChecked);
                    android.widget.Toast.makeText(buttonView.getContext(),
                        "Solo puedes marcar las tareas de hoy",
                        android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void applyExpandedState(Task task) {
            layoutSubtitle.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            boolean hasDescription = task.description != null && !task.description.isEmpty();
            if (hasDescription) {
                taskDescription.setText(task.description);
                taskDescription.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            } else {
                taskDescription.setVisibility(View.GONE);
            }
        }

        // Construye el fondo de la tarjeta en tiempo de ejecucion (no se puede en XML
        // porque el color depende de la categoria). Es un LayerDrawable de dos capas:
        //   Capa 0 (tarjeta): rectangulo redondeado con degradado horizontal que va del
        //     color de la categoria muy diluido (10%) hacia la superficie, mas un borde
        //     tenue. Da el "cuerpo" de la tarjeta y el tinte suave de fondo.
        //   Capa 1 (acento): franja de 4dp del color pleno de la categoria, con las
        //     esquinas izquierdas redondeadas, anclada al borde izquierdo.
        // Se crea una instancia nueva por bind: los Drawables de fondo no deben
        // compartirse entre varias vistas visibles (comparten bounds y se pisan).
        private android.graphics.drawable.Drawable buildCardBackground(int accentColor) {
            float density = itemView.getResources().getDisplayMetrics().density;
            float corner = 12 * density;
            int stroke = Math.round(1 * density);
            int accentWidth = Math.round(4 * density);

            int surface = Color.parseColor("#121A2E"); // surface_elevated
            // Mezcla 8% color de categoria + 92% superficie. Punto medio: 10% apagaba el
            // texto secundario (categoria/hora/descripcion) y 6% casi no se percibia el
            // degradado; 8% deja ver el tinte sin restar legibilidad. El degradado de 3
            // paradas ya lo confina al tercio izquierdo, lejos del grueso del texto.
            int tint = androidx.core.graphics.ColorUtils.blendARGB(surface, accentColor, 0.08f);

            // 3 paradas {tinte, superficie, superficie}: el tinte se concentra en el
            // tercio izquierdo (donde esta el acento) y la superficie plana ocupa el
            // resto, dejando el texto sobre fondo limpio.
            android.graphics.drawable.GradientDrawable card =
                new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{ tint, surface, surface });
            card.setCornerRadius(corner);
            card.setStroke(stroke, Color.parseColor("#232C40")); // borde tenue

            android.graphics.drawable.GradientDrawable accent =
                new android.graphics.drawable.GradientDrawable();
            accent.setColor(accentColor);
            // Radios por esquina: [supIzq, supDer, infDer, infIzq] (x,y cada uno).
            // Solo redondeamos las dos izquierdas para que encaje en la esquina de la tarjeta.
            accent.setCornerRadii(new float[]{ corner, corner, 0, 0, 0, 0, corner, corner });

            android.graphics.drawable.LayerDrawable layers =
                new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{ card, accent });
            // setLayerWidth/Gravity: API 23+ (minSdk 29, ok). Fijan el ancho del acento
            // a 4dp y lo pegan a la izquierda sin necesitar el ancho total de la tarjeta.
            layers.setLayerWidth(1, accentWidth);
            layers.setLayerGravity(1, android.view.Gravity.START);
            return layers;
        }

        private int resolveTaskColor(String colorName) {
            if (colorName == null) return Color.parseColor("#E0922F");
            switch (colorName) {
                case "RED": return Color.parseColor("#EF5350");
                case "ORANGE": return Color.parseColor("#FFA726");
                case "YELLOW": return Color.parseColor("#FFEE58");
                case "GREEN": return Color.parseColor("#66BB6A");
                case "BLUE": return Color.parseColor("#5B8DEF");
                case "PURPLE": return Color.parseColor("#AB47BC");
                case "PINK": return Color.parseColor("#EC407A");
                default: return Color.parseColor("#E0922F");
            }
        }
    }
}
