package com.example.otot

import android.os.Bundle
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Handler
import androidx.navigation.fragment.findNavController

class LoadingFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_loading, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set a delay of 3 seconds (3000 milliseconds) before moving to the next fragment
        Handler(Looper.getMainLooper()).postDelayed({
            // Navigate to the GetStartedFragment using the Navigation component
            findNavController().navigate(R.id.action_loadingFragment_to_getStartedFragment)
        }, 3000) // 3000 ms = 3 seconds
    }
}