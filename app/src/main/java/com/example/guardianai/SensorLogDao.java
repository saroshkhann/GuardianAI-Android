package com.example.guardianai;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SensorLogDao {

    /** FR 3.3: Inserts a new sensor access event. */
    @Insert
    void insertLogEntry(SensorLogEntry logEntry);

    /** Retrieves all log entries, ordered by timestamp descending (newest first). */
    @Query("SELECT * FROM sensor_logs ORDER BY timestamp DESC")
    LiveData<List<SensorLogEntry>> getAllLogs();

    /** Retrieves logs for a specific sensor type. */
    @Query("SELECT * FROM sensor_logs WHERE sensor_type = :sensorType ORDER BY timestamp DESC")
    LiveData<List<SensorLogEntry>> getLogsBySensor(String sensorType);

    /** Retrieves the last N logs. */
    @Query("SELECT * FROM sensor_logs ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<SensorLogEntry>> getRecentLogs(int limit);

    /** Clears old logs (optional maintenance). */
    @Query("DELETE FROM sensor_logs WHERE timestamp < :timestampCutoff")
    int deleteOldLogs(long timestampCutoff);

    // Inside your SensorLogDao interface:

    /** Retrieves all logs *blocking* the current thread (safe for ExecutorService). */
    @Query("SELECT * FROM sensor_logs ORDER BY timestamp DESC")
    List<SensorLogEntry> getAllLogsBlocking();
}