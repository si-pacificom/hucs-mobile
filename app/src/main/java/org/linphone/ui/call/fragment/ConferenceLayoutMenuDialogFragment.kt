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
package org.linphone.ui.call.fragment

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.linphone.databinding.CallConferenceLayoutMenuBinding
import org.linphone.ui.call.model.ConferenceModel

@UiThread
class ConferenceLayoutMenuDialogFragment(
    val conferenceModel: ConferenceModel,
    private val onDismiss: (() -> Unit)? = null
) : BottomSheetDialogFragment() {
    companion object {
        const val TAG = "ConferenceLayoutMenuDialogFragment"
    }

    override fun onCancel(dialog: DialogInterface) {
        onDismiss?.invoke()
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        onDismiss?.invoke()
        super.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = CallConferenceLayoutMenuBinding.inflate(layoutInflater)

        view.viewModel = conferenceModel

        view.setGridClickListener {
            conferenceModel.changeLayout(ConferenceModel.GRID_LAYOUT)
            dismiss()
        }
        view.setActiveSpeakerClickListener {
            conferenceModel.changeLayout(ConferenceModel.ACTIVE_SPEAKER_LAYOUT)
            dismiss()
        }
        view.setAudioOnlyClickListener {
            conferenceModel.changeLayout(ConferenceModel.AUDIO_ONLY_LAYOUT)
            dismiss()
        }

        return view.root
    }
}
