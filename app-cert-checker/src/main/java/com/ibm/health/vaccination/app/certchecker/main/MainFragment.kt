package com.ibm.health.vaccination.app.certchecker.main

import android.os.Bundle
import android.view.View
import com.ibm.health.common.android.utils.viewBinding
import com.ibm.health.common.navigation.android.FragmentNav
import com.ibm.health.common.navigation.android.findNavigator
import com.ibm.health.common.vaccination.app.BaseFragment
import com.ibm.health.common.vaccination.app.information.InformationFragmentNav
import com.ibm.health.vaccination.app.certchecker.R
import com.ibm.health.vaccination.app.certchecker.databinding.CheckerMainBinding
import com.ibm.health.vaccination.app.certchecker.scanner.ValidationQRScannerFragmentNav
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
class MainFragmentNav : FragmentNav(MainFragment::class)

internal class MainFragment : BaseFragment() {

    private val binding by viewBinding(CheckerMainBinding::inflate)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.mainSettingsImagebutton.setOnClickListener { findNavigator().push(InformationFragmentNav()) }
        binding.mainCheckCertButton.setOnClickListener { findNavigator().push(ValidationQRScannerFragmentNav()) }

        // FIXME use correct date and use LocalDateTime
        val date = Calendar.getInstance().time.toString()
        // FIXME use getString's formatting?
        val updateString = String.format(resources.getString(R.string.main_availability_last_update), date)
        binding.mainAvailabilityLastUpdateTextview.text = updateString
    }
}
