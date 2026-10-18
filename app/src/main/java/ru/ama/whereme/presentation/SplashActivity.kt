package ru.ama.whereme.presentation

import dagger.hilt.android.AndroidEntryPoint

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var viewModel: ViewModelSplash


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[ViewModelSplash::class.java]
        viewModel.canStart.observe(this) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}