/*
 * (C) Copyright IBM Deutschland GmbH 2021
 * (C) Copyright IBM Corp. 2021
 */

package de.rki.covpass.app.main

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.viewpager2.widget.ViewPager2
import com.ensody.reactivestate.android.autoRun
import com.ensody.reactivestate.android.onDestroyView
import com.ensody.reactivestate.android.reactiveState
import com.ensody.reactivestate.get
import com.ensody.reactivestate.validUntil
import com.google.android.material.tabs.TabLayoutMediator
import com.ibm.health.common.android.utils.BaseEvents
import com.ibm.health.common.android.utils.viewBinding
import com.ibm.health.common.navigation.android.FragmentNav
import com.ibm.health.common.navigation.android.findNavigator
import com.ibm.health.common.navigation.android.getArgs
import de.rki.covpass.app.R
import de.rki.covpass.app.add.AddCovCertificateFragmentNav
import de.rki.covpass.app.boosterreissue.ReissueCallback
import de.rki.covpass.app.boosterreissue.ReissueNotificationFragmentNav
import de.rki.covpass.app.checkerremark.CheckRemarkCallback
import de.rki.covpass.app.checkerremark.CheckerRemarkFragmentNav
import de.rki.covpass.app.databinding.CovpassMainBinding
import de.rki.covpass.app.dependencies.covpassDeps
import de.rki.covpass.app.detail.DetailCallback
import de.rki.covpass.app.importcertificate.ImportCertificatesResultCallback
import de.rki.covpass.app.importcertificate.ImportCertificatesSelectorFragmentNav
import de.rki.covpass.app.information.CovPassInformationFragmentNav
import de.rki.covpass.app.newregulations.NewRegulationOnboardingCallback
import de.rki.covpass.app.newregulations.NewRegulationOnboardingFragmentNav
import de.rki.covpass.app.updateinfo.UpdateInfoCallback
import de.rki.covpass.app.updateinfo.UpdateInfoCovpassFragmentNav
import de.rki.covpass.app.validitycheck.ValidityCheckFragmentNav
import de.rki.covpass.commonapp.BaseFragment
import de.rki.covpass.commonapp.dialog.DialogAction
import de.rki.covpass.commonapp.dialog.DialogListener
import de.rki.covpass.commonapp.dialog.DialogModel
import de.rki.covpass.commonapp.dialog.showDialog
import de.rki.covpass.sdk.cert.models.GroupedCertificates
import de.rki.covpass.sdk.cert.models.GroupedCertificatesId
import de.rki.covpass.sdk.cert.models.GroupedCertificatesList
import de.rki.covpass.sdk.cert.models.ReissueType
import kotlinx.parcelize.Parcelize

@Parcelize
internal class MainFragmentNav(
    val uri: Uri?,
) : FragmentNav(MainFragment::class)

internal interface NotificationEvents : BaseEvents {
    fun showExpiryNotification()
    fun showNewUpdateInfo()
    fun showNewDataPrivacy()
    fun showDomesticRulesNotification()
    fun showCheckerRemark()
    fun showNewRegulationOnboarding()
    fun showFederalStateOnboarding()
    fun showBoosterNotification()
    fun showBoosterReissueNotification(listIds: List<String>)
    fun showRevokedNotification()
    fun importCertificateFromShareOption(uri: Uri)
}

/**
 * The main fragment hosts a [ViewPager2] to display all [GroupedCertificates] and serves as entry point for further
 * actions (e.g. add new certificate, show settings screen, show selected certificate)
 */
internal class MainFragment :
    BaseFragment(),
    DetailCallback,
    DialogListener,
    UpdateInfoCallback,
    DataProtectionCallback,
    CheckRemarkCallback,
    DomesticRulesNotificationCallback,
    BoosterNotificationCallback,
    ReissueCallback,
    ImportCertificatesResultCallback,
    NotificationEvents,
    NewRegulationOnboardingCallback,
    FederalStateOnboardingCallback {

    private val uri: Uri? by lazy { getArgs<MainFragmentNav>().uri }
    private val viewModel by reactiveState { MainViewModel(scope, uri) }
    private val covPassBackgroundUpdateViewModel by reactiveState {
        CovPassBackgroundUpdateViewModel(
            scope,
        )
    }
    private val binding by viewBinding(CovpassMainBinding::inflate)
    private var fragmentStateAdapter: CertificateFragmentStateAdapter by validUntil(::onDestroyView)
    override val announcementAccessibilityRes: Int =
        R.string.accessibility_start_screen_info_announce

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        autoRun {
            val certs = get(covpassDeps.certRepository.certs)
            updateCertificates(certs, viewModel.selectedCertId)
        }
    }

    override fun onResume() {
        super.onResume()
        covPassBackgroundUpdateViewModel.update()
    }

    private fun setupViews() {
        ViewCompat.setAccessibilityDelegate(
            binding.mainEmptyHeaderTextview,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.isHeading = true
                }
            },
        )
        binding.mainAddButton.setOnClickListener { showAddCovCertificatePopup() }
        binding.mainValidityCheckLayout.setOnClickListener {
            showValidityCheck(covpassDeps.certRepository.certs.value)
        }
        binding.mainSettingsImagebutton.setOnClickListener {
            findNavigator().push(CovPassInformationFragmentNav())
        }
        fragmentStateAdapter = CertificateFragmentStateAdapter(this)
        fragmentStateAdapter.attachTo(binding.mainViewPager)
        TabLayoutMediator(binding.mainTabIndicatorLayout, binding.mainViewPager) { _, _ ->
            // no special tab config necessary
        }.attach()
        setupPageChangeCallback()
        binding.tabNextButton.setOnClickListener {
            nextPage()
        }
        binding.tabBackButton.setOnClickListener {
            previousPage()
        }
    }

    private fun nextPage() {
        val certSize = covpassDeps.certRepository.certs.value.certificates.size
        val currentPage = binding.mainViewPager.currentItem
        val nextPage = if (currentPage + 1 >= certSize) {
            0
        } else {
            currentPage + 1
        }

        binding.mainViewPager.setCurrentItem(nextPage, true)
    }

    private fun previousPage() {
        val currentPage = binding.mainViewPager.currentItem
        val previousPage = if (currentPage - 1 < 0) {
            0
        } else {
            currentPage - 1
        }

        binding.mainViewPager.setCurrentItem(previousPage, true)
    }

    private fun showTabNavigationButtons(position: Int) {
        val certSize = covpassDeps.certRepository.certs.value.certificates.size

        if (position == 0) {
            binding.tabBackButton.isVisible = false
        }
        if (position > 0) {
            binding.tabBackButton.isVisible = true
        }
        binding.tabNextButton.isVisible = position != certSize - 1
    }

    private fun showValidityCheck(certificateList: GroupedCertificatesList) {
        if (certificateList.getValidCertificates().isEmpty()) {
            showInvalidCertValidationDialog()
        } else {
            findNavigator().push(ValidityCheckFragmentNav())
        }
    }

    private fun setupPageChangeCallback() {
        binding.mainViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    viewModel.onPageSelected(position)
                    showTabNavigationButtons(position)
                }
            },
        )
    }

    private fun updateCertificates(
        certificateList: GroupedCertificatesList,
        selectedCertId: GroupedCertificatesId?,
    ) {
        if (certificateList.certificates.isEmpty()) {
            binding.mainEmptyCardview.isVisible = true
            binding.mainViewPager.isVisible = false
        } else {
            fragmentStateAdapter.createFragments(certificateList)
            binding.mainEmptyCardview.isVisible = false
            binding.mainViewPager.isVisible = true
            selectedCertId?.let {
                binding.mainViewPager.setCurrentItem(
                    fragmentStateAdapter.getItemPosition(it),
                    isResumed,
                )
            }
        }
        binding.mainTabLayout.isVisible = certificateList.certificates.size > 1
        binding.mainValidityCheckLayout.isVisible = certificateList.certificates.size > 0
    }

    override fun onDeletionCompleted() {
        val dialogModel = DialogModel(
            titleRes = R.string.delete_result_dialog_header,
            messageString = getString(R.string.delete_result_dialog_message),
            positiveButtonTextRes = R.string.delete_result_dialog_positive_button_text,
        )
        showDialog(dialogModel, childFragmentManager)
    }

    private fun showInvalidCertValidationDialog() {
        val dialogModel = DialogModel(
            titleRes = R.string.no_cert_applicable_for_validation_dialog_header,
            messageString = getString(R.string.no_cert_applicable_for_validation_dialog_message),
            positiveButtonTextRes = R.string.no_cert_applicable_for_validation_positive_button_text,
        )
        showDialog(dialogModel, childFragmentManager)
    }

    override fun displayCert(certId: GroupedCertificatesId) {
        viewModel.selectedCertId = certId
        binding.mainViewPager.setCurrentItem(
            fragmentStateAdapter.getItemPosition(certId),
            isResumed,
        )
    }

    private fun showAddCovCertificatePopup() {
        findNavigator().push(AddCovCertificateFragmentNav())
    }

    override fun onUpdateInfoFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onCheckRemarkFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onNewRegulationOnboardingFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onFederalStateOnboardingFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onDataProtectionFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onDomesticRulesNotificationFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onBoosterNotificationFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onReissueCancel() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onReissueFinish(certificatesId: GroupedCertificatesId?) {
        viewModel.showingNotification.complete(Unit)
    }

    override fun importCertificateFinish() {
        viewModel.showingNotification.complete(Unit)
    }

    override fun onDialogAction(tag: String, action: DialogAction) {
        when (tag) {
            EXPIRED_DIALOG_TAG -> {
                launchWhenStarted {
                    covpassDeps.certRepository.certs.update { groupedCertificateList ->
                        groupedCertificateList.certificates.forEach {
                            it.hasSeenExpiryNotification = true
                            it.hasSeenExpiredReissueNotification = true
                        }
                    }
                    viewModel.showingNotification.complete(Unit)
                }
            }
            REVOKED_DIALOG_TAG -> {
                launchWhenStarted {
                    covpassDeps.certRepository.certs.update { groupedCertificateList ->
                        groupedCertificateList.certificates.forEach {
                            it.hasSeenRevokedNotification = true
                        }
                    }
                    viewModel.showingNotification.complete(Unit)
                }
            }
        }
    }

    override fun showNewUpdateInfo() {
        findNavigator().push(UpdateInfoCovpassFragmentNav())
    }

    override fun showNewDataPrivacy() {
        findNavigator().push(DataProtectionFragmentNav())
    }

    override fun showDomesticRulesNotification() {
        findNavigator().push(DomesticRulesNotificationFragmentNav())
    }

    override fun showCheckerRemark() {
        findNavigator().push(CheckerRemarkFragmentNav())
    }

    override fun showNewRegulationOnboarding() {
        findNavigator().push(NewRegulationOnboardingFragmentNav())
    }

    override fun showFederalStateOnboarding() {
        findNavigator().push(FederalStateOnboardingFragmentNav())
    }

    override fun showBoosterNotification() {
        findNavigator().push(BoosterNotificationFragmentNav())
    }

    override fun showBoosterReissueNotification(listIds: List<String>) {
        if (listIds.isNotEmpty()) {
            findNavigator().push(ReissueNotificationFragmentNav(ReissueType.Booster, listIds))
        } else {
            viewModel.showingNotification.complete(Unit)
        }
    }

    override fun showExpiryNotification() {
        val dialogModel = DialogModel(
            titleRes = R.string.error_validity_check_certificates_title,
            messageString = getString(R.string.error_validity_check_certificates_message),
            positiveButtonTextRes = R.string.error_validity_check_certificates_button_title,
            tag = EXPIRED_DIALOG_TAG,
        )
        showDialog(dialogModel, childFragmentManager)
    }

    override fun showRevokedNotification() {
        val dialogModel = DialogModel(
            titleRes = R.string.certificate_check_invalidity_error_title,
            messageString = getString(R.string.revocation_dialog_single),
            positiveButtonTextRes = R.string.ok,
            tag = REVOKED_DIALOG_TAG,
        )
        showDialog(dialogModel, childFragmentManager)
    }

    override fun importCertificateFromShareOption(uri: Uri) {
        findNavigator().push(ImportCertificatesSelectorFragmentNav(uri, true))
    }

    companion object {
        private const val EXPIRED_DIALOG_TAG = "expired_dialog"
        private const val REVOKED_DIALOG_TAG = "revoked_dialog"
    }
}
