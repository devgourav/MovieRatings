package com.fenchtose.movieratings

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.fenchtose.movieratings.model.api.provider.MovieProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import com.fenchtose.movieratings.analytics.AnalyticsDispatcher
import com.fenchtose.movieratings.analytics.events.Event
import com.fenchtose.movieratings.display.RatingDisplayer
import com.fenchtose.movieratings.features.tts.Speaker
import com.fenchtose.movieratings.model.Movie
import com.fenchtose.movieratings.model.preferences.SettingsPreferences
import com.fenchtose.movieratings.model.preferences.UserPreferences
import com.fenchtose.movieratings.util.Constants
import com.fenchtose.movieratings.util.FixTitleUtils
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit


class NetflixReaderService : AccessibilityService() {

    private var title: String? = null
    private val TAG: String = "NetflixReaderService"

    private var provider: MovieProvider? = null

    private var preferences: UserPreferences? = null

    // For Samsung S6 edge, we are getting TYPE_WINDOW_STATE_CHANGED for adding floating window which triggers removeView()
    private val supportedPackages: Array<String> = arrayOf(Constants.PACKAGE_NETFLIX, Constants.PACKAGE_PRIMEVIDEO, Constants.PACKAGE_PLAY_MOVIES_TV/*, BuildConfig.APPLICATION_ID*/)

    private var lastWindowStateChangeEventTime: Long = 0
    private val WINDOW_STATE_CHANGE_THRESHOLD = 2000

    private var analytics: AnalyticsDispatcher? = null

    private var displayer: RatingDisplayer? = null
    private var speaker: Speaker? = null

    private val RESOURCE_THRESHOLD = 300L

    private var resourceRemover: PublishSubject<Boolean>? = null

    override fun onCreate() {
        super.onCreate()

        preferences = SettingsPreferences(this)
        provider = MovieRatingsApplication.movieProviderModule.movieProvider
        analytics = MovieRatingsApplication.analyticsDispatcher
        displayer = RatingDisplayer(this, analytics!!, preferences!!)
    }

    private fun initResources() {
        synchronized(this) {

            if (speaker == null && preferences?.isSettingEnabled(UserPreferences.USE_TTS) == true
                    && preferences?.isSettingEnabled(UserPreferences.TTS_AVAILABLE) == true) {
                speaker = Speaker(this)
            }

            if (resourceRemover == null) {
                resourceRemover = PublishSubject.create()
                resourceRemover
                        ?.debounce(RESOURCE_THRESHOLD, TimeUnit.SECONDS)
                        ?.subscribe({
                            clearResources()
                        })
            }

            resourceRemover?.onNext(true)
        }
    }

    private fun clearResources() {
        synchronized(this) {
            speaker?.shutdown()
            speaker = null
            resourceRemover?.onComplete()
            resourceRemover = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "eventt: " + AccessibilityEvent.eventTypeToString(event.eventType) + ", " + event.packageName + ", " + event.action + " ${event.text}, ${event.className}\n$event")
        }

        if (!supportedPackages.contains(event.packageName)) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && displayer != null && displayer!!.isShowingView
                    && event.packageName != BuildConfig.APPLICATION_ID) {
                if (System.currentTimeMillis() - lastWindowStateChangeEventTime > WINDOW_STATE_CHANGE_THRESHOLD) {
                    // User has moved to some other app
                    displayer?.removeView()
                    title = null
                }
            }

            return
        }


        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            displayer?.removeView()
            lastWindowStateChangeEventTime = System.currentTimeMillis()
            title = null
        }

        val info = event.source
        info?.let {

            val isAppEnabled = when (it.packageName) {
                BuildConfig.APPLICATION_ID -> true
                Constants.PACKAGE_NETFLIX -> preferences?.isAppEnabled(UserPreferences.NETFLIX)
                Constants.PACKAGE_PRIMEVIDEO -> preferences?.isAppEnabled(UserPreferences.PRIMEVIDEO)
                Constants.PACKAGE_PLAY_MOVIES_TV -> preferences?.isAppEnabled(UserPreferences.PLAY_MOVIES)
                else -> false
            }

            if (isAppEnabled == null || !isAppEnabled) {
                return@let
            }

            val titles: List<CharSequence> = when(it.packageName) {
                BuildConfig.APPLICATION_ID -> it.findAccessibilityNodeInfosByViewId(BuildConfig.APPLICATION_ID + ":id/flutter_test_title").filter { it.text != null }.map { it.text }
                Constants.PACKAGE_NETFLIX -> it.findAccessibilityNodeInfosByViewId(Constants.PACKAGE_NETFLIX + ":id/video_details_title").filter { it.text != null }.map { it.text }
                Constants.PACKAGE_PRIMEVIDEO -> it.findAccessibilityNodeInfosByViewId(Constants.PACKAGE_PRIMEVIDEO + ":id/TitleText").filter { it.text != null }.map { it.text }
                Constants.PACKAGE_PLAY_MOVIES_TV ->  {
                    val nodes = ArrayList<CharSequence>()
                    if (event.className == "com.google.android.apps.play.movies.mobile.usecase.details.DetailsActivity" && event.text != null) {
                        val text = event.text.toString().replace("[", "").replace("]", "")
                        nodes.add(text)
                    }
                    nodes
                }
                else -> {
                    checkNodeRecursively(it, 0)
                    ArrayList()
                }
            }.distinctBy { it }

            val years: List<CharSequence> = when(it.packageName) {
                Constants.PACKAGE_NETFLIX -> it.findAccessibilityNodeInfosByViewId(Constants.PACKAGE_NETFLIX + ":id/video_details_basic_info").filter { it.text != null }.map { it.text }
                Constants.PACKAGE_PRIMEVIDEO -> {
                    it.findAccessibilityNodeInfosByViewId(Constants.PACKAGE_PRIMEVIDEO + ":id/ItemMetadataView")
                            // get children of that node
                            .flatMap {
                                val children = ArrayList<CharSequence>()
                                (0 until it.childCount).map {
                                    i -> it.getChild(i).text
                                }.filter {
                                    it != null
                                }
                                .toCollection(children)
                                children
                            }
                            // filter node which has text containing 4 digits
                            .filter {
                                !FixTitleUtils.fixPrimeVideoYear(it.toString()).isNullOrEmpty()
                            }
                }
                Constants.PACKAGE_PLAY_MOVIES_TV ->  {
                    val nodes = ArrayList<CharSequence>()
                    it.findAccessibilityNodeInfosByViewId(Constants.PACKAGE_PLAY_MOVIES_TV + ":id/play_header_listview")
                            .takeIf { it.size > 0 }
                            ?.first()
                            ?.run {
                                (0 until childCount).map {
                                    i -> getChild(i)
                                }.filter {
                                    it != null && it.text != null && it.className.contains("TextView") && FixTitleUtils.matchesPlayMoviesYear(it.text.toString())
                                }.map { it.text }
                                 .toCollection(nodes)
                            }

                    nodes
                }

                else -> ArrayList()
            }.distinctBy { it }

            if (titles.isNotEmpty()) {
                titles.first { it != null }
                        .let {
                            setMovieTitle(
                                    fixTitle(info.packageName, it.toString()),
                                    years.takeIf { it.isNotEmpty() }?.first()?.let {
                                        fixYear(info.packageName, it.toString())
                                    }
                            )
                        }

            }
        }

//        event.recycle()
    }

    @Suppress("unused")
    private fun checkNodeRecursively(info: AccessibilityNodeInfo?, level: Int) {
        if (!BuildConfig.DEBUG) {
            return
        }

        info?.let {

            Log.d(TAG, "${" ".repeat(level)}info: text: ${it.text}, id: ${it.viewIdResourceName}, class: ${it.className}, parent: ${it.parent?.viewIdResourceName}")
            if (info.childCount > 0) {
                Log.d(TAG, "${" ".repeat(level)}--- <children> ---")
                (0 until info.childCount)
                        .forEach { index ->
                            checkNodeRecursively(it.getChild(index), level + 1)
                        }

                Log.d(TAG, "${" ".repeat(level)}--- </children> ---")
            }
        }
    }

    override fun onInterrupt() {
    }

    private fun setMovieTitle(text: String, year: String?) {

        // When the third condition is added, it could work better but could also be annoying because
        // event when the user scrolls, this would be triggered. This is just for Netflix because they
        // changed activity based navigation.

        if (title == null || title != text /*|| displayer?.isShowingView == false*/) {
            title = text
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "Movie :- title: $text, year: $year")
            }

            displayer?.removeView()

            if (preferences?.isAppEnabled(UserPreferences.USE_YEAR) == true) {
                getMovieInfo(text, year ?: "")
            } else {
                getMovieInfo(text, "")
            }
        }
    }

    private fun fixTitle(packageName: CharSequence, text: String): String {
        return when(packageName) {
            Constants.PACKAGE_PRIMEVIDEO -> FixTitleUtils.fixPrimeVideoTitle(text)
            else -> text
        }
    }

    private fun fixYear(packageName: CharSequence, text: String?): String {
        text?.let {
            val fixed = when(packageName) {
                Constants.PACKAGE_NETFLIX -> FixTitleUtils.fixNetflixYear(it)
                Constants.PACKAGE_PRIMEVIDEO -> FixTitleUtils.fixPrimeVideoYear(it)
                Constants.PACKAGE_PLAY_MOVIES_TV -> FixTitleUtils.fixPlayMoviesYear(it)
                else -> ""
            }

            fixed?.let {
                return it
            }
        }

        return ""
    }

    private fun getMovieInfo(title: String, year: String) {
        initResources()

        analytics?.sendEvent(Event("get_movie"))

        provider?.let {
            it.getMovie(title, year)
                    .debounce(30, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .filter { it.ratings.size > 0 }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(onNext = {
                        showRating(it)
                    }, onError = {
                        it.printStackTrace()
                    })
        }
    }

    private fun showRating(movie: Movie) {
        displayer?.showRatingWindow(movie)
        if (preferences?.isSettingEnabled(UserPreferences.TTS_AVAILABLE) == true && preferences?.isSettingEnabled(UserPreferences.USE_TTS) == true) {
            speaker?.talk(movie)
        }
    }
}
