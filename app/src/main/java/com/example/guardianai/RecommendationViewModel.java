package com.example.guardianai;

import android.app.Application; // Import Application
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel; // Use AndroidViewModel for context
import androidx.lifecycle.LiveData;
import com.example.guardianai.AppDatabase; // Use correct package
import com.example.guardianai.Recommendation; // Use correct package
import com.example.guardianai.RecommendationDao; // Use correct package
import java.util.List;

public class RecommendationViewModel extends AndroidViewModel {

    private RecommendationDao recommendationDao;
    private LiveData<List<Recommendation>> allRecommendations; // The LiveData object

    public RecommendationViewModel(@NonNull Application application) {
        super(application);
        // Get database and DAO instance
        AppDatabase db = AppDatabase.getDatabase(application);
        recommendationDao = db.recommendationDao();
        // Initialize the LiveData using the DAO method
        allRecommendations = recommendationDao.getAllRecommendationsLiveData();
    }

    // Public method for the UI to observe the LiveData
    LiveData<List<Recommendation>> getAllRecommendations() {
        return allRecommendations;
    }

    // Optional: Add methods here to insert/delete if needed from ViewModel,
    // making sure to run them on a background thread.
    // Example (requires ExecutorService):
    /*
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    public void insert(Recommendation recommendation) {
        executorService.execute(() -> recommendationDao.insertRecommendation(recommendation));
    }
    */
}