package com.example.guardianai;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AppPermissionsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateAppPermissions(AppPermissions appPermissions);

    @Query("SELECT permissionsList FROM app_permissions WHERE packageName = :packageName")
    String getPermissionsForApp(String packageName); // Returns the comma-separated string

    @Query("SELECT * FROM app_permissions") // Get all stored apps (optional)
    List<AppPermissions> getAllAppPermissions();

    @Query("DELETE FROM app_permissions WHERE packageName = :packageName")
    void deleteAppPermissions(String packageName); // Needed for app uninstall later
}