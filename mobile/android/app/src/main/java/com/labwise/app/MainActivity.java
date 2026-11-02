package com.labwise.app;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
        setupSystemBars();

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

    /**
     * 边到边（edge-to-edge）适配：
     * Android 15 会强制应用内容延伸到状态栏/导航栏之后，若不处理 Insets，
     * 页面顶部的品牌栏就会被系统状态栏遮住。这里把状态栏、导航栏、刘海与
     * 输入法的占位高度作为内边距补给内容，既不遮挡内容，也不会让键盘盖住表单。
     */
    private void setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        // 状态栏区域显示主题的窗口底色，图标深浅跟随系统明暗模式
        boolean lightBars = getResources().getBoolean(R.bool.labwise_light_system_bars);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);

        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return windowInsets;
        });
    }
}
