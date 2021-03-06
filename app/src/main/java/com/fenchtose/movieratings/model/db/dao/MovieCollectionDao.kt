package com.fenchtose.movieratings.model.db.dao

import android.arch.persistence.room.*
import com.fenchtose.movieratings.model.Movie
import com.fenchtose.movieratings.model.MovieCollection
import com.fenchtose.movieratings.model.MovieCollectionEntry

@Dao
interface MovieCollectionDao {
    @Insert(onConflict = OnConflictStrategy.FAIL)
    fun insert(collection: MovieCollection): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entry: MovieCollectionEntry)

    @Query("SELECT * FROM COLLECTIONS")
    fun getMovieCollections(): List<MovieCollection>

    @Query("SELECT * FROM COLLECTIONS WHERE COLLECTION_NAME = :collectionName LIMIT 1")
    fun findCollectionByName(collectionName: String): MovieCollection?

    @Query("SELECT CASE WHEN EXISTS (" +
            " SELECT * FROM COLLECTION_ENTRIES WHERE COLLECTION_ID = :collectionId AND IMDBID = :imdbId LIMIT 1" +
            ")" +
            "THEN CAST(1 AS BIT)" +
            "ELSE CAST(0 AS BIT) END")
    fun isMovieAddedToCollection(collectionId: Long, imdbId: String): Boolean

    @Query("SELECT * FROM COLLECTIONS as c INNER JOIN COLLECTION_ENTRIES as ce ON c.COLLECTION_ID == ce.COLLECTION_ID WHERE ce.IMDBID == :imdbId")
    fun getCollectionsForMovie(imdbId: String): List<MovieCollection>

    @Query("SELECT * FROM MOVIES as m INNER JOIN COLLECTION_ENTRIES as ce ON m.IMDBID == ce.IMDBID WHERE ce.COLLECTION_ID == :collectionId")
    fun getMoviesForCollection(collectionId: Long): List<Movie>

    @Query("SELECT * FROM COLLECTION_ENTRIES WHERE COLLECTION_ID == :collectionId")
    fun getCollectionEntties(collectionId: Long): List<MovieCollectionEntry>

    @Query("DELETE FROM COLLECTION_ENTRIES WHERE COLLECTION_ID == :collectionId")
    fun deleteCollectionEntries(collectionId: Long): Int

    @Query("DELETE FROM COLLECTION_ENTRIES WHERE COLLECTION_ID == :collectionId AND IMDBID = :imdbId")
    fun deleteCollectionEntry(collectionId: Long, imdbId: String): Int

    @Delete
    fun delete(collection: MovieCollection): Int

    @Query("DELETE FROM COLLECTIONS")
    fun deleteAllCollections(): Int

    @Query("DELETE FROM COLLECTION_ENTRIES")
    fun deleteAllCollectionEntries(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun importEntries(entries: List<MovieCollectionEntry>): List<Long>

}