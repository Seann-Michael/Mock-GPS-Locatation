package com.seannmichael.mockdrive;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public final class CampaignStore {
    private static final String PREFS="campaign_store", KEY="campaigns";
    private CampaignStore() {}

    public static synchronized JSONArray all(Context c) {
        try { return new JSONArray(c.getSharedPreferences(PREFS,0).getString(KEY,"[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static synchronized JSONObject save(Context c, JSONObject campaign) throws Exception {
        if (!campaign.has("id")) campaign.put("id", UUID.randomUUID().toString());
        if (!campaign.has("enabled")) campaign.put("enabled", true);
        campaign.put("updatedAtEpochMs", System.currentTimeMillis());
        JSONArray a=all(c); boolean replaced=false;
        for(int i=0;i<a.length();i++) if(campaign.getString("id").equals(a.getJSONObject(i).optString("id"))){a.put(i,campaign);replaced=true;break;}
        if(!replaced)a.put(campaign);
        c.getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();
        return campaign;
    }

    public static JSONObject get(Context c,String id){
        JSONArray a=all(c); for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&id.equals(x.optString("id")))return x;} return null;
    }

    public static synchronized boolean delete(Context c,String id){
        JSONArray a=all(c),out=new JSONArray(); boolean found=false;
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&id.equals(x.optString("id")))found=true;else out.put(x);}
        c.getSharedPreferences(PREFS,0).edit().putString(KEY,out.toString()).apply(); return found;
    }

    public static void addHistory(Context c,String id,String status,String detail){
        try{JSONObject x=get(c,id);if(x==null)return;JSONArray h=x.optJSONArray("history");if(h==null)h=new JSONArray();h.put(new JSONObject().put("timeEpochMs",System.currentTimeMillis()).put("status",status).put("detail",detail));x.put("history",h);x.put("lastStatus",status);save(c,x);}catch(Exception ignored){}
    }
}
