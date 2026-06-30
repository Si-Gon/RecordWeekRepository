package com.recordweek.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface WeeklyAnalysisDao {
    @Insert
    void insert(WeeklyAnalysis analysis);
    @Query("SELECT * FROM weekly_analysis WHERE year = :year ORDER BY week_number ASC")
    List<WeeklyAnalysis> getByYear(int year);
    @Query("SELECT * FROM weekly_analysis WHERE task_id = :taskId ORDER BY year ASC, week_number ASC")
    List<WeeklyAnalysis> getByTask(int taskId);
    @Query("SELECT * FROM weekly_analysis WHERE task_id = :taskId ORDER BY year DESC, week_number DESC")
    List<WeeklyAnalysis> getByTaskDescending(int taskId);
    @Query("SELECT * FROM weekly_analysis WHERE year = :year ORDER BY task_id ASC, week_number ASC")
    List<WeeklyAnalysis> getByYearForAnalysis(int year);
    @Query("SELECT COUNT(*) FROM weekly_analysis WHERE task_id = :taskId AND week_number = :weekNumber AND year = :year")
    int existsEntry(int taskId, int weekNumber, int year);
}
