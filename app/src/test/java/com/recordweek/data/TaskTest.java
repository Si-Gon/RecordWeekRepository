package com.recordweek.data;

import org.junit.Test;

import static org.junit.Assert.*;

public class TaskTest {

    private Task createRecurrente() {
        return new Task("Gimnasio", "GREEN", "Salud", "",
                "[1,3,5]", 9, 0, 1);
    }

    private Task createPuntual() {
        Task t = new Task("Cita", "BLUE", "Personal", "",
                "[]", 14, 30, 1);
        t.specificDate = "2026-07-18";
        return t;
    }

    @Test
    public void isOneOff_puntual() {
        assertTrue(createPuntual().isOneOff());
    }

    @Test
    public void isOneOff_recurrente() {
        assertFalse(createRecurrente().isOneOff());
    }

    @Test
    public void isOneOff_vacio() {
        Task t = createRecurrente();
        t.specificDate = "";
        assertFalse(t.isOneOff());
    }

    @Test
    public void occursOn_puntual_coincide() {
        assertTrue(createPuntual().occursOn("2026-07-18", 7));
    }

    @Test
    public void occursOn_puntual_noCoincide() {
        assertFalse(createPuntual().occursOn("2026-07-19", 1));
    }

    @Test
    public void occursOn_recurrente_coincide() {
        assertTrue(createRecurrente().occursOn("2026-07-22", 3));
    }

    @Test
    public void occursOn_recurrente_noCoincide() {
        assertFalse(createRecurrente().occursOn("2026-07-23", 4));
    }

    @Test
    public void occursOn_daysOfWeek_nulo() {
        Task t = createRecurrente();
        t.daysOfWeek = null;
        assertFalse(t.occursOn("2026-07-20", 1));
    }

    @Test
    public void occursOn_daysOfWeek_vacio() {
        Task t = createRecurrente();
        t.daysOfWeek = "[]";
        assertFalse(t.occursOn("2026-07-20", 1));
    }

    @Test
    public void occursOn_daysOfWeek_malformed() {
        Task t = createRecurrente();
        t.daysOfWeek = "[mal";
        assertFalse(t.occursOn("2026-07-20", 1));
    }

    @Test
    public void constructor_recurrente_specificDateNull() {
        Task t = createRecurrente();
        assertNull(t.specificDate);
    }

    @Test
    public void constructor_puntual_isOneOffTrue() {
        assertTrue(createPuntual().isOneOff());
    }
}
