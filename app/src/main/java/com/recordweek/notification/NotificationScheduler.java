package com.recordweek.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.recordweek.data.Task;
import com.recordweek.utils.DateUtils;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.Calendar;

public class NotificationScheduler {
    public static void scheduleTask(Context context, Task task) {
        if (task.isActive == 0) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // --- Tarea PUNTUAL: una sola alarma, en su fecha exacta ---
        // Si esa fecha/hora ya paso, no programamos nada (no hay nada que avisar).
        // Como el receiver NO reprograma, la notificacion es de una sola vez de forma
        // natural. Al reiniciar el telefono, BootReceiver vuelve a llamar aqui: si la
        // fecha ya paso, este metodo no hace nada; si sigue en el futuro, la repone.
        if (task.isOneOff()) {
            long triggerTime = getTriggerForDate(task.specificDate, task.notificationHour, task.notificationMinute);
            if (triggerTime <= System.currentTimeMillis()) return;
            Intent intent = new Intent(context, NotificationReceiver.class);
            intent.putExtra(NotificationReceiver.EXTRA_TASK_ID, task.id);
            intent.putExtra(NotificationReceiver.EXTRA_TASK_NAME, task.name);
            intent.putExtra(NotificationReceiver.EXTRA_TASK_CATEGORY, task.category);
            // Los recurrentes usan requestCode = id*10 + dia (dia = 1..7), asi que el
            // "+0" (id*10) nunca lo usan y queda libre para la unica alarma puntual.
            int requestCode = task.id * 10;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            scheduleExactAlarm(alarmManager, triggerTime, pendingIntent);
            return;
        }

        // --- Tarea RECURRENTE: una alarma por cada dia de la semana ---
        try {
            JSONArray daysArray = new JSONArray(task.daysOfWeek);
            for (int i = 0; i < daysArray.length(); i++) {
                int dayOfWeek = daysArray.getInt(i);
                scheduleRecurringDay(context, alarmManager, task.id, task.name, task.category,
                    dayOfWeek, task.notificationHour, task.notificationMinute);
            }
        } catch (JSONException e) { e.printStackTrace(); }
    }

    // Arma (o reprograma) la alarma de UN dia recurrente concreto. Se usa en dos
    // momentos: al crear/editar la tarea (bucle de arriba) y desde el receiver para
    // dejar lista la alarma de la SEMANA SIGUIENTE. Por que hace falta reprogramar:
    // scheduleExactAlarm usa setExact..., que es de UN SOLO DISPARO; sin volver a
    // programar, un recordatorio "semanal" sonaria una vez y nunca mas.
    //
    // El requestCode (id*10 + dia) es el MISMO que al crear la alarma, asi que con
    // FLAG_UPDATE_CURRENT simplemente pisamos la ranura de ese dia (no se duplican).
    // Metemos dia/hora/minuto como extras para que el receiver tenga todo lo que
    // necesita para reprogramarse solo, sin volver a tocar la base de datos.
    static void scheduleRecurringDay(Context context, AlarmManager alarmManager,
            int taskId, String taskName, String taskCategory,
            int dayOfWeek, int hour, int minute) {
        long triggerTime = getNextTriggerTime(dayOfWeek, hour, minute);
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.putExtra(NotificationReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(NotificationReceiver.EXTRA_TASK_NAME, taskName);
        intent.putExtra(NotificationReceiver.EXTRA_TASK_CATEGORY, taskCategory);
        intent.putExtra(NotificationReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek);
        intent.putExtra(NotificationReceiver.EXTRA_HOUR, hour);
        intent.putExtra(NotificationReceiver.EXTRA_MINUTE, minute);
        int requestCode = taskId * 10 + dayOfWeek;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        scheduleExactAlarm(alarmManager, triggerTime, pendingIntent);
    }

    // Version comoda para el receiver: consigue el AlarmManager por su cuenta y
    // delega en scheduleRecurringDay. getNextTriggerTime, al ejecutarse justo
    // despues de que la alarma sono hoy, ve que la hora de hoy ya paso y devuelve
    // la ocurrencia de la proxima semana. Asi la cadena semanal se mantiene viva.
    static void rescheduleNextWeek(Context context, int taskId, String taskName,
            String taskCategory, int dayOfWeek, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        scheduleRecurringDay(context, alarmManager, taskId, taskName, taskCategory,
            dayOfWeek, hour, minute);
    }

    public static void cancelTask(Context context, Task task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Puntual: solo hay una alarma (requestCode = id*10).
        if (task.isOneOff()) {
            cancelCode(context, alarmManager, task.id * 10);
            return;
        }
        // Recurrente: cancelamos la alarma de cada dia (mismo requestCode que al crearla).
        try {
            JSONArray daysArray = new JSONArray(task.daysOfWeek);
            for (int i = 0; i < daysArray.length(); i++) {
                int dayOfWeek = daysArray.getInt(i);
                cancelCode(context, alarmManager, task.id * 10 + dayOfWeek);
            }
        } catch (JSONException e) { e.printStackTrace(); }
    }

    // Cancela una alarma concreta por su requestCode. FLAG_NO_CREATE: si no existe
    // esa alarma, devuelve null y no la creamos solo para cancelarla.
    private static void cancelCode(Context context, AlarmManager alarmManager, int requestCode) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) { alarmManager.cancel(pendingIntent); pendingIntent.cancel(); }
    }

    // API 31+: si el usuario revoco SCHEDULE_EXACT_ALARM, cae a setAndAllowWhileIdle (inexacta).
    static void scheduleExactAlarm(AlarmManager alarmManager, long triggerTime, PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    // Momento exacto (millis) de una fecha "yyyy-MM-dd" a la hora dada. Si la fecha
    // no se puede leer, devolvemos 0 -> el llamador lo interpreta como "ya paso".
    // Usamos DateUtils.stringToCalendar (mismo parser que el resto de la app); ya
    // deja SECOND/MILLISECOND en cero (hace clear()), asi que solo fijamos hora y minuto.
    static long getTriggerForDate(String date, int hour, int minute) {
        Calendar cal = DateUtils.stringToCalendar(date);
        if (cal == null) return 0;
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        return cal.getTimeInMillis();
    }

    static long getNextTriggerTime(int dayOfWeek, int hour, int minute) {
        int calendarDay = DateUtils.ourDayToCalendarDay(dayOfWeek);
        Calendar now = Calendar.getInstance();
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.HOUR_OF_DAY, hour);
        trigger.set(Calendar.MINUTE, minute);
        trigger.set(Calendar.SECOND, 0);
        trigger.set(Calendar.MILLISECOND, 0);
        trigger.set(Calendar.DAY_OF_WEEK, calendarDay);
        if (trigger.before(now)) trigger.add(Calendar.WEEK_OF_YEAR, 1);
        return trigger.getTimeInMillis();
    }
}
