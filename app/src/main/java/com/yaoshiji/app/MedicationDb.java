package com.yaoshiji.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.*;
import java.util.*;

public class MedicationDb extends SQLiteOpenHelper {
    public static final String DB="yaoshiji.db";
    public MedicationDb(Context c){super(c,DB,null,3);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE medication(id INTEGER PRIMARY KEY AUTOINCREMENT, groupUuid TEXT, name TEXT NOT NULL, route TEXT, mealTiming TEXT, startDate TEXT NOT NULL, endDate TEXT, intervalDays INTEGER NOT NULL DEFAULT 1, active INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL, colorIndex INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE dose_schedule(id INTEGER PRIMARY KEY AUTOINCREMENT, medicationId INTEGER NOT NULL, seq INTEGER NOT NULL, time TEXT NOT NULL, amount REAL NOT NULL, unit TEXT NOT NULL, FOREIGN KEY(medicationId) REFERENCES medication(id))");
        db.execSQL("CREATE TABLE dose_record(id INTEGER PRIMARY KEY AUTOINCREMENT, medicationId INTEGER NOT NULL, scheduleId INTEGER NOT NULL, date TEXT NOT NULL, scheduledAt INTEGER NOT NULL, takenAt INTEGER, status TEXT NOT NULL, plannedAmount REAL NOT NULL, unit TEXT NOT NULL, note TEXT, UNIQUE(scheduleId,date))");
        createInventoryTables(db);
    }
    private void createInventoryTables(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_account(groupUuid TEXT PRIMARY KEY, unit TEXT NOT NULL, openingQty REAL NOT NULL DEFAULT 0, alertDays INTEGER NOT NULL DEFAULT 7, enabled INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS stock_txn(id INTEGER PRIMARY KEY AUTOINCREMENT, groupUuid TEXT NOT NULL, delta REAL NOT NULL, kind TEXT NOT NULL, createdAt INTEGER NOT NULL, refKey TEXT UNIQUE, note TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){try{db.execSQL("ALTER TABLE medication ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0");}catch(Exception ignored){}}
        if(oldVersion<3)createInventoryTables(db);
    }

    public long addMedication(String name,String route,String meal,String start,int interval,List<DoseInput> doses,String groupUuid,int colorIndex){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            ContentValues v=new ContentValues();v.put("groupUuid",groupUuid==null?UUID.randomUUID().toString():groupUuid);v.put("name",name);v.put("route",route);v.put("mealTiming",meal);v.put("startDate",start);v.put("intervalDays",Math.max(1,interval));v.put("active",1);v.put("createdAt",System.currentTimeMillis());v.put("colorIndex",Math.max(0,colorIndex));
            long id=db.insertOrThrow("medication",null,v);
            int i=1;for(DoseInput d:doses){ContentValues s=new ContentValues();s.put("medicationId",id);s.put("seq",i++);s.put("time",d.time);s.put("amount",d.amount);s.put("unit",d.unit);db.insertOrThrow("dose_schedule",null,s);}db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public long addMedication(String name,String route,String meal,String start,int interval,List<DoseInput> doses,String groupUuid){return addMedication(name,route,meal,start,interval,doses,groupUuid,0);}

    public long editAsNewVersion(long oldId,String name,String route,String meal,int interval,List<DoseInput>doses,int colorIndex){
        SQLiteDatabase db=getWritableDatabase();Cursor c=db.rawQuery("SELECT groupUuid FROM medication WHERE id=?",new String[]{String.valueOf(oldId)});String group=UUID.randomUUID().toString();if(c.moveToFirst())group=c.getString(0);c.close();
        LocalDate today=LocalDate.now();ContentValues end=new ContentValues();end.put("active",0);end.put("endDate",today.minusDays(1).toString());db.update("medication",end,"id=?",new String[]{String.valueOf(oldId)});
        return addMedication(name,route,meal,today.toString(),interval,doses,group,colorIndex);
    }
    public void editAsNewVersion(long oldId,String name,String route,String meal,int interval,List<DoseInput>doses){Medication m=medication(oldId);editAsNewVersion(oldId,name,route,meal,interval,doses,m==null?0:m.colorIndex);}

    public void endMedication(long id){ContentValues v=new ContentValues();v.put("active",0);v.put("endDate",LocalDate.now().toString());getWritableDatabase().update("medication",v,"id=?",new String[]{String.valueOf(id)});}
    public List<Medication> activeMedications(){return medicationsForSql("active=1",null);}
    public Medication medication(long id){List<Medication> l=medicationsForSql("id=?",new String[]{String.valueOf(id)});return l.isEmpty()?null:l.get(0);}
    private List<Medication> medicationsForSql(String where,String[] args){ArrayList<Medication> out=new ArrayList<>();Cursor c=getReadableDatabase().query("medication",null,where,args,null,null,"createdAt DESC");while(c.moveToNext()){Medication m=readMed(c);m.doses=doses(m.id);out.add(m);}c.close();return out;}
    private Medication readMed(Cursor c){Medication m=new Medication();m.id=c.getLong(c.getColumnIndexOrThrow("id"));m.groupUuid=c.getString(c.getColumnIndexOrThrow("groupUuid"));m.name=c.getString(c.getColumnIndexOrThrow("name"));m.route=c.getString(c.getColumnIndexOrThrow("route"));m.mealTiming=c.getString(c.getColumnIndexOrThrow("mealTiming"));m.startDate=c.getString(c.getColumnIndexOrThrow("startDate"));m.endDate=c.getString(c.getColumnIndexOrThrow("endDate"));m.intervalDays=c.getInt(c.getColumnIndexOrThrow("intervalDays"));m.active=c.getInt(c.getColumnIndexOrThrow("active"))==1;int ci=c.getColumnIndex("colorIndex");m.colorIndex=ci>=0?c.getInt(ci):0;return m;}
    public List<Dose> doses(long medId){ArrayList<Dose> out=new ArrayList<>();Cursor c=getReadableDatabase().query("dose_schedule",null,"medicationId=?",new String[]{String.valueOf(medId)},null,null,"seq");while(c.moveToNext()){Dose d=new Dose();d.id=c.getLong(c.getColumnIndexOrThrow("id"));d.medicationId=medId;d.seq=c.getInt(c.getColumnIndexOrThrow("seq"));d.time=c.getString(c.getColumnIndexOrThrow("time"));d.amount=c.getDouble(c.getColumnIndexOrThrow("amount"));d.unit=c.getString(c.getColumnIndexOrThrow("unit"));out.add(d);}c.close();return out;}

    public List<Task> tasksFor(LocalDate date){
        ArrayList<Task> out=new ArrayList<>();Cursor c=getReadableDatabase().rawQuery("SELECT * FROM medication WHERE startDate<=? AND (endDate IS NULL OR endDate>=?)",new String[]{date.toString(),date.toString()});
        while(c.moveToNext()){
            Medication m=readMed(c);LocalDate start=LocalDate.parse(m.startDate);long days=java.time.temporal.ChronoUnit.DAYS.between(start,date);if(days<0||days%m.intervalDays!=0)continue;
            for(Dose d:doses(m.id)){Task t=new Task();t.medication=m;t.dose=d;t.date=date;t.record=record(d.id,date);out.add(t);}
        }
        c.close();out.sort(Comparator.comparing(a->a.dose.time));return out;
    }
    public Record record(long scheduleId,LocalDate date){Cursor c=getReadableDatabase().query("dose_record",null,"scheduleId=? AND date=?",new String[]{String.valueOf(scheduleId),date.toString()},null,null,null);Record r=null;if(c.moveToFirst()){r=new Record();r.id=c.getLong(c.getColumnIndexOrThrow("id"));r.status=c.getString(c.getColumnIndexOrThrow("status"));r.takenAt=c.isNull(c.getColumnIndexOrThrow("takenAt"))?null:c.getLong(c.getColumnIndexOrThrow("takenAt"));r.note=c.getString(c.getColumnIndexOrThrow("note"));r.scheduledAt=c.getLong(c.getColumnIndexOrThrow("scheduledAt"));}c.close();return r;}
    public void checkIn(Task t,long actualMillis){
        boolean wasTaken=t.record!=null&&t.record.taken();LocalTime lt=LocalTime.parse(t.dose.time);long scheduled=t.date.atTime(lt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();ContentValues v=new ContentValues();v.put("medicationId",t.medication.id);v.put("scheduleId",t.dose.id);v.put("date",t.date.toString());v.put("scheduledAt",scheduled);v.put("takenAt",actualMillis);v.put("status",Math.abs(actualMillis-scheduled)<=60*60*1000L?"TAKEN":"TAKEN_LATE");v.put("plannedAmount",t.dose.amount);v.put("unit",t.dose.unit);Record old=record(t.dose.id,t.date);if(old!=null&&old.note!=null)v.put("note",old.note);getWritableDatabase().insertWithOnConflict("dose_record",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        if(!wasTaken)applyConsumption(t,true);
    }
    public void undo(Task t){ContentValues v=new ContentValues();v.put("status","PENDING");v.putNull("takenAt");getWritableDatabase().update("dose_record",v,"scheduleId=? AND date=?",new String[]{String.valueOf(t.dose.id),t.date.toString()});applyConsumption(t,false);}
    private void applyConsumption(Task t,boolean consume){String ref="dose:"+t.dose.id+":"+t.date;SQLiteDatabase db=getWritableDatabase();if(consume){StockInfo si=stockInfo(t.medication.groupUuid);if(si!=null&&si.enabled){ContentValues v=new ContentValues();v.put("groupUuid",t.medication.groupUuid);v.put("delta",-Math.abs(t.dose.amount));v.put("kind","CONSUME");v.put("createdAt",System.currentTimeMillis());v.put("refKey",ref);v.put("note",t.medication.name);db.insertWithOnConflict("stock_txn",null,v,SQLiteDatabase.CONFLICT_IGNORE);}}else db.delete("stock_txn","refKey=?",new String[]{ref});}
    public void saveNote(Task t,String note){Record r=record(t.dose.id,t.date);if(r==null){LocalTime lt=LocalTime.parse(t.dose.time);long scheduled=t.date.atTime(lt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();ContentValues v=new ContentValues();v.put("medicationId",t.medication.id);v.put("scheduleId",t.dose.id);v.put("date",t.date.toString());v.put("scheduledAt",scheduled);v.put("status","PENDING");v.put("plannedAmount",t.dose.amount);v.put("unit",t.dose.unit);v.put("note",note);getWritableDatabase().insertWithOnConflict("dose_record",null,v,SQLiteDatabase.CONFLICT_REPLACE);}else{ContentValues v=new ContentValues();v.put("note",note);getWritableDatabase().update("dose_record",v,"scheduleId=? AND date=?",new String[]{String.valueOf(t.dose.id),t.date.toString()});}}

    public MonthStats monthStats(YearMonth ym){
        MonthStats s=new MonthStats();LocalDate today=LocalDate.now();long now=System.currentTimeMillis();LocalDate end=ym.atEndOfMonth();if(YearMonth.from(today).equals(ym)&&today.isBefore(end))end=today;if(end.isBefore(ym.atDay(1)))return s;
        for(LocalDate d=ym.atDay(1);!d.isAfter(end);d=d.plusDays(1)){
            int dayDue=0,dayTaken=0;
            for(Task t:tasksFor(d)){
                long scheduled=scheduledMillis(t);boolean taken=t.taken();if(scheduled>now&&!taken)continue;dayDue++;s.total++;
                if(taken){s.taken++;dayTaken++;if(t.record!=null&&"TAKEN_LATE".equals(t.record.status))s.late++;}
                else{s.interrupted++;InterruptedItem x=new InterruptedItem();x.date=d;x.time=t.dose.time;x.medication=t.medication.name;x.amount=t.dose.amount;x.unit=t.dose.unit;x.reason="尚未确认服用";x.medicationId=t.medication.id;x.scheduleId=t.dose.id;s.items.add(x);}
            }
            if(dayDue>0){s.dueDays++;if(dayTaken==dayDue)s.perfectDays++;}
        }
        return s;
    }
    public DayState dayState(LocalDate d){
        List<Task> ts=tasksFor(d);if(ts.isEmpty())return DayState.NONE;long now=System.currentTimeMillis();boolean anyTaken=false,anyPastDuePending=false,allTaken=true;
        for(Task t:ts){boolean taken=t.taken();if(taken)anyTaken=true;else{allTaken=false;if(scheduledMillis(t)<=now)anyPastDuePending=true;}}
        if(allTaken)return DayState.COMPLETE;
        if(anyPastDuePending)return anyTaken?DayState.PARTIAL:DayState.INTERRUPTED;
        if(anyTaken)return DayState.PARTIAL;
        return DayState.PLANNED;
    }

    public void supplement(Task t){
        boolean wasTaken=t.record!=null&&t.record.taken();long scheduled=scheduledMillis(t);ContentValues v=new ContentValues();v.put("medicationId",t.medication.id);v.put("scheduleId",t.dose.id);v.put("date",t.date.toString());v.put("scheduledAt",scheduled);v.put("takenAt",scheduled);v.put("status","SUPPLEMENTED");v.put("plannedAmount",t.dose.amount);v.put("unit",t.dose.unit);Record old=record(t.dose.id,t.date);if(old!=null&&old.note!=null)v.put("note",old.note);getWritableDatabase().insertWithOnConflict("dose_record",null,v,SQLiteDatabase.CONFLICT_REPLACE);if(!wasTaken)applyConsumption(t,true);
    }

    public Task findTask(long medicationId,long scheduleId,LocalDate date){for(Task t:tasksFor(date))if(t.medication.id==medicationId&&t.dose.id==scheduleId)return t;return null;}

    // Editing a schedule creates a new medication row but keeps the same groupUuid.
    // Streaks must span all historical versions in that group, not just the new row's startDate.
    private LocalDate firstStartDateForGroup(Medication m){
        LocalDate first=LocalDate.parse(m.startDate);
        if(m.groupUuid==null)return first;
        Cursor c=getReadableDatabase().rawQuery(
            "SELECT MIN(startDate) FROM medication WHERE groupUuid=?",new String[]{m.groupUuid});
        try{if(c.moveToFirst()&&!c.isNull(0)){LocalDate found=LocalDate.parse(c.getString(0));if(found.isBefore(first))first=found;}}
        finally{c.close();}
        return first;
    }
    public MedicationProgress medicationProgress(Medication m){
        MedicationProgress p=new MedicationProgress();p.medication=m;LocalDate today=LocalDate.now();long now=System.currentTimeMillis();LocalDate cursor=today;int checked=0;
        LocalDate historyStart=firstStartDateForGroup(m);
        while(checked<366&&!cursor.isBefore(historyStart)){
            ArrayList<Task> mine=new ArrayList<>();for(Task t:tasksFor(cursor))if(t.medication.groupUuid.equals(m.groupUuid))mine.add(t);
            if(!mine.isEmpty()){
                boolean allTaken=true;boolean hasDueMissing=false;
                for(Task t:mine){if(!t.taken()){allTaken=false;if(scheduledMillis(t)<=now)hasDueMissing=true;}}
                if(allTaken)p.streakDays++;
                else if(hasDueMissing)break;
                // If today's remaining tasks are still in the future, today is not counted yet,
                // but the previous streak remains valid. An early same-day check-in counts once all tasks are taken.
            }
            cursor=cursor.minusDays(1);checked++;
        }
        int searched=0;
        outer: for(LocalDate d=today;!d.isBefore(historyStart)&&searched<800;d=d.minusDays(1),searched++){
            for(Task t:tasksFor(d)){if(t.medication.groupUuid.equals(m.groupUuid)&&scheduledMillis(t)<=now&&!t.taken()){p.lastInterrupted=d.atTime(LocalTime.parse(t.dose.time));break outer;}}
        }
        p.stock=stockProjection(m);return p;
    }
    private long scheduledMillis(Task t){return t.date.atTime(LocalTime.parse(t.dose.time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();}

    public void configureStock(long medId,boolean enabled,double openingQty,String unit,int alertDays){Medication m=medication(medId);if(m==null)return;SQLiteDatabase db=getWritableDatabase();StockInfo old=stockInfo(m.groupUuid);ContentValues v=new ContentValues();v.put("groupUuid",m.groupUuid);v.put("unit",unit==null||unit.isEmpty()?"粒":unit);v.put("alertDays",Math.max(1,alertDays));v.put("enabled",enabled?1:0);v.put("updatedAt",System.currentTimeMillis());if(old==null)v.put("openingQty",Math.max(0,openingQty));else v.put("openingQty",old.openingQty);db.insertWithOnConflict("stock_account",null,v,SQLiteDatabase.CONFLICT_REPLACE);if(old!=null&&enabled){double delta=openingQty-currentStock(m.groupUuid);if(Math.abs(delta)>0.0001)addStockTxn(m.groupUuid,delta,"ADJUST","调整库存");}}
    public void addStock(long medId,double qty,String note){Medication m=medication(medId);if(m!=null&&qty>0)addStockTxn(m.groupUuid,qty,"PURCHASE",note==null?"补充库存":note);}
    private void addStockTxn(String group,double delta,String kind,String note){ContentValues v=new ContentValues();v.put("groupUuid",group);v.put("delta",delta);v.put("kind",kind);v.put("createdAt",System.currentTimeMillis());v.put("note",note);getWritableDatabase().insert("stock_txn",null,v);}
    public StockInfo stockInfoByMed(long medId){Medication m=medication(medId);return m==null?null:stockInfo(m.groupUuid);}
    public StockInfo stockInfo(String group){Cursor c=getReadableDatabase().query("stock_account",null,"groupUuid=?",new String[]{group},null,null,null);StockInfo s=null;if(c.moveToFirst()){s=new StockInfo();s.groupUuid=group;s.unit=c.getString(c.getColumnIndexOrThrow("unit"));s.openingQty=c.getDouble(c.getColumnIndexOrThrow("openingQty"));s.alertDays=c.getInt(c.getColumnIndexOrThrow("alertDays"));s.enabled=c.getInt(c.getColumnIndexOrThrow("enabled"))==1;s.currentQty=currentStock(group);}c.close();return s;}
    public double currentStock(String group){double q=0;Cursor a=getReadableDatabase().query("stock_account",new String[]{"openingQty"},"groupUuid=?",new String[]{group},null,null,null);if(a.moveToFirst())q=a.getDouble(0);a.close();Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(delta),0) FROM stock_txn WHERE groupUuid=?",new String[]{group});if(c.moveToFirst())q+=c.getDouble(0);c.close();return Math.max(0,q);}
    public StockProjection stockProjection(Medication m){StockInfo s=stockInfo(m.groupUuid);if(s==null||!s.enabled)return null;double perCycle=0;List<Dose> dd=m.doses==null?doses(m.id):m.doses;for(Dose d:dd)perCycle+=d.amount;double daily=perCycle/Math.max(1,m.intervalDays);StockProjection p=new StockProjection();p.current=s.currentQty;p.unit=s.unit;p.alertDays=s.alertDays;p.dailyUse=daily;if(daily>0){p.daysLeft=(int)Math.floor(p.current/daily);p.runOutDate=LocalDate.now().plusDays(p.daysLeft);}return p;}

    public static class DoseInput {public String time,unit;public double amount;public DoseInput(String t,double a,String u){time=t;amount=a;unit=u;}}
    public static class Medication {public long id;public String groupUuid,name,route,mealTiming,startDate,endDate;public int intervalDays,colorIndex;public boolean active;public List<Dose>doses;}
    public static class Dose {public long id,medicationId;public int seq;public String time,unit;public double amount;}
    public static class Record {public long id,scheduledAt;public String status,note;public Long takenAt;public boolean taken(){return ("TAKEN".equals(status)||"TAKEN_LATE".equals(status)||"SUPPLEMENTED".equals(status))&&takenAt!=null;}}
    public static class Task {public Medication medication;public Dose dose;public LocalDate date;public Record record;public boolean taken(){return record!=null&&record.taken();}}
    public static class MonthStats {public int taken,interrupted,total,late,perfectDays,dueDays;public ArrayList<InterruptedItem> items=new ArrayList<>();}
    public static class InterruptedItem {public LocalDate date;public String time,medication,unit,reason;public double amount;public long medicationId,scheduleId;}
    public enum DayState {NONE,PLANNED,COMPLETE,PARTIAL,INTERRUPTED}
    public static class MedicationProgress {public Medication medication;public int streakDays;public LocalDateTime lastInterrupted;public StockProjection stock;}
    public static class StockInfo {public String groupUuid,unit;public double openingQty,currentQty;public int alertDays;public boolean enabled;}
    public static class StockProjection {public double current,dailyUse;public String unit;public int alertDays,daysLeft;public LocalDate runOutDate;}
}
