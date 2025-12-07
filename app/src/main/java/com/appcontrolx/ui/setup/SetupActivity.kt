package com.appcontrolx.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.appcontrolx.R
import com.appcontrolx.databinding.ActivitySetupBinding
import com.appcontrolx.service.XiaomiBridge
import com.appcontrolx.ui.MainActivity
import com.appcontrolx.utils.Constants

class SetupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySetupBinding
    
    private val setupPrefs by lazy { 
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE) 
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (setupPrefs.getBoolean(Constants.PREFS_SETUP_COMPLETE, false)) {
            startMainActivity()
            return
        }
        
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewPager()
    }
    
    private fun setupViewPager() {
        val fragments = mutableListOf<Fragment>(
            ModeSelectionFragment(),
            PermissionsFragment()
        )
        
        if (XiaomiBridge(this, null).isXiaomiDevice()) {
            fragments.add(XiaomiSetupFragment())
        }
        
        fragments.add(SetupCompleteFragment())
        
        binding.viewPager.adapter = SetupPagerAdapter(this, fragments)
        binding.viewPager.isUserInputEnabled = false
        
        setupDotsIndicator(fragments.size)
        
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDotsIndicator(position)
            }
        })
    }
    
    private fun setupDotsIndicator(count: Int) {
        binding.dotsIndicator.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(12, 12).apply {
                    marginStart = 8
                    marginEnd = 8
                }
                setBackgroundResource(R.drawable.dot_inactive)
            }
            binding.dotsIndicator.addView(dot)
        }
        updateDotsIndicator(0)
    }
    
    private fun updateDotsIndicator(position: Int) {
        for (i in 0 until binding.dotsIndicator.childCount) {
            binding.dotsIndicator.getChildAt(i).setBackgroundResource(
                if (i == position) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
    }
    
    fun nextStep() {
        if (binding.viewPager.currentItem < (binding.viewPager.adapter?.itemCount ?: 0) - 1) {
            binding.viewPager.currentItem++
        }
    }
    
    fun completeSetup() {
        setupPrefs.edit().putBoolean(Constants.PREFS_SETUP_COMPLETE, true).apply()
        startMainActivity()
    }
    
    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    private inner class SetupPagerAdapter(
        activity: AppCompatActivity,
        private val fragments: List<Fragment>
    ) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]
    }
}
