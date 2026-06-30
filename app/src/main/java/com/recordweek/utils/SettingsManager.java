package com.recordweek.utils;

import android.content.Context;
import android.content.SharedPreferences;

// Capa de acceso a las preferencias de la app.
// SharedPreferences = almacenamiento clave-valor que Android guarda en un XML
// privado. Ideal para flags pequenos (como "notificaciones activas"). Es el
// equivalente a un dict de Python persistido a disco. Centralizamos aqui las
// claves para no repetir strings sueltos por la app y evitar errores de tipeo.
public class SettingsManager {

    private static final String PREFS_NAME = "recordweek_settings";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notifications_enabled";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        // getApplicationContext evita fugas de memoria si nos pasan una Activity.
        prefs = context.getApplicationContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Interruptor maestro de notificaciones. Por defecto true (activadas).
    public boolean areNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply();
    }
}
