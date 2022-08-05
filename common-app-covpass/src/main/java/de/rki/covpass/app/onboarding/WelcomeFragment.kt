/*
 * (C) Copyright IBM Deutschland GmbH 2021
 * (C) Copyright IBM Corp. 2021
 */

package de.rki.covpass.app.onboarding

import android.net.Uri
import com.ibm.health.common.navigation.android.FragmentNav
import com.ibm.health.common.navigation.android.getArgs
import de.rki.covpass.app.R
import de.rki.covpass.commonapp.onboarding.BaseWelcomeFragment
import kotlinx.parcelize.Parcelize

@Parcelize
internal class WelcomeFragmentNav(
    val uri: Uri?,
) : FragmentNav(WelcomeFragment::class)

/**
 * Covpass specific welcome screen. Overrides the abstract functions from [BaseWelcomeFragment].
 */
internal class WelcomeFragment : BaseWelcomeFragment() {

    val uri: Uri? by lazy { getArgs<WelcomeFragmentNav>().uri }

    override fun getHeaderTextRes() = R.string.start_onboarding_title

    override fun getSubheaderTextRes() = R.string.start_onboarding_message

    override fun getEncryptionHeaderTextRes() = R.string.start_onboarding_secure_title

    override fun getEncryptionTextRes() = R.string.start_onboarding_secure_message

    override fun getMainImageRes() = R.drawable.onboarding_welcome

    override fun getOnboardingNav(): FragmentNav = OnboardingContainerFragmentNav(uri)
}
