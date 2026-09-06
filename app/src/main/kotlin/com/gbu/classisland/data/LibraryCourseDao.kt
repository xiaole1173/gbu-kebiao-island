// SPDX-License-Identifier: MIT
// Copyright (C) 2026 影 / Shadow / xiaole1173
package com.gbu.classisland.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryCourseDao {
    @Query("SELECT * FROM library_course")
    fun observeAll(): Flow<List<LibraryCourse>>

    @Query("SELECT * FROM library_course")
    suspend fun getAll(): List<LibraryCourse>

    @Query("DELETE FROM library_course")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LibraryCourse>)
}