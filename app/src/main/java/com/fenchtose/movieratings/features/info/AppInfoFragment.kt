package com.fenchtose.movieratings.features.info

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import com.fenchtose.movieratings.BuildConfig
import com.fenchtose.movieratings.R
import com.fenchtose.movieratings.base.BaseFragment
import com.fenchtose.movieratings.base.RouterPath
import com.fenchtose.movieratings.util.AccessibilityUtils

class AppInfoFragment: BaseFragment() {

    private var isTV: Boolean = false
    private var testView: View? = null
    private var testContainer: View? = null
    private var settingsView: View? = null
    private var activationWarning: View? = null
    private var handler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTV = AccessibilityUtils.isTV(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(if (isTV) R.layout.info_page_layout_tv else R.layout.info_page_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        testContainer = view.findViewById(R.id.test_container)
        handler = Handler()

        testView = view.findViewById(R.id.test_view)
        testView?.let {
            it.setOnClickListener {
                handler?.postDelayed({
                    testContainer?.visibility = VISIBLE
                    handler?.postDelayed({
                        testContainer?.visibility = GONE
                    }, 3000)
                }, 30)
            }
        }

        settingsView = view.findViewById(R.id.settings_view)

        activationWarning = view.findViewById(R.id.activation_warning_view)

        view.findViewById<TextView>(R.id.version_view).text = BuildConfig.VERSION_NAME

        val contentView = view.findViewById<TextView?>(R.id.info_content_view)
        contentView?.visibility = View.VISIBLE
        contentView?.setText(
                if (AccessibilityUtils.hasAllPermissions(context))
                    R.string.info_screen_content_with_accessibility
                else
                    R.string.info_screen_content_no_accessibility)
    }

    override fun onResume() {
        super.onResume()
        if (isTV) {
            val hasAccessibility = AccessibilityUtils.isAccessibilityEnabled(context)
            testView?.visibility = if (hasAccessibility) VISIBLE else GONE
            settingsView?.visibility = if (hasAccessibility) VISIBLE else GONE
            activationWarning?.visibility = if (hasAccessibility) GONE else VISIBLE
        }
    }

    override fun canGoBack(): Boolean {
        return true
    }

    override fun getScreenTitle(): Int {
        return R.string.search_page_title
    }

    class AppInfoPath(private val showSearchOption: Boolean): RouterPath<AppInfoFragment>() {
        override fun createFragmentInstance(): AppInfoFragment {
            return AppInfoFragment()
        }

        override fun showMenuIcons(): IntArray {
            return if (showSearchOption) intArrayOf(R.id.action_search, R.id.action_settings) else intArrayOf(R.id.action_settings)
        }
    }
}