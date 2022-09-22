package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.ama.whereme.databinding.FragmentInProfileBinding
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


    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = null
        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileInViewModel::class.java]
        binding.frgmntProButCk.setOnClickListener {
            viewModel.checkKod(binding.frgmntProEt.text.toString())
        }

        viewModel.isSuccess.observe(viewLifecycleOwner) {

            (requireActivity() as MainActivity).setCurrentFragment(ProfileOutFragment())

            // Log.e("getLocationlldByDay", postData)
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