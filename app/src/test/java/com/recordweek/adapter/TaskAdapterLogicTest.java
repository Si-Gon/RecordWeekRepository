package com.recordweek.adapter;

import org.junit.Test;

import static org.junit.Assert.*;

public class TaskAdapterLogicTest {

    @Test
    public void resolveTaskColor_blue() {
        assertEquals(0xFF5B8DEF, TaskAdapter.resolveTaskColorStatic("BLUE"));
    }

    @Test
    public void resolveTaskColor_red() {
        assertEquals(0xFFEF5350, TaskAdapter.resolveTaskColorStatic("RED"));
    }

    @Test
    public void resolveTaskColor_yellow() {
        assertEquals(0xFFFFEE58, TaskAdapter.resolveTaskColorStatic("YELLOW"));
    }

    @Test
    public void resolveTaskColor_green() {
        assertEquals(0xFF66BB6A, TaskAdapter.resolveTaskColorStatic("GREEN"));
    }

    @Test
    public void resolveTaskColor_pink() {
        assertEquals(0xFFEC407A, TaskAdapter.resolveTaskColorStatic("PINK"));
    }

    @Test
    public void resolveTaskColor_orange() {
        assertEquals(0xFFFFA726, TaskAdapter.resolveTaskColorStatic("ORANGE"));
    }

    @Test
    public void resolveTaskColor_purple() {
        assertEquals(0xFFAB47BC, TaskAdapter.resolveTaskColorStatic("PURPLE"));
    }

    @Test
    public void resolveTaskColor_null() {
        assertEquals(0xFFE0922F, TaskAdapter.resolveTaskColorStatic(null));
    }

    @Test
    public void resolveTaskColor_desconocido() {
        assertEquals(0xFFE0922F, TaskAdapter.resolveTaskColorStatic("MAGENTA"));
    }
}
