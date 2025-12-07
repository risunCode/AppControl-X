package com.appcontrolx.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.appcontrolx.databinding.FragmentDisclaimerBinding

class DisclaimerFragment : Fragment() {
    
    private var _binding: FragmentDisclaimerBinding? = null
    private val binding get() = _binding!!
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDisclaimerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.checkAgree.setOnCheckedChangeListener { _, isChecked ->
            binding.btnAccept.isEnabled = isChecked
        }
        
        binding.btnAccept.setOnClickListener {
            if (binding.checkAgree.isChecked) {
                // Complete setup (final step)
                (activity as? SetupActivity)?.completeSetup()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
