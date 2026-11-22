package com.example.accidentdetection.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accidentdetection.R;
import com.example.accidentdetection.adapters.HistoryAdapter;
import com.example.accidentdetection.database.AppDatabase;
import com.example.accidentdetection.utils.BottomNavHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        AppDatabase db = AppDatabase.get(this);

        RecyclerView rv = findViewById(R.id.rvHistory);
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        TextView tvClear = findViewById(R.id.tvClear);
        BottomNavigationView nav = findViewById(R.id.bottomNav);

        HistoryAdapter adapter = new HistoryAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        db.historyDao().getAll().observe(this, list -> {
            adapter.setItems(list);
            boolean empty = list.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            tvClear.setVisibility(empty ? View.GONE : View.VISIBLE);
        });

        tvClear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("All logged events will be deleted.")
                .setPositiveButton("Clear", (d, w) ->
                        AppDatabase.executor.execute(() -> db.historyDao().deleteAll()))
                .setNegativeButton("Cancel", null)
                .show());

        BottomNavHelper.setup(this, nav, R.id.nav_history);
    }
}
