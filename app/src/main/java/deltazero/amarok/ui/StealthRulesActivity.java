package deltazero.amarok.ui;

import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.Locale;

import deltazero.amarok.AmarokActivity;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.StealthRule;
import deltazero.amarok.utils.StealthScheduler;

public class StealthRulesActivity extends AmarokActivity {

    private RecyclerView rvRules;
    private FloatingActionButton fabAdd;
    private RulesAdapter adapter;
    private List<StealthRule> rulesList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stealth_rules);

        MaterialToolbar toolbar = findViewById(R.id.stealth_tb_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvRules = findViewById(R.id.stealth_rv_rules);
        fabAdd = findViewById(R.id.stealth_fab_add);

        rulesList = PrefMgr.getStealthRules();

        rvRules.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RulesAdapter(this, rulesList);
        rvRules.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> showEditDialog(null));
    }

    private void showEditDialog(@Nullable StealthRule ruleToEdit) {
        boolean isEdit = ruleToEdit != null;
        StealthRule rule = isEdit ? ruleToEdit : new StealthRule();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_stealth_rule_edit, null);
        
        TextView tvTitle = dialogView.findViewById(R.id.dialog_rule_title);
        EditText etName = dialogView.findViewById(R.id.dialog_rule_et_name);
        View layoutStart = dialogView.findViewById(R.id.dialog_rule_layout_start);
        TextView tvStartTime = dialogView.findViewById(R.id.dialog_rule_tv_start_time);
        View layoutEnd = dialogView.findViewById(R.id.dialog_rule_layout_end);
        TextView tvEndTime = dialogView.findViewById(R.id.dialog_rule_tv_end_time);
        
        CheckBox cbSun = dialogView.findViewById(R.id.dialog_rule_cb_sun);
        CheckBox cbMon = dialogView.findViewById(R.id.dialog_rule_cb_mon);
        CheckBox cbTue = dialogView.findViewById(R.id.dialog_rule_cb_tue);
        CheckBox cbWed = dialogView.findViewById(R.id.dialog_rule_cb_wed);
        CheckBox cbThu = dialogView.findViewById(R.id.dialog_rule_cb_thu);
        CheckBox cbFri = dialogView.findViewById(R.id.dialog_rule_cb_fri);
        CheckBox cbSat = dialogView.findViewById(R.id.dialog_rule_cb_sat);
        CheckBox[] cbDays = new CheckBox[]{cbSun, cbMon, cbTue, cbWed, cbThu, cbFri, cbSat};

        MaterialButton btnDelete = dialogView.findViewById(R.id.dialog_rule_bt_delete);
        MaterialButton btnCancel = dialogView.findViewById(R.id.dialog_rule_bt_cancel);
        MaterialButton btnSave = dialogView.findViewById(R.id.dialog_rule_bt_save);

        // Prepopulate data
        if (isEdit) {
            tvTitle.setText(R.string.stealth_scheduling);
            etName.setText(rule.name);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            tvTitle.setText(R.string.add_rule);
        }

        final int[] startH = {rule.startHour};
        final int[] startM = {rule.startMinute};
        final int[] endH = {rule.endHour};
        final int[] endM = {rule.endMinute};

        tvStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d", startH[0], startM[0]));
        tvEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d", endH[0], endM[0]));

        for (int i = 0; i < 7; i++) {
            cbDays[i].setChecked(rule.weekdays[i]);
        }

        // Setup dialog builder
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        // Setup time pickers
        layoutStart.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                    (view, hourOfDay, minute) -> {
                        startH[0] = hourOfDay;
                        startM[0] = minute;
                        tvStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                    }, startH[0], startM[0], true);
            timePickerDialog.show();
        });

        layoutEnd.setOnClickListener(v -> {
            TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                    (view, hourOfDay, minute) -> {
                        endH[0] = hourOfDay;
                        endM[0] = minute;
                        tvEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                    }, endH[0], endM[0], true);
            timePickerDialog.show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnDelete.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_rule)
                    .setMessage(R.string.remove_hide_path) // Generic confirm delete string
                    .setPositiveButton(R.string.confirm, (d, w) -> {
                        rulesList.remove(ruleToEdit);
                        PrefMgr.setStealthRules(rulesList);
                        StealthScheduler.updateAlarms(this);
                        adapter.notifyDataSetChanged();
                        dialog.dismiss();
                        Toast.makeText(this, "Rule deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                etName.setError("Name is required");
                return;
            }

            rule.name = name;
            rule.startHour = startH[0];
            rule.startMinute = startM[0];
            rule.endHour = endH[0];
            rule.endMinute = endM[0];
            for (int i = 0; i < 7; i++) {
                rule.weekdays[i] = cbDays[i].isChecked();
            }

            if (!isEdit) {
                rulesList.add(rule);
            }

            PrefMgr.setStealthRules(rulesList);
            StealthScheduler.updateAlarms(this);
            adapter.notifyDataSetChanged();
            dialog.dismiss();
            Toast.makeText(this, isEdit ? "Rule updated" : "Rule created", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private static class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.ViewHolder> {

        private final StealthRulesActivity activity;
        private final List<StealthRule> rules;

        public RulesAdapter(StealthRulesActivity activity, List<StealthRule> rules) {
            this.activity = activity;
            this.rules = rules;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stealth_rule, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StealthRule rule = rules.get(position);
            holder.tvName.setText(rule.name);
            holder.tvTime.setText(String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d",
                    rule.startHour, rule.startMinute, rule.endHour, rule.endMinute));
            holder.tvDays.setText(formatWeekdays(rule.weekdays));
            
            // Adjust card opacity based on status (like mockup: opacity-60 when disabled)
            holder.itemView.setAlpha(rule.enabled ? 1.0f : 0.6f);

            // Change icon background tint slightly when disabled
            holder.iconBg.setBackgroundTintList(activity.getColorStateList(
                    rule.enabled ? android.R.color.transparent : android.R.color.transparent
            ));
            // Set soft-square theme color when enabled
            if (rule.enabled) {
                holder.iconBg.setBackgroundResource(R.drawable.bg_soft_square);
                holder.iconBg.setBackgroundTintList(activity.getColorStateList(R.color.calendar_bg_secondary));
                holder.icon.setImageTintList(activity.getColorStateList(R.color.midnight_primary));
            } else {
                holder.iconBg.setBackgroundResource(R.drawable.bg_soft_square);
                holder.iconBg.setBackgroundTintList(activity.getColorStateList(R.color.midnight_surface_container_high));
                holder.icon.setImageTintList(activity.getColorStateList(R.color.midnight_outline));
            }

            holder.swEnabled.setChecked(rule.enabled);

            // Toggling enable switch
            holder.swEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                rule.enabled = isChecked;
                PrefMgr.setStealthRules(rules);
                StealthScheduler.updateAlarms(activity);
                holder.itemView.setAlpha(isChecked ? 1.0f : 0.6f);
                if (isChecked) {
                    holder.iconBg.setBackgroundTintList(activity.getColorStateList(R.color.calendar_bg_secondary));
                    holder.icon.setImageTintList(activity.getColorStateList(R.color.midnight_primary));
                } else {
                    holder.iconBg.setBackgroundTintList(activity.getColorStateList(R.color.midnight_surface_container_high));
                    holder.icon.setImageTintList(activity.getColorStateList(R.color.midnight_outline));
                }
            });

            holder.itemView.setOnClickListener(v -> activity.showEditDialog(rule));
        }

        @Override
        public int getItemCount() {
            return rules.size();
        }

        private String formatWeekdays(boolean[] weekdays) {
            int activeCount = 0;
            for (boolean day : weekdays) {
                if (day) activeCount++;
            }
            if (activeCount == 7) return "Daily";
            if (activeCount == 5 && !weekdays[0] && !weekdays[6]) return "Weekdays";
            if (activeCount == 2 && weekdays[0] && weekdays[6]) return "Weekends";
            if (activeCount == 0) return "Never";

            StringBuilder sb = new StringBuilder();
            String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            for (int i = 0; i < 7; i++) {
                if (weekdays[i]) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(dayNames[i]);
                }
            }
            return sb.toString();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDays, tvTime;
            MaterialSwitch swEnabled;
            FrameLayout iconBg;
            ImageView icon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.item_rule_tv_name);
                tvDays = itemView.findViewById(R.id.item_rule_tv_days);
                tvTime = itemView.findViewById(R.id.item_rule_tv_time);
                swEnabled = itemView.findViewById(R.id.item_rule_switch);
                iconBg = itemView.findViewById(R.id.item_rule_icon_bg);
                icon = itemView.findViewById(R.id.item_rule_icon);
            }
        }
    }
}
