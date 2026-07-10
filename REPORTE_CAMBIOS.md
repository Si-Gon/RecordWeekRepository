# Reporte de cambios — auditoría Record Week

Rama: `sigon`. Todos los cambios están sin commitear (working tree), listos para que los pruebes antes de confirmar.

---

## Resumen en una línea

Arreglé un bug real de notificaciones (las recurrentes sonaban una sola vez), y luego eliminé duplicación y código muerto: **-319 / +253 líneas netas** y **un archivo nuevo** (`CardStyle.java`) que centraliza lo que antes estaba copiado en tres sitios.

---

## Cambio #1 — Bug: las notificaciones recurrentes solo sonaban una vez (CRÍTICO)

### Qué pasaba

En Android, una alarma exacta programada con `setExactAndAllowWhileIdle(...)` es de **un solo disparo**. No se repite sola. El código programaba la alarma del "próximo lunes 8:00", esa alarma sonaba **una vez**, y nadie volvía a programar la del lunes siguiente. Resultado: un recordatorio "semanal" que en la práctica avisaba una sola vez y nunca más.

Es un error fácil de cometer viniendo de otros entornos, porque uno espera que "alarma semanal" signifique que el sistema la repite. Android no lo hace con las alarmas exactas; hay que **reprogramarla a mano cada vez que suena**. Ese es el patrón estándar de una app de despertador.

### Cómo lo arreglé

El patrón se llama *self-rescheduling*: cuando la alarma dispara y entra en `NotificationReceiver.onReceive(...)`, lo primero que hago es **volver a programar la ocurrencia de la semana siguiente**, y solo después muestro la notificación.

Para que el receiver pueda reprogramar sin volver a abrir la base de datos, ahora la alarma lleva consigo (como *extras* del `Intent`) el día de la semana, la hora y el minuto. Con eso se basta.

Archivos tocados:

- **`NotificationScheduler.java`**: extraje la creación de la alarma de un día recurrente a un método reutilizable `scheduleRecurringDay(...)`, y añadí `rescheduleNextWeek(...)` que es el que llama el receiver. Ambos meten los nuevos extras (`EXTRA_DAY_OF_WEEK`, `EXTRA_HOUR`, `EXTRA_MINUTE`) en el Intent. El truco fino: `getNextTriggerTime(...)`, al ejecutarse justo después de que la alarma sonó hoy, ve que la hora de hoy ya pasó y devuelve automáticamente la ocurrencia de la próxima semana. Así la cadena semanal se mantiene viva sola.
- **`NotificationReceiver.java`**: reordené `onReceive(...)`. Ahora reprograma **antes** del "portero" que comprueba si las notificaciones están activadas.

### El detalle sutil (por qué el orden importa)

La reprogramación tiene que ir **antes** del `if (!areNotificationsEnabled()) return;`. Si estuviera después, pasaría esto: apagas las notificaciones un día → la alarma dispara → entra al receiver → el `return` corta la ejecución **antes** de reprogramar → nunca se agenda la semana siguiente → cuando vuelves a activar las notificaciones, ya no hay ninguna alarma viva y **el recordatorio queda muerto para siempre**. Poniéndolo antes, la cadena de alarmas sigue viva aunque las notificaciones estén silenciadas; simplemente no se muestra nada mientras estén apagadas.

> **Nota sobre WorkManager**: sé que lo mencionaste. Lo descarté a propósito. WorkManager tiene un intervalo mínimo de 15 minutos y **no garantiza la hora exacta** (Android agrupa y retrasa el trabajo por Doze/batching). Para un recordatorio "a las 8:00 en punto", AlarmManager con alarma exacta es la herramienta correcta; WorkManager llegaría tarde e impreciso. No perdiste robustez, al contrario.

### Cómo probarlo

Crea una tarea recurrente cuya hora de notificación sea dentro de 1–2 minutos, en el día de hoy. Debería sonar. Lo ideal para confirmar el arreglo es verificar que **la semana siguiente también** queda agendada — se puede ver con `adb shell dumpsys alarm | grep recordweek` justo después de que suene: debe aparecer una alarma futura para dentro de 7 días.

---

## Cambio #2 — Formateo de fechas unificado y a prueba de hilos

### El problema de fondo

Había `new SimpleDateFormat("yyyy-MM-dd", ...)` repartido por 7 lugares. Dos cosas malas:

1. **`SimpleDateFormat` NO es thread-safe.** El repositorio usa un *pool* de 4 hilos (`Executors.newFixedThreadPool(4)`). Si dos hilos comparten una misma instancia de `SimpleDateFormat`, pueden producir fechas corruptas de forma intermitente (bugs rarísimos de reproducir). En el repo cada uso creaba su instancia local, así que no había bug *activo* — pero era una trampa esperando a que alguien "optimizara" moviendo el formatter a un campo compartido.
2. **Duplicación**: el mismo string de formato copiado siete veces.

### Qué hice

Añadí dos helpers a **`DateUtils.java`** (que ya usa `java.time`, thread-safe):

- `calendarToString(Calendar)` → construye `"yyyy-MM-dd"` con `String.format` (inmutable, seguro entre hilos).
- `stringToCalendar(String)` → hace el camino inverso.

Y reemplacé los 7 usos de `SimpleDateFormat` en: `TaskRepository` (3 sitios: `loadMonthData`, `countScheduledInElapsedWeek`, `computeStreaks`), `MonthlyFragment` (2), `DiaryFragment` (1) y `NotificationScheduler` (1). Quité los `import` de `SimpleDateFormat`/`Locale` que quedaron sin uso.

Ojo con un matiz: `Calendar.MONTH` es 0-based (enero = 0), por eso en `calendarToString` sumo 1 al mes. Y `stringToCalendar` hace `cal.clear()` primero, así el Calendar no arrastra la hora/minuto del momento actual.

---

## Cambios #4 y #5 — Un solo lugar para el color y el fondo de tarjeta

### El problema

El mapeo "nombre de color → valor real" y el constructor del fondo de tarjeta estaban **triplicados**:

- `TaskAdapter` tenía `resolveTaskColorStatic(...)` (devuelve enteros `0xFF...`) y `buildCardBackground(...)`.
- `MonthlyFragment` tenía **su propia copia** de ambos (con `Color.parseColor("#...")`).
- `AddTaskActivity` tenía `colorResForName(...)` (mapeaba a `R.color.task_*`).

Tres representaciones del mismo dato. Si algún día cambias el rojo, tienes que acordarte de tocar tres sitios; olvidar uno = inconsistencia visual silenciosa.

### Qué hice

Creé **`utils/CardStyle.java`** con:

- `taskColor(String)` → devuelve el ARGB (`0xFF...`). Verifiqué que estos hex son **idénticos** a los de `res/values/colors.xml`, así que el resultado visual es exactamente el mismo que antes.
- `cardBackground(Context, int)` → el `LayerDrawable` de la tarjeta (degradado + borde + franja de acento), portado tal cual desde `TaskAdapter`.

Luego:

- `TaskAdapter`: borré los tres métodos locales; ahora llama a `CardStyle`.
- `MonthlyFragment`: borré su copia de `buildCardBackground`/`resolveTaskColor`; quité los imports `Color`, `ColorUtils`, `Gravity` que quedaron huérfanos. Dejé intacto `buildNode(...)` (ese sí es de uso único, no había motivo para moverlo).
- `AddTaskActivity`: borré `colorResForName(...)`; ahora el punto indicador de color se tiñe con `CardStyle.taskColor(...)`.

---

## Cambios #6 y #7 — Código muerto eliminado

Antes de borrar, verifiqué con búsqueda que **nadie los llamaba** desde producción ni tests:

- **`getCompletionLive(...)`**: existía en tres capas (DAO → Repository → ViewModel) pero **ningún UI lo usaba**. Era una cadena completa que no llevaba a ninguna parte. Borrado en las tres.
- **`loadAnalytics()`** sin argumentos (los overloads en ViewModel y Repository): solo llamaban a `loadAnalytics(0)`. Ningún otro sitio los usaba. Borrados; queda solo `loadAnalytics(int weekOffset)`, que es el que de verdad se usa.

---

## Tests

- **`TaskAdapterLogicTest.java`**: los 9 asserts de color ahora apuntan a `CardStyle.taskColor(...)` en vez de al viejo `TaskAdapter.resolveTaskColorStatic(...)`. Mismos valores esperados, así que siguen siendo la red de seguridad que confirma que ningún color cambió al centralizar.
- **`TaskViewModelTest.java`**: no lo toqué. Verifiqué que solo usa `loadAnalytics(int, ...)` (el que sobrevive), no los métodos borrados. Sigue válido.

---

## Lo que TIENES que hacer tú (no lo puedo hacer yo aquí)

No puedo compilar Android en este entorno, así que **la verificación de compilación y tests corre por tu cuenta**:

```
./gradlew testDebugUnitTest      # corre los tests unitarios (incluye los de color)
./gradlew assembleDebug          # confirma que todo compila
```

Si algo no compila, lo más probable es un `import` que se me haya escapado; avísame con el error exacto y lo corrijo. Cuando compile y pasen los tests, prueba en el emulador/teléfono el escenario de la notificación recurrente descrito en el Cambio #1, que es el único cambio de comportamiento real — el resto son refactors que no deberían cambiar nada visible.

---

## Archivos modificados

| Archivo | Cambio |
|---|---|
| `notification/NotificationReceiver.java` | Auto-reprograma la semana siguiente antes del portero de notificaciones |
| `notification/NotificationScheduler.java` | `scheduleRecurringDay` + `rescheduleNextWeek`; usa `DateUtils` |
| `utils/CardStyle.java` | **NUEVO** — color + fondo de tarjeta centralizados |
| `utils/DateUtils.java` | + `calendarToString` / `stringToCalendar` (thread-safe) |
| `adapter/TaskAdapter.java` | Usa `CardStyle`; borrados 3 métodos duplicados |
| `MonthlyFragment.java` | Usa `CardStyle`; borrados duplicados e imports huérfanos |
| `AddTaskActivity.java` | Usa `CardStyle`; borrado `colorResForName` |
| `repository/TaskRepository.java` | `SimpleDateFormat`→`DateUtils`; borrado código muerto |
| `viewmodel/TaskViewModel.java` | Borrados `getCompletionLive` y `loadAnalytics()` sin args |
| `data/DailyCompletionDao.java` | Borrado query `getCompletionLive` |
| `test/.../TaskAdapterLogicTest.java` | Apunta a `CardStyle.taskColor` |
