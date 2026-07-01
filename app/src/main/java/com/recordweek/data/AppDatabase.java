package com.recordweek.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {Task.class, DailyCompletion.class},
    version = 4,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;
    public abstract TaskDao taskDao();
    public abstract DailyCompletionDao dailyCompletionDao();

    // ============================================================
    //  MIGRACION 2 -> 3: se elimino la tabla weekly_analysis.
    //  Antes guardaba un resumen semanal por tarea; quedo obsoleta
    //  cuando Analytics paso a leer directo de daily_completions.
    //
    //  Room compara el esquema que ESPERA (deducido de las @Entity)
    //  contra el esquema GRABADO en el archivo SQLite del telefono.
    //  Al quitar la entidad, esos dos dejan de cuadrar; esta migracion
    //  es la instruccion exacta que cierra esa diferencia: borra solo
    //  la tabla sobrante y CONSERVA tasks y daily_completions.
    //
    //  Por que un Migration y no migracion destructiva: la destructiva
    //  borraria TODA la base (perderias tareas e historial). Esto solo
    //  toca la tabla que sobra.
    // ============================================================
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS weekly_analysis");
        }
    };

    // ============================================================
    //  MIGRACION 3 -> 4: se añadio la columna specific_date a tasks,
    //  para las tareas PUNTUALES (que ocurren una sola vez en una fecha).
    //
    //  Es la operacion de migracion mas segura que existe: ADD COLUMN de
    //  una columna ANULABLE (TEXT sin NOT NULL). Todas las filas que ya
    //  existen reciben NULL automaticamente, y en el modelo NULL significa
    //  "tarea recurrente de siempre". Por eso ninguna tarea tuya cambia de
    //  comportamiento: las viejas siguen siendo recurrentes.
    //
    //  No se usa migracion destructiva (que borraria toda la BD): igual que
    //  la 2->3, solo tocamos lo justo y conservamos tareas e historial.
    // ============================================================
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE tasks ADD COLUMN specific_date TEXT");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "recordweek_database"
                        )
                        // Registramos la migracion explicita. Quitamos
                        // fallbackToDestructiveMigration: ya no queremos la red
                        // que borra todo en silencio; preferimos que la app
                        // exija una migracion bien escrita ante cada cambio de
                        // esquema (asi no se pierden datos por accidente).
                        .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
