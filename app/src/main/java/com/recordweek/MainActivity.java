package com.recordweek;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.work.WorkManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

// ============================================================
//  MainActivity: el HOST (marco fijo) de la app.
//
//  Antes MainActivity ERA la vista Diario. Ahora su unico trabajo es sostener el
//  marco que no cambia:
//    - Barra SUPERIOR (del tema): titulo + engranaje de Ajustes (menu_top.xml).
//    - Barra INFERIOR: BottomNavigationView con Diario / Mensual / Analisis.
//    - Un hueco central (fragment_container) donde se intercambia el Fragment
//      de la vista activa.
//
//  Toda la logica de cada vista se mudo a su Fragment (DiaryFragment,
//  MonthlyFragment, AnalyticsFragment). Aqui solo decidimos CUAL mostrar.
//
//  Por que replace() y no show/hide: replace() destruye el Fragment anterior y
//  crea el nuevo en cada cambio de pestana. Asi Mensual y Analisis recalculan sus
//  datos al entrar (igual que antes, cuando eran Activities nuevas) y el codigo
//  queda minimo. La barra NO parpadea porque vive aqui, en el host, no en el
//  Fragment: solo cambia el contenido del centro.
// ============================================================
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Splash del sistema. DEBE ir antes de super.onCreate()/setContentView():
        // intercambia el tema (de splash al normal) en el momento exacto. Si fuera
        // despues, la ventana ya se habria pintado con el tema de splash (parpadeo).
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        // Al tocar una pestana, mostramos su Fragment y ajustamos el titulo.
        bottomNav.setOnItemSelectedListener(item -> {
            showFragment(item.getItemId());
            return true; // true = "acepto la seleccion" (deja la pestana marcada)
        });

        // Primera carga: mostramos Diario. El guard savedInstanceState==null evita
        // volver a crear el Fragment si el sistema recrea la Activity (ya lo restaura
        // el FragmentManager por su cuenta).
        if (savedInstanceState == null) {
            showFragment(R.id.nav_diary);
            bottomNav.setSelectedItemId(R.id.nav_diary); // resalta la pestana Diario
        }

        cancelWeeklyReset();
    }

    // Coloca en el hueco central el Fragment de la vista pedida y pone su titulo en
    // la barra superior. Es el unico sitio que conoce el mapeo pestana -> Fragment.
    private void showFragment(int itemId) {
        Fragment fragment;
        CharSequence title;
        if (itemId == R.id.nav_monthly) {
            fragment = new MonthlyFragment();
            title = "Vista mensual";
        } else if (itemId == R.id.nav_analytics) {
            fragment = new AnalyticsFragment();
            title = "Analisis";
        } else { // nav_diary por defecto
            fragment = new DiaryFragment();
            title = buildDiaryTitle();
        }
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);
    }

    // Titulo con estilos mixtos: "Record" en blanco recto, "Week" en ambar cursiva.
    // La ActionBar solo acepta texto plano, asi que usamos un SpannableString: texto
    // al que se le aplican estilos por TRAMOS de caracteres (spans).
    private CharSequence buildDiaryTitle() {
        String full = "RecordWeek"; // sin espacio: las mayusculas marcan la separacion
        android.text.SpannableString title = new android.text.SpannableString(full);
        int split = "Record".length(); // 6

        // "Record" -> color de texto principal (blanco azulado)
        title.setSpan(
            new android.text.style.ForegroundColorSpan(getColor(R.color.text_primary)),
            0, split, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // "Week" -> color de acento (ambar)
        title.setSpan(
            new android.text.style.ForegroundColorSpan(getColor(R.color.accent_amber)),
            split, full.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        // "Week" -> cursiva para el contraste tipografico
        title.setSpan(
            new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
            split, full.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return title;
    }

    // Barra superior: un unico engranaje que abre Configuracion. Sustituye al viejo
    // desplegable "Menu"; la navegacion entre vistas ahora vive en la barra inferior.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_top, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Antes esto programaba un borrado semanal automatico. Ahora las rutinas son
    // permanentes, asi que CANCELAMOS cualquier trabajo "weekly_reset" que haya
    // quedado encolado en ejecuciones anteriores. WorkManager guarda los trabajos en
    // su propia base de datos, por eso no basta borrar el codigo: hay que cancelarlo.
    private void cancelWeeklyReset() {
        WorkManager.getInstance(this).cancelUniqueWork("weekly_reset");
    }
}
