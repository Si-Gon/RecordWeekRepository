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

public class StreakTest {

    // daysAgo(0) = hoy
    private static String daysAgo(int n) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -n);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime());
    }

    private static AnalyticsData run(List<String> datesDesc) {
        AnalyticsData data = new AnalyticsData();
        TaskRepository.computeStreaks(datesDesc, data);
        return data;
    }

    @Test
    public void sinFechas_rachaCero() {
        AnalyticsData d = run(Collections.emptyList());
        assertEquals(0, d.currentStreak);
        assertEquals(0, d.bestStreak);
    }

    @Test
    public void tresDiasSeguidosHastaHoy() {
        AnalyticsData d = run(Arrays.asList(daysAgo(0), daysAgo(1), daysAgo(2)));
        assertEquals(3, d.currentStreak);
        assertEquals(3, d.bestStreak);
    }

    @Test
    public void rachaVivaAunqueHoyNoEsteMarcado() {
        AnalyticsData d = run(Arrays.asList(daysAgo(1), daysAgo(2)));
        assertEquals(2, d.currentStreak);
        assertEquals(2, d.bestStreak);
    }

    @Test
    public void mejorRachaEnElPasado() {
        AnalyticsData d = run(Arrays.asList(
            daysAgo(0),
            daysAgo(10), daysAgo(11), daysAgo(12), daysAgo(13)));
        assertEquals(1, d.currentStreak);
        assertEquals(4, d.bestStreak);
    }

    // --- Nuevos tests ---

    @Test
    public void rachaUnSoloDia() {
        AnalyticsData d = run(Arrays.asList(daysAgo(0)));
        assertEquals(1, d.currentStreak);
        assertEquals(1, d.bestStreak);
    }

    @Test
    public void rachaRota_actualReinicia() {
        // hoy, ayer, anteayer — salto — bloque de 3 hace tiempo
        AnalyticsData d = run(Arrays.asList(
            daysAgo(0), daysAgo(1), daysAgo(2),
            daysAgo(10), daysAgo(11), daysAgo(12)));
        assertEquals(3, d.currentStreak);
        assertEquals(3, d.bestStreak);
    }

    @Test
    public void rachaNull_listaNull() {
        AnalyticsData d = run(null);
        assertEquals(0, d.currentStreak);
        assertEquals(0, d.bestStreak);
    }

    @Test
    public void sameDay_mismoDia() {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.set(2026, Calendar.JULY, 18, 10, 0);
        b.set(2026, Calendar.JULY, 18, 22, 30);
        assertEquals(true, TaskRepository.sameDay(a, b));
    }

    @Test
    public void sameDay_distintoDia() {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        a.set(2026, Calendar.JULY, 18, 10, 0);
        b.set(2026, Calendar.JULY, 19, 10, 0);
        assertEquals(false, TaskRepository.sameDay(a, b));
    }
}
