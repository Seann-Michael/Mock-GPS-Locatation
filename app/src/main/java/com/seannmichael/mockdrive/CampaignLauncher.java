package com.seannmichael.mockdrive;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;

public final class CampaignLauncher {
    private CampaignLauncher() {}

    public static void start(Context c, JSONObject campaign, boolean openMaps) throws Exception {
        String id=campaign.getString("id");
        if(!campaign.optBoolean("enabled",true))throw new Exception("Campaign is disabled");
        JSONObject origin=campaign.getJSONObject("origin");
        JSONObject destination=campaign.getJSONObject("destination");
        double lat=origin.getDouble("latitude"),lon=origin.getDouble("longitude");

        Intent hold=new Intent(c,MockLocationService.class).setAction(MockLocationService.ACTION_TELEPORT)
                .putExtra(MockLocationService.EXTRA_LAT,lat).putExtra(MockLocationService.EXTRA_LON,lon);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(hold);else c.startService(hold);
        CampaignStore.addHistory(c,id,"origin_set","Mock GPS set before Maps launch");

        if(openMaps){
            String dest=destination.optString("businessName",destination.optString("address",destination.getDouble("latitude")+","+destination.getDouble("longitude")));
            StringBuilder u=new StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving&dir_action=navigate");
            u.append("&origin=").append(lat).append(',').append(lon);
            u.append("&destination=").append(URLEncoder.encode(dest,"UTF-8"));
            String placeId=destination.optString("placeId",""); if(!placeId.isEmpty())u.append("&destination_place_id=").append(URLEncoder.encode(placeId,"UTF-8"));
            JSONArray stops=campaign.optJSONArray("stops");
            if(stops!=null&&stops.length()>0){StringBuilder w=new StringBuilder();for(int i=0;i<stops.length();i++){JSONObject p=stops.getJSONObject(i);if(i>0)w.append('|');w.append(p.getDouble("latitude")).append(',').append(p.getDouble("longitude"));}u.append("&waypoints=").append(URLEncoder.encode(w.toString(),"UTF-8"));}
            Intent maps=new Intent(Intent.ACTION_VIEW, Uri.parse(u.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            maps.setPackage("com.google.android.apps.maps");
            try{c.startActivity(maps);}catch(Exception e){maps.setPackage(null);c.startActivity(maps);}
            CampaignStore.addHistory(c,id,"maps_opened","Google Maps navigation opened to verified destination");
        }

        JSONObject trip=toTrip(campaign);
        TripStore.save(c,trip);
        TripScheduler.launch(c,trip);
        CampaignStore.addHistory(c,id,"running","GPS route simulation started");
    }

    public static JSONObject toTrip(JSONObject campaign) throws Exception {
        JSONArray points=new JSONArray();
        points.put(new JSONObject(campaign.getJSONObject("origin").toString()).put("stopSeconds",0));
        JSONArray stops=campaign.optJSONArray("stops");if(stops!=null)for(int i=0;i<stops.length();i++)points.put(new JSONObject(stops.getJSONObject(i).toString()));
        JSONObject destination=new JSONObject(campaign.getJSONObject("destination").toString());destination.put("stopSeconds",campaign.optInt("arrivalHoldSeconds",0));points.put(destination);
        return new JSONObject().put("id","campaign-"+campaign.getString("id"))
                .put("name",campaign.optString("name","Campaign"))
                .put("waypoints",points)
                .put("averageSpeedMph",campaign.optDouble("averageSpeedMph",35))
                .put("speedVariationPercent",campaign.optDouble("speedVariationPercent",8))
                .put("gpsUpdateIntervalMs",campaign.optInt("gpsUpdateIntervalMs",1000))
                .put("speedProfile",campaign.optString("speedProfile","road_aware"))
                .put("randomStops",campaign.optBoolean("randomStops",true))
                .put("holdAtDestination",campaign.optBoolean("holdAtDestination",true))
                .put("startAtEpochMs",campaign.optLong("startAtEpochMs",0))
                .put("recurrence",campaign.optString("recurrence","none"));
    }
}
