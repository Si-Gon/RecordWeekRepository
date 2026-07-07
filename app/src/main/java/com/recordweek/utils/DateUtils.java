package com.recordweek.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;

public class DateUtils {
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.getDefault());
    private static final DateTimeFormatter dayMonthFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault());

    public static String getTodayString() {
        return LocalDate.now().format(formatter);
    }

    public static int getTodayDayOfWeek() {
        return LocalDate.now().getDayOfWeek().getValue();
    }

    public static int calendarDayToOurDay(int calendarDay) {
        switch (calendarDay) {
            case Calendar.MONDAY: return 1;
            case Calendar.TUESDAY: return 2;
            case Calendar.WEDNESDAY: return 3;
            case Calendar.THURSDAY: return 4;
            case Calendar.FRIDAY: return 5;
            case Calendar.SATURDAY: return 6;
            case Calendar.SUNDAY: return 7;
            default: return 1;
        }
    }

    public static int ourDayToCalendarDay(int ourDay) {
        switch (ourDay) {
            case 1: return Calendar.MONDAY;
            case 2: return Calendar.TUESDAY;
            case 3: return Calendar.WEDNESDAY;
            case 4: return Calendar.THURSDAY;
            case 5: return Calendar.FRIDAY;
            case 6: return Calendar.SATURDAY;
            case 7: return Calendar.SUNDAY;
            default: return Calendar.MONDAY;
        }
    }

    public static String getMondayForOffset(int weekOffset) {
        LocalDate today = LocalDate.now();
        int daysFromMonday = today.getDayOfWeek().getValue() - 1;
        LocalDate monday = today.minusDays(daysFromMonday).plusWeeks(weekOffset);
        return monday.format(formatter);
    }

    public static String getSundayForOffset(int weekOffset) {
        LocalDate today = LocalDate.now();
        int daysFromMonday = today.getDayOfWeek().getValue() - 1;
        LocalDate sunday = today.minusDays(daysFromMonday).plusWeeks(weekOffset).plusDays(6);
        return sunday.format(formatter);
    }

    public static String getWeekRangeLabel(int weekOffset) {
        LocalDate today = LocalDate.now();
        int daysFromMonday = today.getDayOfWeek().getValue() - 1;
        LocalDate monday = today.minusDays(daysFromMonday).plusWeeks(weekOffset);
        LocalDate sunday = monday.plusDays(6);
        return monday.format(dayMonthFormatter) + " - " + sunday.format(dayMonthFormatter);
    }

    public static int dateStringToOurDay(String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr, formatter);
            return date.getDayOfWeek().getValue();
        } catch (Exception e) {
            return -1;
        }
    }

    public static String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }
}
