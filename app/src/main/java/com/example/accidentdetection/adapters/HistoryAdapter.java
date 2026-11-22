package com.example.accidentdetection.adapters;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accidentdetection.R;
import com.example.accidentdetection.database.HistoryEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private List<HistoryEntry> items = new ArrayList<>();
    private final SimpleDateFormat format =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public void setItems(List<HistoryEntry> list) {
        items = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HistoryEntry e = items.get(position);
        h.tvType.setText(e.type);
        h.tvStatus.setText(e.status);
        h.tvDate.setText(format.format(new Date(e.timestamp)));

        String s = e.status == null ? "" : e.status.toLowerCase(Locale.ROOT);
        int color;
        if (s.contains("sent")) color = Color.parseColor("#D32F2F");        // alert went out
        else if (s.contains("cancel")) color = Color.parseColor("#2E7D32"); // user was fine
        else color = Color.parseColor("#1976D2");                           // test
        h.tvStatus.setTextColor(color);

        boolean hasMap = e.mapLink != null && !e.mapLink.isEmpty();
        h.tvMap.setVisibility(hasMap ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(v -> {
            if (!hasMap) return;
            try {
                v.getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(e.mapLink)));
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(v.getContext(), "No app available to open the map",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvType, tvStatus, tvDate, tvMap;

        VH(View v) {
            super(v);
            tvType = v.findViewById(R.id.tvType);
            tvStatus = v.findViewById(R.id.tvStatus);
            tvDate = v.findViewById(R.id.tvDate);
            tvMap = v.findViewById(R.id.tvMap);
        }
    }
}
