package com.labwise.app;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 启动画面：全屏静音播放 {@code res/raw/splash_video.mp4}（9:16 竖版品牌动画），
 * 播放结束后进入 {@link MainActivity}（协议页或主界面由 MainActivity 自行决定）。
 *
 * <p>比例自适应：用 {@link TextureView} + 矩阵变换做「等比铺满 + 居中裁切」（center-crop），
 * 不拉伸变形；在 19.5:9、20:9 等更长屏幕上也不会出现上下黑边。
 * 设备横竖屏切换时会重新计算。</p>
 *
 * <p>播放失败、视频异常或用户点击画面时立即跳过；
 * 另设最长展示时间兜底，避免任何情况下卡在启动页。</p>
 */
public class SplashActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {

    /** 启动画面最长停留时间（视频仅 6 秒，留出解码与首帧准备余量） */
    private static final long MAX_SPLASH_MS = 9000;
    /** 最短展示时间，避免解码失败时品牌画面一闪而过 */
    private static final long MIN_SPLASH_MS = 1200;
    /** 与视频边缘渐变接近的底色，裁切/首帧未就绪时不会出现突兀黑边 */
    private static final int BG_COLOR = Color.parseColor("#E3E8F7");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutTask = this::finishSplash;

    private TextureView videoView;
    private MediaPlayer player;
    private int videoWidth;
    private int videoHeight;

    private long startedAt;
    private boolean finished;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startedAt = System.currentTimeMillis();
        setupFullscreen();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG_COLOR);
        root.setOnClickListener(v -> finishSplash());

        videoView = new TextureView(this);
        videoView.setOnClickListener(v -> finishSplash());
        videoView.setSurfaceTextureListener(this);
        root.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

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

    /* ==================== 视频播放 ==================== */

    private void startVideo(SurfaceTexture surfaceTexture) {
        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.splash_video);
            player = new MediaPlayer();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setSurface(new Surface(surfaceTexture));
            player.setVolume(0f, 0f); // 启动画面静音，避免突然出声
            player.setOnVideoSizeChangedListener((mp, width, height) -> {
                videoWidth = width;
                videoHeight = height;
                applyCoverTransform();
            });
            player.setOnPreparedListener(mp -> {
                applyCoverTransform();
                mp.start();
            });
            player.setOnCompletionListener(mp -> finishSplash());
            player.setOnErrorListener((mp, what, extra) -> {
                finishSplash();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            finishSplash();
        }
    }

    /**
     * 等比铺满并居中裁切：取横纵缩放比的较大值，围绕视图中心缩放，
     * 超出部分被裁掉——保持画面不变形，同时铺满任意长宽比的屏幕。
     */
    private void applyCoverTransform() {
        if (videoView == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        int viewWidth = videoView.getWidth();
        int viewHeight = videoView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        float scale = Math.max((float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale, viewWidth / 2f, viewHeight / 2f);
        videoView.setTransform(matrix);
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
                // 释放失败不影响界面退出
            }
            player = null;
        }
    }

    /* ==================== TextureView 回调 ==================== */

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        startVideo(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        applyCoverTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // 每帧回调，无需处理
    }

    /* ==================== 退出 ==================== */

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
        releasePlayer();
        super.onDestroy();
    }
}
