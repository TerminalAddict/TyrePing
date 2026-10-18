package com.masselis.tpmsadvanced.feature.androidauto.endpoint

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.masselis.tpmsadvanced.feature.androidauto.endpoint.Session as TpmsAdvancedSession

internal class CarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(this)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    override fun onCreateSession(info: SessionInfo): Session = TpmsAdvancedSession()
}
