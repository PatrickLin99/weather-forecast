package com.example.weatherforecast.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.weatherforecast.core.database.entity.CityEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CityDao {

    @Query("SELECT * FROM cities ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<CityEntity>>

    @Query("SELECT * FROM cities WHERE id = :cityId")
    suspend fun getById(cityId: String): CityEntity?

    @Upsert
    suspend fun upsert(city: CityEntity)

    @Query("DELETE FROM cities WHERE id = :cityId")
    suspend fun deleteById(cityId: String)

    @Query("SELECT * FROM cities WHERE isCurrentLocation = 1 LIMIT 1")
    suspend fun getCurrentLocationCity(): CityEntity?
}