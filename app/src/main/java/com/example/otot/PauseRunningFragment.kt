package com.example.otot

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.Manifest
import android.os.Handler
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.w3c.dom.Text

class PauseRunningFragment : Fragment() {
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var polylineOptions: PolylineOptions
    private val pathPoints = mutableListOf<LatLng>()
    private var tracking = true
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var btnPause: ImageButton
    private lateinit var btnFinish: Button
    private lateinit var tvTime: TextView
    private lateinit var tvDistance2: TextView
    private lateinit var tvAvgPace2: TextView
    private lateinit var runId: String
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private val runnable = object : Runnable {
        override fun run() {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            val time = String.format("%02d:%02d:%02d", hours, minutes, secs)
            tvTime.text = time

            val distance = calculateDistance()
            tvDistance2.text = String.format("%.2f", distance)
            val avgPace = calculateAveragePace()
            tvAvgPace2.text = String.format("%.2f", avgPace)
            if (tracking) {
                seconds++
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_pause_running, container, false)
        btnFinish = view.findViewById(R.id.btnFinish)
        btnPause = view.findViewById(R.id.btnPause)
        tvTime = view.findViewById(R.id.tvTime)
        tvDistance2 = view.findViewById(R.id.tvDistance2)
        tvAvgPace2 = view.findViewById(R.id.tvAvgPace2)
        mapView = view.findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { googleMap ->
            googleMap.uiSettings.isZoomControlsEnabled = false
            googleMap.uiSettings.setAllGesturesEnabled(false)
            startLocationUpdates(googleMap)
        }

        btnFinish.setOnClickListener {
            tracking = false
            runId = saveRunData()
            val bundle = Bundle().apply {
                putString(
                    "runId",
                    runId
                )
            }
            findNavController().navigate(
                R.id.action_pauseRunningFragment_to_postRunningFragment,
                bundle
            )
        }

        btnPause.setOnClickListener {
            togglePause()
        }
        return view
    }

    private fun startLocationUpdates(googleMap: GoogleMap) {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions if not granted
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (tracking) {
                    for (location in locationResult.locations) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        pathPoints.add(latLng)
                        googleMap.addPolyline(
                            PolylineOptions().addAll(pathPoints).color(Color.RED).width(5f)
                        )
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }
            }
        }, Looper.getMainLooper())
    }

    private fun togglePause() {
        if (tracking) {
            tracking = false
            btnPause.setImageResource(R.drawable.play_button)
        } else {
            tracking = true
            btnPause.setImageResource(R.drawable.pause_button)
            handler.post(runnable)
        }
    }

    private fun saveRunData(): String {
        val duration = tvTime.text
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return ""
        val runId = FirebaseFirestore.getInstance().collection("runs").document().id
        val timestamp = com.google.firebase.Timestamp.now()

        val runData = mapOf(
            "userId" to userId,
            "duration" to duration,
            "pathPoints" to pathPoints.map {
                mapOf("lat" to it.latitude, "lng" to it.longitude)
            },
            "distance" to calculateDistance(),
            "avgPace" to calculateAveragePace(),
            "timestamp" to timestamp,
            "calories" to calculateCalories()
        )
        FirebaseFirestore.getInstance().collection("runs").document(runId).set(runData)
        return runId
    }


    private fun calculateDistance(): Double {
        var distance = 0.0
        for (i in 0 until pathPoints.size - 1) {
            val startPoint = pathPoints[i]
            val endPoint = pathPoints[i + 1]
            val result = FloatArray(1)
            android.location.Location.distanceBetween(
                startPoint.latitude,
                startPoint.longitude,
                endPoint.latitude,
                endPoint.longitude,
                result
            )
            distance += result[0]
        }
        return (distance / 1000.0) // Convert to kilometers
    }
    private fun calculateCalories(): Double {
        val distance = calculateDistance()
        return distance * 1.036
    }

    private fun calculateAveragePace(): Double {
        val distance = calculateDistance() // in kilometers
        if (seconds == 0) return 0.0
        val pace = distance / (seconds / 60.0) // kilometer per minutes
        return pace
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        handler.post(runnable)
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        handler.removeCallbacks(runnable)
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