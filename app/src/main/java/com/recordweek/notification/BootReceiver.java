package com.recordweek.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.recordweek.data.AppDatabase;
import com.recordweek.data.Task;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(context);
                List<Task> activeTasks = db.taskDao().getActiveTasksSync();
                for (Task task : activeTasks) NotificationScheduler.scheduleTask(context, task);
            });
        }
    }
}
