/*
 ============================================================================
 Name        : ViewPagerAdapter.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : ViewPager Adapter
 ============================================================================
 */

package hev.sockstun.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity, private val fragments: List<Fragment>) : 
    FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = fragments.size
    
    override fun createFragment(position: Int): Fragment = fragments[position]
}
