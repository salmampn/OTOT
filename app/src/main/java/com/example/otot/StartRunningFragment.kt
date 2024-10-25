package com.example.otot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.fragment.findNavController

class StartRunningFragment : Fragment() {
    private lateinit var btnStart: Button
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_start_running, container, false)
        btnStart = view.findViewById(R.id.btnStart)
        btnStart.setOnClickListener{
            findNavController().navigate(R.id.action_startRunningFragment_to_pauseRunningFragment)
        }
        return view
    }
}