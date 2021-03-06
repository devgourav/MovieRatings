package com.fenchtose.movieratings.model.api.provider

import android.content.Context
import com.fenchtose.movieratings.model.Episode
import com.fenchtose.movieratings.model.Movie
import com.fenchtose.movieratings.model.SearchResult
import com.fenchtose.movieratings.model.db.UserPreferenceApplier
import com.fenchtose.movieratings.model.db.dao.MovieDao
import com.fenchtose.movieratings.model.EpisodesList
import io.reactivex.Observable


class PreloadedMovieProvider(context:Context, private val dao: MovieDao): MovieProvider {

    override fun getMovieWithImdb(imdb: String): Observable<Movie> {
        throw RuntimeException("PreloadedMovieProvider should not be used in release")
    }

    override fun getMovie(title: String, year: String): Observable<Movie> {
        throw RuntimeException("PreloadedMovieProvider should not be used in release")
    }

    override fun search(title: String, page:Int): Observable<SearchResult> {
        throw RuntimeException("PreloadedMovieProvider should not be used in release")
    }

    override fun addPreferenceApplier(applier: UserPreferenceApplier) {
        throw RuntimeException("PreloadedMovieProvider should not be used in release")
    }

    override fun getEpisodes(series: Movie, season: Int): Observable<EpisodesList> {
        throw RuntimeException("PreloadedMovieProvider should not be used in release")
    }

    override fun getEpisode(episode: Episode): Observable<Movie> {
        throw RuntimeException("PreloadedMovieProvider should not be used in release")
    }
}