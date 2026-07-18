package com.seannmichael.mockdrive;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class BillingActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int p=dp(16);root.setPadding(p,p,p,p);
        text(root,"Billing",28);
        text(root,"Plan: Development / not connected",17);
        text(root,"Billing is intentionally inactive until a payment provider and backend are connected.",14);
        section(root,"Future billing features");
        text(root,"• Current plan and renewal date\n• Payment method\n• Invoices and receipts\n• Usage limits\n• Upgrade or downgrade\n• Cancel subscription\n• Promo codes",14);
        Button manage=button(root,"Manage Subscription");manage.setEnabled(false);
        Button invoices=button(root,"View Invoices");invoices.setEnabled(false);
        Button support=button(root,"Contact Billing Support");support.setOnClickListener(v->Toast.makeText(this,"Billing support is not configured yet",Toast.LENGTH_LONG).show());
        setContentView(root);
    }
    private void section(LinearLayout p,String s){text(p,s,21);}
    private TextView text(LinearLayout p,String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(0,dp(8),0,dp(5));p.addView(v);return v;}
    private Button button(LinearLayout p,String s){Button b=new Button(this);b.setText(s);p.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}