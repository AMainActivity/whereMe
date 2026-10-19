package ru.ama.whereme.presentation

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.databinding.FragmentInProfileBinding

@AndroidEntryPoint
class ProfileInFragment : Fragment() {
    private var _binding: FragmentInProfileBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var viewModel: ProfileInViewModel
    private var confirmationDialog: AlertDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.setSubtitle(R.string.profile_in_title)
        viewModel = ViewModelProvider(this)[ProfileInViewModel::class.java]
        binding.frgmntProButCk.setOnClickListener { viewModel.checkKod(binding.frgmntProEt.text.toString()) }
        binding.frgmntProInTv.movementMethod = LinkMovementMethod.getInstance()
        binding.frgmntProInTv.text = HtmlCompat.fromHtml(getString(R.string.ma_menu_help), HtmlCompat.FROM_HTML_MODE_LEGACY)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try { viewModel.state.collect(::render) }
                finally { dismissDialog() }
            }
        }
    }

    private fun render(state: SignInState) {
        if (state == SignInState.SignedIn) (requireActivity() as MainActivity).onSessionReplaced()
        val editable = state == SignInState.Editing || state is SignInState.Failed
        binding.frgmntProEt.isEnabled = editable
        binding.frgmntProButCk.isEnabled = editable
        binding.operationError.isVisible = state is SignInState.Failed
        if (state is SignInState.Failed) binding.operationError.setText(state.message)
        if (state is SignInState.Confirmation) {
            if (confirmationDialog != null) return
            confirmationDialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_in_alert_title)
                .setMessage(getString(R.string.profile_in_alert_text, state.profile.name))
                .setPositiveButton(R.string.ma_yes) { _, _ -> viewModel.confirmSignIn() }
                .setNegativeButton(R.string.profile_in_no) { _, _ -> viewModel.dismissConfirmation() }
                .setOnCancelListener { viewModel.dismissConfirmation() }
                .create().also { it.show() }
        } else dismissDialog()
    }

    private fun dismissDialog() {
        // Destroying a view is not a user's refusal. Pending confirmation belongs to the ViewModel.
        confirmationDialog?.setOnCancelListener(null)
        confirmationDialog?.dismiss()
        confirmationDialog = null
    }

    override fun onDestroyView() {
        dismissDialog()
        _binding = null
        super.onDestroyView()
    }
}
