package ru.ama.whereme.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        AlertDialog.Builder(requireContext())
            .setTitle("Важная информация")
            .setMessage(
                "Вы можете поделиться ссылкой для просмотра своих данных." +
                        "  \nДелитесь с сылкой с теми лицами, которым Вы доверяете просмотр своих данных. " +
                        "Перейдя по ссылке, эти лица могут контролировать как и текущие Ваши местоположения," +
                        "так и видеть Ваши местоположения за определенный день.\n" +
                        "Для запрета использования ссылки перейдите в telegram-bot и сформируйте новый код."
            )
            .setCancelable(true)
            .setPositiveButton("поделиться") { _, _ ->
                val res = viewModel.getSetUserInfo()
                if (res.name != null && res.url != null) sharetext(
                    res.name,
                    "https://kol.hhos.ru/gkk/map.php?wm=" + res.url,
                    false
                )
                else
                    Toast.makeText(requireContext(), "нет данных", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun logOutAlertDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Выход")
            .setMessage("Выйти из профиля?  \nПосле выхода данные не будут сохранены на серверной части")
            .setCancelable(true)
            .setPositiveButton("да") { _, _ ->
                viewModel.logOut()
            }
            .setNegativeButton("нет") { dialogInterface, i ->
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (requireActivity() as AppCompatActivity).supportActionBar?.subtitle = "Профиль"
        viewModel = ViewModelProvider(this, viewModelFactory)[ProfileOutViewModel::class.java]
        val res = viewModel.getSetUserInfo()
        binding.frgmntProOutTv.linksClickable = true
        binding.frgmntProOutTv.movementMethod = LinkMovementMethod.getInstance()
        binding.frgmntProOutTv.text =
            HtmlCompat.fromHtml(
                String.format(getString(R.string.frgmnt_out_tv), res.name, res.url),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
        viewModel.isSuccess.observe(viewLifecycleOwner) {
            (requireActivity() as MainActivity).setCurrentFragment(ProfileInFragment())
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SHARE_MAIL_TYPE = "message/rfc822"
        private const val SHARE_TEXT_TYPE = "text/plain"
    }
}