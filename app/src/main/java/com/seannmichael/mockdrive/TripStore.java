package com.seannmichael.mockdrive;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public final class TripStore {
    private static final String PREFS="mock_drive_store";
    private static final String KEY_TRIPS="trips";
    private static final String KEY_TOKEN="api_token";
    private TripStore(){}
    public static synchronized JSONArray all(Context context){try{return new JSONArray(context.getSharedPreferences(PREFS,0).getString(KEY_TRIPS,"[]"));}catch(Exception e){return new JSONArray();}}
    public static synchronized JSONObject save(Context context,JSONObject trip)throws Exception{JSONArray trips=all(context);String id=trip.optString("id","");if(id.isEmpty()){id=UUID.randomUUID().toString();trip.put("id",id);}if(!trip.has("status"))trip.put("status","queued");boolean replaced=false;for(int i=0;i<trips.length();i++){if(id.equals(trips.getJSONObject(i).optString("id"))){trips.put(i,trip);replaced=true;break;}}if(!replaced)trips.put(trip);write(context,trips);return trip;}
    public static synchronized JSONObject get(Context context,String id){JSONArray trips=all(context);for(int i=0;i<trips.length();i++){JSONObject t=trips.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))return t;}return null;}
    public static synchronized boolean delete(Context context,String id){JSONArray src=all(context),dst=new JSONArray();boolean removed=false;for(int i=0;i<src.length();i++){JSONObject t=src.optJSONObject(i);if(t!=null&&id.equals(t.optString("id")))removed=true;else dst.put(t);}write(context,dst);return removed;}
    public static synchronized void updateStatus(Context context,String id,String status){try{JSONObject t=get(context,id);if(t!=null){t.put("status",status);save(context,t);HistoryStore.syncStatus(context,t,status);}}catch(Exception ignored){}}
    public static String token(Context context){SharedPreferences p=context.getSharedPreferences(PREFS,0);String token=p.getString(KEY_TOKEN,"");if(token.isEmpty()){token=UUID.randomUUID().toString().replace("-","");p.edit().putString(KEY_TOKEN,token).apply();}return token;}
    public static void setToken(Context context,String token){context.getSharedPreferences(PREFS,0).edit().putString(KEY_TOKEN,token).apply();}
    private static void write(Context context,JSONArray trips){context.getSharedPreferences(PREFS,0).edit().putString(KEY_TRIPS,trips.toString()).apply();}
}
