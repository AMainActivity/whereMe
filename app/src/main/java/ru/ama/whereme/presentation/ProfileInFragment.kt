package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
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
import javax.inject.Inject


class ProfileInFragment : Fragment() {

    private var _binding: FragmentInProfileBinding? = null
    private val binding
        get() = _binding ?: throw RuntimeException("FragmentInProfileBinding == null")
    private lateinit var viewModel: ProfileInViewModel
    private val component by lazy {
        (requireActivity().application as MyApp).component
    }


    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    override fun onAttach(context: Context) {
        component.inject(this)

        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }


    private fun setActionBarSubTitle(txt: String) {
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = txt
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentInProfileBinding.inflate(inflater, container, false)


        return binding.root

    }
    private fun logInAlertDialog(res:JsonJwt) {
        AlertDialog.Builder(requireContext())
            .setTitle("Подтверждение")
            .setMessage("Нажимая 'Да', Вы подтверждаете, что учетная запись telegram: \n\n'${res.name}'\n\n принадлежит Вам.")
            .setCancelable(true)
            .setPositiveButton("да") { _, _ ->
viewModel.saveUserInfo(res)
                (requireActivity() as MainActivity).setCurrentFragment(ProfileOutFragment())
            }
            .setNegativeButton("нет") { dialogInterface, i ->
                dialogInterface.dismiss()
            }
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = "Профиль"
        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileInViewModel::class.java]
        binding.frgmntProButCk.setOnClickListener {
            viewModel.checkKod(binding.frgmntProEt.text.toString())


        }

        binding.frgmntProInTv.linksClickable = true
        binding.frgmntProInTv.movementMethod = LinkMovementMethod.getInstance()
        binding.frgmntProInTv.text =
            HtmlCompat.fromHtml(getString(R.string.ma_menu_help), HtmlCompat.FROM_HTML_MODE_LEGACY)
        viewModel.isSuccess.observe(viewLifecycleOwner) {

            logInAlertDialog(it)
            // Log.e("getLocationlldByDay", postData)
        }
        viewModel.isError.observe(viewLifecycleOwner) {
            Toast.makeText(
                requireContext(),
                "неверный код, повторите попытку",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}