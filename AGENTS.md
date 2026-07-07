# AGENTS.md — Record Week

**Platform:** Android nativo · Java 1.8 · minSdk 29 · targetSdk 34  
**Package:** `com.recordweek`  
**DB version:** Room v4 (2 migraciones registradas)

\---

## Arquitectura

MVVM estricto. Flujo de dependencias:

```
Fragment/Activity → ViewModel → Repository → DAO → Room (SQLite)
```

* **No** acceder a DAOs directamente desde UI
* **No** hacer operaciones de BD en el hilo principal — siempre `executor.execute()`
* LiveData notifica a UI automáticamente en hilo principal

Capas y sus clases:

|Capa|Clases|
|-|-|
|UI|`MainActivity`, `DiaryFragment`, `MonthlyFragment`, `AnalyticsFragment`, `AddTaskActivity`, `SettingsActivity`, `TaskAdapter`|
|ViewModel|`TaskViewModel`|
|Repository|`TaskRepository`|
|Data|`Task`, `DailyCompletion`, `AppDatabase`, `TaskDao`, `DailyCompletionDao`, `AnalyticsData`, `MonthData`|
|Notificaciones|`NotificationScheduler`, `NotificationReceiver`, `BootReceiver`|
|Utils|`DateUtils`, `SettingsManager`|

\---

## Modelo de datos crítico

### Task

* `specificDate == null` → tarea **recurrente** (usa `daysOfWeek` JSON array)
* `specificDate != null` → tarea **puntual** (ocurre solo en esa fecha)
* `isOneOff()` distingue el tipo
* `occursOn(date, ourDay)` es el **único punto de decisión** para saber si una tarea aplica a un día — no reimplementar esta lógica en otro lado

### Días de semana

* Convención propia: **Lunes=1 … Domingo=7** (NO usa Calendar.DAY\_OF\_WEEK de Java directamente)
* `DateUtils.calendarDayToOurDay()` y `ourDayToCalendarDay()` para traducir — siempre usar estas utilidades

### DailyCompletion

* Índice único `(task\\\_id, date)` — insert usa `OnConflictStrategy.REPLACE`
* Formato de fecha en BD: `"yyyy-MM-dd"` como String — `DateUtils` centraliza todos los cálculos de fecha

\---

## Migraciones de BD

Al modificar entidades Room **siempre** agregar `Migration(x, y)` en `AppDatabase` y registrarla con `.addMigrations()`. Nunca usar `fallbackToDestructiveMigration()`.

Migraciones existentes:

* `v2 → v3`: DROP TABLE `weekly\\\_analysis`
* `v3 → v4`: ALTER TABLE tasks ADD COLUMN `specific\\\_date TEXT`

Versión actual: **v4**. Próxima migración debe ser `v4 → v5`.

\---

## Observers en Fragments

Siempre usar `getViewLifecycleOwner()` en observers dentro de Fragments (no `this`). Llamar `removeObservers()` antes de agregar uno nuevo para evitar acumulación:

```java
viewModel.getTasks().removeObservers(getViewLifecycleOwner());
viewModel.getTasks().observe(getViewLifecycleOwner(), tasks -> { ... });
```

\---

## Notificaciones

* `AlarmManager` exact + `AllowWhileIdle` — requiere permiso `SCHEDULE\\\_EXACT\\\_ALARM` (API 31+)
* `BootReceiver` reprograma todas las alarmas al reinicio del dispositivo
* Al crear, editar o eliminar una tarea, **siempre** llamar a `NotificationScheduler` para actualizar la alarma

\---

## Exportación / Importación JSON

Estrategia "reemplazar todo":

1. Validar que JSON tenga array `"tasks"`
2. `TaskDao.deleteAll()` (CASCADE borra completaciones)
3. Insertar tareas nuevas → construir `Map<oldId, newId>`
4. Insertar completaciones traduciendo `task\\\_id` con el mapa

\---

## Comandos de build

```bash
./gradlew assembleDebug          # compilar
./gradlew installDebug           # instalar en dispositivo
./gradlew testDebugUnitTest      # tests unitarios
./gradlew clean                  # limpiar build
```

\---

## Colores por categoría

Mapeo fijo — no pedir color al usuario:

|Categoría|Color|
|-|-|
|Estudio|BLUE|
|Trabajo|RED|
|Gimnasio|YELLOW|
|Salud|GREEN|
|Tiempo Personal|PINK|
|Taller Deportivo|ORANGE|
|Comida|PURPLE|

\---

## Convenciones

* Toda operación de BD → `ExecutorService` (4 hilos configurados en Repository)
* Callbacks de Repository: interfaces internas (`OnTaskInsertedCallback`, `OnAnalyticsLoadedCallback`, etc.)
* `SettingsManager` para todo lo que use `SharedPreferences`
* Diagramas PlantUML en `docs/architecture/` — no modificar sin actualizar el `.puml` correspondiente

