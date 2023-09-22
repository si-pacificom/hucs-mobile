/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.voip.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.utils.LinphoneUtils

class CallModel @WorkerThread constructor(val call: Call) {
    val displayName = MutableLiveData<String>()

    val state = MutableLiveData<String>()

    val isPaused = MutableLiveData<Boolean>()

    val friend = coreContext.contactsManager.findContactByAddress(call.remoteAddress)

    val contact = MutableLiveData<ContactAvatarModel>()

    private val callListener = object : CallListenerStub() {
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            this@CallModel.state.postValue(LinphoneUtils.callStateToString(state))
            isPaused.postValue(LinphoneUtils.isCallPaused(state))
        }
    }

    init {
        call.addListener(callListener)

        displayName.postValue(friend?.name ?: LinphoneUtils.getDisplayName(call.remoteAddress))
        if (friend != null) {
            contact.postValue(ContactAvatarModel(friend))
        }

        state.postValue(LinphoneUtils.callStateToString(call.state))
        isPaused.postValue(LinphoneUtils.isCallPaused(call.state))
    }

    @WorkerThread
    fun destroy() {
        call.removeListener(callListener)
    }
}
