/*
 * (C) Copyright IBM Deutschland GmbH 2021
 * (C) Copyright IBM Corp. 2021
 */

package de.rki.covpass.app.main

import com.ensody.reactivestate.DependencyAccessor
import de.rki.covpass.commonapp.BackgroundUpdateViewModel
import de.rki.covpass.sdk.dependencies.SdkDependencies
import de.rki.covpass.sdk.dependencies.sdkDeps
import de.rki.covpass.sdk.revocation.isBeforeUpdateInterval
import kotlinx.coroutines.CoroutineScope

public class CovPassBackgroundUpdateViewModel @OptIn(DependencyAccessor::class) constructor(
    scope: CoroutineScope,
    private val sdkDependencies: SdkDependencies = sdkDeps,
) : BackgroundUpdateViewModel(scope, sdkDependencies) {

    private val domesticRulesUpdater: Updater = Updater {
        if (sdkDependencies.rulesUpdateRepository.lastDomesticRulesUpdate.value.isBeforeUpdateInterval()) {
            sdkDependencies.covPassDomesticRulesRepository.loadRules()
        }
    }
    private val boosterRulesUpdater: Updater = Updater {
        if (sdkDependencies.rulesUpdateRepository.lastBoosterRulesUpdate.value.isBeforeUpdateInterval()) {
            sdkDependencies.covPassBoosterRulesRepository.loadBoosterRules()
        }
    }

    public override fun update() {
        super.update()
        domesticRulesUpdater.update()
        boosterRulesUpdater.update()
    }
}
