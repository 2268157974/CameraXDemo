package com.lch.camerax_example;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.lch.camerax_example.databinding.ActivityShowBinding;

import java.io.IOException;

public class ShowActivity extends AppCompatActivity {
    private static final String TAG = "BASE_TAG" + ShowActivity.class.getSimpleName() + " ";
    public static final String DATA_PATH = "path";
    public static final String IS_VIDEO = "isVideo";
    private ActivityShowBinding mBinding;
    private MediaPlayer mPlayer;

    public static void startActivity(Context context, String path, boolean isVideo) {
        Log.d(TAG, "startActivity: path " + path + " isVideo " + isVideo);
        Intent intent = new Intent(context, ShowActivity.class);
        intent.putExtra(DATA_PATH, path);
        intent.putExtra(IS_VIDEO, isVideo);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityShowBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        if (!getIntent().getBooleanExtra(IS_VIDEO, false)) {
            mBinding.show.setVisibility(View.VISIBLE);
            Glide.with(ShowActivity.this).load(getIntent().getStringExtra(DATA_PATH)).into(mBinding.show);
        } else {
            mBinding.video.setVisibility(View.VISIBLE);
            playVideo();
        }
    }

    private void playVideo() {
        mPlayer = new MediaPlayer();
        SurfaceHolder holder = mBinding.video.getHolder();
        holder.setKeepScreenOn(true);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    mPlayer.setDataSource(getIntent().getStringExtra(DATA_PATH));
                    mPlayer.prepareAsync();
                    mPlayer.setOnPreparedListener(mp -> {
                        Log.d(TAG, "video: prepared");
                        mp.setLooping(true);
                        mp.setDisplay(holder);
                        mp.start();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "play video fail " + e.getMessage());
                }

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mPlayer != null) {
                    mPlayer.release();
                    mPlayer = null;
                }
            }
        });

    }

    private void initSurface() {

    }
}
