package com.gunizi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** A one-time setup; no sensitive audio stored in preferences. */
final class Prefs {
    static final String MODE_WORK = "work";
    static final String MODE_CONTINUOUS = "continuous";
    static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private final SharedPreferences p;
    Prefs(Context c) { p = c.getSharedPreferences("gunizi", Context.MODE_PRIVATE); }
    String mode() { return p.getString("mode", MODE_WORK); }
    void mode(String x) { p.edit().putString("mode", x).apply(); }
    int startHour() { return p.getInt("sh", 8); }
    int startMinute() { return p.getInt("sm", 0); }
    int endHour() { return p.getInt("eh", 17); }
    int endMinute() { return p.getInt("em", 30); }
    void start(int h,int m) { p.edit().putInt("sh",h).putInt("sm",m).apply(); }
    void end(int h,int m) { p.edit().putInt("eh",h).putInt("em",m).apply(); }
    int days() { return p.getInt("days",31); }
    void days(int mask) { p.edit().putInt("days",mask).apply(); }
    boolean isDayEnabled(DayOfWeek day) { return (days() & (1 << (day.getValue()-1)))!=0; }
    boolean validRange() {
        return LocalTime.of(startHour(),startMinute()).isBefore(LocalTime.of(endHour(),endMinute()));
    }
    boolean inWorkWindow() {
        LocalDateTime dt=LocalDateTime.now(ZONE);
        LocalTime t=dt.toLocalTime();
        return isDayEnabled(dt.getDayOfWeek()) && validRange()
            && !t.isBefore(LocalTime.of(startHour(),startMinute()))
            && t.isBefore(LocalTime.of(endHour(),endMinute()));
    }
    Uri folder() {
        String s=p.getString("folder",null);
        return s==null?null:Uri.parse(s);
    }
    void folder(Uri uri) { p.edit().putString("folder",uri.toString()).apply(); }
    String timeString(boolean start) {
        return String.format(java.util.Locale.forLanguageTag("tr-TR"),"%02d.%02d",
            start?startHour():endHour(),start?startMinute():endMinute());
    }
    boolean setupDone() { return p.getBoolean("setup_done",false); }
    void setSetupDone(boolean b) { p.edit().putBoolean("setup_done",b).apply(); }
}
