package com.example.guardianai;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SensorLogAdapter extends RecyclerView.Adapter<SensorLogAdapter.LogViewHolder> {

    private List<SensorLogEntry> logList;
    private Context context;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d", Locale.getDefault());

    public SensorLogAdapter(List<SensorLogEntry> logList) {
        this.logList = logList;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_sensor_log, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        SensorLogEntry entry = logList.get(position);

        // 1. App and Sensor Text
        holder.appName.setText(entry.getAppName());
        holder.sensorType.setText("Accessed " + entry.getSensorType() + (entry.isAlert() ? " (ALERT!)" : ""));

        // 2. Time Formatting
        Date logDate = new Date(entry.getTimestamp());
        String timeString = timeFormat.format(logDate);
        String dateString = dateFormat.format(logDate);

        // Show today, yesterday, or date
        if (android.text.format.DateUtils.isToday(entry.getTimestamp())) {
            holder.logTime.setText(timeString);
        } else if (android.text.format.DateUtils.isToday(entry.getTimestamp() + 86400000)) { // Check if it was yesterday
            holder.logTime.setText("Yesterday, " + timeString);
        } else {
            holder.logTime.setText(dateString + ", " + timeString);
        }

        // 3. Icon and Color Styling
        int iconRes;
        int colorRes;
        switch (entry.getSensorType()) {
            case "CAMERA":
                iconRes = R.drawable.ic_camera;
                colorRes = R.color.high_risk_color;
                break;
            case "MICROPHONE":
                iconRes = R.drawable.ic_microphone;
                colorRes = R.color.medium_risk_color;
                break;
            case "LOCATION":
                iconRes = R.drawable.ic_location;
                colorRes = R.color.primary_blue;
                break;
            case "CLIPBOARD":
                iconRes = R.drawable.ic_clipboard;
                colorRes = R.color.default_recommendation_color;
                break;
            default:
                iconRes = R.drawable.ic_lightbulb;
                colorRes = R.color.black;
        }

        if (entry.isAlert()) {
            colorRes = R.color.high_risk_color; // Highlight if it was an alert
        }

        holder.sensorIcon.setImageResource(iconRes);
        holder.sensorIcon.setColorFilter(ContextCompat.getColor(context, colorRes));

        // Background color if it's an alert
        if (entry.isAlert()) {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.high_risk_color_faded));
            // NOTE: You would need to define a faded color in your colors.xml!
        } else {
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.white));
        }
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    public void updateLogs(List<SensorLogEntry> newLogs) {
        this.logList = newLogs;
        notifyDataSetChanged();
    }

    public static class LogViewHolder extends RecyclerView.ViewHolder {
        ImageView sensorIcon;
        TextView appName;
        TextView sensorType;
        TextView logTime;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            sensorIcon = itemView.findViewById(R.id.icon_sensor);
            appName = itemView.findViewById(R.id.tv_log_app_name);
            sensorType = itemView.findViewById(R.id.tv_log_sensor_type);
            logTime = itemView.findViewById(R.id.tv_log_time);
        }
    }
}