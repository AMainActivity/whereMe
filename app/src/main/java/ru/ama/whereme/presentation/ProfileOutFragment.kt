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
import ru.ama.whereme.databinding.FragmentOutProfileBinding
import javax.inject.Inject


class ProfileOutFragment : Fragment() {

    private var _binding: FragmentOutProfileBinding? = null
    private val binding
        get() = _binding ?: throw RuntimeException("FragmentOutProfileBinding == null")
    private lateinit var viewModel: ProfileOutViewModel
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

        _binding = FragmentOutProfileBinding.inflate(inflater, container, false)
        binding.frgmntProButCk1.setOnClickListener {
           // viewModel.checkKod(binding.frgmntProEt.text.toString())
        }
        return binding.root

    }


    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = null
        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileOutViewModel::class.java]


    }

    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}