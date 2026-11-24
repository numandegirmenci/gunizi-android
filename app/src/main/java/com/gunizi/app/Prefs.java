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
    static final String MODE_HISTORY = "history";
    static final String MODE_MANUAL = "manual";
    static final String MODE_CONTINUOUS = "continuous";
    static final ZoneId ZONE = ZoneId.systemDefault();
    private final SharedPreferences p;
    Prefs(Context c) { p = c.getSharedPreferences("gunizi", Context.MODE_PRIVATE); }
    String mode() {
        String v=p.getString("mode",MODE_WORK);
        return (MODE_CONTINUOUS.equals(v)||MODE_MANUAL.equals(v))?MODE_HISTORY:v;
    }
    void mode(String x) { p.edit().putString("mode", x).apply(); }
    String aiModel(){
        String selected=p.getString("ai_model",AiModelStore.GEMMA);
        return AiModelStore.QWEN.equals(selected)||AiModelStore.QWEN4.equals(selected)?
            selected:AiModelStore.GEMMA;
    }
    void aiModel(String model){
        if(!AiModelStore.QWEN.equals(model)&&!AiModelStore.QWEN4.equals(model)&&
           !AiModelStore.GEMMA.equals(model))
            throw new IllegalArgumentException("Model");
        p.edit().putString("ai_model",model).apply();
    }
    int durationMinutes() {
        int n=p.getInt("manual_minutes",15);
        return (n==1||n==5||n==15||n==60||n==120||n==240||n==360)?n:15;
    }
    void durationMinutes(int value){
        if(value!=1&&value!=5&&value!=15&&value!=60&&value!=120&&value!=240&&value!=360)
            throw new IllegalArgumentException("Geçerli geçmiş seçimi 1–360 dk");
        p.edit().putInt("manual_minutes",value).apply();
    }
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
        LocalDateTime dt=LocalDateTime.now(); // current phone time and time zone
        LocalTime t=dt.toLocalTime();
        return isDayEnabled(dt.getDayOfWeek()) && validRange()
            && !t.isBefore(LocalTime.of(startHour(),startMinute()))
            && t.isBefore(LocalTime.of(endHour(),endMinute()));
    }
    /** Independent schedule for the temporary rolling history, not the Mesai file recorder. */
    boolean historyScheduleEnabled(){return p.getBoolean("history_schedule_armed",false);}
    void historyScheduleEnabled(boolean value){
        p.edit().putBoolean("history_schedule_armed",value).apply();
    }
    int historyStartHour(){return p.getInt("history_start_hour",startHour());}
    int historyStartMinute(){return p.getInt("history_start_minute",startMinute());}
    int historyEndHour(){return p.getInt("history_end_hour",endHour());}
    int historyEndMinute(){return p.getInt("history_end_minute",endMinute());}
    int historyDays(){return p.getInt("history_days",days());}
    void historyTime(boolean start,int hour,int minute){
        if(hour<0||hour>23||minute<0||minute>59)
            throw new IllegalArgumentException("Geçersiz saat");
        p.edit().putInt(start?"history_start_hour":"history_end_hour",hour)
            .putInt(start?"history_start_minute":"history_end_minute",minute).apply();
    }
    void historyDays(int mask){p.edit().putInt("history_days",mask&127).apply();}
    String historyTimeString(boolean start){
        return String.format(java.util.Locale.forLanguageTag("tr-TR"),"%02d:%02d",
            start?historyStartHour():historyEndHour(),
            start?historyStartMinute():historyEndMinute());
    }
    boolean validHistorySchedule(){
        return HistoryScheduleWindow.valid(
            historyStartHour()*60+historyStartMinute(),
            historyEndHour()*60+historyEndMinute(),historyDays());
    }
    boolean inHistoryWindow(){
        return HistoryScheduleWindow.active(LocalDateTime.now(ZONE),
            historyStartHour()*60+historyStartMinute(),
            historyEndHour()*60+historyEndMinute(),historyDays());
    }
    RecordingOptions recordingOptions(){
        return new RecordingOptions(p.getInt("record_format",0),p.getInt("record_quality",0));
    }
    void recordingOptions(int format,int quality){
        RecordingOptions o=new RecordingOptions(format,quality);
        p.edit().putInt("record_format",o.format).putInt("record_quality",o.quality).apply();
    }
    String recordingPrefix(){return p.getString("record_prefix","GunIzi");}
    int recordingNameFields(){return p.getInt("record_name_fields",RecordingOptions.DEFAULT_FIELDS);}
    void recordingName(String prefix,int fields){
        p.edit().putString("record_prefix",RecordingOptions.safePrefix(prefix))
            .putInt("record_name_fields",fields&31).apply();
    }
    String recordingName(String mode,LocalDateTime time,RecordingOptions options){
        return RecordingOptions.name(recordingPrefix(),recordingNameFields(),mode,time,options.extension());
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
