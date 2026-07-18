package com.seannmichael.mockdrive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MockLocationService extends Service {
    public static final String ACTION_START="com.seannmichael.mockdrive.START";
    public static final String ACTION_STOP="com.seannmichael.mockdrive.STOP";
    public static final String ACTION_PAUSE="com.seannmichael.mockdrive.PAUSE";
    public static final String ACTION_RESUME="com.seannmichael.mockdrive.RESUME";
    public static final String ACTION_TELEPORT="com.seannmichael.mockdrive.TELEPORT";
    public static final String ACTION_SET_SPEED="com.seannmichael.mockdrive.SET_SPEED";
    public static final String EXTRA_TRIP="trip_json";
    public static final String EXTRA_LAT="lat";
    public static final String EXTRA_LON="lon";
    public static final String EXTRA_SPEED_MPH="speed_mph";

    private static final String CHANNEL="mock_drive";
    private static final int NOTICE=42;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile double liveSpeedMph=35;
    private Thread worker;
    private LocationManager manager;
    private Point heldPoint;

    @Override public void onCreate(){super.onCreate();manager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);createChannel();}

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_STICKY;
        String action=intent.getAction();
        if(ACTION_STOP.equals(action)){stopEverything();return START_NOT_STICKY;}
        if(ACTION_PAUSE.equals(action)){paused=true;updateNotice("Paused");return START_STICKY;}
        if(ACTION_RESUME.equals(action)){paused=false;updateNotice("Resumed at "+Math.round(liveSpeedMph)+" mph");return START_STICKY;}
        if(ACTION_SET_SPEED.equals(action)){
            liveSpeedMph=clamp(intent.getDoubleExtra(EXTRA_SPEED_MPH,liveSpeedMph),1,150);
            updateNotice("Speed changed to "+Math.round(liveSpeedMph)+" mph");
            return START_STICKY;
        }
        if(ACTION_TELEPORT.equals(action)){
            startForeground(NOTICE,notice("Holding mock location"));
            teleport(intent.getDoubleExtra(EXTRA_LAT,0),intent.getDoubleExtra(EXTRA_LON,0));
            return START_STICKY;
        }
        if(ACTION_START.equals(action)){
            startForeground(NOTICE,notice("Preparing route"));
            startTrip(intent.getStringExtra(EXTRA_TRIP));
        }
        return START_STICKY;
    }

    private void teleport(double lat,double lon){
        stopWorker();
        try{
            enableProvider();heldPoint=new Point(lat,lon);running=true;inject(heldPoint,0f,0f,3f);
            worker=new Thread(()->{while(running&&heldPoint!=null){try{inject(heldPoint,0f,0f,3f);Thread.sleep(1000);}catch(InterruptedException e){return;}}},"mock-hold");worker.start();
        }catch(SecurityException e){updateNotice("Select Mock Drive as mock location app");}
    }

    private void startTrip(String tripJson){
        stopWorker();
        worker=new Thread(()->{
            String id="";
            try{
                JSONObject trip=new JSONObject(tripJson==null?"{}":tripJson);id=trip.optString("id","");
                if(!id.isEmpty())TripStore.updateStatus(this,id,"routing");
                JSONArray waypoints=trip.getJSONArray("waypoints");
                List<Point> points=parse(RouteEngine.roadRoute(waypoints));
                if(points.size()<2)throw new Exception("Route is empty");
                enableProvider();running=true;paused=false;heldPoint=null;
                liveSpeedMph=clamp(trip.optDouble("averageSpeedMph",35),1,150);
                if(!id.isEmpty())TripStore.updateStatus(this,id,"active");
                double variation=clamp(trip.optDouble("speedVariationPercent",0),0,80)/100.0;
                int updateMs=(int)clamp(trip.optInt("gpsUpdateIntervalMs",1000),200,10000);
                boolean randomStops=trip.optBoolean("randomStops",false);
                int randomStopChance=trip.optInt("randomStopChancePercent",2);
                int randomStopMax=trip.optInt("randomStopMaxSeconds",20);
                boolean hold=trip.optBoolean("holdAtDestination",true);
                float accuracy=(float)clamp(trip.optDouble("accuracyMeters",3),1,100);
                Random random=new Random();int segment=0;double onSegment=0;Point current=points.get(0);inject(current,0f,0f,accuracy);
                while(running&&segment<points.size()-1){
                    while(paused&&running){inject(current,0f,0f,accuracy);Thread.sleep(500);}if(!running)break;
                    if(randomStops&&random.nextInt(100)<randomStopChance){int seconds=1+random.nextInt(Math.max(1,randomStopMax));for(int s=0;s<seconds&&running;s++){updateNotice("Traffic stop: "+(seconds-s)+" sec");inject(current,0f,0f,accuracy);Thread.sleep(1000);}}
                    double mph=liveSpeedMph*(1+((random.nextDouble()*2-1)*variation));
                    double metersPerSecond=mph*0.44704,step=metersPerSecond*updateMs/1000.0;
                    Point from=points.get(segment),to=points.get(segment+1);double length=distance(from,to);
                    if(length<.2){segment++;onSegment=0;continue;}onSegment+=step;
                    while(onSegment>=length&&segment<points.size()-1){onSegment-=length;segment++;if(segment>=points.size()-1)break;from=points.get(segment);to=points.get(segment+1);length=distance(from,to);}
                    if(segment>=points.size()-1)break;
                    from=points.get(segment);to=points.get(segment+1);length=distance(from,to);
                    current=interpolate(from,to,Math.min(1,onSegment/Math.max(.1,length)));
                    inject(current,bearing(from,to),(float)metersPerSecond,accuracy);updateNotice("Driving "+Math.round(mph)+" mph");Thread.sleep(updateMs);
                }
                if(running){current=points.get(points.size()-1);inject(current,0f,0f,accuracy);if(!id.isEmpty())TripStore.updateStatus(this,id,"completed");updateNotice("Destination reached");if(hold){heldPoint=current;while(running){inject(heldPoint,0f,0f,accuracy);Thread.sleep(1000);}}}
            }catch(SecurityException e){updateNotice("Select Mock Drive as mock location app");if(!id.isEmpty())TripStore.updateStatus(this,id,"failed");}
            catch(Exception e){updateNotice("Trip failed: "+e.getMessage());if(!id.isEmpty())TripStore.updateStatus(this,id,"failed");}
        },"mock-trip");worker.start();
    }

    private List<Point> parse(JSONArray a)throws Exception{List<Point> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONArray c=a.getJSONArray(i);out.add(new Point(c.getDouble(1),c.getDouble(0)));}return out;}
    private void enableProvider(){try{manager.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true);}
    private void inject(Point p,float bearing,float speed,float accuracy){Location l=new Location(LocationManager.GPS_PROVIDER);l.setLatitude(p.lat);l.setLongitude(p.lon);l.setAccuracy(accuracy);l.setAltitude(0);l.setBearing(bearing);l.setSpeed(speed);l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());if(Build.VERSION.SDK_INT>=26){l.setBearingAccuracyDegrees(3f);l.setSpeedAccuracyMetersPerSecond(.5f);l.setVerticalAccuracyMeters(accuracy);}manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,l);}
    private void stopEverything(){stopWorker();try{manager.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}stopForeground(true);stopSelf();}
    private void stopWorker(){running=false;paused=false;heldPoint=null;if(worker!=null)worker.interrupt();worker=null;}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Mock Drive",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private Notification notice(String text){PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,SimpleDriveActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);return b.setContentTitle("Mock Drive").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true).setContentIntent(pi).build();}
    private void updateNotice(String text){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notice(text));}
    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
    private static double distance(Point a,Point b){double earth=6371000,p1=Math.toRadians(a.lat),p2=Math.toRadians(b.lat),dp=Math.toRadians(b.lat-a.lat),dl=Math.toRadians(b.lon-a.lon);double h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);return earth*2*Math.atan2(Math.sqrt(h),Math.sqrt(1-h));}
    private static Point interpolate(Point a,Point b,double f){return new Point(a.lat+(b.lat-a.lat)*f,a.lon+(b.lon-a.lon)*f);}
    private static float bearing(Point a,Point b){double p1=Math.toRadians(a.lat),p2=Math.toRadians(b.lat),dl=Math.toRadians(b.lon-a.lon);double y=Math.sin(dl)*Math.cos(p2),x=Math.cos(p1)*Math.sin(p2)-Math.sin(p1)*Math.cos(p2)*Math.cos(dl);return(float)((Math.toDegrees(Math.atan2(y,x))+360)%360);}
    @Override public IBinder onBind(Intent intent){return null;}
    private static final class Point{final double lat,lon;Point(double lat,double lon){this.lat=lat;this.lon=lon;}}
}
