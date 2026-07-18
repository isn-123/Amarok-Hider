package deltazero.amarok.ui.settings;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import deltazero.amarok.R;
import deltazero.amarok.ui.StealthRulesActivity;

public class SchedulingCategory extends BaseCategory {
    public SchedulingCategory(@NonNull FragmentActivity activity, @NonNull PreferenceScreen screen) {
        super(activity, screen);
        setTitle(R.string.stealth_scheduling);

        var openSchedulingPref = new Preference(activity);
        openSchedulingPref.setTitle(R.string.stealth_scheduling);
        openSchedulingPref.setIcon(R.drawable.ic_null);
        openSchedulingPref.setSummary(R.string.stealth_scheduling_description);
        openSchedulingPref.setOnPreferenceClickListener(preference -> {
            activity.startActivity(new Intent(activity, StealthRulesActivity.class));
            return true;
        });
        addPreference(openSchedulingPref);
    }
}
