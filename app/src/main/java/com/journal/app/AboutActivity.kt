package com.journal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val appName = getString(R.string.app_info_name)
        val appVersion = getString(R.string.app_info_version)
        val appEmail = getString(R.string.app_info_email)
        val appUrl = getString(R.string.app_info_url)
        val appCompanyUrl = getString(R.string.app_info_company_url)
        val appDescription = getString(R.string.app_info_description)

        findViewById<TextView>(R.id.about_app_name).text = appName
        findViewById<TextView>(R.id.about_version).text = "Version $appVersion"
        findViewById<TextView>(R.id.about_description).text = appDescription

        findViewById<TextView>(R.id.about_email).apply {
            text = appEmail
            setOnClickListener {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$appEmail")
                    putExtra(Intent.EXTRA_SUBJECT, "$appName - Support")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }
        }

        findViewById<TextView>(R.id.about_url).apply {
            text = appUrl
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)))
            }
        }

        findViewById<TextView>(R.id.about_company_url).apply {
            text = "View on Google Play"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appCompanyUrl)))
            }
        }

        findViewById<android.view.View>(R.id.about_back_btn).setOnClickListener { finish() }
    }
}
