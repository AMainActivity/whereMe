package ru.ama.whereme.presentation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import ru.ama.whereme.presentationn.ViewModelSplash
import javax.inject.Inject

class SplashActivity : AppCompatActivity() {

    private lateinit var viewModel: ViewModelSplash

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private val component by lazy {
        (application as MyApp).component
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component.inject(this)
        viewModel = ViewModelProvider(this, viewModelFactory)[ViewModelSplash::class.java]
       // startActivity(Intent(this, MainActivity::class.java))
       // finish()
         viewModel.canStart.observe(this) {
             startActivity(Intent(this, MainActivity::class.java))
             finish()
         }
    }


}
