package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.google.android.material.snackbar.Snackbar
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import ru.ama.whereme.R
import ru.ama.whereme.databinding.ActivityMainBinding

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val mapFragment = MapFragment()
        val setFragment = SettingsFragment()
        val aboutFragment = AboutFragment()
        viewModel.session.asLiveData().observe(this) { state ->
            if (binding.contentMain.bottomNavigationView.selectedItemId == R.id.page_3) openProfile()
        }
        setCurrentFragment(mapFragment)
        binding.contentMain.bottomNavigationView.setOnItemSelectedListener {
            setCurrentFragment(
                when (it.itemId) {
                    R.id.page_1 -> mapFragment
                    R.id.page_2 -> setFragment
                    R.id.page_3 -> {
                        openProfile()
                        return@setOnItemSelectedListener true
                    }

                    R.id.page_4 -> aboutFragment
                    else -> mapFragment
                }
            )
            true
        }
    }

    private fun openProfile() {
        when (val state = viewModel.session.value) {
            is LoadState.Ready -> {
                val current = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (state.value.profile.tokenJwt.isNotBlank()) {
                    if (current !is ProfileOutFragment) setCurrentFragment(ProfileOutFragment())
                } else if (current !is ProfileInFragment) setCurrentFragment(ProfileInFragment())
            }
            LoadState.Loading -> Toast.makeText(this, R.string.storage_loading, Toast.LENGTH_SHORT).show()
            LoadState.Failed -> Snackbar.make(binding.root, R.string.storage_read_failed, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.storage_retry) { viewModel.retrySession() }.show()
        }
    }

    fun setCurrentFragment(fragment: Fragment) =
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.nav_host_fragment_content_main, fragment)
            commit()
        }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

}
