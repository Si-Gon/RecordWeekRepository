package com.recordweek.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            return;
        }

        // --- Tarea RECURRENTE: una alarma por cada dia de la semana ---
        try {
            JSONArray daysArray = new JSONArray(task.daysOfWeek);
            for (int i = 0; i < daysArray.length(); i++) {
                int dayOfWeek = daysArray.getInt(i);
                long triggerTime = getNextTriggerTime(dayOfWeek, task.notificationHour, task.notificationMinute);
                Intent intent = new Intent(context, NotificationReceiver.class);
                intent.putExtra(NotificationReceiver.EXTRA_TASK_ID, task.id);
                intent.putExtra(NotificationReceiver.EXTRA_TASK_NAME, task.name);
                intent.putExtra(NotificationReceiver.EXTRA_TASK_CATEGORY, task.category);
                int requestCode = task.id * 10 + dayOfWeek;
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
        } catch (JSONException e) { e.printStackTrace(); }
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

    // Momento exacto (millis) de una fecha "yyyy-MM-dd" a la hora dada. Si la fecha
    // no se puede leer, devolvemos 0 -> el llamador lo interpreta como "ya paso".
    private static long getTriggerForDate(String date, int hour, int minute) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(date));
            cal.set(Calendar.HOUR_OF_DAY, hour);
            cal.set(Calendar.MINUTE, minute);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private static long getNextTriggerTime(int dayOfWeek, int hour, int minute) {
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
