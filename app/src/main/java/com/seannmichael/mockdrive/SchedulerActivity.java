package com.seannmichael.mockdrive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;

public class SchedulerActivity extends Activity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=UiKit.page(this);
        UiKit.topBar(this,root,"Scheduler",true);

        LinearLayout intro=UiKit.card(this,root);
        intro.addView(UiKit.text(this,"Upcoming campaigns",22,true));
        intro.addView(UiKit.text(this,"One-time and recurring schedules are stored and executed directly on this phone.",14,false));

        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);root.addView(content);
        refresh();

        UiKit.bottomNav(this,root,"Schedule");
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }

    @Override protected void onResume(){super.onResume();refresh();}

    private void refresh(){
        if(content==null)return;
        content.removeAllViews();
        JSONArray all=CampaignStore.all(this);
        int shown=0;
        for(int i=0;i<all.length();i++){
            JSONObject c=all.optJSONObject(i);if(c==null)continue;
            long when=c.optLong("startAtEpochMs",0);
            String recurrence=c.optString("recurrence","none");
            if(when<=0&&"none".equals(recurrence))continue;
            LinearLayout card=UiKit.card(this,content);
            card.addView(UiKit.text(this,c.optString("name","Campaign"),18,true));
            String first=when>0?DateFormat.getDateTimeInstance().format(when):"Not scheduled yet";
            card.addView(UiKit.text(this,"First run: "+first,14,false));
            card.addView(UiKit.text(this,"Repeats: "+recurrence,14,false));
            card.addView(UiKit.text(this,"Status: "+(c.optBoolean("enabled",true)?"Enabled":"Disabled"),14,false));
            shown++;
        }
        if(shown==0){LinearLayout empty=UiKit.card(this,content);empty.addView(UiKit.text(this,"No scheduled campaigns",18,true));empty.addView(UiKit.text(this,"Create or edit a campaign and assign a one-time, daily, weekly, or monthly schedule.",14,false));}
    }
}