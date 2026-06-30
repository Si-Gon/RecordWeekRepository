package com.recordweek.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.getDefault());

    public static String getTodayString() { return sdf.format(new Date()); }

    public static int getTodayDayOfWeek() {
        Calendar cal = Calendar.getInstance();
        return calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK));
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

    // ============================================================
    //  SEMANAS CON OFFSET (para el historico navegable de Analisis)
    //  weekOffset = 0  -> semana actual
    //  weekOffset = -1 -> semana pasada
    //  weekOffset = -2 -> hace dos semanas ... y asi.
    //  La base es la misma cuenta del lunes actual; luego sumamos
    //  (offset * 7) dias para movernos de semana en semana.
    // ============================================================
    public static String getMondayForOffset(int weekOffset) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek + 5) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday + (weekOffset * 7));
        return sdf.format(cal.getTime());
    }

    public static String getSundayForOffset(int weekOffset) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek + 5) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday + 6 + (weekOffset * 7));
        return sdf.format(cal.getTime());
    }

    // Devuelve un rango legible "dd/MM - dd/MM" para mostrar la semana elegida.
    public static String getWeekRangeLabel(int weekOffset) {
        SimpleDateFormat dm = new SimpleDateFormat("dd/MM", Locale.getDefault());
        try {
            Date monday = sdf.parse(getMondayForOffset(weekOffset));
            Date sunday = sdf.parse(getSundayForOffset(weekOffset));
            return dm.format(monday) + " - " + dm.format(sunday);
        } catch (Exception e) {
            return "";
        }
    }

    // Convierte una fecha "yyyy-MM-dd" a nuestro dia de la semana (Lunes=1..Domingo=7).
    // Devuelve -1 si la cadena no se puede parsear.
    public static int dateStringToOurDay(String dateStr) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            return calendarDayToOurDay(cal.get(Calendar.DAY_OF_WEEK));
        } catch (Exception e) {
            return -1;
        }
    }

    public static String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }
}
