package com.example.accidentdetection.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    LiveData<List<HistoryEntry>> getAll();

    @Insert
    void insert(HistoryEntry entry);

    @Query("DELETE FROM history")
    void deleteAll();
}
