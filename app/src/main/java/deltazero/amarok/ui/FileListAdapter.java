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
        public MaterialButton btnVisibility, btnOpen, btnDelete;

        public FileListHolder(@NonNull View itemView, FileListAdapter adapter) {
            super(itemView);
            tvFolderName = itemView.findViewById(R.id.hidefile_tv_foldername);
            tvPath = itemView.findViewById(R.id.hidefile_tv_path);
            llPathItem = itemView.findViewById(R.id.hideapp_ll_pathitem);
            btnVisibility = itemView.findViewById(R.id.hidefile_btn_visibility);
            btnOpen = itemView.findViewById(R.id.hidefile_btn_open);
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
}
