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
        try {
            JSONArray daysArray = new JSONArray(task.daysOfWeek);
            for (int i = 0; i < daysArray.length(); i++) {
                int dayOfWeek = daysArray.getInt(i);
                int requestCode = task.id * 10 + dayOfWeek;
                Intent intent = new Intent(context, NotificationReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pendingIntent != null) { alarmManager.cancel(pendingIntent); pendingIntent.cancel(); }
            }
        } catch (JSONException e) { e.printStackTrace(); }
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
