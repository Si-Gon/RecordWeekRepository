package com.recordweek.repository;

import static org.junit.Assert.assertEquals;

import com.recordweek.data.AnalyticsData;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Prueba de la logica de rachas (computeStreaks) del TaskRepository.
 *
 * Por que existe: computeStreaks es la unica logica no trivial de la app
 * (cuenta dias consecutivos con calendario y saltos). Si un cambio futuro
 * la rompe, la app NO se cae: simplemente muestra una racha equivocada en
 * Analisis, un fallo silencioso que costaria mucho notar. Esta prueba lo
 * atrapa en el JVM (sin emulador) en segundos.
 *
 * Por que fechas relativas a "hoy": computeStreaks lee Calendar.getInstance()
 * para saber que dia es hoy. Si escribiera fechas fijas ("2026-06-30"), la
 * prueba pasaria hoy y fallaria manana. daysAgo(n) genera la fecha de hace n
 * dias, asi el resultado no depende de cuando se ejecute.
 */
public class StreakTest {

    // Devuelve la fecha de hace n dias como "yyyy-MM-dd". daysAgo(0) = hoy.
    private static String daysAgo(int n) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -n);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    // computeStreaks escribe en un AnalyticsData; lo creamos vacio y leemos campos.
    private static AnalyticsData run(List<String> datesDesc) {
        AnalyticsData data = new AnalyticsData();
        TaskRepository.computeStreaks(datesDesc, data);
        return data;
    }

    @Test
    public void sinFechas_rachaCero() {
        // Sin actividad no hay racha: ni actual ni mejor.
        AnalyticsData d = run(Collections.emptyList());
        assertEquals(0, d.currentStreak);
        assertEquals(0, d.bestStreak);
    }

    @Test
    public void tresDiasSeguidosHastaHoy() {
        // hoy, ayer, anteayer -> racha actual 3 (termina en hoy), mejor 3.
        AnalyticsData d = run(Arrays.asList(daysAgo(0), daysAgo(1), daysAgo(2)));
        assertEquals(3, d.currentStreak);
        assertEquals(3, d.bestStreak);
    }

    @Test
    public void rachaVivaAunqueHoyNoEsteMarcado() {
        // ayer y anteayer, pero hoy sin marcar: la racha de ayer sigue viva -> 2.
        AnalyticsData d = run(Arrays.asList(daysAgo(1), daysAgo(2)));
        assertEquals(2, d.currentStreak);
        assertEquals(2, d.bestStreak);
    }

    @Test
    public void mejorRachaEnElPasado() {
        // hoy suelto (actual 1) + un bloque viejo de 4 dias seguidos (mejor 4).
        AnalyticsData d = run(Arrays.asList(
            daysAgo(0),
            daysAgo(10), daysAgo(11), daysAgo(12), daysAgo(13)));
        assertEquals(1, d.currentStreak);
        assertEquals(4, d.bestStreak);
    }
}
