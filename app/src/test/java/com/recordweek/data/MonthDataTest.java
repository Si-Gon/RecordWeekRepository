package com.recordweek.data;

import org.junit.Test;

import static org.junit.Assert.*;

public class MonthDataTest {

    @Test
    public void key_format() {
        assertEquals("12|2026-07-18", MonthData.key(12, "2026-07-18"));
    }

    @Test
    public void key_conIdCero() {
        assertEquals("0|2026-01-01", MonthData.key(0, "2026-01-01"));
    }

    @Test
    public void key_fechaVacia() {
        assertEquals("5|", MonthData.key(5, ""));
    }
}
