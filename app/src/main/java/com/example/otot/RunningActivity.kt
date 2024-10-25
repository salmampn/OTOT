package com.example.otot

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.MarkerOptions
import java.util.*

class RunningActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var map: GoogleMap
    private lateinit var btnStart: Button
    private lateinit var btnFinish: Button
    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvHeartRate: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var isRunning = false
    private var totalDistance = 0.0
    private var startTime: Long = 0
    private val routeCoordinates = ArrayList<LatLng>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_running)

        // Initialize UI elements
//        mapView = findViewById(R.id.mapView)
//        btnStart = findViewById(R.id.btnStart)
//        btnFinish = findViewById(R.id.btnFinish)
//        tvDistance = findViewById(R.id.tvDistance1)
//        tvTime = findViewById(R.id.tvTime)
//        tvHeartRate = findViewById(R.id.tvHeartRate1)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Start button click listener
        btnStart.setOnClickListener {
            if (!isRunning) {
                startRunning()
            }
        }

        // Finish button click listener
        btnFinish.setOnClickListener {
            if (isRunning) {
                stopRunning()
            }
        }
    }

    private fun startRunning() {
        isRunning = true
        startTime = System.currentTimeMillis()
        btnStart.visibility = View.GONE
        btnFinish.visibility = View.VISIBLE
        // Start tracking the runner's location and update the route on the map
        getLocationUpdates()
    }

    private fun stopRunning() {
        isRunning = false
        btnStart.visibility = View.VISIBLE
        btnFinish.visibility = View.GONE
        // Stop the location tracking and save data if needed
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        MapsInitializer.initialize(this)

        // Check location permissions and enable location tracking
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        } else {
            enableLocationTracking()
        }
    }

    private fun enableLocationTracking() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            map.isMyLocationEnabled = true

            // Get last known location and set it as the starting point
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    // Optionally, add a marker at the start
                    map.addMarker(MarkerOptions().position(currentLatLng).title("Start Point"))
                }
            }
        }
    }

    // Handle runtime permission results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableLocationTracking()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Continuously track location updates while running
    private fun getLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                // Capture the runner's current location and add to the polyline
                val currentLatLng = LatLng(location.latitude, location.longitude)
                routeCoordinates.add(currentLatLng)

                // Draw polyline on the map to show the running route
                val polylineOptions = PolylineOptions()
                    .addAll(routeCoordinates)
                    .color(Color.RED)
                    .width(5f)

                map.addPolyline(polylineOptions)

                // Move camera to current location
                map.moveCamera(CameraUpdateFactory.newLatLng(currentLatLng))

                // You can calculate distance between points and update `tvDistance`
                // This is where you can calculate totalDistance if needed
            }
        }
    }

    // Handle other lifecycle methods for mapView
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
