package com.example.accidentdetection.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "history")
public class HistoryEntry {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public long timestamp;

    public String type;
    public String status;
    public String mapLink;
}
