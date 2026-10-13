package com.tankeyunkeime.keyboard;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent().setClassName(this, "com.paopaoime.aikeyboard.ime.ui.activity.SplashActivity"));
        finish();
    }
}
