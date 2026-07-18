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
import android.os.PowerManager;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MockLocationService extends Service {
    public static final String ACTION_START="com.seannmichael.mockdrive.START";
    public static final String ACTION_STOP="com.seannmichael.mockdrive.STOP";
    public static final String ACTION_TELEPORT="com.seannmichael.mockdrive.TELEPORT";
    public static final String EXTRA_TRIP="trip_json";
    public static final String EXTRA_LAT="lat";
    public static final String EXTRA_LON="lon";

    private static final String CHANNEL="mock_drive";
    private static final int NOTICE=42;
    private static final String PREFS="mock_drive_service";
    private static final String KEY_ACTIVE_TRIP="active_trip";

    private volatile boolean running;
    private volatile long injectionCount;
    private volatile long lastInjectionElapsed;
    private volatile int currentSegment;
    private volatile int totalSegments;
    private volatile double currentLat;
    private volatile double currentLon;
    private volatile double currentSpeedMph;
    private volatile String phase="idle";
    private volatile String lastError="";
    private Thread worker;
    private LocationManager manager;
    private Point heldPoint;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate(){
        super.onCreate();
        manager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
        wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MockDrive:Navigation");
        wakeLock.setReferenceCounted(false);
        createChannel();
        DiagnosticLogger.log(this,"SERVICE","onCreate");
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        DiagnosticLogger.log(this,"SERVICE","onStartCommand startId="+startId+" flags="+flags+" intent="+(intent==null?"null":intent.getAction()));
        if(intent==null){
            String saved=getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY_ACTIVE_TRIP,"");
            if(!saved.isEmpty()){
                startForeground(NOTICE,notice("Restoring navigation"));
                DiagnosticLogger.log(this,"SERVICE","Restoring saved active trip");
                startTrip(saved,false);
                return START_REDELIVER_INTENT;
            }
            return START_NOT_STICKY;
        }
        String action=intent.getAction();
        if(ACTION_STOP.equals(action)){stopEverything();return START_NOT_STICKY;}
        if(ACTION_TELEPORT.equals(action)){
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove(KEY_ACTIVE_TRIP).apply();
            startForeground(NOTICE,notice("Holding mock location"));
            teleport(intent.getDoubleExtra(EXTRA_LAT,0),intent.getDoubleExtra(EXTRA_LON,0));
            return START_STICKY;
        }
        if(ACTION_START.equals(action)){
            String tripJson=intent.getStringExtra(EXTRA_TRIP);
            if(tripJson!=null&&!tripJson.isEmpty())getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(KEY_ACTIVE_TRIP,tripJson).apply();
            startForeground(NOTICE,notice("Starting navigation"));
            startTrip(tripJson,true);
            return START_REDELIVER_INTENT;
        }
        return START_STICKY;
    }

    private void acquireWakeLock(){
        if(wakeLock!=null&&!wakeLock.isHeld()){
            wakeLock.acquire();
            DiagnosticLogger.log(this,"POWER","Wake lock acquired");
        }
    }

    private void releaseWakeLock(){
        if(wakeLock!=null&&wakeLock.isHeld()){
            wakeLock.release();
            DiagnosticLogger.log(this,"POWER","Wake lock released");
        }
    }

    private void teleport(double lat,double lon){
        stopWorker();acquireWakeLock();heldPoint=new Point(lat,lon);running=true;phase="holding";currentLat=lat;currentLon=lon;
        DiagnosticLogger.log(this,"TELEPORT","Holding "+lat+","+lon);
        worker=new Thread(()->{
            try{
                enableProvider();
                while(running&&heldPoint!=null){safeInject(heldPoint,0f,0f,3f);writeState();Thread.sleep(1000);}
            }catch(InterruptedException e){Thread.currentThread().interrupt();DiagnosticLogger.log(this,"THREAD","Hold worker interrupted");}
            catch(Throwable e){recordFailure("teleport worker",e);}
            finally{writeState();if(!running)releaseWakeLock();}
        },"mock-hold");worker.setUncaughtExceptionHandler((t,e)->recordFailure("uncaught "+t.getName(),e));worker.start();
    }

    private void startTrip(String tripJson,boolean newSession){
        stopWorker();acquireWakeLock();running=true;heldPoint=null;phase="starting";lastError="";injectionCount=0;lastInjectionElapsed=0;currentSegment=0;totalSegments=0;
        if(newSession)DiagnosticLogger.beginTrip(this,tripJson);else DiagnosticLogger.log(this,"SESSION","Continuing restored trip");
        worker=new Thread(()->runTrip(tripJson),"mock-trip");
        worker.setUncaughtExceptionHandler((t,e)->recordFailure("uncaught "+t.getName(),e));
        worker.start();
    }

    private void runTrip(String tripJson){
        String id="";
        try{
            JSONObject trip=new JSONObject(tripJson==null?"{}":tripJson);id=trip.optString("id","");
            JSONArray waypoints=trip.getJSONArray("waypoints");
            if(waypoints.length()<2)throw new Exception("Trip requires a start and destination");
            JSONObject first=waypoints.getJSONObject(0);Point startPoint=new Point(first.getDouble("latitude"),first.getDouble("longitude"));
            phase="provider_setup";enableProvider();safeInject(startPoint,0f,0f,3f);updateNotice("Preparing road route");
            DiagnosticLogger.log(this,"ROUTE","Requesting road route for "+waypoints.length()+" waypoints");
            if(!id.isEmpty())TripStore.updateStatus(this,id,"routing");
            JSONArray routeJson=RouteEngine.roadRoute(waypoints);DiagnosticLogger.saveRoute(this,routeJson.toString());
            List<Point> points=parse(routeJson);if(points.size()<2)throw new Exception("Route is empty");
            totalSegments=points.size()-1;DiagnosticLogger.log(this,"ROUTE","Route loaded points="+points.size()+" segments="+totalSegments);
            if(!running)return;
            double mph=clamp(trip.optDouble("averageSpeedMph",35),1,150);currentSpeedMph=mph;
            double metersPerSecond=mph*0.44704;int updateMs=(int)clamp(trip.optInt("gpsUpdateIntervalMs",1000),500,5000);
            boolean hold=trip.optBoolean("holdAtDestination",true);float accuracy=(float)clamp(trip.optDouble("accuracyMeters",3),1,100);
            if(!id.isEmpty())TripStore.updateStatus(this,id,"active");
            phase="driving";int segment=0;double onSegment=0;Point current=points.get(0);safeInject(current,0f,(float)metersPerSecond,accuracy);writeState();
            long lastHeartbeat=0;
            while(running&&segment<points.size()-1){
                double step=metersPerSecond*updateMs/1000.0;Point from=points.get(segment),to=points.get(segment+1);double length=distance(from,to);
                if(length<0.2){segment++;onSegment=0;continue;}
                onSegment+=step;
                while(onSegment>=length&&segment<points.size()-1){onSegment-=length;segment++;if(segment>=points.size()-1)break;from=points.get(segment);to=points.get(segment+1);length=distance(from,to);}
                if(segment>=points.size()-1)break;
                from=points.get(segment);to=points.get(segment+1);length=distance(from,to);current=interpolate(from,to,Math.min(1,onSegment/Math.max(0.1,length)));
                currentSegment=segment;safeInject(current,bearing(from,to),(float)metersPerSecond,accuracy);updateNotice("Driving "+Math.round(mph)+" mph");writeState();
                long now=SystemClock.elapsedRealtime();
                if(now-lastHeartbeat>=5000){DiagnosticLogger.log(this,"HEARTBEAT","segment="+segment+"/"+totalSegments+" injections="+injectionCount+" lat="+current.lat+" lon="+current.lon);lastHeartbeat=now;}
                Thread.sleep(updateMs);
            }
            if(running){
                current=points.get(points.size()-1);phase="destination";safeInject(current,0f,0f,accuracy);
                if(!id.isEmpty())TripStore.updateStatus(this,id,"completed");getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove(KEY_ACTIVE_TRIP).apply();
                updateNotice("Destination reached");DiagnosticLogger.log(this,"COMPLETE","Destination reached after injections="+injectionCount);writeState();
                if(hold){heldPoint=current;phase="holding_destination";while(running){safeInject(heldPoint,0f,0f,accuracy);writeState();Thread.sleep(1000);}}
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();DiagnosticLogger.log(this,"THREAD","Trip worker interrupted while phase="+phase);}
        catch(Throwable e){recordFailure("runTrip phase="+phase,e);if(!id.isEmpty())TripStore.updateStatus(this,id,"failed");}
        finally{DiagnosticLogger.log(this,"THREAD","Trip worker exiting running="+running+" phase="+phase);writeState();if(!running)releaseWakeLock();}
    }

    private void safeInject(Point p,float bearing,float speed,float accuracy){
        RuntimeException last=null;
        for(int attempt=1;attempt<=3;attempt++){
            try{
                inject(p,bearing,speed,accuracy);injectionCount++;lastInjectionElapsed=SystemClock.elapsedRealtime();currentLat=p.lat;currentLon=p.lon;
                DiagnosticLogger.log(this,"INJECT","ok #"+injectionCount+" attempt="+attempt+" lat="+p.lat+" lon="+p.lon+" speedMps="+speed+" bearing="+bearing);
                return;
            }catch(SecurityException e){recordFailure("inject security attempt="+attempt,e);throw e;}
            catch(RuntimeException e){last=e;DiagnosticLogger.exception(this,"inject attempt="+attempt,e);try{enableProvider();Thread.sleep(100);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new RuntimeException(interrupted);}}
        }
        if(last!=null)throw last;
    }

    private List<Point> parse(JSONArray a)throws Exception{List<Point> out=new ArrayList<>();for(int i=0;i<a.length();i++){JSONArray c=a.getJSONArray(i);out.add(new Point(c.getDouble(1),c.getDouble(0)));}return out;}

    private void enableProvider(){
        DiagnosticLogger.log(this,"PROVIDER","Rebuilding GPS test provider");
        try{manager.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(Exception e){DiagnosticLogger.log(this,"PROVIDER","remove ignored: "+e.getClass().getSimpleName());}
        try{manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,Criteria.POWER_LOW,Criteria.ACCURACY_FINE);}catch(IllegalArgumentException e){DiagnosticLogger.log(this,"PROVIDER","add already exists");}
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true);
        DiagnosticLogger.log(this,"PROVIDER","enabled="+manager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }

    private void inject(Point p,float bearing,float speed,float accuracy){
        Location l=new Location(LocationManager.GPS_PROVIDER);l.setLatitude(p.lat);l.setLongitude(p.lon);l.setAccuracy(accuracy);l.setAltitude(0);l.setBearing(bearing);l.setSpeed(speed);l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        if(Build.VERSION.SDK_INT>=26){l.setBearingAccuracyDegrees(3f);l.setSpeedAccuracyMetersPerSecond(0.5f);l.setVerticalAccuracyMeters(accuracy);}manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,l);
    }

    private void recordFailure(String where,Throwable error){
        lastError=error.getClass().getSimpleName()+": "+(error.getMessage()==null?"":error.getMessage());phase="failed";DiagnosticLogger.exception(this,where,error);updateNotice("Trip failed: "+lastError);writeState();
    }

    private void writeState(){DiagnosticLogger.state(this,running,worker!=null&&worker.isAlive(),injectionCount,lastInjectionElapsed,currentSegment,totalSegments,currentLat,currentLon,currentSpeedMph,phase,lastError);}

    private void stopEverything(){DiagnosticLogger.log(this,"SERVICE","Stop requested");getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove(KEY_ACTIVE_TRIP).apply();stopWorker();try{manager.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}releaseWakeLock();stopForeground(true);stopSelf();writeState();}
    private void stopWorker(){running=false;heldPoint=null;Thread old=worker;worker=null;if(old!=null)old.interrupt();}
    @Override public void onDestroy(){DiagnosticLogger.log(this,"SERVICE","onDestroy running="+running);stopWorker();releaseWakeLock();writeState();super.onDestroy();}
    @Override public void onTaskRemoved(Intent rootIntent){DiagnosticLogger.log(this,"SERVICE","onTaskRemoved");writeState();super.onTaskRemoved(rootIntent);}

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
