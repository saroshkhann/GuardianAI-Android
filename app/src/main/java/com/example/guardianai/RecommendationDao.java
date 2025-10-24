package com.example.guardianai; // Use correct package (without .db)

import androidx.lifecycle.LiveData; // Import LiveData
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

/**
 * Data Access Object for the Recommendation table.
 */
@Dao
public interface RecommendationDao {

    /**
     * Inserts a single recommendation. If a recommendation with the same primary key
     * already exists, it replaces the old one.
     * @param recommendation The recommendation object to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecommendation(Recommendation recommendation);

    /**
     * Selects all recommendations from the table, ordered by timestamp descending (newest first).
     * This returns a simple List and must be called off the main thread.
     * @return A list of all Recommendation objects.
     */
    @Query("SELECT * FROM recommendations ORDER BY timestamp DESC")
    List<Recommendation> getAllRecommendations();

    /**
     * Selects all recommendations from the table, ordered by timestamp descending (newest first).
     * This returns LiveData, which automatically updates the UI when the data changes.
     * Room handles running this query on a background thread.
     * @return LiveData containing a list of all Recommendation objects.
     */
    @Query("SELECT * FROM recommendations ORDER BY timestamp DESC")
    LiveData<List<Recommendation>> getAllRecommendationsLiveData();

    /**
     * Deletes all recommendations of a specific type.
     * Useful for clearing old recommendations before adding new ones (e.g., clearing old UNUSED_APP checks).
     * @param recommendationType The 'type' string to match for deletion.
     */
    @Query("DELETE FROM recommendations WHERE type = :recommendationType")
    void deleteRecommendationsByType(String recommendationType);

    /**
     * Deletes a specific recommendation using its unique ID.
     * @param id The primary key ID of the recommendation to delete.
     */
    @Query("DELETE FROM recommendations WHERE id = :id")
    void deleteRecommendationById(int id);

    /**
     * Deletes all recommendations associated with a specific package name.
     * Useful when an app is uninstalled.
     * @param packageName The package name to match for deletion.
     */
    @Query("DELETE FROM recommendations WHERE associatedPackageName = :packageName")
    void deleteRecommendationsByPackage(String packageName);
}