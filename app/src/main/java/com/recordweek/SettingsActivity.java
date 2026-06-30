package com.recordweek;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.recordweek.utils.SettingsManager;
import com.recordweek.viewmodel.TaskViewModel;

public class SettingsActivity extends AppCompatActivity {
    private TaskViewModel viewModel;
    private SettingsManager settings;
    private Switch switchNotifications;

    // Lanzador del selector de archivos del sistema. Se registra UNA vez en onCreate.
    // Cuando el usuario elige un archivo, Android nos devuelve su URI aqui.
    private androidx.activity.result.ActivityResultLauncher<String[]> openFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Configuracion");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        settings = new SettingsManager(this);

        // Registramos el contrato OpenDocument: abre el explorador para elegir UN
        // archivo. El resultado (la URI elegida) llega al callback. Debe registrarse
        // antes de que la Activity este activa, por eso va aqui en onCreate.
        openFileLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) confirmImport(uri); });

        setupNotificationSwitch();
        findViewById(R.id.row_system_notifications).setOnClickListener(v -> openSystemNotificationSettings());
        findViewById(R.id.row_export).setOnClickListener(v -> exportData());
        findViewById(R.id.row_import).setOnClickListener(v -> launchFilePicker());
        findViewById(R.id.row_delete_completions).setOnClickListener(v -> confirmDeleteCompletions());
    }

    // Abre el selector de archivos filtrando por JSON. Aceptamos tambien
    // "*/*" como red de seguridad porque algunos gestores etiquetan mal los .json.
    private void launchFilePicker() {
        openFileLauncher.launch(new String[]{"application/json", "text/plain", "*/*"});
    }

    // Antes de importar avisamos: la estrategia es REEMPLAZAR todo. Pedimos
    // confirmacion para que no se pierdan datos por accidente.
    private void confirmImport(android.net.Uri uri) {
        new AlertDialog.Builder(this)
            .setTitle("Importar datos")
            .setMessage("Esto REEMPLAZARA todas tus tareas e historial actuales por los del archivo. Esta accion no se puede deshacer.\n\nContinuar?")
            .setPositiveButton("Importar", (d, w) -> doImport(uri))
            .setNegativeButton("Cancelar", null)
            .show();
    }

    // Lee el contenido del archivo elegido y se lo pasa al ViewModel. La lectura
    // del InputStream se hace aqui; el procesamiento pesado va en hilo de fondo
    // dentro del repository. Por eso el resultado vuelve por callback a la UI.
    private void doImport(android.net.Uri uri) {
        Toast.makeText(this, "Importando...", Toast.LENGTH_SHORT).show();
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();

            viewModel.importData(sb.toString(), (success, message, tasks, comps) -> runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this,
                        "Importado: " + tasks + " tareas, " + comps + " completaciones",
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo leer el archivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupNotificationSwitch() {
        switchNotifications = findViewById(R.id.switch_notifications);
        // Primero fijamos el estado guardado SIN listener, para que no se dispare
        // al inicializar (mismo patron que los checkbox del adapter).
        switchNotifications.setOnCheckedChangeListener(null);
        switchNotifications.setChecked(settings.areNotificationsEnabled());
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setNotificationsEnabled(isChecked);
            Toast.makeText(this,
                isChecked ? "Notificaciones activadas" : "Notificaciones desactivadas",
                Toast.LENGTH_SHORT).show();
        });
    }

    // No podemos cambiar permisos del sistema desde la app; solo abrir la pantalla
    // de ajustes de notificaciones de Android para esta app.
    private void openSystemNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo abrir los ajustes", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportData() {
        Toast.makeText(this, "Preparando exportacion...", Toast.LENGTH_SHORT).show();
        viewModel.exportData(json -> runOnUiThread(() -> shareJsonAsFile(json)));
    }

    // Escribe el JSON en un archivo dentro de la cache de la app y lo comparte como
    // ADJUNTO (no como texto). Asi se puede guardar en Drive, correo, etc. y luego
    // re-importarlo. Desde Android 7 no se puede compartir un archivo por su ruta
    // directa: hay que generar una URI "content://" a traves del FileProvider.
    private void shareJsonAsFile(String json) {
        try {
            java.io.File dir = getCacheDir();
            java.io.File file = new java.io.File(dir, "recordweek_backup.json");
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(json);
            writer.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", file);

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_SUBJECT, "recordWeek - respaldo");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            // Permiso temporal de lectura para la app que reciba el archivo.
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Exportar datos"));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDeleteCompletions() {
        new AlertDialog.Builder(this)
            .setTitle("Borrar historial")
            .setMessage("Se borrara TODO el historial de completaciones. Tus tareas se conservan. Esta accion no se puede deshacer.")
            .setPositiveButton("Borrar", (d, w) -> {
                viewModel.deleteAllCompletions();
                Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
