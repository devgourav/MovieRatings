package com.fenchtose.movieratings.model.db.recentlyBrowsed

import android.support.annotation.WorkerThread
import com.fenchtose.movieratings.model.RecentlyBrowsed
import com.google.gson.JsonArray
import io.reactivex.Observable

interface RecentlyBrowsedStore {
    fun update(data: RecentlyBrowsed)
    fun deleteAll(): Observable<Int>
    fun export(): Observable<JsonArray>
    @WorkerThread
    fun import(history: List<RecentlyBrowsed>): Int
}