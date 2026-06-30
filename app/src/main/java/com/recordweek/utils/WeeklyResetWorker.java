package com.recordweek.utils;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// OBSOLETO: este worker antes archivaba el resumen semanal y luego BORRABA todas
// las completaciones (completionDao.deleteAll()). Se desactivo porque las rutinas
// ahora son permanentes y el historial no debe borrarse: Analytics lee directamente
// de la tabla daily_completions. Se deja la clase vacia (no se programa en ningun
// lado y no hace nada) para no romper compilacion si quedara alguna referencia.
public class WeeklyResetWorker extends Worker {
    public WeeklyResetWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // No-op: intencionalmente no hace nada.
        return Result.success();
    }
}
