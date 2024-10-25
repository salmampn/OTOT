package com.example.otot

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Set status bar color to match the app bar color
        val appBarColor = Color.parseColor("#D70000")
        window.statusBarColor = appBarColor

        // Set status bar text visibility (e.g., light or dark)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false

        // Check if the user is logged in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // User is not logged in, redirect to AuthActivity
            val intent = Intent(this, SplashActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Check if need to navigate to GetStartedFragment
        val navigateTo = intent.getStringExtra("navigateTo")
        if (navigateTo == "getStarted") {
            navController.navigate(R.id.getStartedFragment)
        }

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.historyFragment, R.id.profileFragment)
        )
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)

        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        bottomNavigationView.setupWithNavController(navController)

        // Set listener for navigation item selection
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.navigate(R.id.homeFragment)
                    true
                }
                R.id.nav_track -> {
                    navController.navigate(R.id.runningActivity)
                    true
                }
                R.id.nav_history -> {
                    navController.navigate(R.id.historyFragment)
                    true
                }
                R.id.nav_profile -> {
                    navController.navigate(R.id.profileFragment)
                    true
                }
                else -> false
            }
        }

        // Add a destination change listener to update icons
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment -> updateBottomNavIcons(R.drawable.home, R.drawable.inactive_track, R.drawable.inactive_history, R.drawable.inactive_profile)
                R.id.runningActivity -> updateBottomNavIcons(R.drawable.inactive_home, R.drawable.track, R.drawable.inactive_history, R.drawable.inactive_profile)
                R.id.historyFragment -> updateBottomNavIcons(R.drawable.inactive_home, R.drawable.inactive_track, R.drawable.history, R.drawable.inactive_profile)
                R.id.profileFragment -> updateBottomNavIcons(R.drawable.inactive_home, R.drawable.inactive_track, R.drawable.inactive_history, R.drawable.profile)
            }
        }
    }

    private fun updateBottomNavIcons(homeIcon: Int, trackIcon: Int, historyIcon: Int, profileIcon: Int) {
        bottomNavigationView.menu.findItem(R.id.nav_home).setIcon(homeIcon)
        bottomNavigationView.menu.findItem(R.id.nav_track).setIcon(trackIcon)
        bottomNavigationView.menu.findItem(R.id.nav_history).setIcon(historyIcon)
        bottomNavigationView.menu.findItem(R.id.nav_profile).setIcon(profileIcon)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
