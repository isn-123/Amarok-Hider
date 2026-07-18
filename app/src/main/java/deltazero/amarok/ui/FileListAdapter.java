package deltazero.amarok.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;

import deltazero.amarok.filehider.ChmodFileHider;
import deltazero.amarok.filehider.NoMediaFileHider;
import deltazero.amarok.filehider.ObfuscateFileHider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;

public class FileListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public final LinkedList<String> lsPath;
    private final LayoutInflater inflater;
    private final Context context;

    private final int TYPE_FILE_ITEM = 0;
    private final int TYPE_FOOTAGE = 1;

    public FileListAdapter(Context context) {
        inflater = LayoutInflater.from(context);
        lsPath = new LinkedList<>(PrefMgr.getHideFilePath());
        this.context = context;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_FOOTAGE)
            return new FootageHolder(inflater.inflate(R.layout.item_hidefiles_footage, parent, false), this);
        return new FileListHolder(inflater.inflate(R.layout.item_hidefiles, parent, false), this);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position < lsPath.size()) {
            String currPath = lsPath.get(position);
            FileListHolder fileHolder = (FileListHolder) holder;
            
            fileHolder.tvFolderName.setText(
                    currPath.substring(currPath.lastIndexOf(File.separator) + 1)
            );
            fileHolder.tvPath.setText(currPath);

            // Check if folder is currently hidden in tracked state
            boolean isFolderHidden = PrefMgr.getCurrentlyHiddenPaths().contains(currPath);
            if (!isFolderHidden) {
                // Exposed / Visible state: Show "Hide" eye action button and enable Open Folder
                fileHolder.btnVisibility.setIconResource(R.drawable.visibility_off_24dp);
                fileHolder.btnVisibility.setIconTint(context.getColorStateList(R.color.midnight_on_surface_variant));
                fileHolder.btnOpen.setVisibility(View.VISIBLE);
            } else {
                // Locked / Hidden state: Show "Reveal" padlock action button and hide Open Folder
                fileHolder.btnVisibility.setIconResource(R.drawable.lock_black_24dp);
                fileHolder.btnVisibility.setIconTint(context.getColorStateList(R.color.midnight_primary));
                fileHolder.btnOpen.setVisibility(View.GONE);
            }

            // Open Folder in system file manager
            fileHolder.btnOpen.setOnClickListener(v -> {
                File file = new File(currPath);
                if (!file.exists()) {
                    Toast.makeText(context, "Folder does not exist", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Bypass StrictMode file exposure death
                try {
                    java.lang.reflect.Method m = android.os.StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                    m.invoke(null);
                } catch (Exception e) {
                    Log.e("FileListAdapter", "Failed to disable StrictMode exposure check", e);
                }

                Uri uri = Uri.fromFile(file);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

                // Try standard directory MIME type
                intent.setDataAndType(uri, "vnd.android.document/directory");
                try {
                    context.startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.d("FileListAdapter", "vnd.android.document/directory failed, trying resource/folder");
                }

                // Try legacy MIME type
                intent.setDataAndType(uri, "resource/folder");
                try {
                    context.startActivity(intent);
                    return;
                } catch (Exception e) {
                    Log.d("FileListAdapter", "resource/folder failed, trying generic folder");
                }

                // Try generic
                intent.setDataAndType(uri, "*/*");
                try {
                    context.startActivity(intent);
                } catch (Exception ex) {
                    Toast.makeText(context, "No file manager found", Toast.LENGTH_SHORT).show();
                }
            });

            // Toggle visibility in background thread
            fileHolder.btnVisibility.setOnClickListener(v -> {
                boolean isHidden = PrefMgr.getCurrentlyHiddenPaths().contains(currPath);
                new Thread(() -> {
                    try {
                        if (!isHidden) {
                            PrefMgr.getFileHider(context).hide(Collections.singleton(currPath));
                            new Handler(Looper.getMainLooper()).post(() -> {
                                Set<String> currentlyHidden = PrefMgr.getCurrentlyHiddenPaths();
                                currentlyHidden.add(currPath);
                                PrefMgr.setCurrentlyHiddenPaths(currentlyHidden);
                                notifyItemChanged(position);
                                Toast.makeText(context, "Path hidden", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            PrefMgr.getFileHider(context).unhide(Collections.singleton(currPath));
                            new Handler(Looper.getMainLooper()).post(() -> {
                                Set<String> currentlyHidden = PrefMgr.getCurrentlyHiddenPaths();
                                currentlyHidden.remove(currPath);
                                PrefMgr.setCurrentlyHiddenPaths(currentlyHidden);
                                notifyItemChanged(position);
                                Toast.makeText(context, "Path revealed", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        Log.e("FileListAdapter", "Visibility toggle failed", e);
                        new Handler(Looper.getMainLooper()).post(() ->
                                Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show()
                        );
                    }
                }).start();
            });

            fileHolder.btnSubfolders.setOnClickListener(v -> showSubfoldersDialog(currPath));

            fileHolder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.remove_hide_path)
                        .setMessage(context.getString(R.string.remove_hide_path_description, currPath))
                        .setPositiveButton(R.string.confirm, (dialog, which) -> {
                            Set<String> hideFilePath = PrefMgr.getHideFilePath();
                            hideFilePath.remove(currPath);
                            PrefMgr.setHideFilePath(hideFilePath);

                            // Clean from hidden paths state if deleted
                            Set<String> currentlyHidden = PrefMgr.getCurrentlyHiddenPaths();
                            currentlyHidden.remove(currPath);
                            PrefMgr.setCurrentlyHiddenPaths(currentlyHidden);

                            lsPath.remove(currPath);
                            notifyItemRemoved(position);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        }
    }

    @Override
    public int getItemCount() {
        return lsPath.size() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == lsPath.size())
            return TYPE_FOOTAGE;
        return TYPE_FILE_ITEM;
    }

    public static class FileListHolder extends RecyclerView.ViewHolder {

        public MaterialTextView tvFolderName, tvPath;
        public LinearLayout llPathItem;
        public MaterialButton btnVisibility, btnOpen, btnSubfolders, btnDelete;

        public FileListHolder(@NonNull View itemView, FileListAdapter adapter) {
            super(itemView);
            tvFolderName = itemView.findViewById(R.id.hidefile_tv_foldername);
            tvPath = itemView.findViewById(R.id.hidefile_tv_path);
            llPathItem = itemView.findViewById(R.id.hideapp_ll_pathitem);
            btnVisibility = itemView.findViewById(R.id.hidefile_btn_visibility);
            btnOpen = itemView.findViewById(R.id.hidefile_btn_open);
            btnSubfolders = itemView.findViewById(R.id.hidefile_btn_subfolders);
            btnDelete = itemView.findViewById(R.id.hidefile_btn_delete);
        }
    }

    public class FootageHolder extends RecyclerView.ViewHolder {

        public FootageHolder(@NonNull View itemView, FileListAdapter adapter) {
            super(itemView);

            MaterialButton btAddFolder = itemView.findViewById(R.id.hidefiles_bt_add_folder);
            btAddFolder.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(itemView.getContext())
                            .setTitle(R.string.manually_set_path)
                            .setMessage(R.string.manually_set_path_description)
                            .setNeutralButton(R.string.cancel, null);

                    View dlPathInput = LayoutInflater.from(alertBuilder.getContext()).inflate(R.layout.dialog_path_input, null);
                    EditText etPathInput = dlPathInput.findViewById(R.id.dialog_path_input_et_input);
                    etPathInput.setHint(Environment.getExternalStorageDirectory().getPath() + "/...");

                    alertBuilder.setView(dlPathInput)
                            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                                String input = Objects.requireNonNull(etPathInput.getText()).toString();

                                /* Check for experiment flags */
                                switch (input) {
                                    case "#nomedia-filehider" -> {
                                        PrefMgr.setFileHiderMode(NoMediaFileHider.class);
                                        Toast.makeText(context, "NoMedia filehider activated.", Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    case "#obfuscate-filehider" -> {
                                        PrefMgr.setFileHiderMode(ObfuscateFileHider.class);
                                        Toast.makeText(context, "Obfuscate filehider activated.", Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    case "#chmod-filehider" -> {
                                        PrefMgr.setFileHiderMode(ChmodFileHider.class);
                                        Toast.makeText(context, "Chmod filehider activated.", Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                }

                                /* Check path availability */
                                try {
                                    Paths.get(input);
                                } catch (Exception e) {
                                    Log.w("FilePicker", String.format("Invalid path %s: ", input), e);
                                    Toast.makeText(context, R.string.invalid_path, Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                Set<String> hideFilePaths = PrefMgr.getHideFilePath();
                                hideFilePaths.add(input);
                                PrefMgr.setHideFilePath(hideFilePaths);

                                lsPath.add(input);
                                adapter.notifyItemInserted(lsPath.size() - 1);
                            })
                            .show();
                    return true;
                }
            });

        }
    }

    private void showSubfoldersDialog(String parentPath) {
        File parentDir = new File(parentPath);
        if (!parentDir.exists() || !parentDir.isDirectory()) {
            Toast.makeText(context, "Parent folder does not exist or is hidden itself.", Toast.LENGTH_SHORT).show();
            return;
        }

        // List all subdirectories
        File[] files = parentDir.listFiles();
        if (files == null) {
            Toast.makeText(context, "Cannot read folder contents", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.List<SubfolderItem> subfolders = new java.util.ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                boolean isHidden = name.startsWith(".") && (name.endsWith("!amk") || name.endsWith("!amk1") || name.endsWith("!amk2"));
                String displayName = name;
                if (isHidden) {
                    try {
                        String base64Name = name.substring(1); // strip leading dot
                        if (base64Name.endsWith("!amk1")) {
                            base64Name = base64Name.substring(0, base64Name.length() - 5);
                        } else if (base64Name.endsWith("!amk2")) {
                            base64Name = base64Name.substring(0, base64Name.length() - 5);
                        } else if (base64Name.endsWith("!amk")) {
                            base64Name = base64Name.substring(0, base64Name.length() - 4);
                        }
                        displayName = new String(android.util.Base64.decode(base64Name, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        Log.w("SubfoldersDialog", "Failed to decode: " + name, e);
                    }
                }
                subfolders.add(new SubfolderItem(file, displayName, isHidden));
            }
        }

        if (subfolders.isEmpty()) {
            Toast.makeText(context, "No subfolders found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Sort by display name
        java.util.Collections.sort(subfolders, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

        // Create dialog views dynamically
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 20, context.getResources().getDisplayMetrics());
        container.setPadding(padding, padding, padding, padding);

        for (SubfolderItem item : subfolders) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics()));

            // Folder Icon
            android.widget.ImageView ivFolder = new android.widget.ImageView(context);
            ivFolder.setImageResource(R.drawable.domino_mask_fill0_wght400_grad0_opsz24);
            ivFolder.setImageTintList(context.getColorStateList(R.color.midnight_primary));
            LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(
                (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics()),
                (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics())
            );
            ivParams.rightMargin = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
            row.addView(ivFolder, ivParams);

            // Text Name
            android.widget.TextView tvName = new android.widget.TextView(context);
            tvName.setText(item.displayName);
            tvName.setTextSize(16);
            tvName.setTextColor(context.getColor(R.color.midnight_on_surface));
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            row.addView(tvName, tvParams);

            // Switch
            com.google.android.material.materialswitch.MaterialSwitch swVisibility = new com.google.android.material.materialswitch.MaterialSwitch(context);
            swVisibility.setChecked(!item.isHidden);
            row.addView(swVisibility);

            swVisibility.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!buttonView.isPressed()) return;

                swVisibility.setEnabled(false);

                new Thread(() -> {
                    try {
                        String currentPath = item.file.getAbsolutePath();
                        if (isChecked) {
                            // Unhide subfolder
                            PrefMgr.getFileHider(context).unhide(java.util.Collections.singleton(currentPath));
                        } else {
                            // Hide subfolder
                            PrefMgr.getFileHider(context).hide(java.util.Collections.singleton(currentPath));
                        }

                        // Rescan the directory to update the File object mapping
                        new Handler(Looper.getMainLooper()).post(() -> {
                            swVisibility.setEnabled(true);
                            File parent = new File(parentPath);
                            File[] updatedFiles = parent.listFiles();
                            if (updatedFiles != null) {
                                for (File f : updatedFiles) {
                                    String fn = f.getName();
                                    boolean fHidden = fn.startsWith(".") && (fn.endsWith("!amk") || fn.endsWith("!amk1") || fn.endsWith("!amk2"));
                                    String fDecrypted = fn;
                                    if (fHidden) {
                                        try {
                                            String base64Name = fn.substring(1);
                                            if (base64Name.endsWith("!amk1")) {
                                                base64Name = base64Name.substring(0, base64Name.length() - 5);
                                            } else if (base64Name.endsWith("!amk2")) {
                                                base64Name = base64Name.substring(0, base64Name.length() - 5);
                                            } else if (base64Name.endsWith("!amk")) {
                                                base64Name = base64Name.substring(0, base64Name.length() - 4);
                                            }
                                            fDecrypted = new String(android.util.Base64.decode(base64Name, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING), java.nio.charset.StandardCharsets.UTF_8);
                                        } catch (Exception ignore) {}
                                    }
                                    if (fDecrypted.equals(item.displayName)) {
                                        item.file = f;
                                        item.isHidden = fHidden;
                                        break;
                                    }
                                }
                            }
                            Toast.makeText(context, isChecked ? "Subfolder unhidden" : "Subfolder hidden", Toast.LENGTH_SHORT).show();
                        });

                    } catch (Exception e) {
                        Log.e("SubfoldersDialog", "Subfolder toggle failed", e);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            swVisibility.setEnabled(true);
                            swVisibility.setChecked(!isChecked);
                            Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            });

            container.addView(row);
        }

        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(context);
        scrollView.addView(container);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Manage Subfolders")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .show();
    }

    private static class SubfolderItem {
        File file;
        String displayName;
        boolean isHidden;

        SubfolderItem(File file, String displayName, boolean isHidden) {
            this.file = file;
            this.displayName = displayName;
            this.isHidden = isHidden;
        }
    }
}
