package ru.ama.whereme.presentation

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ru.ama.whereme.R
import ru.ama.whereme.databinding.ActivityMainBinding

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.contentMain.bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.page_3) openProfile()
            else {
                showScreen(item.itemId)
                true
            }
        }
        if (savedInstanceState == null) showScreen(R.id.page_1)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.session.collect {
                    if (binding.contentMain.bottomNavigationView.selectedItemId == R.id.page_3) openProfile()
                }
            }
        }
    }

    private fun showScreen(id: Int) {
        val current = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        val next = when (id) {
            R.id.page_2 -> if (current is SettingsFragment) return else SettingsFragment()
            R.id.page_4 -> if (current is AboutFragment) return else AboutFragment()
            else -> if (current is MapFragment) return else MapFragment()
        }
        setCurrentFragment(next)
    }

    private fun openProfile(): Boolean {
        when (val state = viewModel.session.value) {
            is LoadState.Ready -> {
                val current = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                if (state.value.profile.tokenJwt.isNotBlank()) {
                    if (current !is ProfileOutFragment) setCurrentFragment(ProfileOutFragment())
                } else if (current !is ProfileInFragment) setCurrentFragment(ProfileInFragment())
                return true
            }
            LoadState.Loading -> Toast.makeText(this, R.string.storage_loading, Toast.LENGTH_SHORT).show()
            LoadState.Failed -> {
                if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) !is ProfileInFragment)
                    setCurrentFragment(ProfileInFragment())
                Snackbar.make(binding.root, R.string.profile_reenter, Snackbar.LENGTH_LONG)
                    .setAction(R.string.storage_retry) { viewModel.retrySession() }.show()
                return true
            }
        }
        return false
    }

    fun onSessionReplaced() { viewModel.retrySession() }

    private fun setCurrentFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment_content_main, fragment)
            .commit()
    }
}
