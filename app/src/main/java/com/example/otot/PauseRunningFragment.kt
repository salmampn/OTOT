package com.example.otot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.navigation.fragment.findNavController

class PauseRunningFragment : Fragment() {
    private lateinit var btnFinish : Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_pause_running, container, false)
        btnFinish = view.findViewById(R.id.btnFinish)
        btnFinish.setOnClickListener{
            findNavController().navigate(R.id.action_pauseRunningFragment_to_postRunningFragment)
        }
        return view
    }
}