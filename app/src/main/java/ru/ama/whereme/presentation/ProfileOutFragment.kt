package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import ru.ama.whereme.R
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

        return binding.root

    }


    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = null
        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileOutViewModel::class.java]

        binding.frgmntProButCk1.setOnClickListener {
            viewModel.logOut()
        }
        val res = viewModel.getSetUserInfo()
        binding.frgmntProButShare.setOnClickListener{
           if(res.name!=null&&res.url!=null) sharetext(res.name,"https://kol.hhos.ru/gkk/map.php?wm="+res.url,false)
            else
                Toast.makeText(requireContext(),"нет данных",Toast.LENGTH_SHORT).show()
        }
        binding.frgmntProOutTv.linksClickable = true
        binding.frgmntProOutTv.movementMethod = LinkMovementMethod.getInstance()
        binding.frgmntProOutTv.text =
            HtmlCompat.fromHtml(
                String.format(getString(R.string.frgmnt_out_tv), res.name, res.url),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        viewModel.isSuccess.observe(viewLifecycleOwner) {

            (requireActivity() as MainActivity).setCurrentFragment(ProfileInFragment())

            // Log.e("getLocationlldByDay", postData)
        }
    }


    private fun sharetext(
        textZagol: String,
        textBody: String,
        isEmail: Boolean
    ) {
        val sharingIntent = Intent(Intent.ACTION_SEND)

        if (isEmail) {
            sharingIntent.putExtra(
                Intent.EXTRA_EMAIL,
                arrayOf(getString(R.string.frgmnt_menu_share_mail))
            )
            sharingIntent.type = SHARE_MAIL_TYPE
        } else
            sharingIntent.type = SHARE_TEXT_TYPE
        sharingIntent.putExtra(
            android.content.Intent.EXTRA_SUBJECT,
            textZagol
        )
        sharingIntent.putExtra(
            android.content.Intent.EXTRA_TEXT,
            textBody
        )
        val d = Intent.createChooser(
            sharingIntent,
            getString(R.string.frgmnt_menu_share_use)
        )
        requireActivity().startActivity(d)
    }

    override fun onDestroy() {
        super.onDestroy()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
companion object{

    private const val SHARE_MAIL_TYPE = "message/rfc822"
    private const val SHARE_TEXT_TYPE = "text/plain"
}

}