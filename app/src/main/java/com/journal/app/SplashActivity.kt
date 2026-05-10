package com.journal.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bootstrap_prefs", MODE_PRIVATE)
        if (prefs.getString("skip_splash", null) == "true") {
            navigateToLogin()
            return
        }

        val img = ImageView(this).apply {
            setImageResource(R.drawable.splash_portrait)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF000000.toInt())
            setOnClickListener { navigateToLogin() }
        }
        setContentView(img)

        Handler(Looper.getMainLooper()).postDelayed({ navigateToLogin() }, 3000)
    }

    private fun navigateToLogin() {
        if (!isFinishing) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
