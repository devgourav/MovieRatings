package com.fenchtose.movieratings.model.db.like

import android.support.annotation.WorkerThread
import com.fenchtose.movieratings.MovieRatingsApplication
import com.fenchtose.movieratings.model.Fav
import com.fenchtose.movieratings.model.Movie
import com.fenchtose.movieratings.model.db.dao.FavDao
import com.google.gson.JsonArray
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers

class DbLikeStore private constructor(private val likeDao: FavDao) : LikeStore {

    companion object {
        private var instance: DbLikeStore? = null

        fun getInstance(likeDao: FavDao) :DbLikeStore {
            if (instance == null) {
                instance = DbLikeStore(likeDao)
            }

            return instance!!
        }
    }

    @WorkerThread
    override fun apply(movie: Movie) {
        movie.liked = isLiked(movie.imdbId)
        movie.appliedPreferences.liked = true
    }

    @WorkerThread
    override fun isLiked(imdbId: String): Boolean {
        val fav = likeDao.getFav(imdbId)
        fav?.let {
            return it.liked
        }

        return false
    }

    override fun setLiked(imdbId: String, liked: Boolean) {
        val fav = Fav()
        fav.id = imdbId
        fav.liked = liked
        Observable.just(fav)
                .subscribeOn(Schedulers.io())
                .subscribe {
                    likeDao.insert(it)
                }
    }

    override fun deleteAll(): Observable<Int> {
        return Observable.defer {
            Observable.just(likeDao.deleteAll())
        }
    }

    override fun export(): Observable<JsonArray> {
        val gson = MovieRatingsApplication.gson
        return Observable.defer {
            Observable.fromCallable { likeDao.exportData() }
        }.map {
            gson.toJsonTree(it).asJsonArray
        }
    }

    @WorkerThread
    override fun import(favs: List<Fav>): Int {
        return likeDao.importData(favs.filter { it.liked }).filter { it != -1L }.count()
    }
}