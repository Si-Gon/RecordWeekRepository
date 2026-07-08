package com.recordweek.data;

import org.junit.Test;

import static org.junit.Assert.*;

public class AnalyticsDataTest {

    @Test
    public void isEmpty_recienCreado() {
        AnalyticsData data = new AnalyticsData();
        assertTrue(data.isEmpty());
    }

    @Test
    public void isEmpty_conCompletado() {
        AnalyticsData data = new AnalyticsData();
        data.totalCompletedAllTime = 5;
        assertFalse(data.isEmpty());
    }

    @Test
    public void isEmpty_conScheduled() {
        AnalyticsData data = new AnalyticsData();
        data.weekScheduled = 3;
        assertFalse(data.isEmpty());
    }
}
