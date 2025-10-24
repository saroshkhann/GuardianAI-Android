package com.example.guardianai;

import com.example.guardianai.RecommendationDao;
import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.util.Log;

// Add Recommendation.class to the entities list and increment version to 2
@Database(entities = {AppPermissions.class, Recommendation.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    // Abstract methods to get the DAOs for each table
    public abstract AppPermissionsDao appPermissionsDao();
    public abstract RecommendationDao recommendationDao(); // Added this line

    // Singleton pattern to prevent multiple instances of the database opening at the same time.
    private static volatile AppDatabase INSTANCE;

    // Method to get the singleton database instance
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            // Use synchronization to ensure only one thread creates the instance
            synchronized (AppDatabase.class) {
                // Double-check if instance is still null inside the synchronized block
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "guardianai_database") // Name of the database file
                            // --- Migration Strategy ---
                            // Since we changed the schema (added a table), we need a migration plan.
                            // fallbackToDestructiveMigration() deletes the old database and creates
                            // a new one. This is simple for development but ERASES ALL EXISTING DATA.
                            // For a production app, you'd implement a proper migration path.
                            .fallbackToDestructiveMigration()
                            .build();
                    Log.d("AppDatabase", "Database instance created.");
                }
            }
        }
        return INSTANCE;
    }
}