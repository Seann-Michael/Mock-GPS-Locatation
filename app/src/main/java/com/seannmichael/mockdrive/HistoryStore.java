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

    public static synchronized JSONArray all(Context c){try{return new JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}}

    public static synchronized JSONObject begin(Context c,JSONObject trip){
        JSONObject existing=findByTrip(c,trip.optString("id"));if(existing!=null&&"running".equals(existing.optString("status")))return existing;
        try{
            JSONObject r=new JSONObject();r.put("historyId",UUID.randomUUID().toString());r.put("trip",new JSONObject(trip.toString()));r.put("tripId",trip.optString("id"));
            r.put("startTime",System.currentTimeMillis());r.put("endTime",0);r.put("durationMs",0);r.put("miles",estimateMiles(trip));r.put("status","running");
            r.put("startAddress",trip.optString("startAddress",coordinateLabel(trip,0)));r.put("endAddress",trip.optString("endAddress",coordinateLabel(trip,1)));
            r.put("diagnostics","Navigation record created. Detailed runtime logging begins when the GPS service starts.");
            r.put("diagnosticFilesAvailable",false);
            JSONArray src=all(c),dst=new JSONArray();dst.put(r);for(int i=0;i<src.length()&&dst.length()<MAX;i++)dst.put(src.optJSONObject(i));write(c,dst);return r;
        }catch(Exception e){return new JSONObject();}
    }

    public static synchronized void syncStatus(Context c,JSONObject trip,String status){
        try{
            JSONObject r=findByTrip(c,trip.optString("id"));if(r==null)r=begin(c,trip);
            String id=r.optString("historyId");String normalized=status;
            if("active".equals(status)||"routing".equals(status)||"queued".equals(status))normalized="running";
            if("completed".equals(status))normalized="success";
            String existing=r.optString("diagnostics","");
            String details="Trip status changed to "+normalized+" at "+System.currentTimeMillis()+". Trip ID: "+trip.optString("id")+". Speed: "+Math.round(trip.optDouble("averageSpeedMph",35))+" mph.";
            update(c,id,normalized,estimateMiles(trip),existing+"\n"+details);
        }catch(Exception ignored){}
    }

    public static synchronized void attachDiagnostics(Context c,String historyId,String diagnostics){
        try{
            JSONArray a=all(c);
            for(int i=0;i<a.length();i++){
                JSONObject r=a.optJSONObject(i);
                if(r==null||!historyId.equals(r.optString("historyId")))continue;
                r.put("diagnostics",diagnostics==null?"":diagnostics);
                r.put("diagnosticFilesAvailable",true);
                r.put("diagnosticsUpdatedAt",System.currentTimeMillis());
                break;
            }
            write(c,a);
        }catch(Exception ignored){}
    }

    public static synchronized void update(Context c,String historyId,String status,double miles,String diagnostics){
        try{JSONArray a=all(c);for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r==null||!historyId.equals(r.optString("historyId")))continue;long end=System.currentTimeMillis();r.put("status",status);r.put("miles",miles);r.put("diagnostics",diagnostics);if(!"running".equals(status)){r.put("endTime",end);r.put("durationMs",Math.max(0,end-r.optLong("startTime",end)));}break;}write(c,a);}catch(Exception ignored){}
    }

    public static synchronized JSONObject get(Context c,String id){JSONArray a=all(c);for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&id.equals(r.optString("historyId")))return r;}return null;}
    public static synchronized JSONObject findByTrip(Context c,String tripId){if(tripId==null||tripId.isEmpty())return null;JSONArray a=all(c);for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&tripId.equals(r.optString("tripId")))return r;}return null;}
    public static synchronized JSONObject cloneTrip(Context c,String id)throws Exception{JSONObject r=get(c,id);if(r==null)throw new Exception("History record not found");JSONObject t=new JSONObject(r.getJSONObject("trip").toString());t.remove("id");t.put("status","queued");return TripStore.save(c,t);}

    private static double estimateMiles(JSONObject trip){try{JSONArray w=trip.getJSONArray("waypoints");double meters=0;for(int i=1;i<w.length();i++){JSONObject a=w.getJSONObject(i-1),b=w.getJSONObject(i);meters+=distance(a.getDouble("latitude"),a.getDouble("longitude"),b.getDouble("latitude"),b.getDouble("longitude"));}return meters/1609.344;}catch(Exception e){return 0;}}
    private static double distance(double la1,double lo1,double la2,double lo2){double r=6371000,p1=Math.toRadians(la1),p2=Math.toRadians(la2),dp=Math.toRadians(la2-la1),dl=Math.toRadians(lo2-lo1);double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return r*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    private static String coordinateLabel(JSONObject trip,int index){try{JSONObject w=trip.getJSONArray("waypoints").getJSONObject(index);return w.getDouble("latitude")+", "+w.getDouble("longitude");}catch(Exception e){return "Unknown";}}
    private static void write(Context c,JSONArray a){c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();}
}