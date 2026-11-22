package com.gunizi.app;

import org.junit.Test;
import java.time.LocalDateTime;
import static org.junit.Assert.*;

public class HistoryScheduleWindowTest {
    private static LocalDateTime at(int day,int hour,int minute){
        return LocalDateTime.of(2026,11,23+day,hour,minute);
    }
    @Test public void weekdayBoundaries(){
        int weekdays=31;
        assertFalse(HistoryScheduleWindow.active(at(0,7,59),480,1050,weekdays));
        assertTrue(HistoryScheduleWindow.active(at(0,8,0),480,1050,weekdays));
        assertTrue(HistoryScheduleWindow.active(at(0,17,29),480,1050,weekdays));
        assertFalse(HistoryScheduleWindow.active(at(0,17,30),480,1050,weekdays));
        assertFalse(HistoryScheduleWindow.active(at(5,9,0),480,1050,weekdays));
    }
    @Test public void overnightWindowUsesStartDay(){
        int monday=1;
        assertFalse(HistoryScheduleWindow.active(at(0,21,59),1320,360,monday));
        assertTrue(HistoryScheduleWindow.active(at(0,22,0),1320,360,monday));
        assertTrue(HistoryScheduleWindow.active(at(1,5,59),1320,360,monday));
        assertFalse(HistoryScheduleWindow.active(at(1,6,0),1320,360,monday));
        assertFalse(HistoryScheduleWindow.active(at(1,22,0),1320,360,monday));
    }
    @Test public void disablingDaysAndInvalidRangesFailsClosed(){
        assertFalse(HistoryScheduleWindow.valid(480,480,31));
        assertFalse(HistoryScheduleWindow.valid(500,1000,0));
        assertFalse(HistoryScheduleWindow.active(at(0,9,0),500,1000,0));
        assertFalse(HistoryScheduleWindow.active(at(0,9,0),480,480,31));
        assertFalse(HistoryScheduleWindow.active(at(0,9,0),-1,1000,31));
    }
}
