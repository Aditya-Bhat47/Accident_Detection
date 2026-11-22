package com.example.accidentdetection.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY isPrimary DESC, name ASC")
    LiveData<List<Contact>> getAll();

    @Query("SELECT * FROM contacts ORDER BY isPrimary DESC, name ASC")
    List<Contact> getAllNow();

    @Query("SELECT COUNT(*) FROM contacts")
    LiveData<Integer> count();

    @Insert
    long insert(Contact contact);

    @Update
    void update(Contact contact);

    @Delete
    void delete(Contact contact);

    @Query("UPDATE contacts SET isPrimary = 0")
    void clearPrimary();
}
