package com.gunizi.app;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A schedule selects the day the capture WINDOW starts. Overnight windows
 * continue into the next calendar day even when that following day is unselected.
 * No Android dependency so boundary cases can be unit tested.
 */
final class HistoryScheduleWindow {
    private HistoryScheduleWindow(){}
    static boolean valid(int startMinutes,int endMinutes,int days){
        return startMinutes>=0&&startMinutes<1440&&endMinutes>=0&&
            endMinutes<1440&&startMinutes!=endMinutes&&(days&127)!=0;
    }
    static boolean active(LocalDateTime now,int startMinutes,int endMinutes,int days){
        if(now==null||!valid(startMinutes,endMinutes,days))return false;
        int current=now.getHour()*60+now.getMinute();
        int today=bit(now.getDayOfWeek());
        if(startMinutes<endMinutes){
            return (days&today)!=0&&current>=startMinutes&&current<endMinutes;
        }
        // Crosses midnight; a Monday 22:00 plan includes Tuesday 05:59.
        if(current>=startMinutes)return (days&today)!=0;
        return current<endMinutes&&(days&bit(now.minusDays(1).getDayOfWeek()))!=0;
    }
    private static int bit(DayOfWeek day){return 1<<(day.getValue()-1);}
}
