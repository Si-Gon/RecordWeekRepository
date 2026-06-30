package com.recordweek.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.recordweek.MainActivity;
import com.recordweek.R;
import com.recordweek.utils.SettingsManager;

public class NotificationReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "recordweek_channel";
    public static final String CHANNEL_NAME = "Recordatorios semanales";
    public static final String EXTRA_TASK_ID = "task_id";
    public static final String EXTRA_TASK_NAME = "task_name";
    public static final String EXTRA_TASK_CATEGORY = "task_category";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Portero maestro: si el usuario apago las notificaciones en Configuracion,
        // no mostramos nada aunque la alarma ya estuviera programada en el sistema.
        if (!new SettingsManager(context).areNotificationsEnabled()) return;
        int taskId = intent.getIntExtra(EXTRA_TASK_ID, -1);
        String taskName = intent.getStringExtra(EXTRA_TASK_NAME);
        String taskCategory = intent.getStringExtra(EXTRA_TASK_CATEGORY);
        if (taskId == -1 || taskName == null) return;
        createNotificationChannel(context);
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, taskId, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(taskName)
            .setContentText("Categoria: " + (taskCategory != null ? taskCategory : ""))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(taskId, builder.build());
    }

    private void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Notificaciones de tareas semanales programadas");
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }
}
