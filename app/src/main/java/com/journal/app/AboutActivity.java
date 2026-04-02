package com.journal.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Read values from app_info.xml (compiled into R.string at build time)
        String appName = getString(R.string.app_info_name);
        String appVersion = getString(R.string.app_info_version);
        String appEmail = getString(R.string.app_info_email);
        String appUrl = getString(R.string.app_info_url);
        String appCompanyUrl = getString(R.string.app_info_company_url);
        String appDescription = getString(R.string.app_info_description);

        // Populate text views
        TextView nameView = findViewById(R.id.about_app_name);
        nameView.setText(appName);

        TextView versionView = findViewById(R.id.about_version);
        versionView.setText("Version " + appVersion);

        TextView descView = findViewById(R.id.about_description);
        descView.setText(appDescription);

        TextView emailView = findViewById(R.id.about_email);
        emailView.setText(appEmail);
        emailView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + appEmail));
            intent.putExtra(Intent.EXTRA_SUBJECT, appName + " - Support");
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        });

        TextView urlView = findViewById(R.id.about_url);
        urlView.setText(appUrl);
        urlView.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)));
        });

        TextView companyUrlView = findViewById(R.id.about_company_url);
        companyUrlView.setText("View on Google Play");
        companyUrlView.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(appCompanyUrl)));
        });

        // Back button
        findViewById(R.id.about_back_btn).setOnClickListener(v -> finish());
    }
}
