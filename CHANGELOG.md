# Changelog

Todos los cambios relevantes de Record Week se documentan en este archivo.

El formato sigue la convención [Keep a Changelog](https://keepachangelog.com/es-ES/)
y el versionado es [Semántico](https://semver.org/lang/es/): `MAYOR.MENOR.PARCHE`.

- **MAYOR**: cambios grandes o rediseños que rompen la forma de usar la app.
- **MENOR**: funciones nuevas que no rompen lo anterior.
- **PARCHE**: correcciones de errores y ajustes menores.

> Recordatorio: al publicar una versión nueva hay que subir `versionCode` (entero,
> siempre +1) y `versionName` (el texto, ej. "1.1") en `app/build.gradle`.

---

## [2.0] — 2026-07-01

`versionCode 5`

### Cambiado
- **Rediseño completo de la navegación: de menú desplegable a barra inferior con
  Fragments.** Las tres vistas principales (Diario, Mensual, Análisis) ahora viven en
  una **barra de navegación inferior fija** (`BottomNavigationView`), y **Configuración**
  pasó a un **engranaje en la barra superior**. Antes cada vista era una `Activity`
  independiente a la que se llegaba desde un desplegable "Menú"; el cambio de vista
  recreaba la pantalla completa. Ahora `MainActivity` es un único **host**: solo cambia
  el contenido del centro mientras las barras superior e inferior permanecen fijas (sin
  parpadeo). La pestaña activa se resalta en ámbar y el botón "+" (FAB) aparece solo en
  Diario.

### Notas técnicas
- **De Activities a Fragments.** `MainActivity` dejó de heredar de `BaseActivity` y pasó
  a ser el host (`AppCompatActivity` con `BottomNavigationView`). Las vistas se
  convirtieron en `DiaryFragment`, `MonthlyFragment` y `AnalyticsFragment`. El cambio de
  pestaña usa `FragmentManager.replace()`, de modo que Mensual y Análisis recalculan sus
  datos al entrar (mismo comportamiento que antes, cuando eran Activities nuevas) con
  código mínimo. Diario sigue actualizándose solo vía LiveData de Room.
- **Limpieza de la arquitectura anterior.** Se eliminaron `BaseActivity`,
  `MonthlyActivity`, `AnalyticsActivity`, `res/menu/menu_main.xml`,
  `res/layout/activity_monthly.xml` y `res/layout/activity_analytics.xml` (código muerto
  tras la migración). Nuevos recursos: `menu/bottom_nav_menu.xml`, `menu/menu_top.xml`,
  íconos de navegación (`ic_nav_daily/monthly/analytics`) y el selector de color
  `color/bottom_nav_item_color.xml` (ámbar activo / gris inactivo).
- **Manifest.** Se quitaron las declaraciones de `MonthlyActivity` y `AnalyticsActivity`
  (ya no son Activities). `MainActivity`, `AddTaskActivity` y `SettingsActivity` siguen
  siendo las únicas Activities declaradas.

---

## [1.3] — 2026-06-30

`versionCode 4`

### Cambiado
- **Rediseño de la lista de tareas a formato tarjeta.** Cada tarea pasa de una fila
  separada por línea fina a una tarjeta con esquinas redondeadas, sobre un tono de
  fondo levemente elevado (`surface_elevated`) con un degradado muy suave del color
  de su categoría (8%) concentrado a la izquierda, borde tenue y una franja de acento
  del color de la categoría en el borde izquierdo. Se quitó la barra de color de 3dp
  y la línea divisoria entre filas.

### Corregido
- **Barra superior vacía entre el título y el contenido.** En las pantallas Principal,
  Análisis y Configuración aparecía una franja oscura sin uso: un `AppBarLayout` +
  `Toolbar` en el layout que no se conectaba a nada (ningún `setSupportActionBar`), así
  que se dibujaba vacío. La barra superior real (título y botón atrás) la aporta la
  ActionBar del tema, así que ese `Toolbar` fantasma se eliminó de las tres pantallas.

### Notas técnicas
- **Prueba unitaria de la lógica de rachas.** Se añadió `StreakTest` (JUnit 4, test
  local en la JVM) para `computeStreaks`: cubre lista vacía, días consecutivos hasta
  hoy, racha aún viva sin marcar hoy, y mejor racha en el pasado. Para poder probarla
  sin abrir la base de datos, `computeStreaks` y `sameDay` pasaron de privadas a
  `static` package-private. Nueva dependencia `testImplementation junit:junit:4.13.2`.

---

## [1.2] — 2026-06-30

`versionCode 3`

### Cambiado
- **Limpieza interna de código muerto** (sin cambios visibles para el usuario). Se
  eliminó la cadena `WeeklyAnalysis` completa (entidad, DAO, métodos en repositorio
  y ViewModel), que quedó obsoleta cuando Análisis pasó a leer directo del historial
  de completaciones. También se retiraron métodos y consultas sin uso en `DateUtils`,
  `DailyCompletionDao` y `TaskRepository`, el worker obsoleto `WeeklyResetWorker`, y
  colores duplicados/sin uso (`accent_purple`, `accent_amber_translucent`).

### Notas técnicas
- **Migración de base de datos 2 → 3.** Al quitar la tabla `weekly_analysis`, el
  esquema de Room cambió. Se escribió una `Migration(2, 3)` con `DROP TABLE
  weekly_analysis` que conserva las tareas y el historial existentes. Se reemplazó
  `fallbackToDestructiveMigration()` por `addMigrations(...)`: a partir de ahora cada
  cambio de esquema exige una migración explícita, evitando borrados de datos por
  accidente.

---

## [1.1] — 2026-06-29

`versionCode 2`

### Añadido
- **Histórico semanal navegable en Análisis.** Flechas ‹ › alrededor del rango de
  fechas para retroceder y avanzar entre semanas. La flecha "siguiente" se desactiva
  en la semana actual (no se navega al futuro). El encabezado indica "Esta semana",
  "Semana pasada" o "Hace N semanas" junto al rango de días.
- **Indicador de color de categoría.** Un punto de color junto al selector de
  categoría que muestra el color que tendrá la tarea, y se actualiza al cambiar la
  categoría.

### Cambiado
- **El color de la tarea ahora se asigna automáticamente por categoría** (se eliminó
  el selector manual de color). Mapeo fijo:
  Estudio → azul · Trabajo → rojo · Gimnasio → amarillo · Salud → verde ·
  Tiempo Personal → rosa · Taller Deportivo → naranja · Comida & Suplementos → púrpura.
  - *Nota:* las tareas creadas antes de esta versión conservan su color original;
    el mapeo solo aplica al crear o editar.
- En el cálculo de cumplimiento, las semanas pasadas cuentan los 7 días completos,
  mientras que la semana actual solo cuenta los días ya transcurridos.

### Corregido
- **Letras de los botones de día invisibles en algunos teléfonos.** Se eliminó el
  estilo `Widget.AppCompat.Button.Borderless`, que en ciertas capas de fabricante
  (Samsung, Xiaomi) pisaba el color del texto aplicado por código. Ahora el contraste
  entre día seleccionado / no seleccionado es consistente en cualquier dispositivo.
- Se reemplazó la llamada deprecada `getResources().getColor()` por `getColor()`.

### Notas técnicas
- Compatibilidad con Android 15 revisada: con `targetSdk 34` la app corre en modo de
  compatibilidad y no le afecta el edge-to-edge forzado de API 35. Sin cambios necesarios.
- Limpieza menor: se eliminó un mapa de tareas sin uso en el cálculo de Analytics.

---

## [1.0] — Versión base

`versionCode 1`

Primera versión funcional de Record Week (rastreador semanal de hábitos y tareas).

### Funcionalidad incluida
- Creación, edición y eliminación de tareas con categoría, días de la semana,
  hora de notificación y estado activo/inactivo.
- Marcado diario de cumplimiento por tarea, con historial persistente.
- Pantalla de Análisis: cumplimiento semanal, racha actual y mejor racha, total
  histórico de tareas completadas, actividad por día y cumplimiento por categoría.
- Notificaciones programadas por tarea, con reprogramación tras reinicio del equipo.
- Exportar / importar datos como respaldo en formato JSON.
- Tema oscuro (azul medianoche + ámbar), icono de launcher y splash screen propios.
