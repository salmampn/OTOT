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
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Handler
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.findNavController
import com.example.otot.model.PathPoint
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
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

class PauseRunningFragment : Fragment() {
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var polylineOptions: PolylineOptions
    private val pathPoints = mutableListOf<PathPoint>()
    private var tracking = true
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var btnPause: ImageButton
    private lateinit var btnFinish: Button
    private lateinit var btnCamera: ImageButton
    private lateinit var tvTime: TextView
    private lateinit var tvDistance2: TextView
    private lateinit var tvAvgPace2: TextView
    private lateinit var tvCalories2: TextView
    private var runId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private var trackingService: TrackingService? = null
    private var bound = false
//    private var startMarker: Marker? = null
//    private var endMarker: Marker? = null

    private val runnable = object : Runnable {
        override fun run() {
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
            googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(0.0, 0.0),
                    1f
                )
            ) // Default position
            startLocationUpdates(googleMap)
        }

        btnFinish.setOnClickListener {
            tracking = false
            if (runId == null) {
                runId = saveRunData() // Save run data and get runId
            }
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
            animateButton(btnCamera)
            showImageSelectionDialog()
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
                        // Create a PathPoint object and add it to the list
                        val pathPoint = PathPoint(lat = location.latitude, lng = location.longitude, imageUrl = null)
                        pathPoints.add(pathPoint)
                        googleMap.addPolyline(
                            PolylineOptions().addAll(pathPoints.map { LatLng(it.lat, it.lng) }).color(Color.RED).width(5f)
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
        val runId = this.runId ?: FirebaseFirestore.getInstance().collection("history").document().id // Use existing runId

        val timestamp = com.google.firebase.Timestamp.now()

        val runData = mapOf(
            "userId" to userId,
            "duration" to duration,
            "pathPoints" to pathPoints.map {
                mapOf("lat" to it.lat, "lng" to it.lng, "imageUrl" to it.imageUrl) // Include imageUrl
            },
            "distance" to calculateDistance(),
            "avgPace" to calculateAveragePace(),
            "calories" to calculateCalories(),
            "timestamp" to timestamp
        )

        // Save the run data to Firestore
        FirebaseFirestore.getInstance().collection("history").document(runId).set(runData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Run data saved successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Failed to save run data: ${e.message}")
                Toast.makeText(requireContext(), "Failed to save run data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        return runId
    }

    private fun calculateDistance(): Double {
        var distance = 0.0
        for (i in 0 until pathPoints.size - 1) {
            val startPoint = pathPoints[i]
            val endPoint = pathPoints[i + 1]
            val result = FloatArray(1)
            android.location.Location.distanceBetween(
                startPoint.lat,
                startPoint.lng,
                endPoint.lat,
                endPoint.lng,
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

    private fun showImageSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_selection, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val takePictureButton: Button = dialogView.findViewById(R.id.take_picture_button)
        val chooseGalleryButton: Button = dialogView.findViewById(R.id.choose_gallery_button)

        takePictureButton.setOnClickListener {
            openCamera()
            dialog.dismiss()
        }

        chooseGalleryButton.setOnClickListener {
            openGallery()
            dialog.dismiss()
        }

        dialog.show()
        dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
        } else {
            Toast.makeText(requireContext(), "Camera not available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

            when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    val imageBitmap = data?.extras?.get("data") as? Bitmap
                    imageBitmap?.let { showConfirmationDialog(it, userId) }
                }
                GALLERY_REQUEST_CODE -> {
                    val selectedImageUri = data?.data
                    selectedImageUri?.let { showConfirmationDialog(it, userId) }
                }
            }
        }
    }

    private fun showConfirmationDialog(image: Bitmap, userId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirmation_upload, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val dialogImage: ImageView = dialogView.findViewById(R.id.dialog_image)
        val retakeButton: Button = dialogView.findViewById(R.id.retake_button)
        val uploadButton: Button = dialogView.findViewById(R.id.upload_button)

        dialogImage.setImageBitmap(image)

        retakeButton.setOnClickListener {
            dialog.dismiss()
            showImageSelectionDialog() // Show the selection dialog again
        }

        uploadButton.setOnClickListener {
            dialog.dismiss()
            uploadImageToFirebase(image, userId) // Upload the image to Firebase
        }

        dialog.show()
        dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showConfirmationDialog(imageUri: Uri, userId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirmation_upload, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val dialogImage: ImageView = dialogView.findViewById(R.id.dialog_image)
        val retakeButton: Button = dialogView.findViewById(R.id.retake_button)
        val uploadButton: Button = dialogView.findViewById(R.id.upload_button)

        dialogImage.setImageURI(imageUri)

        retakeButton.setOnClickListener {
            dialog.dismiss()
            showImageSelectionDialog() // Show the selection dialog again
        }

        uploadButton.setOnClickListener {
            dialog.dismiss()
            uploadImageToFirebase(imageUri, userId) // Upload the image to Firebase
        }

        dialog.show()
        dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun uploadImageToFirebase(image: Bitmap, userId: String) {
        // Use a timestamp to create a unique filename for each image
        val timestamp = System.currentTimeMillis()
        val storageRef = FirebaseStorage.getInstance().reference
            .child("images/$userId/$timestamp.jpg") // Use timestamp for uniqueness

        val baos = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos) // No compression
        val data = baos.toByteArray()

        val uploadTask = storageRef.putBytes(data)
        uploadTask.addOnSuccessListener {
            // Get the download URL after the upload is successful
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                // Update the imageUrl in the last PathPoint after successful upload
                if (pathPoints.isNotEmpty()) {
                    pathPoints.last().imageUrl = uri.toString() // Get the download URL
                }
                Toast.makeText(requireContext(), "Image uploaded successfully", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadImageToFirebase(imageUri: Uri, userId: String) {
        // Use a timestamp to create a unique filename for each image
        val timestamp = System.currentTimeMillis()
        val storageRef = FirebaseStorage.getInstance().reference
            .child("images/$userId/$timestamp.jpg") // Use timestamp for uniqueness

        val uploadTask = storageRef.putFile(imageUri)
        uploadTask.addOnSuccessListener {
            // Get the download URL after the upload is successful
            storageRef.downloadUrl.addOnSuccessListener { uri ->
                // Update the imageUrl in the last PathPoint after successful upload
                if (pathPoints.isNotEmpty()) {
                    pathPoints.last().imageUrl = uri.toString() // Get the download URL
                }
                Toast.makeText(requireContext(), "Image uploaded successfully", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun animateButton(button: View) {
        val scaleAnimation = ScaleAnimation(
            1f, 0.9f, // Start and end scale for X axis
            1f, 0.9f, // Start and end scale for Y axis
            Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point X
            Animation.RELATIVE_TO_SELF, 0.5f // Pivot point Y
        )
        scaleAnimation.duration = 100 // Duration of the animation
        scaleAnimation.fillAfter = true // Keep the end state of the animation
        scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                // Optional: Do something when the animation starts
            }

            override fun onAnimationEnd(animation: Animation?) {
                // Reset the button scale back to normal
                button.startAnimation(ScaleAnimation(
                    0.9f, 1f, // Start and end scale for X axis
                    0.9f, 1f, // Start and end scale for Y axis
                    Animation.RELATIVE_TO_SELF, 0.5f, // Pivot point X
                    Animation.RELATIVE_TO_SELF, 0.5f // Pivot point Y
                ).apply {
                    duration = 100
                })
            }

            override fun onAnimationRepeat(animation: Animation?) {
                // Optional: Do something when the animation repeats
            }
        })
        button.startAnimation(scaleAnimation)
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
        private const val GALLERY_REQUEST_CODE = 1003
    }
}