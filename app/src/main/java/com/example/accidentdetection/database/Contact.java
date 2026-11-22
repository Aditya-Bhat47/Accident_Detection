package com.example.accidentdetection.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "contacts")
public class Contact {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String phone;
    public String relation;
    public boolean isPrimary;
}
