package com.labwise.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import com.getcapacitor.BridgeActivity;

/**
 * 移动端入口：
 * 首次启动先展示《用户协议与隐私说明》，同意后才进入 Capacitor WebView（服务器页面）。
 * 协议内容与桌面端共用同一份 assets/eula.html。
 */
public class MainActivity extends BridgeActivity {

    private static final String PREF = "labwise";
    private static final String KEY_EULA = "eula_accepted";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        if (sp.getBoolean(KEY_EULA, false)) {
            return;
        }

        setContentView(R.layout.activity_eula);

        WebView web = findViewById(R.id.eula_web);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/eula.html?readonly=1");

        Button agree = findViewById(R.id.btn_agree);
        Button decline = findViewById(R.id.btn_decline);
        agree.setOnClickListener(v -> {
            sp.edit().putBoolean(KEY_EULA, true).apply();
            recreate();
        });
        decline.setOnClickListener(v -> finishAffinity());
    }
}
