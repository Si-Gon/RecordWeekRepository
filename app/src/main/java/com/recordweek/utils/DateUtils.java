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

    public static String getDayLabel(int day) {
        switch (day) {
            case 1: return "L"; case 2: return "M"; case 3: return "X";
            case 4: return "J"; case 5: return "V"; case 6: return "S";
            case 7: return "D"; default: return "?";
        }
    }

    public static int getCurrentWeekNumber() { return Calendar.getInstance().get(Calendar.WEEK_OF_YEAR); }
    public static int getCurrentYear() { return Calendar.getInstance().get(Calendar.YEAR); }
    public static int getCurrentMonth() { return Calendar.getInstance().get(Calendar.MONTH) + 1; }

    public static String getLastMondayString() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek + 5) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
        cal.add(Calendar.DAY_OF_YEAR, -7);
        return sdf.format(cal.getTime());
    }

    public static String getLastSundayString() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek + 5) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
        cal.add(Calendar.DAY_OF_YEAR, -1);
        return sdf.format(cal.getTime());
    }

    // Lunes de la semana ACTUAL (a diferencia de getLastMondayString, que da el de
    // la semana pasada). (dayOfWeek + 5) % 7 = cuantos dias retroceder hasta el lunes
    // segun el esquema de Calendar (SUNDAY=1..SATURDAY=7).
    public static String getThisMondayString() {
        return getMondayForOffset(0);
    }

    // Domingo de la semana ACTUAL (lunes + 6 dias).
    public static String getThisSundayString() {
        return getSundayForOffset(0);
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

    public static String getFirstDayOfMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return sdf.format(cal.getTime());
    }

    public static String getLastDayOfMonth(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month - 1);
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        return sdf.format(cal.getTime());
    }

    public static String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    public static String getMonthName(int month) {
        String[] months = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                           "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        if (month >= 1 && month <= 12) return months[month - 1];
        return "";
    }
}
