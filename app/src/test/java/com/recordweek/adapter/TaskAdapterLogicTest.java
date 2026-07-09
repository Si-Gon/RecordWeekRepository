package com.recordweek.adapter;

import com.recordweek.utils.CardStyle;
import org.junit.Test;

import static org.junit.Assert.*;

// El mapeo nombre-de-color -> ARGB vivia antes en TaskAdapter.resolveTaskColorStatic.
// Se movio a CardStyle.taskColor (utils) para dejar de duplicarlo en TaskAdapter,
// MonthlyFragment y AddTaskActivity. Los valores esperados no cambian, asi que estos
// tests siguen siendo la red de seguridad de que ningun color se altero al centralizar.
public class TaskAdapterLogicTest {

    @Test
    public void resolveTaskColor_blue() {
        assertEquals(0xFF5B8DEF, CardStyle.taskColor("BLUE"));
    }

    @Test
    public void resolveTaskColor_red() {
        assertEquals(0xFFEF5350, CardStyle.taskColor("RED"));
    }

    @Test
    public void resolveTaskColor_yellow() {
        assertEquals(0xFFFFEE58, CardStyle.taskColor("YELLOW"));
    }

    @Test
    public void resolveTaskColor_green() {
        assertEquals(0xFF66BB6A, CardStyle.taskColor("GREEN"));
    }

    @Test
    public void resolveTaskColor_pink() {
        assertEquals(0xFFEC407A, CardStyle.taskColor("PINK"));
    }

    @Test
    public void resolveTaskColor_orange() {
        assertEquals(0xFFFFA726, CardStyle.taskColor("ORANGE"));
    }

    @Test
    public void resolveTaskColor_purple() {
        assertEquals(0xFFAB47BC, CardStyle.taskColor("PURPLE"));
    }

    @Test
    public void resolveTaskColor_null() {
        assertEquals(0xFFE0922F, CardStyle.taskColor(null));
    }

    @Test
    public void resolveTaskColor_desconocido() {
        assertEquals(0xFFE0922F, CardStyle.taskColor("MAGENTA"));
    }
}
