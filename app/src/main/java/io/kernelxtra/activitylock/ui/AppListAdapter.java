package io.kernelxtra.activitylock.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import io.kernelxtra.activitylock.R;
import io.kernelxtra.activitylock.data.AppEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.Holder> {

    public interface Listener {
        void onAppSelected(@NonNull AppEntry entry);
    }

    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private final Listener listener;
    private String selectedPackage;
    private String query = "";

    public AppListAdapter(Listener listener, String selectedPackage) {
        this.listener = listener;
        this.selectedPackage = selectedPackage;
        setHasStableIds(true);
    }

    public void submit(List<AppEntry> entries) {
        all.clear();
        all.addAll(entries);
        filter(query);
    }

    public void setSelectedPackage(String packageName) {
        selectedPackage = packageName;
        notifyDataSetChanged();
    }

    public void filter(String rawQuery) {
        query = rawQuery == null ? "" : rawQuery.trim();
        String needle = query.toLowerCase(Locale.ROOT);
        shown.clear();
        if (needle.isEmpty()) {
            shown.addAll(all);
        } else {
            for (AppEntry entry : all) {
                if (entry.label.toLowerCase(Locale.ROOT).contains(needle)
                        || entry.packageName.toLowerCase(Locale.ROOT).contains(needle)) {
                    shown.add(entry);
                }
            }
        }
        notifyDataSetChanged();
    }

    public int getShownCount() {
        return shown.size();
    }

    @Override
    public long getItemId(int position) {
        return shown.get(position).packageName.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppEntry entry = shown.get(position);
        holder.name.setText(entry.label);
        holder.pkg.setText(entry.packageName);
        holder.icon.setImageDrawable(entry.icon);
        boolean selected = entry.packageName.equals(selectedPackage);
        holder.check.setVisibility(selected ? View.VISIBLE : View.GONE);
        holder.itemView.setOnClickListener(v -> listener.onAppSelected(entry));
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final ImageView check;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            name = itemView.findViewById(R.id.item_name);
            pkg = itemView.findViewById(R.id.item_package);
            check = itemView.findViewById(R.id.item_check);
        }
    }
}
