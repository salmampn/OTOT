package com.example.otot

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.provider.MediaStore
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

class PauseRunningFragment : Fragment() {
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var polylineOptions: PolylineOptions
    private val pathPoints = mutableListOf<LatLng>()
    private var tracking = true
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var btnPause: ImageButton
    private lateinit var btnFinish: Button
    private lateinit var btnCamera: ImageButton
    private lateinit var tvTime: TextView
    private lateinit var tvDistance2: TextView
    private lateinit var tvAvgPace2: TextView
    private lateinit var tvCalories2: TextView
    private lateinit var runId: String
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private var trackingService: TrackingService? = null
    private var bound = false

    private val runnable = object : Runnable {
        override fun run() {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
//            val time = String.format("%02d:%02d:%02d", hours, minutes, secs)
//            tvTime.text = time

            val distance = calculateDistance()
            tvDistance2.text = String.format("%.2f", distance)
            val avgPace = calculateAveragePace()
            tvAvgPace2.text = String.format("%.2f", avgPace)
            val calories = calculateCalories()
            tvCalories2.text = String.format("%.2f", calories)
            if (tracking) {
                seconds++
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TrackingService.LocalBinder
            trackingService = binder.getService()
            bound = true
            handler.post(timerRunnable)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            trackingService?.let {
                val seconds = it.getSeconds()
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60
                val time = String.format("%02d:%02d:%02d", hours, minutes, secs)
                tvTime.text = time
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val serviceIntent = Intent(requireContext(), TrackingService::class.java)
        requireContext().startService(serviceIntent)
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_pause_running, container, false)
        btnFinish = view.findViewById(R.id.btnFinish)
        btnPause = view.findViewById(R.id.btnPause)
        btnCamera = view.findViewById(R.id.btnCamera)
        tvTime = view.findViewById(R.id.tvTime)
        tvDistance2 = view.findViewById(R.id.tvDistance2)
        tvAvgPace2 = view.findViewById(R.id.tvAvgPace2)
        tvCalories2 = view.findViewById(R.id.tvCalories2)
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
                putString("runId", runId)
            }
            trackingService?.stopTimer()
            findNavController().navigate(
                R.id.action_pauseRunningFragment_to_postRunningFragment,
                bundle
            )
        }

        btnPause.setOnClickListener {
            togglePause()
        }

        btnCamera.setOnClickListener {
            openCamera()
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
            trackingService?.pauseTimer()
        } else {
            tracking = true
            btnPause.setImageResource(R.drawable.pause_button)
            trackingService?.startTimer()
            handler.post(runnable) // Restart the timer when resuming
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
            "calories" to calculateCalories(),
            "timestamp" to timestamp
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
        val caloriesPerKm = 65
        return distance * caloriesPerKm
    }

    private fun calculateAveragePace(): Double {
        val distance = calculateDistance() // in kilometers
        if (seconds == 0) return 0.0
        val pace = (seconds / 60.0) / distance // minute per kilometer
        return pace
    }

    private fun openCamera() {
        // Check for camera permission
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Launch the camera
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (cameraIntent.resolveActivity(requireActivity().packageManager) != null) {
                startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == -1) { // -1 is RESULT_OK
            // Handle the image captured from the camera
            val imageUri: Uri? = data?.data
            // You can now use the imageUri (e.g., display it in an ImageView)
        }
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
        val serviceIntent = Intent(requireContext(), TrackingService::class.java)
        requireContext().stopService(serviceIntent)
        requireContext().unbindService(serviceConnection)
        trackingService = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    companion object {
        private const val CAMERA_REQUEST_CODE = 1002
    }
}