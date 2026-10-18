package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    private val REQUEST_PERMISSION_LOCATION = 10
    private val REQUEST_PERMISSION_LOCATION_BACK = 11
    private lateinit var viewModel: MainViewModel

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        startService()
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

    private fun startService() {
        when {
            isAccessFineLocationGranted(this) -> {
                if (!isLocationEnabled(this)) {
                    showGPSNotEnabledDialog(this)
                }
            }

            else -> {
                requestAccessFineLocationPermission(
                    this,
                    REQUEST_PERMISSION_LOCATION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.e(javaClass.simpleName, "onRequestPermissionsResult: $requestCode")
        when (requestCode) {


            REQUEST_PERMISSION_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!isLocationEnabled(this)) {
                        showGPSNotEnabledDialog(this)
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.ma_no_access),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                requestNotificationPermission(this, 111)
                requestBackLocationPermission(this, REQUEST_PERMISSION_LOCATION_BACK)
            }
        }
    }

    private fun requestAccessFineLocationPermission(activity: AppCompatActivity, requestId: Int) {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)

       /* if (
            (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }*/

        ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), requestId)
    }

    private fun requestBackLocationPermission(activity: AppCompatActivity, requestId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            requestId
        )
    }

    private fun requestNotificationPermission(activity: AppCompatActivity, requestId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            requestId
        )
    }
    /*private fun requestAccessFineLocationPermission(activity: AppCompatActivity, requestId: Int) {
        Log.e(javaClass.simpleName, "requestAccessFineLocationPermission: ", )
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS,
            ),
            requestId
        )
    }*/

    private fun isAccessFineLocationGranted(context: Context): Boolean {
        return ContextCompat
            .checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(context: Context): Boolean {
        val locationManager: LocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showGPSNotEnabledDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(getString(R.string.ma_turn_on_gps))
            .setMessage(getString(R.string.ma_yes_no))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.ma_yes)) { _, _ ->
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .show()
    }
}