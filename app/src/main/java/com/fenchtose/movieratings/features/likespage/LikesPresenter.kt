package com.fenchtose.movieratings.features.likespage

import com.fenchtose.movieratings.features.baselistpage.BaseMovieListPresenter
import com.fenchtose.movieratings.model.Movie
import com.fenchtose.movieratings.model.Sort
import com.fenchtose.movieratings.model.api.provider.FavoriteMovieProvider
import com.fenchtose.movieratings.model.db.like.LikeStore
import com.fenchtose.movieratings.model.preferences.UserPreferences
import io.reactivex.Observable

class LikesPresenter(private val provider: FavoriteMovieProvider, private val likeStore: LikeStore,
                     private val userPreferences: UserPreferences) : BaseMovieListPresenter<LikesPage>(likeStore = likeStore) {

    private var currentSort: Sort = userPreferences.getLatestLikeSort()
        set(value) {
        field = value
        userPreferences.setLatestLikeSort(value)
    }

    override fun load(): Observable<List<Movie>> {
        return provider.getMovies()
                .map {
                    getSorted(currentSort, it)
                }
    }

    override fun toggleLike(movie: Movie) {
        data?.let {
            val index = it.indexOf(movie)
            if (index >= 0) {
                it.removeAt(index)
                likeStore.setLiked(movie.imdbId, false)
                getView()?.showRemoved(movie, index)
            }
        }
    }

    fun undoUnlike(movie: Movie, index: Int) {
        likeStore.setLiked(movie.imdbId, true)
        data?.let {
            val addedIndex = when {
                (index >= 0 && index < it.size) -> {
                    it.add(index, movie)
                    index
                }
                else -> {
                    it.add(movie)
                    it.size - 1
                }
            }
            getView()?.showAdded(movie, addedIndex)
        }
    }

    fun sort(type: Sort) {

        if (type == currentSort) {
            return
        }

        data?.let {
            updateData(ArrayList(getSorted(type, it)))
            currentSort = type
        }
    }

    private fun getSorted(type: Sort, data: ArrayList<Movie>): List<Movie> = when(type) {
        Sort.YEAR -> data.sortedWith(compareByDescending { it.year })
        Sort.ALPHABETICAL -> data.sortedBy { it.title }
        else -> data
    }
}