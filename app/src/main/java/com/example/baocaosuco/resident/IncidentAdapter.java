package com.example.baocaosuco.resident;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baocaosuco.R;
import com.example.baocaosuco.data.incident.IncidentSnapshot;
import com.example.baocaosuco.manager.ManagerIncidentCoordinator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class IncidentAdapter extends RecyclerView.Adapter<IncidentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(IncidentSnapshot incident);
    }

    private final List<IncidentSnapshot> items = new ArrayList<>();
    private final OnItemClickListener listener;
    private final boolean isManager;

    public IncidentAdapter(boolean isManager, OnItemClickListener listener) {
        this.isManager = isManager;
        this.listener = listener;
    }

    public void setItems(List<IncidentSnapshot> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_incident, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        IncidentSnapshot item = items.get(position);
        holder.bind(item, isManager, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvPriorityBadge;
        private final TextView tvOverdueBadge;
        private final TextView tvStatusBadge;
        private final TextView tvTitle;
        private final TextView tvLocation;
        private final TextView tvTimestamp;
        private final TextView tvHandler;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPriorityBadge = itemView.findViewById(R.id.tv_priority_badge);
            tvOverdueBadge = itemView.findViewById(R.id.tv_overdue_badge);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvLocation = itemView.findViewById(R.id.tv_location);
            tvTimestamp = itemView.findViewById(R.id.tv_timestamp);
            tvHandler = itemView.findViewById(R.id.tv_handler);
        }

        public void bind(IncidentSnapshot item, boolean isManager, OnItemClickListener listener) {
            tvTitle.setText(item.getTitle());
            tvLocation.setText(item.getLocation() != null ? item.getLocation() : "");

            String priority = item.getPriority();
            tvPriorityBadge.setText(priority);

            tvStatusBadge.setText(item.getStatusCode());

            boolean overdue = ManagerIncidentCoordinator.isOverdue(item, System.currentTimeMillis());
            if (overdue) {
                tvOverdueBadge.setVisibility(View.VISIBLE);
            } else {
                tvOverdueBadge.setVisibility(View.GONE);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            tvTimestamp.setText(sdf.format(new Date(item.getCreatedAtMillis())));

            String handler = item.getAssignedTo();
            if (handler != null && !handler.trim().isEmpty()) {
                tvHandler.setText("Phụ trách: " + handler);
                tvHandler.setVisibility(View.VISIBLE);
            } else {
                tvHandler.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
