/*
 ============================================================================
 Name        : DnsFragment.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : DNS Configuration Fragment
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

class DnsFragment : Fragment() {
    private lateinit var edittextDnsIpv4: TextInputEditText
    private lateinit var edittextDnsIpv6: TextInputEditText
    private lateinit var prefs: Preferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dns, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = Preferences(requireContext())
        
        initViews(view)
        loadPreferences()
    }

    private fun initViews(view: View) {
        edittextDnsIpv4 = view.findViewById(R.id.dns_ipv4)
        edittextDnsIpv6 = view.findViewById(R.id.dns_ipv6)
    }

    private fun loadPreferences() {
        edittextDnsIpv4.setText(prefs.dnsIpv4)
        edittextDnsIpv6.setText(prefs.dnsIpv6)
    }

    fun savePreferences() {
        prefs.dnsIpv4 = edittextDnsIpv4.text.toString()
        prefs.dnsIpv6 = edittextDnsIpv6.text.toString()
    }
}
