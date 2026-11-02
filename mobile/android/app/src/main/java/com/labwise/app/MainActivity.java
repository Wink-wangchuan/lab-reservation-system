package com.labwise.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 移动端入口：
 * 首次启动先展示《用户协议与隐私说明》，同意后才进入 Capacitor WebView（服务器页面）。
 * 协议内容与桌面端共用同一份 assets/eula.html。
 *
 * <p>客户端更新：优先查服务器 {@code /api/app/update}，失败后回退 GitHub Releases；
 * 下载 APK 后通过 FileProvider 调起系统安装器。旧版本（1.0.0）没有本功能，
 * 需要手动安装一次 4.5.0 后才能使用应用内更新。</p>
 */
public class MainActivity extends BridgeActivity {

    private static final String PREF = "labwise";
    private static final String KEY_EULA = "eula_accepted";
    private static final String KEY_IGNORED_UPDATE = "ignored_update_version";

    /** 后端 API 地址（更新接口；与前端 8092 分开） */
    private static final String API_BASE = "http://100.96.0.100:8091";
    private static final String GITHUB_LATEST =
            "https://api.github.com/repos/Wink-wangchuan/lab-reservation-system/releases/latest";
    private static final String TAG = "LabWiseUpdate";

    private SharedPreferences prefs;
    private final AtomicBoolean updateChecking = new AtomicBoolean(false);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupSystemBars();

        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_EULA, false)) {
            showEula();
            return;
        }

        attachUpdateBridge();
    }

    private void showEula() {
        setContentView(R.layout.activity_eula);

        WebView web = findViewById(R.id.eula_web);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("file:///android_asset/eula.html?readonly=1");

        Button agree = findViewById(R.id.btn_agree);
        Button decline = findViewById(R.id.btn_decline);
        agree.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_EULA, true).apply();
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

    /* ==================== 客户端更新 ==================== */

    /**
     * 把「检查更新」暴露给网页（个人中心的手动入口），并在进入主界面后自动检查一次。
     */
    private void attachUpdateBridge() {
        WebView webview = getBridge() == null ? null : getBridge().getWebView();
        if (webview == null) {
            return;
        }
        webview.addJavascriptInterface(new UpdateBridge(), "LabWiseNative");
        webview.postDelayed(() -> checkForUpdate(false), 5000);
    }

    /** 网页侧只允许触发一次检查，不暴露安装、文件等能力 */
    public class UpdateBridge {
        @JavascriptInterface
        public void checkUpdate() {
            runOnUiThread(() -> checkForUpdate(true));
        }
    }

    private void checkForUpdate(boolean manual) {
        if (!updateChecking.compareAndSet(false, true)) {
            if (manual) {
                toast("正在检查更新，请稍候…");
            }
            return;
        }
        new Thread(() -> {
            UpdateInfo info = null;
            try {
                info = fetchServerUpdate();
            } catch (Exception e) {
                Log.w(TAG, "服务器检查更新失败：" + e.getMessage());
            }
            if (info == null) {
                try {
                    info = fetchGithubUpdate();
                } catch (Exception e) {
                    Log.w(TAG, "GitHub 检查更新失败：" + e.getMessage());
                }
            }
            updateChecking.set(false);

            if (info == null) {
                if (manual) {
                    toast("检查更新失败，请稍后重试");
                }
                return;
            }
            if (!isNewer(info.version, currentVersion())) {
                if (manual) {
                    toast("当前已是最新版本 v" + currentVersion());
                }
                return;
            }
            if (!manual && info.version.equals(prefs.getString(KEY_IGNORED_UPDATE, ""))) {
                return;
            }
            final UpdateInfo found = info;
            runOnUiThread(() -> showUpdateDialog(found));
        }, "labwise-update-check").start();
    }

    private void showUpdateDialog(UpdateInfo info) {
        if (isFinishing()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        if (info.notes != null && !info.notes.trim().isEmpty()) {
            message.append(info.notes.trim()).append("\n\n");
        }
        if (info.size > 0) {
            message.append("安装包大小：")
                    .append(String.format(Locale.CHINA, "%.1f MB", info.size / 1024.0 / 1024.0));
        }
        new AlertDialog.Builder(this)
                .setTitle("发现新版本 v" + info.version)
                .setMessage(message.length() == 0 ? "建议更新到最新版本。" : message.toString())
                .setPositiveButton("立即更新", (dialog, which) -> startDownload(info))
                .setNegativeButton("稍后提醒", null)
                .setNeutralButton("忽略此版本", (dialog, which) ->
                        prefs.edit().putString(KEY_IGNORED_UPDATE, info.version).apply())
                .show();
    }

    private void startDownload(UpdateInfo info) {
        // Android 8+ 安装 APK 需要「允许安装未知应用」，先引导用户授权
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要安装权限")
                    .setMessage("Android 需要允许本应用安装其他应用，才能完成更新。")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            try {
                                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES));
                            } catch (Exception ignored) {
                                toast("请到系统设置中允许本应用安装应用");
                            }
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("正在下载更新");
        progress.setMessage("v" + info.version);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(true);
        progress.setCancelable(true);
        progress.setCanceledOnTouchOutside(false);
        progress.show();

        AtomicBoolean canceled = new AtomicBoolean(false);
        progress.setOnCancelListener(dialog -> canceled.set(true));

        new Thread(() -> {
            File dir = new File(getCacheDir(), "update");
            if (!dir.exists() && !dir.mkdirs()) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    toast("无法创建更新缓存目录");
                });
                return;
            }
            File apk = new File(dir, "LabWise-" + info.version + ".apk");
            Exception error = null;
            try {
                downloadApk(info.url, apk, progress, canceled);
            } catch (Exception e) {
                error = e;
            }
            if (error != null && !canceled.get() && info.fallbackUrl != null && !info.fallbackUrl.isEmpty()) {
                Log.w(TAG, "服务器下载失败，回退 GitHub：" + error.getMessage());
                error = null;
                try {
                    downloadApk(info.fallbackUrl, apk, progress, canceled);
                } catch (Exception e) {
                    error = e;
                }
            }
            if (canceled.get()) {
                apk.delete();
                return;
            }
            if (error != null) {
                final Exception failed = error;
                runOnUiThread(() -> {
                    progress.dismiss();
                    toast("下载失败：" + (failed.getMessage() == null ? "网络异常" : failed.getMessage()));
                });
                return;
            }
            if (info.sha256 != null && !info.sha256.isEmpty()) {
                String actual = sha256(apk);
                if (!actual.equalsIgnoreCase(info.sha256)) {
                    apk.delete();
                    runOnUiThread(() -> {
                        progress.dismiss();
                        toast("安装包校验失败，请重新下载");
                    });
                    return;
                }
            }
            runOnUiThread(() -> {
                progress.dismiss();
                installApk(apk);
            });
        }, "labwise-update-download").start();
    }

    private void downloadApk(String urlStr, File dest, ProgressDialog progress, AtomicBoolean canceled)
            throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "LabWise-Android");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            int totalInt = conn.getContentLength();
            long total = totalInt > 0 ? totalInt : 0;
            if (total > 0) {
                runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setMax(100);
                });
            }
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                long received = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (canceled.get()) {
                        throw new IOException("已取消");
                    }
                    out.write(buffer, 0, count);
                    received += count;
                    if (total > 0) {
                        int percent = (int) (received * 100 / total);
                        runOnUiThread(() -> progress.setProgress(percent));
                    }
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            // 部分安装器需要 ClipData 才会获得读取授权
            intent.setClipData(ClipData.newRawUri("apk", uri));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "启动安装器失败", e);
            toast("无法启动安装程序：" + e.getMessage());
        }
    }

    private UpdateInfo fetchServerUpdate() throws Exception {
        String body = httpGet(API_BASE + "/api/app/update?platform=android");
        JSONObject root = new JSONObject(body);
        if (root.optInt("code") != 200) {
            return null;
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return null;
        }
        UpdateInfo info = new UpdateInfo();
        info.version = data.optString("version", "");
        info.notes = data.optString("notes", "");
        info.sha256 = data.optString("sha256", "");
        String downloadUrl = data.optString("downloadUrl", "");
        info.url = downloadUrl.startsWith("http") ? downloadUrl : API_BASE + downloadUrl;
        info.fallbackUrl = data.optString("fallbackUrl", "");
        info.size = data.optLong("size", 0);
        return info.version.isEmpty() || info.url.isEmpty() ? null : info;
    }

    private UpdateInfo fetchGithubUpdate() throws Exception {
        String body = httpGet(GITHUB_LATEST);
        JSONObject root = new JSONObject(body);
        UpdateInfo info = new UpdateInfo();
        info.version = root.optString("tag_name", "").replaceFirst("^v", "");
        info.notes = root.optString("body", "");
        JSONArray assets = root.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    info.url = asset.optString("browser_download_url", "");
                    info.size = asset.optLong("size", 0);
                    break;
                }
            }
        }
        return info.version.isEmpty() || info.url.isEmpty() ? null : info;
    }

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "LabWise-Android");
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private String sha256(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String currentVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "0.0.0" : info.versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    private boolean isNewer(String remote, String local) {
        int[] a = versionParts(remote);
        int[] b = versionParts(local);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private int[] versionParts(String version) {
        String value = version == null ? "" : version.replaceFirst("^v", "");
        String[] parts = value.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].replaceAll("\\D.*$", ""));
            } catch (Exception e) {
                numbers[i] = 0;
            }
        }
        return numbers;
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private static class UpdateInfo {
        String version = "";
        String notes = "";
        String url = "";
        String fallbackUrl = "";
        String sha256 = "";
        long size = 0;
    }
}
