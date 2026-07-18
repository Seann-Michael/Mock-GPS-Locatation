package com.seannmichael.mockdrive;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public final class HistoryStore {
    private static final String PREFS="mock_drive_history";
    private static final String KEY="records";
    private static final int MAX=100;
    private HistoryStore(){}

    public static synchronized JSONArray all(Context c){
        try{return new JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]"));}
        catch(Exception e){return new JSONArray();}
    }

    public static synchronized JSONObject begin(Context c,JSONObject trip){
        try{
            JSONObject r=new JSONObject();
            String id=UUID.randomUUID().toString();
            r.put("historyId",id);
            r.put("trip",new JSONObject(trip.toString()));
            r.put("tripId",trip.optString("id"));
            r.put("startTime",System.currentTimeMillis());
            r.put("endTime",0);
            r.put("durationMs",0);
            r.put("miles",0);
            r.put("status","running");
            r.put("startAddress",trip.optString("startAddress",coordinateLabel(trip,0)));
            r.put("endAddress",trip.optString("endAddress",coordinateLabel(trip,1)));
            r.put("diagnostics","Navigation started");
            JSONArray src=all(c),dst=new JSONArray();dst.put(r);
            for(int i=0;i<src.length()&&dst.length()<MAX;i++)dst.put(src.optJSONObject(i));
            write(c,dst);return r;
        }catch(Exception e){return new JSONObject();}
    }

    public static synchronized void update(Context c,String historyId,String status,double miles,String diagnostics){
        try{
            JSONArray a=all(c);
            for(int i=0;i<a.length();i++){
                JSONObject r=a.optJSONObject(i);if(r==null||!historyId.equals(r.optString("historyId")))continue;
                long end=System.currentTimeMillis();r.put("status",status);r.put("miles",miles);r.put("diagnostics",diagnostics);
                if(!"running".equals(status)){r.put("endTime",end);r.put("durationMs",Math.max(0,end-r.optLong("startTime",end)));}
                break;
            }
            write(c,a);
        }catch(Exception ignored){}
    }

    public static synchronized JSONObject get(Context c,String id){JSONArray a=all(c);for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&id.equals(r.optString("historyId")))return r;}return null;}

    public static synchronized JSONObject cloneTrip(Context c,String id)throws Exception{
        JSONObject r=get(c,id);if(r==null)throw new Exception("History record not found");
        JSONObject t=new JSONObject(r.getJSONObject("trip").toString());t.remove("id");t.put("status","queued");return TripStore.save(c,t);
    }

    private static String coordinateLabel(JSONObject trip,int index){try{JSONObject w=trip.getJSONArray("waypoints").getJSONObject(index);return w.getDouble("latitude")+", "+w.getDouble("longitude");}catch(Exception e){return "Unknown";}}
    private static void write(Context c,JSONArray a){c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();}
}
