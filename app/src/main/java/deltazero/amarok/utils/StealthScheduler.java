package deltazero.amarok.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.List;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.StealthRule;
import deltazero.amarok.receivers.StealthSchedulerReceiver;

public class StealthScheduler {
    private static final String TAG = "StealthScheduler";

    public static void updateAlarms(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        List<StealthRule> rules = PrefMgr.getStealthRules();
        Log.i(TAG, "Updating alarms. Total rules: " + rules.size());

        for (StealthRule rule : rules) {
            int startReqCode = rule.id.hashCode();
            int endReqCode = rule.id.hashCode() + 1;

            // Intent for starting hide
            Intent startIntent = new Intent(context, StealthSchedulerReceiver.class);
            startIntent.setAction(StealthSchedulerReceiver.ACTION_TRIGGER_HIDE);
            startIntent.putExtra(StealthSchedulerReceiver.EXTRA_RULE_ID, rule.id);
            PendingIntent startPI = PendingIntent.getBroadcast(
                    context,
                    startReqCode,
                    startIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Intent for starting unhide
            Intent endIntent = new Intent(context, StealthSchedulerReceiver.class);
            endIntent.setAction(StealthSchedulerReceiver.ACTION_TRIGGER_UNHIDE);
            endIntent.putExtra(StealthSchedulerReceiver.EXTRA_RULE_ID, rule.id);
            PendingIntent endPI = PendingIntent.getBroadcast(
                    context,
                    endReqCode,
                    endIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Cancel old alarms first
            alarmManager.cancel(startPI);
            alarmManager.cancel(endPI);

            if (rule.enabled) {
                // Schedule start alarm
                long nextStartMillis = getNextTriggerTimeMillis(rule.startHour, rule.startMinute, rule.weekdays);
                scheduleAlarm(alarmManager, nextStartMillis, startPI);
                Log.i(TAG, "Scheduled START alarm for rule " + rule.name + " (" + rule.id + ") at " + formatMillis(nextStartMillis));

                // Schedule end alarm
                long nextEndMillis = getNextTriggerTimeMillis(rule.endHour, rule.endMinute, rule.weekdays);
                scheduleAlarm(alarmManager, nextEndMillis, endPI);
                Log.i(TAG, "Scheduled END alarm for rule " + rule.name + " (" + rule.id + ") at " + formatMillis(nextEndMillis));
            } else {
                Log.i(TAG, "Rule " + rule.name + " is disabled, alarms cancelled.");
            }
        }
    }

    private static void scheduleAlarm(AlarmManager alarmManager, long triggerAtMillis, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException e) {
            // Fallback for Android 12+ if SCHEDULE_EXACT_ALARM is not granted
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    public static long getNextTriggerTimeMillis(int hour, int minute, boolean[] weekdays) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        // Check if weekdays contains at least one active day
        boolean hasActiveDay = false;
        for (boolean day : weekdays) {
            if (day) {
                hasActiveDay = true;
                break;
            }
        }

        // If no active days, default to every day
        boolean[] activeWeekdays = weekdays;
        if (!hasActiveDay) {
            activeWeekdays = new boolean[]{true, true, true, true, true, true, true};
        }

        // Find the next active day
        for (int i = 0; i < 8; i++) {
            int dayOfWeek = target.get(Calendar.DAY_OF_WEEK); // 1 = Sunday, 2 = Monday, ..., 7 = Saturday
            int arrayIndex = dayOfWeek - 1; // Map to 0-6 index
            
            if (activeWeekdays[arrayIndex]) {
                if (target.after(now)) {
                    return target.getTimeInMillis();
                }
            }
            target.add(Calendar.DAY_OF_YEAR, 1);
        }

        return target.getTimeInMillis();
    }

    private static String formatMillis(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return String.format("%04d-%02d-%02d %02d:%02d:%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND)
        );
    }
}
