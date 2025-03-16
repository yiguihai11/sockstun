/*
 ============================================================================
 Name        : ServerFragment.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Server Configuration Fragment
 ============================================================================
 */

package hev.sockstun.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import hev.sockstun.Preferences
import hev.sockstun.R

class ServerFragment : Fragment() {
    private lateinit var edittextSocksAddr: TextInputEditText
    private lateinit var edittextSocksPort: TextInputEditText
    private lateinit var edittextSocksUser: TextInputEditText
    private lateinit var edittextSocksPass: TextInputEditText
    private lateinit var prefs: Preferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_server, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = Preferences(requireContext())
        
        initViews(view)
        loadPreferences()
    }

    private fun initViews(view: View) {
        edittextSocksAddr = view.findViewById(R.id.socks_addr)
        edittextSocksPort = view.findViewById(R.id.socks_port)
        edittextSocksUser = view.findViewById(R.id.socks_user)
        edittextSocksPass = view.findViewById(R.id.socks_pass)
    }

    private fun loadPreferences() {
        edittextSocksAddr.setText(prefs.socksAddress)
        edittextSocksPort.setText(prefs.socksPort.toString())
        edittextSocksUser.setText(prefs.socksUsername)
        edittextSocksPass.setText(prefs.socksPassword)
    }

    fun savePreferences() {
        prefs.socksAddress = edittextSocksAddr.text.toString()
        prefs.socksPort = edittextSocksPort.text.toString().toIntOrNull() ?: 1080
        prefs.socksUsername = edittextSocksUser.text.toString()
        prefs.socksPassword = edittextSocksPass.text.toString()
    }
}
