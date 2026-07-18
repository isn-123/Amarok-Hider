package deltazero.amarok.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import deltazero.amarok.Hider;
import deltazero.amarok.utils.StealthScheduler;

public class StealthSchedulerReceiver extends BroadcastReceiver {
    private static final String TAG = "StealthSchedulerRcvr";

    public static final String ACTION_TRIGGER_HIDE = "deltazero.amarok.action.STEALTH_TRIGGER_HIDE";
    public static final String ACTION_TRIGGER_UNHIDE = "deltazero.amarok.action.STEALTH_TRIGGER_UNHIDE";
    public static final String EXTRA_RULE_ID = "extra_rule_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        
        String action = intent.getAction();
        String ruleId = intent.getStringExtra(EXTRA_RULE_ID);
        Log.i(TAG, "Stealth schedule alarm triggered! Action: " + action + ", Rule ID: " + ruleId);

        if (ACTION_TRIGGER_HIDE.equals(action)) {
            Log.i(TAG, "Triggering automatic HIDE.");
            Hider.hide(context.getApplicationContext());
        } else if (ACTION_TRIGGER_UNHIDE.equals(action)) {
            Log.i(TAG, "Triggering automatic UNHIDE.");
            Hider.unhide(context.getApplicationContext());
        }

        // Reschedule alarms for the next day/cycle
        StealthScheduler.updateAlarms(context.getApplicationContext());
    }
}
