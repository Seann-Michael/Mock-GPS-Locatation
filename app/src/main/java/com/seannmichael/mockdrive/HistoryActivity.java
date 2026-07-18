package com.seannmichael.mockdrive;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;

public class HistoryActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);render();}
    @Override protected void onResume(){super.onResume();render();}

    private void render(){
        LinearLayout root=UiKit.page(this);UiKit.topBar(this,root,"History",true);
        LinearLayout hero=UiKit.hero(this,root);hero.addView(UiKit.whiteText(this,"Simulation history",27,true));hero.addView(UiKit.whiteText(this,"Your last 100 simulated navigations, newest first.",15,false));
        JSONArray records=HistoryStore.all(this);
        if(records.length()==0){LinearLayout empty=UiKit.card(this,root);empty.addView(UiKit.text(this,"No simulations yet",20,true));empty.addView(UiKit.text(this,"Completed and failed navigations will appear here.",14,false));}
        for(int i=0;i<records.length();i++){
            JSONObject r=records.optJSONObject(i);if(r==null)continue;
            LinearLayout card=UiKit.card(this,root);
            TextView title=UiKit.text(this,r.optString("startAddress","Start")+" → "+r.optString("endAddress","Destination"),17,true);card.addView(title);
            String status=r.optString("status","unknown").toUpperCase();
            card.addView(UiKit.text(this,status+"  •  "+formatTime(r.optLong("startTime"))+"  •  "+String.format("%.1f mi",r.optDouble("miles")),13,false));
            Button open=UiKit.secondaryButton(this,"View simulation details");card.addView(open);
            String id=r.optString("historyId");open.setOnClickListener(v->startActivity(new Intent(this,HistoryDetailActivity.class).putExtra("history_id",id)));
        }
        UiKit.setStickyScreen(this,root,"History");
    }
    private String formatTime(long t){return t<=0?"Unknown":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(t));}
}
