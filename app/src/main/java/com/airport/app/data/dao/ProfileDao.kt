package com.airport.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.airport.app.data.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE subscriptionId = :subscriptionId ORDER BY id")
    fun observeBySubscription(subscriptionId: Long): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles WHERE subscriptionId = :subscriptionId")
    suspend fun countBySubscription(subscriptionId: Long): Int

    /** 各订阅下的节点数（GROUP BY） */
    @Query("SELECT subscriptionId, COUNT(*) AS cnt FROM profiles GROUP BY subscriptionId")
    fun observeCounts(): Flow<List<SubscriptionCount>>

    @Insert
    suspend fun insertAll(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: Long)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}

/** 订阅下的节点数量统计（供订阅列表展示） */
data class SubscriptionCount(
    val subscriptionId: Long,
    val cnt: Int,
)
