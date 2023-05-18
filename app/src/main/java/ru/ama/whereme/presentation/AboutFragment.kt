package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.ama.whereme.R
import ru.ama.whereme.databinding.FragmentAboutBinding
import javax.inject.Inject

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding
        get() = _binding ?: throw RuntimeException("FragmentAboutBinding == null")
    private val component by lazy {
        (requireActivity().application as MyApp).component
    }


    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    override fun onAttach(context: Context) {
        component.inject(this)

        super.onAttach(context)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root

    }


    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = "О приложении"

        binding.frgmntAbTv.linksClickable = true
        binding.frgmntAbTv.movementMethod = LinkMovementMethod.getInstance()
        binding.frgmntAbTv.text =
            HtmlCompat.fromHtml(
                getString(R.string.frgmnt_ab_main),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )

    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}