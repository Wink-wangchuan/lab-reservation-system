package com.labwise.app;

import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 启动画面：全屏静音播放 {@code res/raw/splash_video.mp4}（9:16 竖版品牌动画），
 * 播放结束后进入 {@link MainActivity}（协议页或主界面由 MainActivity 自行决定）。
 *
 * <p>播放失败、视频异常或用户点击画面时立即跳过；
 * 另设最长展示时间兜底，避免任何情况下卡在启动页。</p>
 */
public class SplashActivity extends AppCompatActivity {

    /** 启动画面最长停留时间（视频仅 6 秒，留出解码与首帧准备余量） */
    private static final long MAX_SPLASH_MS = 9000;
    /** 最短展示时间，避免解码失败时品牌画面一闪而过 */
    private static final long MIN_SPLASH_MS = 1200;
    /** 与视频边缘渐变接近的底色，letterscreen 时不会出现突兀黑边 */
    private static final int BG_COLOR = Color.parseColor("#E3E8F7");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutTask = this::finishSplash;

    private long startedAt;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startedAt = System.currentTimeMillis();
        setupFullscreen();

        VideoView video = new VideoView(this);
        video.setBackgroundColor(BG_COLOR);
        setContentView(video, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        video.setOnClickListener(v -> finishSplash());

        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.splash_video);
        video.setVideoURI(uri);
        video.setOnPreparedListener(mp -> {
            mp.setVolume(0f, 0f); // 启动画面静音，避免突然出声
            // 尽量铺满（部分机型可能忽略该缩放模式，失败时保持等比留边，底色已对齐）
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            video.start();
        });
        video.setOnCompletionListener(mp -> finishSplash());
        video.setOnErrorListener((mp, what, extra) -> {
            finishSplash();
            return true;
        });

        handler.postDelayed(timeoutTask, MAX_SPLASH_MS);
    }

    /** 全屏沉浸：隐藏状态栏与导航栏，内容延伸到屏幕边缘 */
    private void setupFullscreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /** 结束启动画面：保证最短展示时间后跳转主界面（可重复调用） */
    private void finishSplash() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacks(timeoutTask);
        long elapsed = System.currentTimeMillis() - startedAt;
        handler.postDelayed(this::launchMain, Math.max(0, MIN_SPLASH_MS - elapsed));
    }

    private void launchMain() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
