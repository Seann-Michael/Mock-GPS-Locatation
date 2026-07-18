package com.seannmichael.mockdrive;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class BillingActivity extends BaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = UiKit.page(this);
        UiKit.topBar(this, root, "Billing", true);

        LinearLayout plan = UiKit.card(this, root);
        plan.addView(UiKit.text(this, "Current plan", 19, true));
        UiKit.statusRow(this, plan, "Plan", "Development", UiKit.MUTED, UiKit.BLUE_LIGHT);
        UiKit.statusRow(this, plan, "Payments", "Not connected", UiKit.WARN, UiKit.WARN_BG);
        plan.addView(UiKit.text(this, "Billing is intentionally inactive until a payment provider and backend are connected.", 13, false));

        LinearLayout features = UiKit.card(this, root);
        features.addView(UiKit.text(this, "Future billing features", 19, true));
        features.addView(UiKit.text(this, "• Current plan and renewal date\n• Payment method\n• Invoices and receipts\n• Usage limits\n• Upgrade or downgrade\n• Cancel subscription\n• Promo codes", 14, false));

        LinearLayout actions = UiKit.card(this, root);
        actions.addView(UiKit.text(this, "Manage", 19, true));
        Button manage = UiKit.secondaryButton(this, "Manage subscription"); manage.setEnabled(false); manage.setAlpha(0.5f); actions.addView(manage);
        Button invoices = UiKit.secondaryButton(this, "View invoices"); invoices.setEnabled(false); invoices.setAlpha(0.5f); actions.addView(invoices);
        Button support = UiKit.secondaryButton(this, "Contact billing support"); actions.addView(support);
        support.setOnClickListener(v -> Toast.makeText(this, "Billing support is not configured yet", Toast.LENGTH_LONG).show());

        UiKit.setStickyScreen(this, root, "Settings");
    }
}
