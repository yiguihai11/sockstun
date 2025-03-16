/*
 ============================================================================
 Name        : OptionsFragment.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Options Configuration Fragment
 ============================================================================
 */

package hev.sockstun.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import hev.sockstun.AppListActivity
import hev.sockstun.MainActivity
import hev.sockstun.Preferences
import hev.sockstun.R

class OptionsFragment : Fragment(), View.OnClickListener {
    private lateinit var checkboxUdpInTcp: MaterialSwitch
    private lateinit var checkboxIpv4: MaterialSwitch
    private lateinit var checkboxIpv6: MaterialSwitch
    private lateinit var checkboxExclude: MaterialSwitch
    private lateinit var edittextExcludeRoutes: TextInputEditText
    private lateinit var excludeRoutesLayout: TextInputLayout
    private lateinit var radioGroupAppFilter: RadioGroup
    private lateinit var radioAppFilterOff: MaterialRadioButton
    private lateinit var radioAppFilterBypass: MaterialRadioButton
    private lateinit var radioAppFilterOnly: MaterialRadioButton
    private lateinit var buttonAppFilterSelect: MaterialButton
    private lateinit var prefs: Preferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = Preferences(requireContext())
        
        initViews(view)
        setupListeners()
        loadPreferences()
    }

    private fun initViews(view: View) {
        checkboxUdpInTcp = view.findViewById(R.id.udp_in_tcp)
        checkboxIpv4 = view.findViewById(R.id.ipv4)
        checkboxIpv6 = view.findViewById(R.id.ipv6)
        checkboxExclude = view.findViewById(R.id.exclude)
        edittextExcludeRoutes = view.findViewById(R.id.exclude_routes)
        excludeRoutesLayout = view.findViewById(R.id.exclude_routes_layout)
        radioGroupAppFilter = view.findViewById(R.id.app_filter_mode)
        radioAppFilterOff = view.findViewById(R.id.app_filter_off)
        radioAppFilterBypass = view.findViewById(R.id.app_filter_bypass)
        radioAppFilterOnly = view.findViewById(R.id.app_filter_only)
        buttonAppFilterSelect = view.findViewById(R.id.app_filter_select_button)
    }

    private fun setupListeners() {
        checkboxExclude.setOnClickListener(this)
        buttonAppFilterSelect.setOnClickListener(this)
        
        // 设置RadioGroup监听器
        radioGroupAppFilter.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.app_filter_off -> {
                    // 关闭模式，隐藏选择应用按钮
                    buttonAppFilterSelect.visibility = View.GONE
                }
                R.id.app_filter_bypass, R.id.app_filter_only -> {
                    // 绕行或仅代理模式，显示选择应用按钮
                    buttonAppFilterSelect.visibility = View.VISIBLE
                }
            }
            savePreferences() // 保存当前选中状态
        }
    }

    private fun loadPreferences() {
        checkboxUdpInTcp.isChecked = prefs.isUdpInTcp
        checkboxIpv4.isChecked = prefs.isIpv4
        checkboxIpv6.isChecked = prefs.isIpv6
        checkboxExclude.isChecked = prefs.isExcludeRoutes
        edittextExcludeRoutes.setText(prefs.excludeRoutes)
        excludeRoutesLayout.visibility = if (prefs.isExcludeRoutes) View.VISIBLE else View.GONE
        
        // 设置应用过滤模式
        when (prefs.appFilterMode) {
            MainActivity.APP_FILTER_MODE_OFF -> radioAppFilterOff.isChecked = true
            MainActivity.APP_FILTER_MODE_BYPASS -> radioAppFilterBypass.isChecked = true
            MainActivity.APP_FILTER_MODE_ONLY -> radioAppFilterOnly.isChecked = true
        }
        
        // 更新选择应用按钮的可见性
        buttonAppFilterSelect.visibility = if (prefs.appFilterMode == MainActivity.APP_FILTER_MODE_OFF) 
            View.GONE else View.VISIBLE
    }

    fun savePreferences() {
        prefs.isUdpInTcp = checkboxUdpInTcp.isChecked
        prefs.isIpv4 = checkboxIpv4.isChecked
        prefs.isIpv6 = checkboxIpv6.isChecked
        prefs.isExcludeRoutes = checkboxExclude.isChecked
        prefs.excludeRoutes = edittextExcludeRoutes.text.toString()
        
        // 保存应用过滤模式
        val appFilterMode = when {
            radioAppFilterOff.isChecked -> MainActivity.APP_FILTER_MODE_OFF
            radioAppFilterBypass.isChecked -> MainActivity.APP_FILTER_MODE_BYPASS
            radioAppFilterOnly.isChecked -> MainActivity.APP_FILTER_MODE_ONLY
            else -> MainActivity.APP_FILTER_MODE_OFF
        }
        prefs.appFilterMode = appFilterMode
    }

    override fun onClick(view: View) {
        when (view) {
            checkboxExclude -> {
                savePreferences()
                // 根据复选框状态控制输入框的显示/隐藏
                excludeRoutesLayout.visibility = if (checkboxExclude.isChecked) View.VISIBLE else View.GONE
            }
            buttonAppFilterSelect -> {
                startActivity(Intent(requireContext(), AppListActivity::class.java))
            }
        }
    }
}
