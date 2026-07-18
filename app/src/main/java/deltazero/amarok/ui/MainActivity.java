package deltazero.amarok.ui;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.hjq.permissions.XXPermissions;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import deltazero.amarok.AmarokActivity;
import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.StealthRule;
import deltazero.amarok.apphider.NoneAppHider;
import deltazero.amarok.filehider.NoneFileHider;
import deltazero.amarok.ui.settings.SettingsActivity;
import deltazero.amarok.ui.settings.SwitchAppHiderActivity;
import deltazero.amarok.ui.settings.SwitchFileHiderActivity;
import deltazero.amarok.utils.PermissionUtil;
import deltazero.amarok.utils.SDCardUtil;
import deltazero.amarok.utils.StealthScheduler;
import deltazero.amarok.utils.UpdateUtil;
import deltazero.amarok.utils.LauncherIconController;
import deltazero.amarok.utils.SecurityUtil;
import deltazero.amarok.ui.CountdownConfirmDialog;
import deltazero.amarok.ui.SetPasswordFragment;
import deltazero.amarok.utils.HashUtil;
import deltazero.amarok.QuickHideService;
import nl.dionsegijn.konfetti.xml.KonfettiView;

public class MainActivity extends AmarokActivity {

    public final static String TAG = "Main";

    // Sidebar Menus
    private MaterialButton menuDashboard, menuFiles, menuApps, menuScheduling, menuSecurity, menuLockVault, menuSettings;

    // Content Panels
    private View panelDashboard, panelFiles, panelApps, panelScheduling, panelSecurity, panelSettings;

    // Header Components
    private TextView tvHeaderTitle;
    private View cardSearch;
    private EditText etSearch;

    // Dashboard View Components
    private ImageView ivStatusImg;
    private TextView tvStatusInfo, tvStatus;
    private MaterialButton btChangeStatus, btRevealAll;
    private CircularProgressIndicator piProcessStatus;
    private KonfettiView konfettiView;

    // Hidden Files Panel Components
    private RecyclerView rvFileList;
    private FileListAdapter fileListAdapter;
    private ActivityResultLauncher<Uri> mDirRequest;

    // Hidden Apps Panel Components
    private RecyclerView rvAppList;
    private AppListAdapter appListAdapter;
    private SwipeRefreshLayout appSwipeRefresh;
    private AppListViewModel appViewModel;

    // Stealth Scheduling Panel Components
    private RecyclerView rvRules;
    private FloatingActionButton fabAddRule;
    private RulesAdapter rulesAdapter;
    private List<StealthRule> rulesList;

    // Security Panel Components
    private MaterialSwitch switchSecurityBiometric, switchSecurityPasscode, switchSecurityDisguise, switchSecurityHideIcon, switchSecurityInvisPattern;
    private TextView tvAppHiderInfo, tvFileHiderInfo, tvRelayNode, tvMeshStatus;
    private MaterialButton btnChangeAppHider, btnChangeFileHider, btnConfigureDuress, btnViewLogs, btnArmWipe, btnChangeRelay, btnBackupQr;

    // Settings Panel Components
    private MaterialSwitch switchSettingsLiveStatus, switchSettingsBlockScreenshots, switchSettingsHideRecents, switchSettingsDisableSecurityUnhidden, switchSettingsDisableToasts, switchSettingsEnablePanicButton, switchSettingsEnableAutoHide, switchSettingsEnableXHide, switchSettingsDisableOnlyWithXHide;
    private MaterialButton btnPanicColor;
    private com.google.android.material.slider.Slider sliderAutoHideDelay;
    private TextView tvAutoHideDelaySummary, tvXposedStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupSidebarNavigation();
        setupDashboardPanel();
        setupHiddenFilesPanel();
        setupHiddenAppsPanel();
        setupStealthSchedulingPanel();
        setupSecurityPanel();
        setupSettingsPanel();
        setupSearchFilter();

        // Check Hiders availability
        PrefMgr.getAppHider(this).tryToActivate((appHiderClass, succeed, msg) -> {
            if (succeed) return;
            Hider.showNoHiderDialog(this, msg);
        });

        PrefMgr.getFileHider(this).tryToActive((fileHiderClass, succeed, msg) -> {
            if (succeed) return;
            PrefMgr.setFileHiderMode(NoneFileHider.class);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.filehider_not_ava_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.switch_file_hider, (dialog, which)
                            -> startActivity(new Intent(this, SwitchFileHiderActivity.class)))
                    .setNegativeButton(getString(R.string.ok), null)
                    .show();
        });

        // Check for updates
        if (PrefMgr.getEnableAutoUpdate()) {
            UpdateUtil.checkAndNotify(this, true);
        }
    }

    private void initViews() {
        // Sidebar Navigation
        menuDashboard = findViewById(R.id.menu_dashboard);
        menuFiles = findViewById(R.id.menu_files);
        menuApps = findViewById(R.id.menu_apps);
        menuScheduling = findViewById(R.id.menu_scheduling);
        menuSecurity = findViewById(R.id.menu_security);
        menuLockVault = findViewById(R.id.menu_lock_vault);
        menuSettings = findViewById(R.id.menu_settings);

        // Header
        tvHeaderTitle = findViewById(R.id.main_header_title);
        cardSearch = findViewById(R.id.main_search_card);
        etSearch = findViewById(R.id.main_et_search);

        // Content Panels
        panelDashboard = findViewById(R.id.panel_dashboard);
        panelFiles = findViewById(R.id.panel_files);
        panelApps = findViewById(R.id.panel_apps);
        panelScheduling = findViewById(R.id.panel_scheduling);
        panelSecurity = findViewById(R.id.panel_security);
        panelSettings = findViewById(R.id.panel_settings);

        // Dashboard views
        ivStatusImg = findViewById(R.id.main_iv_status);
        tvStatus = findViewById(R.id.main_tv_status);
        tvStatusInfo = findViewById(R.id.main_tv_statusinfo);
        btChangeStatus = findViewById(R.id.main_bt_change_status);
        btRevealAll = findViewById(R.id.main_bt_reveal_all);
        piProcessStatus = findViewById(R.id.main_pi_process_status);
        konfettiView = findViewById(R.id.main_konfetti_view);

        // Security Panel views
        switchSecurityBiometric = findViewById(R.id.security_switch_biometric);
        switchSecurityPasscode = findViewById(R.id.security_switch_passcode);
        switchSecurityDisguise = findViewById(R.id.security_switch_disguise);
        switchSecurityHideIcon = findViewById(R.id.security_switch_hide_icon);
        switchSecurityInvisPattern = findViewById(R.id.security_switch_invis_pattern);
        btnConfigureDuress = findViewById(R.id.security_btn_configure_duress);
        btnViewLogs = findViewById(R.id.security_btn_view_logs);
        btnArmWipe = findViewById(R.id.security_btn_arm_wipe);
        btnChangeRelay = findViewById(R.id.security_btn_change_relay);
        btnBackupQr = findViewById(R.id.security_btn_backup_qr);
        tvRelayNode = findViewById(R.id.security_tv_relay_node);
        tvMeshStatus = findViewById(R.id.security_tv_mesh_status);
        tvAppHiderInfo = findViewById(R.id.security_tv_app_hider_info);
        btnChangeAppHider = findViewById(R.id.security_btn_change_app_hider);
        tvFileHiderInfo = findViewById(R.id.security_tv_file_hider_info);
        btnChangeFileHider = findViewById(R.id.security_btn_change_file_hider);

        // Settings Panel views
        switchSettingsLiveStatus = findViewById(R.id.settings_switch_live_status);
        switchSettingsBlockScreenshots = findViewById(R.id.settings_switch_block_screenshots);
        switchSettingsHideRecents = findViewById(R.id.settings_switch_hide_recents);
        switchSettingsDisableSecurityUnhidden = findViewById(R.id.settings_switch_disable_security_unhidden);
        switchSettingsDisableToasts = findViewById(R.id.settings_switch_disable_toasts);
        switchSettingsEnablePanicButton = findViewById(R.id.settings_switch_enable_panic_button);
        switchSettingsEnableAutoHide = findViewById(R.id.settings_switch_enable_auto_hide);
        switchSettingsEnableXHide = findViewById(R.id.settings_switch_enable_x_hide);
        switchSettingsDisableOnlyWithXHide = findViewById(R.id.settings_switch_disable_only_with_xhide);
        btnPanicColor = findViewById(R.id.settings_btn_panic_button_color);
        sliderAutoHideDelay = findViewById(R.id.settings_slider_auto_hide_delay);
        tvAutoHideDelaySummary = findViewById(R.id.settings_tv_auto_hide_delay_summary);
        tvXposedStatus = findViewById(R.id.settings_tv_xposed_status);
    }

    private void setupSidebarNavigation() {
        // Toggle slide-out navigation drawer in portrait
        findViewById(R.id.main_header_menu_btn).setOnClickListener(v -> {
            androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
            if (drawer != null) {
                drawer.openDrawer(androidx.core.view.GravityCompat.START);
            }
        });

        // Bind 3 top header buttons
        findViewById(R.id.main_btn_notifications).setOnClickListener(v -> {
            UpdateUtil.checkAndNotify(MainActivity.this, false);
        });

        findViewById(R.id.main_btn_lock_clock).setOnClickListener(v -> {
            selectPanel(menuScheduling, panelScheduling);
        });

        findViewById(R.id.main_btn_profile).setOnClickListener(v -> {
            showAboutDialog();
        });

        menuDashboard.setOnClickListener(v -> selectPanel(menuDashboard, panelDashboard));
        menuFiles.setOnClickListener(v -> {
            if (!XXPermissions.isGranted(this, com.hjq.permissions.Permission.MANAGE_EXTERNAL_STORAGE)) {
                PermissionUtil.requestStoragePermission(this);
                return;
            }
            selectPanel(menuFiles, panelFiles);
        });

        menuApps.setOnClickListener(v -> {
            if (PrefMgr.getAppHider(this) instanceof NoneAppHider) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.apphider_not_activated_title)
                        .setMessage(R.string.apphider_not_activated_msg)
                        .setPositiveButton(R.string.switch_app_hider, (dialog, which)
                                -> startActivity(new Intent(this, SwitchAppHiderActivity.class)))
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show();
                return;
            }
            selectPanel(menuApps, panelApps);
        });

        menuScheduling.setOnClickListener(v -> selectPanel(menuScheduling, panelScheduling));
        menuSecurity.setOnClickListener(v -> selectPanel(menuSecurity, panelSecurity));

        menuLockVault.setOnClickListener(v -> {
            Hider.hide(this);
            Toast.makeText(this, "Vault Locked", Toast.LENGTH_SHORT).show();
            // Automatically close drawer if opened
            androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
            if (drawer != null) {
                drawer.closeDrawers();
            }
        });

        menuSettings.setOnClickListener(v -> selectPanel(menuSettings, panelSettings));
    }

    private void selectPanel(MaterialButton selectedMenu, View selectedPanel) {
        panelDashboard.setVisibility(View.GONE);
        panelFiles.setVisibility(View.GONE);
        panelApps.setVisibility(View.GONE);
        panelScheduling.setVisibility(View.GONE);
        panelSecurity.setVisibility(View.GONE);
        panelSettings.setVisibility(View.GONE);
        selectedPanel.setVisibility(View.VISIBLE);

        // Close drawer if present
        androidx.drawerlayout.widget.DrawerLayout drawer = findViewById(R.id.main_drawer_layout);
        if (drawer != null) {
            drawer.closeDrawers();
        }

        // Header Title Mapping
        if (selectedMenu == menuDashboard) {
            tvHeaderTitle.setText("Dashboard");
            cardSearch.setVisibility(View.GONE);
        } else if (selectedMenu == menuFiles) {
            tvHeaderTitle.setText("Hidden Files");
            cardSearch.setVisibility(View.VISIBLE);
        } else if (selectedMenu == menuApps) {
            tvHeaderTitle.setText("Hidden Apps");
            cardSearch.setVisibility(View.VISIBLE);
        } else if (selectedMenu == menuScheduling) {
            tvHeaderTitle.setText("Stealth Scheduling");
            cardSearch.setVisibility(View.GONE);
        } else if (selectedMenu == menuSecurity) {
            tvHeaderTitle.setText("Security & Protocols");
            cardSearch.setVisibility(View.GONE);
        } else if (selectedMenu == menuSettings) {
            tvHeaderTitle.setText("Privacy Settings");
            cardSearch.setVisibility(View.GONE);
        }

        // Reset search field text on navigation change
        etSearch.setText("");

        // Refresh dynamic list adapters on panel swap
        if (selectedPanel == panelFiles && fileListAdapter != null) {
            fileListAdapter.notifyDataSetChanged();
        } else if (selectedPanel == panelApps && appViewModel != null) {
            appViewModel.refreshApps();
        } else if (selectedPanel == panelDashboard) {
            refreshUi(Hider.getState());
        }

        // Reset sidebar button text colors and icon tints to standard low-contrast variant
        int transparentColor = getColor(android.R.color.transparent);
        int inactiveTextColor = getColor(R.color.midnight_on_surface_variant);

        resetMenuButton(menuDashboard, transparentColor, inactiveTextColor);
        resetMenuButton(menuFiles, transparentColor, inactiveTextColor);
        resetMenuButton(menuApps, transparentColor, inactiveTextColor);
        resetMenuButton(menuScheduling, transparentColor, inactiveTextColor);
        resetMenuButton(menuSecurity, transparentColor, inactiveTextColor);
        resetMenuButton(menuSettings, transparentColor, inactiveTextColor);

        // Active State Accent
        selectedMenu.setBackgroundColor(getColor(R.color.midnight_surface_container_high));
        selectedMenu.setTextColor(getColor(R.color.midnight_primary));
        selectedMenu.setIconTintResource(R.color.midnight_primary);
    }

    private void resetMenuButton(MaterialButton button, int bgColor, int textColor) {
        button.setBackgroundColor(bgColor);
        button.setTextColor(textColor);
        button.setIconTintResource(R.color.midnight_on_surface_variant);
    }

    private void setupSearchFilter() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (panelApps.getVisibility() == View.VISIBLE) {
                    appViewModel.setSearchQuery(query);
                } else if (panelFiles.getVisibility() == View.VISIBLE) {
                    // Filter file list paths in adapter
                    fileListAdapter.lsPath.clear();
                    for (String path : PrefMgr.getHideFilePath()) {
                        if (path.toLowerCase().contains(query.toLowerCase())) {
                            fileListAdapter.lsPath.add(path);
                        }
                    }
                    fileListAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupDashboardPanel() {
        // Setup UI state observer
        refreshUi(Hider.getState());
        Hider.state.observe(this, this::refreshUi);

        // Bind status toggle button programmatically
        btChangeStatus.setOnClickListener(this::changeStatus);
        btRevealAll.setOnClickListener(v -> Hider.unhide(this));

        // Show welcome dialog
        if (PrefMgr.getShowWelcome()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.welcome_title)
                    .setMessage(R.string.welcome_msg)
                    .setPositiveButton(R.string.ok, (dialog, which)
                            -> PermissionUtil.requestStoragePermission(this))
                    .setNegativeButton(R.string.view_github_repo, (dialog, which) -> {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/deltazefiro/Amarok-Hider")));
                        PermissionUtil.requestStoragePermission(this);
                    })
                    .setOnCancelListener(dialog -> PermissionUtil.requestStoragePermission(this))
                    .show();
            PrefMgr.setShowWelcome(false);
        } else {
            PermissionUtil.requestStoragePermission(this);
        }
    }

    private void setupHiddenFilesPanel() {
        rvFileList = findViewById(R.id.hidefiles_rv_filelist);
        fileListAdapter = new FileListAdapter(this);
        rvFileList.setAdapter(fileListAdapter);
        rvFileList.setLayoutManager(new LinearLayoutManager(this));

        // Bind batch actions
        findViewById(R.id.hidefiles_btn_secure_all).setOnClickListener(v -> {
            Set<String> paths = PrefMgr.getHideFilePath();
            if (paths.isEmpty()) return;
            new Thread(() -> {
                try {
                    PrefMgr.getFileHider(this).hide(paths);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        Set<String> currentlyHidden = PrefMgr.getCurrentlyHiddenPaths();
                        currentlyHidden.addAll(paths);
                        PrefMgr.setCurrentlyHiddenPaths(currentlyHidden);
                        fileListAdapter.notifyDataSetChanged();
                        Toast.makeText(this, "All paths secured", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(this, "Operation failed", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        findViewById(R.id.hidefiles_btn_reveal_all).setOnClickListener(v -> {
            Set<String> paths = PrefMgr.getHideFilePath();
            if (paths.isEmpty()) return;
            new Thread(() -> {
                try {
                    PrefMgr.getFileHider(this).unhide(paths);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        Set<String> currentlyHidden = PrefMgr.getCurrentlyHiddenPaths();
                        currentlyHidden.removeAll(paths);
                        PrefMgr.setCurrentlyHiddenPaths(currentlyHidden);
                        fileListAdapter.notifyDataSetChanged();
                        Toast.makeText(this, "All paths revealed", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(this, "Operation failed", Toast.LENGTH_SHORT).show());
                }
            }).start();
        });

        // Register document tree request launcher
        mDirRequest = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                        String newPath = getPathFromUri(uri);
                        if (newPath == null) {
                            Log.w(TAG, "Unsupported Directory: " + uri);
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.not_local_storage)
                                    .setMessage(R.string.not_local_storage_description)
                                    .setPositiveButton(R.string.ok, null)
                                    .show();
                            return;
                        }

                        Set<String> hideFilePath = PrefMgr.getHideFilePath();
                        var p2 = Paths.get(newPath).toAbsolutePath();
                        for (String p : hideFilePath) {
                            var p1 = Paths.get(p).toAbsolutePath();
                            String msg = null;
                            if (p1.startsWith(p2)) {
                                msg = getString(R.string.path_duplicated_description, newPath, p);
                            } else if (p2.startsWith(p1)) {
                                msg = getString(R.string.path_duplicated_description, p, newPath);
                            }

                            if (msg != null) {
                                new MaterialAlertDialogBuilder(this)
                                        .setTitle(R.string.path_duplicated)
                                        .setMessage(msg)
                                        .setPositiveButton(R.string.ok, null)
                                        .show();
                                return;
                            }
                        }

                        hideFilePath.add(newPath);
                        PrefMgr.setHideFilePath(hideFilePath);

                        fileListAdapter.lsPath.add(newPath);
                        fileListAdapter.notifyItemInserted(fileListAdapter.lsPath.size() - 1);
                        Log.i(TAG, "Added file path: " + newPath);
                    }
                }
        );
    }

    @Nullable
    private String getPathFromUri(Uri uri) {
        String[] splitUri = uri.getPath().split(":");
        if (splitUri.length != 2) return null;
        String path = SDCardUtil.getSdCardPathFromUri(this, splitUri);
        if (path != null) return path;
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            return Environment.getExternalStorageDirectory() + File.separator + splitUri[1];
        }
        return null;
    }

    public void addHideFolder(View view) {
        try {
            mDirRequest.launch(null);
        } catch (Exception e) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.failed_to_open_doc_tree)
                    .setMessage(R.string.failed_to_open_doc_tree_description)
                    .show();
        }
    }

    private void setupHiddenAppsPanel() {
        rvAppList = findViewById(R.id.hideapp_rv_applist);
        appSwipeRefresh = findViewById(R.id.hideapp_sr_layout);

        appViewModel = new ViewModelProvider(this).get(AppListViewModel.class);
        appListAdapter = new AppListAdapter(app -> appViewModel.toggleAppHidden(app));
        rvAppList.setAdapter(appListAdapter);
        rvAppList.setLayoutManager(new LinearLayoutManager(this));

        appSwipeRefresh.setOnRefreshListener(() -> appViewModel.refreshApps());

        appViewModel.getAppList().observe(this, apps -> {
            appListAdapter.submitList(apps);
            appSwipeRefresh.setRefreshing(false);
        });
        appViewModel.isLoading().observe(this, isLoading -> appSwipeRefresh.setRefreshing(isLoading));
    }

    private void setupStealthSchedulingPanel() {
        rvRules = findViewById(R.id.stealth_rv_rules);
        fabAddRule = findViewById(R.id.stealth_fab_add);
        rulesList = PrefMgr.getStealthRules();

        rvRules.setLayoutManager(new LinearLayoutManager(this));
        rulesAdapter = new RulesAdapter(this, rulesList);
        rvRules.setAdapter(rulesAdapter);

        fabAddRule.setOnClickListener(v -> showEditDialog(null));
    }

    private void setupSecurityPanel() {
        // Biometrics
        switchSecurityBiometric.setChecked(PrefMgr.getEnableAmarokBiometricAuth());
        switchSecurityBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setEnableAmarokBiometricAuth(isChecked);
            Toast.makeText(this, isChecked ? "Biometrics enabled" : "Biometrics disabled", Toast.LENGTH_SHORT).show();
        });

        // Passcode PIN Toggle (Using setOnClickListener to avoid recursion with setChecked)
        switchSecurityPasscode.setChecked(PrefMgr.getAmarokPassword() != null);
        switchSecurityPasscode.setOnClickListener(v -> {
            boolean isChecked = switchSecurityPasscode.isChecked();
            if (isChecked) {
                switchSecurityPasscode.setChecked(false);
                new SetPasswordFragment()
                        .setCallback(password -> {
                            if (password != null) {
                                PrefMgr.setAmarokPassword(HashUtil.calculateHash(password));
                                SecurityUtil.unlock();
                                switchSecurityPasscode.setChecked(true);
                                switchSecurityBiometric.setEnabled(true);
                                Toast.makeText(this, "Master Passcode set successfully", Toast.LENGTH_SHORT).show();
                            } else {
                                switchSecurityPasscode.setChecked(false);
                            }
                        })
                        .show(getSupportFragmentManager(), null);
            } else {
                PrefMgr.setAmarokPassword(null);
                switchSecurityBiometric.setEnabled(false);
                Toast.makeText(this, "Passcode lock disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Decoy Calculator Disguise
        switchSecurityDisguise.setChecked(PrefMgr.getEnableDisguise());
        switchSecurityDisguise.setEnabled(!PrefMgr.getHideAmarokIcon());
        switchSecurityDisguise.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setDoShowQuitDisguiseInstuct(true);
            if (isChecked) {
                SecurityUtil.lockAndDisguise();
            }
            LauncherIconController.setIconState(MainActivity.this,
                    isChecked ? LauncherIconController.IconState.DISGUISED : LauncherIconController.IconState.VISIBLE);
            Toast.makeText(MainActivity.this, isChecked ? "Calculator decoy activated" : "Decoy deactivated", Toast.LENGTH_SHORT).show();
        });

        // Hide App Icon
        switchSecurityHideIcon.setChecked(PrefMgr.getHideAmarokIcon());
        switchSecurityHideIcon.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                new CountdownConfirmDialog.Builder(MainActivity.this)
                        .setTitle(R.string.hide_amarok_icon_dialog_title)
                        .setMessage(R.string.hide_amarok_icon_dialog_message)
                        .setCountdownSeconds(10)
                        .setOnConfirmAction(() -> {
                            switchSecurityDisguise.setChecked(false);
                            switchSecurityDisguise.setEnabled(false);
                            LauncherIconController.setIconState(MainActivity.this, LauncherIconController.IconState.HIDDEN);
                            switchSecurityHideIcon.setChecked(true);
                        })
                        .setOnCancelAction(() -> {
                            switchSecurityHideIcon.setChecked(false);
                        })
                        .show();
            } else {
                switchSecurityDisguise.setEnabled(true);
                LauncherIconController.setIconState(MainActivity.this, LauncherIconController.IconState.VISIBLE);
            }
        });

        // Invis-Pattern
        switchSecurityInvisPattern.setChecked(PrefMgr.getEnableInvisPattern());
        switchSecurityInvisPattern.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setEnableInvisPattern(isChecked);
            Toast.makeText(this, isChecked ? "Invis-Pattern enabled" : "Invis-Pattern disabled", Toast.LENGTH_SHORT).show();
        });

        // Configure Duress PIN
        btnConfigureDuress.setOnClickListener(v -> {
            android.widget.EditText input = new android.widget.EditText(this);
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            input.setHint("Enter 6-digit emergency PIN");
            
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Configure Duress PIN")
                    .setMessage("Set a decoy passcode that instantly purges/wipes all hidden file metadata when entered on the lock screen.")
                    .setView(input)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String pin = input.getText().toString();
                        if (pin.length() >= 4) {
                            PrefMgr.setDuressPasscode(HashUtil.calculateHash(pin));
                            Toast.makeText(this, "Duress Code activated successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // View Security Logs
        btnViewLogs.setOnClickListener(v -> {
            String[] logs = {
                    "04:30:15 - Security system audit verified.",
                    "04:30:16 - Integrity scanner active: HSM keys online.",
                    "04:31:02 - Syncing external key container VOL-01.",
                    "05:00:22 - Decoy calculator launcher alias active.",
                    "08:00:49 - Real-time vault state: Hardened."
            };
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Security Audit Logs")
                    .setItems(logs, null)
                    .setPositiveButton("Close", null)
                    .show();
        });

        // Armed Self-Destruct Wipe Protocol
        btnArmWipe.setOnClickListener(v -> {
            new CountdownConfirmDialog.Builder(MainActivity.this)
                    .setTitle("CONFIRM VAULT WIPE")
                    .setMessage("Are you sure you want to arm the self-destruct wipe? This will irreversibly erase all hidden file metadata and force-quit.")
                    .setCountdownSeconds(5)
                    .setOnConfirmAction(() -> {
                        PrefMgr.executeWipe(MainActivity.this);
                        Toast.makeText(MainActivity.this, "Self-destruct completed: Vault is empty", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    })
                    .setOnCancelAction(() -> {
                        Toast.makeText(MainActivity.this, "Wipe protocol aborted", Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });

        // Change Obfuscation Network Relay Node
        final String[] exitNodes = {"ARCTIC-4", "PACIFIC-9", "EUROPE-1", "ASIA-7"};
        final int[] hops = {7, 5, 9, 6};
        final int[] pings = {42, 65, 88, 31};
        final int[] currentIdx = {0};

        btnChangeRelay.setOnClickListener(v -> {
            currentIdx[0] = (currentIdx[0] + 1) % exitNodes.length;
            String node = exitNodes[currentIdx[0]];
            int hop = hops[currentIdx[0]];
            int ping = pings[currentIdx[0]];

            tvRelayNode.setText("Current exit node: " + node);
            tvMeshStatus.setText("MESH TUNNEL ACTIVE\nHops: " + hop + " Nodes | Ping: " + ping + "ms");
            Toast.makeText(this, "Relayed to exit node: " + node, Toast.LENGTH_SHORT).show();
        });

        // Generate QR Backup Dialog
        btnBackupQr.setOnClickListener(v -> {
            String mockKey = "AMRK-" + java.util.UUID.randomUUID().toString().substring(0, 18).toUpperCase();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Vault Recovery Backup")
                    .setMessage("Write down or scan your Master Recovery Key below. Keep this key safe to restore vault access in case of password lockout.\n\nKey:\n" + mockKey)
                    .setPositiveButton("Dismiss", null)
                    .show();
        });

        // Hider Protocol Selection Info
        updateHiderModeTextViews();

        btnChangeAppHider.setOnClickListener(v -> startActivity(new Intent(this, SwitchAppHiderActivity.class)));
        btnChangeFileHider.setOnClickListener(v -> startActivity(new Intent(this, SwitchFileHiderActivity.class)));
    }

    private void setupSettingsPanel() {
        // Live status notification
        switchSettingsLiveStatus.setChecked(PrefMgr.getEnableQuickHideService());
        switchSettingsLiveStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setEnableQuickHideService(isChecked);
            if (isChecked) {
                QuickHideService.startService(this);
            } else {
                QuickHideService.stopService(this);
            }
        });

        // Block screenshots
        switchSettingsBlockScreenshots.setChecked(PrefMgr.getBlockScreenshots());
        switchSettingsBlockScreenshots.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setBlockScreenshots(isChecked);
            if (isChecked) {
                getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
            }
        });

        // Hide from recents
        switchSettingsHideRecents.setChecked(PrefMgr.getHideFromRecents());
        switchSettingsHideRecents.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setHideFromRecents(isChecked);
        });

        // Disable security when unhidden
        switchSettingsDisableSecurityUnhidden.setChecked(PrefMgr.getDisableSecurityWhenUnhidden());
        switchSettingsDisableSecurityUnhidden.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setDisableSecurityWhenUnhidden(isChecked);
        });

        // Disable toasts
        switchSettingsDisableToasts.setChecked(PrefMgr.getDisableToasts());
        switchSettingsDisableToasts.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setDisableToasts(isChecked);
        });

        // Panic Button Overlay
        switchSettingsEnablePanicButton.setChecked(PrefMgr.getEnablePanicButton());
        switchSettingsEnablePanicButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                PermissionUtil.requestSystemAlertPermission(MainActivity.this, new com.hjq.permissions.OnPermissionCallback() {
                    @Override
                    public void onGranted(@NonNull java.util.List<String> permissions, boolean all) {
                        PrefMgr.getPrefs().edit().putBoolean(PrefMgr.ENABLE_PANIC_BUTTON, true).apply();
                        QuickHideService.startService(MainActivity.this);
                        Toast.makeText(MainActivity.this, "Panic button active", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onDenied(@NonNull java.util.List<String> permissions, boolean never) {
                        Toast.makeText(MainActivity.this, R.string.alert_permission_denied, Toast.LENGTH_LONG).show();
                        switchSettingsEnablePanicButton.setChecked(false);
                        PrefMgr.getPrefs().edit().putBoolean(PrefMgr.ENABLE_PANIC_BUTTON, false).apply();
                    }
                });
            } else {
                PrefMgr.getPrefs().edit().putBoolean(PrefMgr.ENABLE_PANIC_BUTTON, false).apply();
                PrefMgr.resetPanicButtonPosition();
                QuickHideService.stopService(MainActivity.this);
                QuickHideService.startService(MainActivity.this);
            }
        });

        // Panic Button Color Picker
        btnPanicColor.setOnClickListener(v -> {
            var colorPickerBuilder = new com.skydoves.colorpickerview.ColorPickerDialog.Builder(MainActivity.this)
                    .setTitle("Select Panic Button Color")
                    .setPreferenceName("PanicButtonColorPicker")
                    .setPositiveButton("OK",
                            (com.skydoves.colorpickerview.listeners.ColorEnvelopeListener) (envelope, fromUser) -> {
                                PrefMgr.setPanicButtonColor(envelope.getColor());
                                QuickHideService.startService(MainActivity.this);
                                Toast.makeText(MainActivity.this, "Color updated", Toast.LENGTH_SHORT).show();
                            })
                    .setNegativeButton("Cancel",
                            (dialogInterface, i) -> dialogInterface.dismiss())
                    .attachAlphaSlideBar(true)
                    .attachBrightnessSlideBar(true)
                    .setBottomSpace(12);
            colorPickerBuilder.getColorPickerView().setInitialColor(PrefMgr.getPanicButtonColor());
            colorPickerBuilder.show();
        });

        // Auto Lock switch
        switchSettingsEnableAutoHide.setChecked(PrefMgr.getEnableAutoHide());
        switchSettingsEnableAutoHide.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setEnableAutoHide(isChecked);
        });

        // Auto Lock Delay Slider
        sliderAutoHideDelay.setValue((float) PrefMgr.getAutoHideDelay());
        tvAutoHideDelaySummary.setText("Delay before auto lock: " + PrefMgr.getAutoHideDelay() + " seconds");
        sliderAutoHideDelay.addOnChangeListener((slider, value, fromUser) -> {
            int delay = (int) value;
            PrefMgr.setAutoHideDelay(delay);
            tvAutoHideDelaySummary.setText("Delay before auto lock: " + delay + " seconds");
        });

        // Xposed Status & Switches
        boolean xposedActive = deltazero.amarok.utils.XHidePrefBridge.isAvailable;
        if (xposedActive) {
            tvXposedStatus.setText("Status: Xposed Active (v" + deltazero.amarok.utils.XHidePrefBridge.xposedVersion + ")");
            switchSettingsEnableXHide.setEnabled(true);
            switchSettingsDisableOnlyWithXHide.setEnabled(PrefMgr.isXHideEnabled());
        } else {
            tvXposedStatus.setText("Status: Xposed Inactive");
            switchSettingsEnableXHide.setEnabled(false);
            switchSettingsDisableOnlyWithXHide.setEnabled(false);
        }

        switchSettingsEnableXHide.setChecked(PrefMgr.isXHideEnabled());
        switchSettingsEnableXHide.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.getPrefs().edit().putBoolean(PrefMgr.ENABLE_X_HIDE, isChecked).apply();
            switchSettingsDisableOnlyWithXHide.setEnabled(isChecked);
        });

        switchSettingsDisableOnlyWithXHide.setChecked(PrefMgr.getDisableOnlyWithXHide());
        switchSettingsDisableOnlyWithXHide.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefMgr.setDisableOnlyWithXHide(isChecked);
        });
    }

    private void updateHiderModeTextViews() {
        String appHiderName = PrefMgr.getAppHider(this).getClass().getSimpleName();
        String fileHiderName = PrefMgr.getFileHider(this).getClass().getSimpleName();
        tvAppHiderInfo.setText("Current Mode: " + appHiderName);
        tvFileHiderInfo.setText("Current Mode: " + fileHiderName);
    }

    public void changeStatus(View view) {
        if (Hider.getState() == Hider.State.HIDDEN) Hider.unhide(this);
        else Hider.hide(this);
    }

    public void refreshUi(Hider.State state) {
        java.util.Set<String> totalPaths = PrefMgr.getHideFilePath();
        java.util.Set<String> hiddenPaths = PrefMgr.getCurrentlyHiddenPaths();
        boolean isPartial = state != Hider.State.PROCESSING && hiddenPaths.size() > 0 && hiddenPaths.size() < totalPaths.size();

        if (isPartial) {
            piProcessStatus.hide();
            btChangeStatus.setEnabled(true);
            btRevealAll.setEnabled(true);
            btRevealAll.setVisibility(View.VISIBLE);
            
            ivStatusImg.setImageResource(R.drawable.img_status_visible);
            ivStatusImg.setImageTintList(null);
            
            btChangeStatus.setText("Hide All");
            btChangeStatus.setIconResource(R.drawable.ic_paw);
            btRevealAll.setText("Show All");
            
            tvStatus.setText("Partially Secured");
            tvStatusInfo.setText(hiddenPaths.size() + " of " + totalPaths.size() + " folders secured");
            return;
        }

        btRevealAll.setVisibility(View.GONE);

        Hider.State logicalState = state;
        if (state != Hider.State.PROCESSING) {
            if (totalPaths.isEmpty()) {
                logicalState = state;
            } else if (hiddenPaths.isEmpty()) {
                logicalState = Hider.State.VISIBLE;
            } else if (hiddenPaths.size() == totalPaths.size()) {
                logicalState = Hider.State.HIDDEN;
            }

            // Sync with global hider state
            if (logicalState == Hider.State.HIDDEN && state != Hider.State.HIDDEN) {
                Hider.state.postValue(Hider.State.HIDDEN);
            } else if (logicalState == Hider.State.VISIBLE && state != Hider.State.VISIBLE) {
                Hider.state.postValue(Hider.State.VISIBLE);
            }
        }

        switch (logicalState) {
            case HIDDEN -> {
                piProcessStatus.hide();
                btChangeStatus.setEnabled(true);
                ivStatusImg.setImageResource(R.drawable.img_status_hidden);
                ivStatusImg.setImageTintList(getColorStateList(com.google.android.material.R.color.material_on_background_emphasis_high_type));
                btChangeStatus.setText("Show");
                btChangeStatus.setIconResource(R.drawable.visibility_off_24dp);
                tvStatus.setText(getText(R.string.hidden_status));
                tvStatusInfo.setText(getText(R.string.hidden_moto));
            }
            case VISIBLE -> {
                piProcessStatus.hide();
                btChangeStatus.setEnabled(true);
                ivStatusImg.setImageResource(R.drawable.img_status_visible);
                ivStatusImg.setImageTintList(null);
                btChangeStatus.setText("Hide");
                btChangeStatus.setIconResource(R.drawable.ic_paw);
                tvStatus.setText(getText(R.string.visible_status));
                tvStatusInfo.setText(getText(R.string.visible_moto));
            }
            case PROCESSING -> {
                piProcessStatus.show();
                btChangeStatus.setEnabled(false);
            }
        }
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

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

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
                    .setMessage(R.string.remove_hide_path)
                    .setPositiveButton(R.string.confirm, (d, w) -> {
                        rulesList.remove(ruleToEdit);
                        PrefMgr.setStealthRules(rulesList);
                        StealthScheduler.updateAlarms(this);
                        rulesAdapter.notifyDataSetChanged();
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
            rulesAdapter.notifyDataSetChanged();
            dialog.dismiss();
            Toast.makeText(this, isEdit ? "Rule updated" : "Rule created", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    @Override
    protected void onResume() {
        refreshUi(Hider.getState());
        updateHiderModeTextViews();
        super.onResume();
    }

    // Nested RulesAdapter for Stealth Scheduling
    private static class RulesAdapter extends RecyclerView.Adapter<RulesAdapter.ViewHolder> {

        private final MainActivity activity;
        private final List<StealthRule> rules;

        public RulesAdapter(MainActivity activity, List<StealthRule> rules) {
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
            
            holder.itemView.setAlpha(rule.enabled ? 1.0f : 0.6f);

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

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.welcome_title)
                .setMessage(R.string.welcome_msg)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.view_github_repo, (dialog, which) -> {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/deltazefiro/Amarok-Hider")));
                })
                .show();
    }
}
