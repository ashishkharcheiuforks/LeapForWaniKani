package com.leapsoftware.leapforwanikani

import android.app.Activity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.leapsoftware.leapforwanikani.utils.PreferencesManager

class MainNavDrawerAdapter(
    private val navigationView: NavigationView,
    private val mainViewModel: MainViewModel
) {

    fun setOnItemSelectedListener(onNavigationItemSelectedListener: NavigationView.OnNavigationItemSelectedListener) {
        navigationView.setNavigationItemSelectedListener(onNavigationItemSelectedListener)
    }

    fun setLoginStatus(activity: Activity, apiKey: String) {
        if (apiKey.isEmpty()) {
            showLoginView(activity)
        } else {
            showLogoutView(activity)
        }
    }

    fun login(activity: Activity) {
        val builder = MaterialAlertDialogBuilder(activity)

        builder.setTitle(activity.getString(R.string.dialog_api_key_title))
        builder.setMessage(activity.getString(R.string.dialog_api_key_message))

        val editText = EditText(activity)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        editText.layoutParams = lp
        builder.setView(editText)

        builder.setPositiveButton(activity.getString(R.string.dialog_api_key_positive_button)) { dialog, whichButton ->
            val input = editText.text.toString()
            PreferencesManager.saveApiKey(activity, input)
            activity.invalidateOptionsMenu()
            mainViewModel.refreshData()
            mainViewModel.onLogin.postValue(Unit)
        }

        builder.show()
    }

    fun logout(activity: Activity) {
        PreferencesManager.deleteApiKey(activity)
        activity.invalidateOptionsMenu()
        mainViewModel.clearCache()
        mainViewModel.refreshData()
        mainViewModel.onLogout.postValue(Unit)
    }

    fun showLoginView(activity: Activity) {
        activity.runOnUiThread({
            navigationView.menu.findItem(R.id.nav_login).isVisible = true
            navigationView.menu.findItem(R.id.nav_logout).isVisible = false
        })
    }

    fun showLogoutView(activity: Activity) {
        activity.runOnUiThread({
            navigationView.menu.findItem(R.id.nav_login).isVisible = false
            navigationView.menu.findItem(R.id.nav_logout).isVisible = true
        })
    }

    fun bindUserName(userName: String) {
        val navHeader = navigationView.getHeaderView(0)
        navHeader.findViewById<TextView>(R.id.nav_header_title).text = userName
    }

    fun bindUserLevel(userLevel: String) {
        val navHeader = navigationView.getHeaderView(0)
        navHeader.findViewById<TextView>(R.id.nav_header_subtitle).text = userLevel
    }

    fun bindVersionName(versionName: String) {
        navigationView.menu.findItem(R.id.nav_version_name).title =
            String.format("v%s", versionName)
    }

}