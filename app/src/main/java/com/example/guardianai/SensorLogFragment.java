package com.example.guardianai;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.guardianai.SensorLogDao; // Assuming AppDatabase is updated
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SensorLogFragment extends Fragment {

    private RecyclerView logRecyclerView;
    private TextView emptyStateTextView;
    private SensorLogAdapter logAdapter;
    private SensorLogDao sensorLogDao;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sensor_log, container, false);

        logRecyclerView = view.findViewById(R.id.log_recycler_view);
        emptyStateTextView = view.findViewById(R.id.tv_empty_state);
        view.findViewById(R.id.btn_clear_logs).setOnClickListener(v -> confirmClearLogs());

        // Initialize DAO
        sensorLogDao = AppDatabase.getDatabase(getContext()).sensorLogDao();

        setupRecyclerView();
        observeLogs();

        return view;
    }

    private void setupRecyclerView() {
        logAdapter = new SensorLogAdapter(new ArrayList<>());
        logRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        logRecyclerView.setAdapter(logAdapter);
    }

    private void observeLogs() {
        // Observe the LiveData list from the database
        sensorLogDao.getAllLogs().observe(getViewLifecycleOwner(), logEntries -> {
            if (logEntries != null && !logEntries.isEmpty()) {
                logAdapter.updateLogs(logEntries);
                emptyStateTextView.setVisibility(View.GONE);
                logRecyclerView.setVisibility(View.VISIBLE);
            } else {
                logAdapter.updateLogs(new ArrayList<>()); // Clear adapter
                emptyStateTextView.setVisibility(View.VISIBLE);
                logRecyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void confirmClearLogs() {
        // Simple confirmation dialog
        new android.app.AlertDialog.Builder(getContext())
                .setTitle("Clear Logs")
                .setMessage("Are you sure you want to delete all sensor access history?")
                .setPositiveButton("Clear", (dialog, which) -> clearAllLogs())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAllLogs() {
        // Run database operation on a background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            // Delete logs older than 1 minute for simplicity in testing
            long oneMinuteAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
            int deletedCount = sensorLogDao.deleteOldLogs(Long.MAX_VALUE); // Deletes all if max value

            // Post result back to main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getContext(), deletedCount + " logs cleared.", Toast.LENGTH_SHORT).show();
            });
        });
    }
}