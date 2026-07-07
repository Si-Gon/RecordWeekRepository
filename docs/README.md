# Record Week — Documentación Técnica Completa

**Versión:** 2.0.1 · **versionCode 6** · **Fecha:** 07/07/2026
**Plataforma:** Android (minSdk 29 · targetSdk 34)
**Paquete:** `com.recordweek`

---

## Índice

1. [Resumen Ejecutivo](#1-resumen-ejecutivo)
2. [Arquitectura de Capas](#2-arquitectura-de-capas)
3. [Diagramas C4](#3-diagramas-c4)
4. [Modelo de Datos](#4-modelo-de-datos)
5. [Flujo de Navegación](#5-flujo-de-navegación)
6. [Flujos Principales (Sequence Diagrams)](#6-flujos-principales)
7. [Notificaciones y Alarmas](#7-notificaciones-y-alarmas)
8. [Exportación / Importación](#8-exportación--importación)
9. [Decisiones Arquitectónicas (ADRs)](#9-decisiones-arquitectónicas)
10. [Guía para Desarrolladores](#10-guía-para-desarrolladores)
11. [Diagramas PlantUML](#11-diagramas-plantuml)

---

## 1. Resumen Ejecutivo

**Record Week** es una aplicación Android nativa para el tracking de hábitos y tareas semanales. Permite al usuario crear tareas recurrentes (días de la semana) o puntuales (fecha única), marcar su cumplimiento diario y visualizar analíticas de progreso.

### Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| **Lenguaje** | Java 1.8 |
| **UI** | Android Views (XML layouts) |
| **Navegación** | BottomNavigationView + Fragment replace |
| **Arquitectura** | MVVM (Model-View-ViewModel) |
| **Persistencia** | Room (SQLite) v2.6.1 |
| **Reactividad** | LiveData + ViewModel |
| **Gráficos** | MPAndroidChart v3.1.0 |
| **Notificaciones** | AlarmManager + NotificationManager |
| **Tareas en background** | ExecutorService (4 hilos) |
| **Splash** | AndroidX SplashScreen API |
| **Build** | Gradle 8.2.0, compileSdk 34 |

### Funcionalidades Principales

- **Vista Diaria:** Lista de tareas del día con selector de día (Lun–Dom). Marcar/desmarcar completadas (solo hoy).
- **Vista Mensual:** Calendario compacto con puntitos de categoría. Agenda del día seleccionado (solo lectura).
- **Vista Análisis:** Cumplimiento semanal navegable (histórico), racha actual/mejor, total histórico, gráfico de actividad por día, cumplimiento por categoría.
- **Tareas:** Crear/editar/eliminar. Dos tipos: recurrente (días semana, JSON array) o puntual (fecha específica). Categoría asigna color automáticamente.
- **Notificaciones:** AlarmManager (exacta, con fallback a inexacta si el permiso de alarmas exactas está revocado en API 31+). Reprogramación al reinicio (BootReceiver con `goAsync`).
- **Exportar/Importar:** Respaldo completo en JSON (tareas + completaciones). Estrategia "reemplazar todo" con mapeo de IDs.
- **Tema oscuro:** Azul medianoche (#0D1117) + ámbar (#E0922F).

---

## 2. Arquitectura de Capas

La aplicación sigue un patrón **MVVM** con separación en 4 capas:

```
┌─────────────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER (UI)                    │
│  MainActivity · DiaryFragment · MonthlyFragment             │
│  AnalyticsFragment · AddTaskActivity · SettingsActivity     │
│  TaskAdapter · TaskViewModel                                │
├─────────────────────────────────────────────────────────────┤
│                    DOMAIN LAYER                              │
│  TaskRepository · Task · DailyCompletion                    │
│  AnalyticsData · MonthData · DateUtils                      │
│  NotificationScheduler · SettingsManager                    │
├─────────────────────────────────────────────────────────────┤
│                     DATA LAYER                               │
│  AppDatabase · TaskDao · DailyCompletionDao                 │
│  Migrations (v2→3, v3→4)                                   │
│  SQLite (recordweek_database) · SharedPreferences           │
├─────────────────────────────────────────────────────────────┤
│                  INFRASTRUCTURE LAYER                        │
│  AlarmManager · NotificationManager · BroadcastReceiver    │
│  ExecutorService · WorkManager (legacy) · FileProvider      │
└─────────────────────────────────────────────────────────────┘
```

### Reglas de Dependencia

| Desde | Hacia | Permitido |
|-------|-------|-----------|
| Presentation | Domain | ✅ ViewModel observa LiveData, llama Repository |
| Presentation | Data | ❌ No accede directamente a DAOs/BD |
| Domain | Data | ✅ Repository usa DAOs |
| Domain | Infrastructure | ✅ NotificationScheduler usa AlarmManager |
| Data | Infrastructure | ✅ Room usa SQLite |
| Infrastructure | Domain | ❌ No conoce entidades de dominio |

---

## 3. Diagramas C4

### 3.1 Contexto del Sistema

Ver: [`docs/architecture/01-c4-context.puml`](architecture/01-c4-context.puml)

El usuario interactúa con la app para gestionar tareas. La app se comunica con Android OS (alarmas, notificaciones) y almacena datos localmente en SQLite.

### 3.2 Contenedores

Ver: [`docs/architecture/02-c4-container.puml`](architecture/02-c4-container.puml)

La app se compone de:

- **Activities:** MainActivity (host), AddTaskActivity, SettingsActivity
- **Fragments:** DiaryFragment, MonthlyFragment, AnalyticsFragment
- **ViewModel:** TaskViewModel (orquestador de estado)
- **Repository:** TaskRepository (fuente única de verdad)
- **Database:** Room AppDatabase v4
- **Notificaciones:** NotificationScheduler + AlarmManager + BroadcastReceiver

### 3.3 Componentes

Ver: [`docs/architecture/03-c4-component.puml`](architecture/03-c4-component.puml)

Diagrama detallado de todos los componentes y sus relaciones internas.

---

## 4. Modelo de Datos

### 4.1 Diagrama ER

```
┌──────────────────────┐       ┌──────────────────────────┐
│        TASKS         │       │    DAILY_COMPLETIONS     │
├──────────────────────┤       ├──────────────────────────┤
│ id (PK, autoincrement)│◄───┐ │ id (PK, autoincrement)  │
│ name                 │   │   │ task_id (FK → tasks.id) │
│ color                │   │   │ date (yyyy-MM-dd)       │
│ category             │   │   │ completed (0/1)         │
│ description          │   │   ├──────────────────────────┤
│ daysOfWeek (JSON)    │   └───│ Índice único:           │
│ notification_hour    │       │   (task_id, date)       │
│ notification_minute  │       │ CASCADE ON DELETE        │
│ is_active (0/1)      │       └──────────────────────────┘
│ specific_date        │
│   (nullable, null=   │
│    recurrente)       │
└──────────────────────┘
```

### 4.2 Entidades Room

#### Task

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | int (PK) | Auto-incrementado |
| `name` | String | Nombre de la tarea |
| `color` | String | Color asignado por categoría (RED/ORANGE/YELLOW/GREEN/BLUE/PURPLE/PINK) |
| `category` | String | Nombre de la categoría |
| `description` | String | Descripción opcional |
| `daysOfWeek` | String | JSON array: `[1,2,3]` (Lunes=1..Domingo=7). Para puntuales, puede estar vacío |
| `notification_hour` | int | Hora de notificación (0-23) |
| `notification_minute` | int | Minuto de notificación (0-59) |
| `is_active` | int | 1=activa, 0=inactiva |
| `specific_date` | String (nullable) | `null`=recurrente, `"2026-07-18"`=puntual |

**Métodos clave:**
- `isOneOff()`: retorna `true` si `specificDate` no es null ni vacío
- `occursOn(date, ourDay)`: punto central de decisión. Puntual: compara fecha exacta. Recurrente: verifica si `ourDay` está en `daysOfWeek`

#### DailyCompletion

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | int (PK) | Auto-incrementado |
| `task_id` | int (FK) | Referencia a Task.id, CASCADE delete |
| `date` | String | Formato `yyyy-MM-dd` |
| `completed` | int | 1=completada, 0=pendiente |

**Índice único:** `(task_id, date)` — garantiza una sola fila por tarea/día. El insert usa `OnConflictStrategy.REPLACE`.

### 4.3 Migraciones de Base de Datos

| De → A | Operación | Justificación |
|--------|-----------|---------------|
| v2 → v3 | `DROP TABLE IF EXISTS weekly_analysis` | Tabla obsoleta cuando Analytics pasó a leer directo de `daily_completions` |
| v3 → v4 | `ALTER TABLE tasks ADD COLUMN specific_date TEXT` | Soporte para tareas puntuales (fecha única). Las filas existentes quedan con `null` (=recurrentes, sin cambio de comportamiento) |

---

## 5. Flujo de Navegación

```
                    ┌──────────────────┐
                    │   MainActivity   │
                    │    (Host)        │
                    │                  │
                    │  ┌────────────┐  │
                    │  │ ActionBar  │  │  ← Título "RecordWeek" + engranaje Settings
                    │  └────────────┘  │
                    │                  │
                    │  ┌────────────┐  │
                    │  │  fragment_ │  │
                    │  │  container │  │  ← FragmentContainerView
                    │  └────────────┘  │
                    │                  │
                    │  ┌────────────┐  │
                    │  │ BottomNav  │  │  ← BottomNavigationView
                    │  │ DIA MES AN │  │
                    │  └────────────┘  │
                    └────────┬─────────┘
                             │
            ┌────────────────┼────────────────┐
            │                │                │
            ▼                ▼                ▼
    ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
    │ DiaryFragment│ │MonthlyFragment│ │AnalyticsFrag │
    │              │ │              │ │              │
    │ • Selector   │ │ • Calendario │ │ • Gráfico    │
    │   día (L-D)  │ │   compacto   │ │   barras     │
    │ • Lista      │ │ • Puntitos   │ │ • Cumplim.   │
    │   tareas     │ │   categoría  │ │   semanal    │
    │ • Checkbox   │ │ • Agenda     │ │ • Rachas     │
    │   completar  │ │   solo       │ │ • Por cat.   │
    │   (solo hoy) │ │   lectura    │ │ • Naveg.     │
    │ • FAB +      │ │ • Naveg.     │ │   semanas    │
    │   AddTask    │ │   meses      │ │              │
    └──────────────┘ └──────────────┘ └──────────────┘
```

### Comportamiento de `replace()`

Al cambiar de pestaña, `FragmentManager.replace()` destruye el Fragment anterior y crea el nuevo. Esto significa:

- **Mensual y Análisis** recalculan sus datos al entrar (mismo comportamiento que antes, cuando eran Activities).
- **Diario** se recrea pero sus datos se actualizan vía LiveData de Room (siempre frescos).
- Las barras superior e inferior **no parpadean** porque viven en MainActivity, no en los Fragments.

---

## 6. Flujos Principales

### 6.1 Flujo Diario (Vista)

Ver: [`docs/architecture/05-sequence-diary.puml`](architecture/05-sequence-diary.puml)

**Inicio:** MainActivity carga DiaryFragment → observe de tareas activas → observe de completaciones del día → render lista.

**Marcar tarea:** Solo funciona para el día seleccionado = hoy. El adapter bloquea checkboxes de otros días (alpha=0.4, toast "Solo puedes marcar las tareas de hoy").

**Cambiar día:** Observer de completaciones se reemplaza (removeObservers + nuevo observe) para evitar acumulación de observers viejos que causaban el bug "se marcan las demás".

### 6.2 Flujo Análisis

Ver: [`docs/architecture/06-sequence-analytics.puml`](architecture/06-sequence-analytics.puml)

**Cálculo en background:** `loadAnalytics(weekOffset)` ejecuta en `ExecutorService(4)`:

1. **Cumplimiento semanal:** Para cada tarea, cuenta días programados en el tramo transcurrido (denominador) y completados (numerador). Semana actual: lunes→hoy. Semana pasada: lunes→domingo (7 días completos).
2. **Actividad por día:** Recorre completaciones de la semana, agrupa por día de semana.
3. **Total histórico:** `getTotalCompletedAllTime()`.
4. **Rachas:** Días consecutivos con actividad. Actual: termina en hoy o ayer. Mejor: tramo más largo del historial.

**Navegación:** Flechas ‹ › con `currentWeekOffset` (0=actual, -1=pasada, -2=hace 2 semanas...). Nunca positivo (sin futuro).

### 6.3 Flujo Mensual

Ver: [`docs/architecture/07-sequence-monthly.puml`](architecture/07-sequence-monthly.puml)

**Calendario:** Grilla 7 columnas, filas dinámicas. Cada celda muestra número del día + hasta 4 puntitos de color (uno por categoría con tarea ese día).

**Agenda:** Tareas del día seleccionado, ordenadas por hora. Tarjetas con nodo del riel (lleno=hecha, hueco=pendiente).

**Cálculo:** Recorre día 1→hoy, para cada día verifica `task.occursOn(dateStr, ourDay)`. Dias futuros no cuentan (no son exigibles).

---

## 7. Notificaciones y Alarmas

Ver: [`docs/architecture/09-sequence-notification.puml`](architecture/09-sequence-notification.puml)

### Estrategia de programación

| Tipo | RequestCode | Alarma | Reprogramación |
|------|-------------|--------|----------------|
| **Recurrente** | `taskId * 10 + dayOfWeek` | Una por cada día (L=1, M=2...) | Sí (semanal, automática por AlarmManager) |
| **Puntual** | `taskId * 10` | Una sola vez en `specificDate` | No (si fecha pasó, no se reprograma) |

### Permiso de alarmas exactas (API 31+) — fallback

Desde Android 12 (API 31), programar una alarma exacta requiere el permiso
`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`, que el usuario puede revocar en
Ajustes → "Alarmas y recordatorios". Si el código llama
`setExactAndAllowWhileIdle()` con el permiso revocado, Android lanza
`SecurityException` y la app se cierra.

Para evitarlo, toda programación pasa por el método intermedio
`NotificationScheduler.scheduleExactAlarm()`:

```
if (SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms())
    setAndAllowWhileIdle(...)        // inexacta: no requiere permiso
else
    setExactAndAllowWhileIdle(...)   // exacta: comportamiento normal
```

La alarma inexacta sigue disparando la notificación, solo que Android puede
retrasarla unos minutos para agrupar wakeups y ahorrar batería. El guard de versión
va **antes** del `&&`, así que en Android < 12 nunca se invoca
`canScheduleExactAlarms()` (que no existe en esas versiones).

### BootReceiver

Al reiniciar el dispositivo (o tras actualizar el APK), el broadcast
`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` ejecuta `BootReceiver.onReceive()`: lee
todas las tareas activas (`getActiveTasksSync()`) y reprograma sus alarmas vía
`NotificationScheduler.scheduleTask()`. Para puntuales, si la fecha ya pasó, no
programa nada.

Como la lectura de base de datos ocurre en un hilo de fondo, el receiver usa
`goAsync()` para pedirle a Android que **no** dé por terminado el receiver hasta
llamar a `PendingResult.finish()`. Ese `finish()` —junto con el `shutdown()` del
`ExecutorService`— va dentro de un bloque `finally`, de modo que se ejecuta aunque
la reprogramación falle, evitando tanto que el proceso muera a medias como fugas de
hilos.

### Canales de notificación

Android 8+ (API 26+): canal `recordweek` con prioridad por defecto.

---

## 8. Exportación / Importación

Ver: [`docs/architecture/10-sequence-export-import.puml`](architecture/10-sequence-export-import.puml)

### Formato JSON del respaldo

```json
{
  "exportedAt": "2026-07-03",
  "tasks": [
    {
      "id": 1,
      "name": "Gimnasio",
      "category": "Salud",
      "color": "GREEN",
      "description": "Rutina de fuerza",
      "daysOfWeek": "[1,3,5]",
      "specificDate": null,
      "hour": 9,
      "minute": 0,
      "active": 1
    }
  ],
  "completions": [
    {
      "taskId": 1,
      "date": "2026-07-01",
      "completed": 1
    }
  ]
}
```

### Estrategia de importación: "Reemplazar todo"

1. **Validación:** Verifica que el JSON tenga el arreglo `"tasks"`.
2. **Borrado:** `TaskDao.deleteAll()` — CASCADE borra completaciones también.
3. **Inserción de tareas:** Cada tarea recibe un `id` NUEVO (autoincrement). Se construye un `Map<Integer, Integer>` (oldId → newId).
4. **Inserción de completaciones:** Se traduce el `task_id` usando el mapa. Huérfanas (sin tarea válida) se saltan.

---

## 9. Decisiones Arquitectónicas (ADRs)

### ADR-001: MVVM con Room + LiveData

**Estado:** Aceptado

**Contexto:** Se necesita una arquitectura que separe la lógica de negocio de la UI y soporte datos reactivos.

**Decisión:** MVVM con `AndroidViewModel` + `Room` + `LiveData`. Repository como capa intermedia.

**Consecuencias:**
- ✅ Separación clara de responsabilidades
- ✅ Datos reactivos (LiveData auto-actualiza la UI)
- ✅ Testeable (Repository y ViewModel se pueden probar sin UI)
- ⚠️ Room requiere anotaciones y compilation-time code generation

### ADR-002: De Activities a Fragments (v2.0)

**Estado:** Aceptado

**Contexto:** Las 3 vistas principales eran Activities independientes con menú desplegable. El cambio de vista recreaba la pantalla completa y parpadeaba la barra superior.

**Decisión:** MainActivity como host con BottomNavigationView. Las vistas se convirtieron en Fragments. `replace()` en FragmentManager.

**Consecuencias:**
- ✅ Barra superior e inferior fijas (sin parpadeo)
- ✅ Navegación más fluida
- ✅ Código más limpio (un solo host)
- ⚠️ Fragments se destruyen y recrean al cambiar de pestaña

### ADR-003: Migraciones explícitas (sin fallback destructivo)

**Estado:** Aceptado

**Contexto:** `fallbackToDestructiveMigration()` borraba TODA la base silenciosamente ante cada cambio de esquema.

**Decisión:** Registrar cada migración explícitamente (`addMigrations()`). Cada cambio de esquema requiere una `Migration(x, y)` bien escrita.

**Consecuencias:**
- ✅ No se pierden datos por accidente
- ✅ Control total sobre cada cambio de esquema
- ⚠️ Más trabajo al agregar/modificar entidades

### ADR-004: Tareas recurrentes vs puntuales (v2.0+)

**Estado:** Aceptado

**Contexto:** Solo existían tareas recurrentes. Se necesitaba soporte para eventos de una sola vez.

**Decisión:** Campo `specificDate` (nullable). `null`=recurrente, valor=date=puntual. Lógica centralizada en `Task.occursOn()`.

**Consecuencias:**
- ✅ Retrocompatible (tareas viejas quedan con null = recurrentes)
- ✅ Un solo punto de decisión (`occursOn()`) para todas las vistas
- ✅ No duplica estructura de la tabla

### ADR-005: Colores automáticos por categoría

**Estado:** Aceptado

**Contexto:** El usuario seleccionaba manualmente el color de cada tarea, lo que causaba inconsistencia visual.

**Decisión:** Mapeo fijo categoría→color. Estudio=azul, Trabajo=rojo, Gimnasio=amarillo, Salud=verde, Tiempo Personal=rosa, Taller Deportivo=naranja, Comida= púrpura.

**Consecuencias:**
- ✅ Consistencia visual automática
- ✅ Menos decisiones para el usuario al crear tareas
- ⚠️ Las tareas creadas antes de v1.1 conservan su color original

---

## 10. Guía para Desarrolladores

### Estructura del Proyecto

```
app/src/main/java/com/recordweek/
├── MainActivity.java              # Host (BottomNav + FragmentContainer)
├── DiaryFragment.java             # Vista diaria
├── MonthlyFragment.java           # Vista mensual
├── AnalyticsFragment.java         # Vista analíticas
├── AddTaskActivity.java           # Crear/editar tarea
├── SettingsActivity.java          # Configuración
├── adapter/
│   └── TaskAdapter.java           # RecyclerView adapter
├── data/
│   ├── Task.java                  # Entidad Room
│   ├── DailyCompletion.java       # Entidad Room
│   ├── AppDatabase.java           # RoomDatabase (v4, migraciones)
│   ├── TaskDao.java               # DAO tareas
│   ├── DailyCompletionDao.java    # DAO completaciones
│   ├── AnalyticsData.java         # POJO métricas Analytics
│   └── MonthData.java             # POJO datos mensuales
├── repository/
│   └── TaskRepository.java        # Fuente única de verdad
├── viewmodel/
│   └── TaskViewModel.java         # State holder
├── notification/
│   ├── NotificationScheduler.java # Programación alarmas
│   ├── NotificationReceiver.java  # BroadcastReceiver alarmas
│   └── BootReceiver.java          # BroadcastReceiver boot
└── utils/
    ├── DateUtils.java             # Utilidades fecha/semana
    └── SettingsManager.java       # SharedPreferences
```

### Comandos Útiles

```bash
# Compilar debug
./gradlew assembleDebug

# Instalar en dispositivo/emulador
./gradlew installDebug

# Ejecutar tests unitarios
./gradlew testDebugUnitTest

# Limpiar build
./gradlew clean
```

### Convenciones de Código

- **Hilos:** Toda operación de BD va en `ExecutorService` (4 hilos). LiveData notifica a la UI en el hilo principal.
- **Observer pattern:** `removeObservers()` antes de agregar uno nuevo (evita acumulación).
- **Callbacks:** Interfaces internas en Repository (`OnTaskInsertedCallback`, `OnAnalyticsLoadedCallback`, etc.)
- **Fechas:** Formato `"yyyy-MM-dd"` como String en BD. `DateUtils` centraliza todos los cálculos con `java.time.LocalDate` + `DateTimeFormatter` (inmutables y **thread-safe**; no usar `SimpleDateFormat` estático compartido, que no es seguro entre hilos).
- **Días de semana:** Convención propia (Lunes=1..Domingo=7). `DateUtils.calendarDayToOurDay()` y `ourDayToCalendarDay()` para traducir.

### Agregar una nueva funcionalidad

1. **Entidad/DAO:** Si se necesita nueva tabla, crear entidad Room + DAO + migración en `AppDatabase`.
2. **Repository:** Agregar métodos que usen los DAOs. Ejecutar en `executor.execute {}`.
3. **ViewModel:** Expuesto como `LiveData` o con callbacks.
4. **UI:** Observer en Fragment/Activity. Usar `getViewLifecycleOwner()` para evitar memory leaks.
5. **Migración:** Agregar `Migration(oldVersion, newVersion)` en `AppDatabase` y registrarla con `.addMigrations()`.

---

## 11. Diagramas PlantUML

Todos los diagramas están en `docs/architecture/` y se pueden renderizar con cualquier herramienta compatible con PlantUML (VS Code extensión, IntelliJ, CLI, etc.).

| Archivo | Tipo | Descripción |
|---------|------|-------------|
| `01-c4-context.puml` | C4 Context | Actor + sistema + externos |
| `02-c4-container.puml` | C4 Container | Contenedores internos de la app |
| `03-c4-component.puml` | C4 Component | Componentes detallados por capa |
| `04-class-diagram.puml` | Class | Entidades, DAOs, Repository, ViewModel, UI |
| `05-sequence-diary.puml` | Sequence | Flujo vista diaria |
| `06-sequence-analytics.puml` | Sequence | Flujo vista análisis |
| `07-sequence-monthly.puml` | Sequence | Flujo vista mensual |
| `08-deployment.puml` | Deployment | Dispositivo Android + almacenamiento |
| `09-sequence-notification.puml` | Sequence | Crear/alarmar/cancelar notificaciones |
| `10-sequence-export-import.puml` | Sequence | Exportar/importar respaldo JSON |

---

*Documento generado el 03/07/2026. Actualizado el 07/07/2026. Proyecto Record Week v2.0.1.*
