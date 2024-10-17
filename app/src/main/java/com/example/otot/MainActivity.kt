package com.example.otot

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // User is not logged in, redirect to AuthActivity
            val intent = Intent(this, SplashActivity::class.java)
            startActivity(intent)
            finish() // Close MainActivity so that the user cannot go back to it
            return
        }
        setContentView(R.layout.activity_main)
    }
}
