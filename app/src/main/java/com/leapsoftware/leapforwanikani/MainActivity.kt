package com.leapsoftware.leapforwanikani

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.navigation.NavigationView
import com.leapsoftware.leapforwanikani.dashboard.WebDelegate
import com.leapsoftware.leapforwanikani.data.source.remote.api.WKData
import com.leapsoftware.leapforwanikani.workers.SummaryNotifyWorker
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.snackbar.Snackbar
import com.leapsoftware.leapforwanikani.data.LeapResult
import com.leapsoftware.leapforwanikani.data.source.WaniKaniRepository
import com.leapsoftware.leapforwanikani.data.source.remote.api.WKReport
import com.leapsoftware.leapforwanikani.utils.LeapNotificationManager
import com.leapsoftware.leapforwanikani.utils.PreferencesManager
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val TAG by lazy { MainActivity::class.java.simpleName }

    private lateinit var mainViewModel: MainViewModel
    private lateinit var mainNavDrawerAdapter: MainNavDrawerAdapter

    companion object {
        const val EXTRAS_REQUEST_CODE = "leap_notification_action_request_code"
        const val REQUEST_CODE_LESSONS = 1
        const val REQUEST_CODE_REVIEWS = 2
        const val REQUEST_CODE_LESSONS_AND_REVIEWS = 3

        fun getActivityRequestCode(summaryData: WKData.SummaryData): Int {
            return if (summaryData.lessons.isNotEmpty() && summaryData.reviews.isNotEmpty()) {
                REQUEST_CODE_LESSONS_AND_REVIEWS
            } else if (summaryData.lessons.isNotEmpty()) {
                REQUEST_CODE_LESSONS
            } else if (summaryData.reviews.isNotEmpty()) {
                REQUEST_CODE_REVIEWS
            } else {
                -1
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        setActionBarDrawerToggle()
        setNavControllerFragment()
        LeapNotificationManager(this).createNotificationChannels()
        SummaryNotifyWorker.enqueueUniquePeriodicWork(this)

        val repository = WaniKaniRepository.getInstance(this)
        val factory = ViewModelFactory(repository)
        mainViewModel = ViewModelProviders.of(this, factory).get(MainViewModel::class.java)

        mainNavDrawerAdapter = MainNavDrawerAdapter(nav_view, mainViewModel)
        mainNavDrawerAdapter.setOnItemSelectedListener(this)

        // Respond to notification open actions
        val requestCode: Int = intent.getIntExtra(EXTRAS_REQUEST_CODE, -1)
        when (requestCode) {
            REQUEST_CODE_LESSONS -> WebDelegate.openLessons(this)
            REQUEST_CODE_REVIEWS -> WebDelegate.openReviews(this)
        }

        val offlineSnackbar = Snackbar.make(
            findViewById<CoordinatorLayout>(R.id.coordinator_layout),
            getString(R.string.offline_message),
            Snackbar.LENGTH_INDEFINITE
        )

        registerNetworkCallback(this, offlineSnackbar)

        subscribeToUi(mainNavDrawerAdapter, offlineSnackbar)
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.refreshData()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        mainNavDrawerAdapter.setLoginStatus(this, PreferencesManager.getApiKey(this))
        mainNavDrawerAdapter.bindVersionName(BuildConfig.VERSION_NAME)
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_login -> {
                mainNavDrawerAdapter.login(this)
            }
            R.id.nav_logout -> {
                mainNavDrawerAdapter.logout(this)
            }
            R.id.nav_clear_cache -> {
                mainViewModel.clearCache()
            }
            R.id.nav_get_key -> {
                WebDelegate.openApiKey(this)
            }
            R.id.nav_wk_community -> {
                WebDelegate.openWaniKaniForum(this)
            }
            R.id.nav_github -> {
                WebDelegate.openGitHub(this)
            }
            R.id.nav_wanikani_terms -> {
                WebDelegate.openTerms(this)
            }
            R.id.nav_lpwk_license -> {
                WebDelegate.openLicense(this)
            }
        }
        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun subscribeToUi(mainNavDrawerAdapter: MainNavDrawerAdapter, offlineSnackbar: Snackbar) {
        mainViewModel.liveDataUser.observe(this, Observer { user ->
            when (user) {
                is LeapResult.Success<WKReport.User> -> {
                    Log.d(TAG, "User updated")
                    mainNavDrawerAdapter.bindUserName(user.resultData.data.username)
                    mainNavDrawerAdapter.bindUserLevel(
                        getString(
                            R.string.nav_drawer_level,
                            user.resultData.data.level.toString()
                        )
                    )
                }
                is LeapResult.Error -> {
                    Log.e(TAG, "Error getting user")
                    // An error getting the user object from the backend doesn't necessarily
                    // mean that we want to delete the user's api key or log them out
                }
                is LeapResult.Offline -> {
                    Log.e(TAG, "No connection getting user")
                    offlineSnackbar.show()
                }
            }
        })

        mainViewModel.onClearCache.observe(this, Observer {
            mainViewModel.refreshData()
        })

        mainViewModel.onLogout.observe(this, Observer {
            mainNavDrawerAdapter.bindUserName("")
            mainNavDrawerAdapter.bindUserLevel("")
        })
    }

    private fun setActionBarDrawerToggle() {
        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
    }

    private fun setNavControllerFragment() {
        val navController: NavController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration.Builder(R.id.dashboard_dest).build()
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    private fun registerNetworkCallback(context: Context, offlineSnackbar: Snackbar) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d(TAG, "network available")
                    runOnUiThread({ offlineSnackbar.dismiss() })
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.e(TAG, "network unavailable")
                    runOnUiThread({ offlineSnackbar.show() })
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.e(TAG, "network lost")
                    runOnUiThread({ offlineSnackbar.show() })
                }
            })
        } else {
            // no-op. Users should swipe to refresh
        }
    }

}
