package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.widget.Toast
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import ru.ama.whereme.R
import ru.ama.whereme.databinding.FragmentOutProfileBinding


@AndroidEntryPoint
class ProfileOutFragment : Fragment() {

    private var _binding: FragmentOutProfileBinding? = null
    private val binding
        get() = _binding ?: throw RuntimeException("FragmentOutProfileBinding == null")
    private var actionDialog: AlertDialog? = null
    private lateinit var viewModel: ProfileOutViewModel



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_profil_out_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_p_out_share -> {
                shereUrlAlertDialog()
                true
            }
            R.id.menu_p_out_logout -> {
                logOutAlertDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun shereUrlAlertDialog() {
        actionDialog?.dismiss()
        actionDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_out_alert_title))
            .setMessage(
                getString(R.string.profile_out_alert_text)
            )
            .setCancelable(true)
            .setPositiveButton(getString(R.string.menu_profil_share)) { _, _ ->
                val res = viewModel.getSetUserInfo() ?: return@setPositiveButton
                val name = res.name
                val url = res.url
                if (name != null && url != null) shareText(
                    name,
                    getString(R.string.profile_out_urd_body) + url
                )
                else
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.map_nodata),
                        Toast.LENGTH_SHORT
                    ).show()
            }
            .show()
    }

    private fun logOutAlertDialog() {
        actionDialog?.dismiss()
        actionDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_logout_title))
            .setMessage(getString(R.string.profile_logout_text))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ma_yes)) { _, _ ->
                viewModel.logOut()
            }
            .setNegativeButton(getString(R.string.profile_in_no)) { dialogInterface, i ->
                dialogInterface.dismiss()
            }
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOutProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle =
            getString(R.string.profile_in_title)
        viewModel = ViewModelProvider(this)[ProfileOutViewModel::class.java]
        binding.frgmntProOutTv.linksClickable = true
        binding.frgmntProOutTv.movementMethod = LinkMovementMethod.getInstance()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.session.collect { state ->
                        when (state) {
                            is LoadState.Ready -> {
                                val res = state.value.profile
                                binding.frgmntProOutTv.text = HtmlCompat.fromHtml(
                                    getString(R.string.frgmnt_out_tv, res.name.orEmpty(), res.url.orEmpty()),
                                    HtmlCompat.FROM_HTML_MODE_LEGACY)
                            }
                            LoadState.Loading -> binding.frgmntProOutTv.setText(R.string.storage_loading)
                            LoadState.Failed -> Snackbar.make(binding.root, R.string.storage_read_failed, Snackbar.LENGTH_INDEFINITE)
                                .setAction(R.string.storage_retry) { viewModel.retrySession() }.show()
                        }
                    }
                }
                launch {
                    viewModel.errorMessage.collect { error ->
                        binding.operationError.isVisible = error != null
                        if (error != null) binding.operationError.setText(error)
                    }
                }
            }
        }
    }

    private fun shareText(title: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.frgmnt_menu_share_use)))
    }

    override fun onDestroyView() {
        actionDialog?.dismiss()
        actionDialog = null
        super.onDestroyView()
        _binding = null
    }

}
