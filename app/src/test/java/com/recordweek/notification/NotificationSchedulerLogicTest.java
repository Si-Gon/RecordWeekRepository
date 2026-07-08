package com.recordweek.notification;

import com.recordweek.utils.DateUtils;

import org.junit.Test;

import static org.junit.Assert.*;

public class NotificationSchedulerLogicTest {

    @Test
    public void getTriggerForDate_valido() {
        long millis = NotificationScheduler.getTriggerForDate("2026-07-18", 9, 30);
        assertTrue("Trigger should be positive", millis > 0);
    }

    @Test
    public void getTriggerForDate_invalido() {
        long millis = NotificationScheduler.getTriggerForDate("mal", 0, 0);
        assertEquals(0, millis);
    }

    @Test
    public void getNextTriggerTime_futuroEnSemana() {
        // Siempre retorna millis > 0 sin importar el día
        long millis = NotificationScheduler.getNextTriggerTime(
                DateUtils.getTodayDayOfWeek(), 23, 59);
        assertTrue("Trigger should be positive", millis > 0);
    }

    @Test
    public void getNextTriggerTime_siemprePositivo() {
        // Cualquier día de la semana siempre da un trigger futuro
        for (int ourDay = 1; ourDay <= 7; ourDay++) {
            long millis = NotificationScheduler.getNextTriggerTime(ourDay, 8, 0);
            assertTrue("Trigger for day " + ourDay + " should be positive", millis > 0);
        }
    }
}
