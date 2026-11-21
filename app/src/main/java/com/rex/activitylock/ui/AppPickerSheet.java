package com.rex.activitylock.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.rex.activitylock.R;
import com.rex.activitylock.data.AppEntry;
import com.rex.activitylock.util.PackageUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppPickerSheet extends DialogFragment {

    public interface Listener {
        void onAppPicked(@NonNull AppEntry entry);
    }

    private static final String ARG_SELECTED = "selected";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Listener listener;
    private AppListAdapter adapter;
    private TextView countView;
    private TextView emptyView;
    private boolean loaded;

    public static AppPickerSheet newInstance(@Nullable String selectedPackage) {
        AppPickerSheet sheet = new AppPickerSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SELECTED, selectedPackage);
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            listener = (Listener) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), R.style.Theme_ActivityLock_BottomSheet);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_app_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String selected = getArguments() == null ? null : getArguments().getString(ARG_SELECTED);
        adapter = new AppListAdapter(entry -> {
            if (listener != null) {
                listener.onAppPicked(entry);
            }
            dismissAllowingStateLoss();
        }, selected);

        RecyclerView list = view.findViewById(R.id.app_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        countView = view.findViewById(R.id.app_count);
        emptyView = view.findViewById(R.id.empty_view);

        EditText search = view.findViewById(R.id.search_input);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
                updateCount();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        view.findViewById(R.id.btn_close_picker).setOnClickListener(v -> dismiss());

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        loadApps();
    }

    private void loadApps() {
        Context app = requireContext().getApplicationContext();
        executor.execute(() -> {
            List<AppEntry> apps = PackageUtils.loadLaunchableApps(app);
            main.post(() -> {
                if (!isAdded()) {
                    return;
                }
                loaded = true;
                adapter.submit(apps);
                updateCount();
            });
        });
    }

    private void updateCount() {
        if (countView == null || adapter == null || !loaded) {
            return;
        }
        int count = adapter.getShownCount();
        countView.setText(getString(R.string.picker_count, count));
        emptyView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        emptyView.setText(R.string.picker_empty);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetDialog sheet = (BottomSheetDialog) dialog;
            Window window = sheet.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                window.setDimAmount(0.6f);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            View bottomSheet = sheet.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setDraggable(true);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        executor.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
