package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.ama.whereme.R
import ru.ama.whereme.databinding.FragmentInProfileBinding
import ru.ama.whereme.domain.entity.JsonJwt


@AndroidEntryPoint
class ProfileInFragment : Fragment() {

    private var _binding: FragmentInProfileBinding? = null
    private val binding
        get() = _binding ?: throw RuntimeException("FragmentInProfileBinding == null")
    private lateinit var viewModel: ProfileInViewModel

    override fun onAttach(context: Context) {

        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun logInAlertDialog(res: JsonJwt) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.profile_in_alert_title))
            .setMessage(String.format(getString(R.string.profile_in_alert_text), res.name))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.ma_yes)) { _, _ ->
                viewModel.saveUserInfo(res)
            }
            .setNegativeButton(getString(R.string.profile_in_no)) { dialogInterface, i ->
                dialogInterface.dismiss()
            }
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle =
            getString(R.string.profile_in_title)
        viewModel = ViewModelProvider(this)[ProfileInViewModel::class.java]
        binding.frgmntProButCk.setOnClickListener {
            viewModel.checkKod(binding.frgmntProEt.text.toString())
        }
        binding.frgmntProInTv.linksClickable = true
        binding.frgmntProInTv.movementMethod = LinkMovementMethod.getInstance()
        binding.frgmntProInTv.text =
            HtmlCompat.fromHtml(getString(R.string.ma_menu_help), HtmlCompat.FROM_HTML_MODE_LEGACY)
        viewModel.isSuccess.observe(viewLifecycleOwner) {
            logInAlertDialog(it)
        }
        viewModel.isError.observe(viewLifecycleOwner) {
            Toast.makeText(
                requireContext(),
                getString(it),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}