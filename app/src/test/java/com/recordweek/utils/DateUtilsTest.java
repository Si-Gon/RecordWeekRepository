package com.recordweek.utils;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.*;

public class DateUtilsTest {

    @Test
    public void getTodayString_formatoCorrecto() {
        String today = DateUtils.getTodayString();
        assertTrue(today.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void getTodayDayOfWeek_rangoValido() {
        int day = DateUtils.getTodayDayOfWeek();
        assertTrue("Day " + day + " out of range 1-7", day >= 1 && day <= 7);
    }

    @Test
    public void calendarDayToOurDay_monday() {
        assertEquals(1, DateUtils.calendarDayToOurDay(Calendar.MONDAY));
    }

    @Test
    public void calendarDayToOurDay_tuesday() {
        assertEquals(2, DateUtils.calendarDayToOurDay(Calendar.TUESDAY));
    }

    @Test
    public void calendarDayToOurDay_wednesday() {
        assertEquals(3, DateUtils.calendarDayToOurDay(Calendar.WEDNESDAY));
    }

    @Test
    public void calendarDayToOurDay_thursday() {
        assertEquals(4, DateUtils.calendarDayToOurDay(Calendar.THURSDAY));
    }

    @Test
    public void calendarDayToOurDay_friday() {
        assertEquals(5, DateUtils.calendarDayToOurDay(Calendar.FRIDAY));
    }

    @Test
    public void calendarDayToOurDay_saturday() {
        assertEquals(6, DateUtils.calendarDayToOurDay(Calendar.SATURDAY));
    }

    @Test
    public void calendarDayToOurDay_sunday() {
        assertEquals(7, DateUtils.calendarDayToOurDay(Calendar.SUNDAY));
    }

    @Test
    public void calendarDayToOurDay_desconocido() {
        assertEquals(1, DateUtils.calendarDayToOurDay(-1));
    }

    @Test
    public void ourDayToCalendarDay_all() {
        assertEquals(Calendar.MONDAY, DateUtils.ourDayToCalendarDay(1));
        assertEquals(Calendar.TUESDAY, DateUtils.ourDayToCalendarDay(2));
        assertEquals(Calendar.WEDNESDAY, DateUtils.ourDayToCalendarDay(3));
        assertEquals(Calendar.THURSDAY, DateUtils.ourDayToCalendarDay(4));
        assertEquals(Calendar.FRIDAY, DateUtils.ourDayToCalendarDay(5));
        assertEquals(Calendar.SATURDAY, DateUtils.ourDayToCalendarDay(6));
        assertEquals(Calendar.SUNDAY, DateUtils.ourDayToCalendarDay(7));
    }

    @Test
    public void ourDayToCalendarDay_desconocido() {
        assertEquals(Calendar.MONDAY, DateUtils.ourDayToCalendarDay(0));
    }

    @Test
    public void getMondayForOffset_currentWeek_retornaLunes() {
        String monday = DateUtils.getMondayForOffset(0);
        int ourDay = DateUtils.dateStringToOurDay(monday);
        assertEquals("Monday should be day 1", 1, ourDay);
    }

    @Test
    public void getMondayForOffset_lastWeek_esMenorQueCurrent() {
        String lastMonday = DateUtils.getMondayForOffset(-1);
        String thisMonday = DateUtils.getMondayForOffset(0);
        assertTrue("Last week monday should be before this week", lastMonday.compareTo(thisMonday) < 0);
    }

    @Test
    public void getSundayForOffset_caeSeisDiasDespuesDelLunes() {
        String monday = DateUtils.getMondayForOffset(0);
        String sunday = DateUtils.getSundayForOffset(0);
        assertEquals("Monday + 6 days should equal Sunday",
                DateUtils.dateStringToOurDay(sunday), 7);
    }

    @Test
    public void getWeekRangeLabel_formatoCorrecto() {
        String label = DateUtils.getWeekRangeLabel(0);
        assertTrue(label.matches("\\d{2}/\\d{2} - \\d{2}/\\d{2}"));
    }

    @Test
    public void dateStringToOurDay_valido() {
        assertEquals(1, DateUtils.dateStringToOurDay("2026-07-06"));
        assertEquals(2, DateUtils.dateStringToOurDay("2026-07-07"));
        assertEquals(3, DateUtils.dateStringToOurDay("2026-07-08"));
    }

    @Test
    public void dateStringToOurDay_invalido() {
        assertEquals(-1, DateUtils.dateStringToOurDay("no-es-fecha"));
    }

    @Test
    public void formatTime_zeroPad() {
        assertEquals("09:05", DateUtils.formatTime(9, 5));
    }

    @Test
    public void formatTime_midnight() {
        assertEquals("00:00", DateUtils.formatTime(0, 0));
    }

    @Test
    public void formatTime_mediaNoche() {
        assertEquals("23:59", DateUtils.formatTime(23, 59));
    }
}
