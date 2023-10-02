package org.linphone.ui.main.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountProfileFragmentBinding
import org.linphone.ui.main.fragment.GenericFragment
import org.linphone.ui.main.history.model.ConfirmationDialogModel
import org.linphone.ui.main.settings.viewmodel.AccountProfileViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.FileUtils

@UiThread
class AccountProfileFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Account Profile Fragment]"
    }

    private lateinit var binding: AccountProfileFragmentBinding

    private val viewModel: AccountProfileViewModel by navGraphViewModels(
        R.id.main_nav_graph
    )

    private val args: AccountProfileFragmentArgs by navArgs()

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Log.i("$TAG Picture picked [$uri]")
            lifecycleScope.launch {
                val localFileName = FileUtils.getFilePath(requireContext(), uri, true)
                if (localFileName != null) {
                    Log.i("$TAG Picture will be locally stored as [$localFileName]")
                    val path = FileUtils.getProperFilePath(localFileName)
                    viewModel.setNewPicturePath(path)
                } else {
                    Log.e("$TAG Failed to copy [$uri] to local storage")
                }
            }
        } else {
            Log.w("$TAG No picture picked")
        }
    }

    private val dropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val dialPlan = viewModel.dialPlansList[position]
            Log.i(
                "$TAG Selected dialplan updated [+${dialPlan.countryCallingCode}] / [${dialPlan.country}]"
            )
            viewModel.setDialPlan(dialPlan)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountProfileFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val identity = args.accountIdentity
        Log.i("$TAG Looking up for account with identity address [$identity]")
        viewModel.findAccountMatchingIdentity(identity)

        binding.setBackClickListener {
            goBack()
        }

        binding.setPickImageClickListener {
            pickImage()
        }

        binding.setDeleteImageClickListener {
            viewModel.setNewPicturePath("")
        }

        binding.setPrefixTooltipClickListener {
            // TODO
        }

        binding.setChangeModeClickListener {
            val action = AccountProfileFragmentDirections.actionAccountProfileFragmentToAccountProfileModeFragment()
            findNavController().navigate(action)
        }

        binding.setSettingsClickListener {
            val action = AccountProfileFragmentDirections.actionAccountProfileFragmentToAccountSettingsFragment(
                identity
            )
            findNavController().navigate(action)
        }

        binding.setDeleteAccountClickListener {
            val model = ConfirmationDialogModel()
            val dialog = DialogUtils.getConfirmAccountRemovalDialog(
                requireActivity(),
                model,
                viewModel.displayName.value.orEmpty()
            )

            model.dismissEvent.observe(viewLifecycleOwner) {
                it.consume {
                    dialog.dismiss()
                }
            }

            model.confirmEvent.observe(viewLifecycleOwner) {
                it.consume {
                    viewModel.deleteAccount()
                    dialog.dismiss()
                }
            }

            dialog.show()
        }

        viewModel.accountRemovedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Account has been removed, leaving profile")
                findNavController().popBackStack()
            }
        }

        viewModel.accountFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()

                        setupDialPlanPicker()
                    }
                } else {
                    Log.e(
                        "$TAG Failed to find an account matching this identity address [$identity]"
                    )
                    // TODO: show error
                    goBack()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        Log.i("$TAG Leaving account profile, saving changes")
        viewModel.saveChangesWhenLeaving()
        sharedViewModel.refreshDrawerMenuAccountsListEvent.value = Event(true)
    }

    private fun pickImage() {
        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun setupDialPlanPicker() {
        val dialPlanIndex = viewModel.selectedDialPlan.value ?: 0
        Log.i("$TAG Setting default dial plan at index [$dialPlanIndex]")
        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.dialPlansLabelList
        )
        adapter.setDropDownViewResource(
            R.layout.assistant_country_picker_dropdown_cell
        )
        binding.prefix.adapter = adapter
        binding.prefix.onItemSelectedListener = dropdownListener
        binding.prefix.setSelection(dialPlanIndex)
    }
}
