package com.appcontrolx.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.appcontrolx.R
import com.appcontrolx.databinding.FragmentAboutBinding
import com.appcontrolx.service.PermissionBridge

class AboutFragment : Fragment() {
    
    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAppInfo()
        setupSystemInfo()
        setupLinks()
    }
    
    private fun setupAppInfo() {
        val packageInfo = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
        
        binding.tvVersion.text = getString(R.string.about_version_format, 
            packageInfo.versionName, packageInfo.longVersionCode)
        
        // Current mode
        val mode = PermissionBridge().detectMode()
        binding.tvCurrentMode.text = mode.displayName()
    }
    
    private fun setupSystemInfo() {
        binding.tvDeviceInfo.text = getString(R.string.about_device_format,
            Build.MANUFACTURER, Build.MODEL)
        binding.tvAndroidVersion.text = getString(R.string.about_android_format,
            Build.VERSION.RELEASE, Build.VERSION.SDK_INT)
    }
    
    private fun setupLinks() {
        binding.btnGithub.setOnClickListener {
            openUrl("https://github.com/risunCode/AppControl-X")
        }
        
        binding.btnShare.setOnClickListener {
            shareApp()
        }
        
        binding.btnRate.setOnClickListener {
            openUrl("https://github.com/risunCode/AppControl-X/stargazers")
        }
        
        binding.btnBugReport.setOnClickListener {
            openUrl("https://github.com/risunCode/AppControl-X/issues/new")
        }
    }
    
    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    
    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, getString(R.string.about_share_text))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.about_share_via)))
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
