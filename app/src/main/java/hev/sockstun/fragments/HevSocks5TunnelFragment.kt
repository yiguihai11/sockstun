package hev.sockstun.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import hev.sockstun.MainActivity
import hev.sockstun.Preferences
import hev.sockstun.R
import hev.sockstun.adapters.ViewPagerAdapter

class HevSocks5TunnelFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var buttonSave: MaterialButton
    private lateinit var prefs: Preferences
    
    private lateinit var serverFragment: ServerFragment
    private lateinit var dnsFragment: DnsFragment
    private lateinit var optionsFragment: OptionsFragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hev_socks5_tunnel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = Preferences(requireContext())
        
        // 初始化视图
        tabLayout = view.findViewById(R.id.tab_layout)
        viewPager = view.findViewById(R.id.view_pager)
        buttonSave = view.findViewById(R.id.save)
        
        // 设置ViewPager
        setupViewPager()
        
        // 设置保存按钮点击监听器
        buttonSave.setOnClickListener {
            savePrefs()
            (activity as? MainActivity)?.showToast("设置已保存")
        }
    }
    
    private fun setupViewPager() {
        // 初始化Fragment
        serverFragment = ServerFragment()
        dnsFragment = DnsFragment()
        optionsFragment = OptionsFragment()
        
        // 设置ViewPager适配器
        val fragments = listOf(serverFragment, dnsFragment, optionsFragment)
        val adapter = ViewPagerAdapter(requireActivity(), fragments)
        viewPager.adapter = adapter
        
        // 连接TabLayout和ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_server)
                1 -> getString(R.string.tab_dns)
                2 -> getString(R.string.tab_options)
                else -> ""
            }
        }.attach()
    }
    
    private fun savePrefs() {
        try {
            // 保存所有Fragment的设置
            if (::serverFragment.isInitialized) {
                serverFragment.savePreferences()
            }
            
            if (::dnsFragment.isInitialized) {
                dnsFragment.savePreferences()
            }
            
            if (::optionsFragment.isInitialized) {
                optionsFragment.savePreferences()
            }
        } catch (e: Exception) {
            // 记录错误，但不中断操作
            (activity as? MainActivity)?.showToast("保存设置时出错: ${e.message}")
        }
    }
} 