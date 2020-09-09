package com.lch.camerax_example;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.ZoomState;
import androidx.camera.extensions.AutoImageCaptureExtender;
import androidx.camera.extensions.AutoPreviewExtender;
import androidx.camera.extensions.BeautyImageCaptureExtender;
import androidx.camera.extensions.BeautyPreviewExtender;
import androidx.camera.extensions.BokehImageCaptureExtender;
import androidx.camera.extensions.BokehPreviewExtender;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.extensions.HdrPreviewExtender;
import androidx.camera.extensions.ImageCaptureExtender;
import androidx.camera.extensions.NightImageCaptureExtender;
import androidx.camera.extensions.NightPreviewExtender;
import androidx.camera.extensions.PreviewExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.ListenableFuture;
import com.lch.camerax_example.databinding.FragmentCameraBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CameraFragment extends Fragment implements View.OnTouchListener {
    private static final String TAG = "BASE_TAG " + CameraFragment.class.getSimpleName() + " ";
    private static final int DEFAULT_CAMERA = CameraSelector.LENS_FACING_BACK;
    private FragmentCameraBinding mBinding;
    private FocusView mFocusView;
    private Preview mPreview;
    private ImageCapture mImageCapture;
    private ImageAnalysis mImageAnalysis;
    private VideoCapture mVideoCapture;
    private ProcessCameraProvider mCameraProvider;
    private ListenableFuture<ProcessCameraProvider> future;
    private CameraSelector mCameraSelector;
    private Camera mCamera;
    private CameraControl mCameraControl;
    private CameraInfo mCameraInfo;
    private ExecutorService mImageAnalysisExecutor;
    private ExecutorService mImageCaptureExecutor;
    private ExecutorService mVideoExecutor;
    private ExecutorService mFocusExecutor;
    private Handler mHandler;
    private Handler mFlashHandler;
    private String mPath;
    private boolean mIsVideoPath;
    private int mCameraID = DEFAULT_CAMERA;
    private boolean mIsVideoRecording = false;
    private float mLastDistance;
    private float mMaxZoom;
    private float mMinZoom;
    private float mZoomNow;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = FragmentCameraBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initData();
        initView();
    }

    private void initData() {
        mHandler = new Handler();
        mFlashHandler = new Handler();
        mImageAnalysisExecutor = Executors.newSingleThreadExecutor();
        mImageCaptureExecutor = Executors.newSingleThreadExecutor();
        mVideoExecutor = Executors.newSingleThreadExecutor();
        mFocusExecutor = Executors.newSingleThreadExecutor();
        future = ProcessCameraProvider.getInstance(requireContext());
        future.addListener(this::bindLifecycle, ContextCompat.getMainExecutor(requireContext()));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initView() {
        mBinding.takePicture.setOnClickListener(v -> {
            if (mIsVideoRecording) return;
            takePhoto();
        });

        mBinding.takePicture.setOnLongClickListener(v -> {
            if (mIsVideoRecording) stopVideo();
            else takeVideo();
            return true;
        });

        mBinding.cameraChange.setOnClickListener(v -> {
            if (mIsVideoRecording) return;
            mCameraID = mCameraID == CameraSelector.LENS_FACING_BACK ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
            bindLifecycle();
        });

        mBinding.showPicture.setOnClickListener(v -> {
            if (mIsVideoRecording) return;
            ShowActivity.startActivity(requireActivity(), mPath, mIsVideoPath);
        });

        mBinding.flash.setOnClickListener(v -> {
            showFlash();
            mFlashHandler.postDelayed(flashRunnable, 3000);
        });

        mBinding.flashOn.setOnClickListener(v -> setFLashMode(R.drawable.ic_baseline_flash_on_24));

        mBinding.flashAuto.setOnClickListener(v -> setFLashMode(R.drawable.ic_baseline_flash_auto_24));

        mBinding.flashOff.setOnClickListener(v -> setFLashMode(R.drawable.ic_baseline_flash_off_24));

        mBinding.preview.setOnTouchListener(this);
        mBinding.preview.post(this::bindLifecycle);

        mFocusView = new FocusView(requireActivity());
        requireActivity().addContentView(mFocusView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showFlash() {
        if (mBinding.flashOn.getVisibility() == View.GONE) {
            mBinding.flashAuto.setVisibility(View.VISIBLE);
            mBinding.flashOn.setVisibility(View.VISIBLE);
            mBinding.flashOff.setVisibility(View.VISIBLE);
        } else {
            mBinding.flashAuto.setVisibility(View.GONE);
            mBinding.flashOn.setVisibility(View.GONE);
            mBinding.flashOff.setVisibility(View.GONE);
        }

    }

    private void setFLashMode(int resId) {
        switch (resId) {
            case R.drawable.ic_baseline_flash_on_24:
                mImageCapture.setFlashMode(ImageCapture.FLASH_MODE_ON);
                break;
            case R.drawable.ic_baseline_flash_auto_24:
                mImageCapture.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
                break;
            default:
                mImageCapture.setFlashMode(ImageCapture.FLASH_MODE_OFF);
        }
        mFlashHandler.removeCallbacks(flashRunnable);
        mBinding.flash.setImageResource(resId);
        showFlash();
    }

    private Runnable flashRunnable = this::showFlash;

    private void initUseCases() {
        initPreview();
        initImageAnalysis();
        initImageCapture();
        initVideoCapture();
    }


    @SuppressLint("RestrictedApi")
    private void initPreview() {
        mPreview = new Preview.Builder().build();

    }

    @SuppressLint("RestrictedApi")
    private void initImageCapture() {
        mImageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();
    }

    private void initImageAnalysis() {
        mImageAnalysis = new ImageAnalysis.Builder().build();
        mImageAnalysis.setAnalyzer(mImageAnalysisExecutor, image -> {
            Log.d(TAG, "ImageAnalysis: " + image.getFormat() + " height " + image.getHeight() + " weight " + image.getWidth());
            image.close();
        });

    }

    @SuppressLint("RestrictedApi")
    private void initVideoCapture() {
        mVideoCapture = new VideoCapture.Builder()
                .build();
    }

    private void initCameraSelector() {
        mCameraSelector = new CameraSelector.Builder()
                .requireLensFacing(mCameraID)
                .build();
    }

    private void bindLifecycle() {

        try {
            mCameraProvider = future.get();
            initUseCases();
            initCameraSelector();

            checkExtender();

            mCameraProvider.unbindAll();
            mCamera = mCameraProvider.bindToLifecycle(this, mCameraSelector, mPreview, mImageCapture, mVideoCapture);
            mPreview.setSurfaceProvider(mBinding.preview.createSurfaceProvider());
            mCameraControl = mCamera.getCameraControl();
            mCameraInfo = mCamera.getCameraInfo();
            LiveData<ZoomState> zoomState = mCameraInfo.getZoomState();
            zoomState.observe(this, zoomState1 -> {
                mMaxZoom = zoomState1.getMaxZoomRatio();
                mMinZoom = zoomState1.getMinZoomRatio();
                mZoomNow = zoomState1.getZoomRatio();
                Log.d(TAG, " max zoom " + mMaxZoom + " min zoom " + mMinZoom + " zoom now " + mZoomNow);
            });
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void takePhoto() {
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(createFile(false)).build();
        mImageCapture.takePicture(outputFileOptions, mImageCaptureExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri savedUri = outputFileResults.getSavedUri();
                if (savedUri == null) {
                    Log.d(TAG, "onImageSaved: URI IS NULL");
                }
                mHandler.post(() -> {
                    Glide.with(requireActivity()).load(mPath).into(mBinding.showPicture);
                    Log.d(TAG, "onImageSaved: success ");
                });

            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.d(TAG, "onError: take photo");
            }
        });
    }

    @SuppressLint({"RestrictedApi", "UseCompatLoadingForDrawables"})
    private void takeVideo() {
        mIsVideoRecording = true;
        VideoCapture.OutputFileOptions outputFileOptions = new VideoCapture.OutputFileOptions.Builder(createFile(true)).build();
        mVideoCapture.startRecording(outputFileOptions, mVideoExecutor, new VideoCapture.OnVideoSavedCallback() {
            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                Uri savedUri = outputFileResults.getSavedUri();
                if (savedUri == null) {
                    Log.d(TAG, "onVideoSaved: SAVE URI IS NULL");
                }

                mHandler.post(() -> {
                    Glide.with(requireActivity()).load(mPath).into(mBinding.showPicture);
                    Log.d(TAG, "onVideoSaved: success ");
                });
            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                Log.d(TAG, "onError: VIDEO ERROR " + message);
            }
        });

        mBinding.takePicture.setBackground(getResources().getDrawable(R.drawable.ic_video));
    }

    @SuppressLint({"RestrictedApi", "UseCompatLoadingForDrawables"})
    private void stopVideo() {
        mBinding.takePicture.setBackground(getResources().getDrawable(R.drawable.bg_take));
        mVideoCapture.stopRecording();
        mIsVideoRecording = false;
    }


    private File createFile(boolean isVideo) {
        File file = new File(requireActivity().getExternalFilesDir("camerax"), System.currentTimeMillis() + (isVideo ? ".mp4" : ".jpg"));
        mPath = file.getPath();
        mIsVideoPath = isVideo;
        Log.d(TAG, "createFile: " + file.getAbsolutePath() + "  " + file.getPath());
        return file;
    }

    private List<ImageCaptureExtender> mImgExtenders = new ArrayList<>();
    private List<PreviewExtender> mPreViewExtenders = new ArrayList<>();

    private void checkExtender() {
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        CameraSelector selector2 = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
        ImageCapture.Builder imgCapture = new ImageCapture.Builder();
        Preview.Builder preview = new Preview.Builder();
        HdrImageCaptureExtender hdrImageCaptureExtender = HdrImageCaptureExtender.create(imgCapture);
        HdrPreviewExtender hdrPreviewExtender = HdrPreviewExtender.create(preview);
        BeautyPreviewExtender beautyPreviewExtender = BeautyPreviewExtender.create(preview);
        BeautyImageCaptureExtender beautyImageCaptureExtender = BeautyImageCaptureExtender.create(imgCapture);
        NightImageCaptureExtender nightImageCaptureExtender = NightImageCaptureExtender.create(imgCapture);
        NightPreviewExtender nightPreviewExtender = NightPreviewExtender.create(preview);
        BokehImageCaptureExtender bokehImageCaptureExtender = BokehImageCaptureExtender.create(imgCapture);
        BokehPreviewExtender bokehPreviewExtender = BokehPreviewExtender.create(preview);
        AutoImageCaptureExtender autoImageCaptureExtender = AutoImageCaptureExtender.create(imgCapture);
        AutoPreviewExtender autoPreviewExtender = AutoPreviewExtender.create(preview);
        mImgExtenders.add(hdrImageCaptureExtender);
        mImgExtenders.add(beautyImageCaptureExtender);
        mImgExtenders.add(bokehImageCaptureExtender);
        mImgExtenders.add(nightImageCaptureExtender);
        mImgExtenders.add(autoImageCaptureExtender);
        mPreViewExtenders.add(hdrPreviewExtender);
        mPreViewExtenders.add(beautyPreviewExtender);
        mPreViewExtenders.add(bokehPreviewExtender);
        mPreViewExtenders.add(nightPreviewExtender);
        mPreViewExtenders.add(autoPreviewExtender);

        for (ImageCaptureExtender mImgExtender : mImgExtenders) {
            if (mImgExtender.isExtensionAvailable(selector)) {
                Log.d(TAG, "checkExtender: available  back " + mImgExtender.getClass().getSimpleName());
            } else {
                Log.d(TAG, "checkExtender:not available  back " + mImgExtender.getClass().getSimpleName());
            }
            if (mImgExtender.isExtensionAvailable(selector2)) {
                Log.d(TAG, "checkExtender: available  front " + mImgExtender.getClass().getSimpleName());
            } else {
                Log.d(TAG, "checkExtender:not available  front " + mImgExtender.getClass().getSimpleName());
            }
        }

        for (PreviewExtender mPreViewExtender : mPreViewExtenders) {
            if (mPreViewExtender.isExtensionAvailable(selector)) {
                Log.d(TAG, "checkExtender: available  back " + mPreViewExtender.getClass().getSimpleName());
            } else {
                Log.d(TAG, "checkExtender:not available  back " + mPreViewExtender.getClass().getSimpleName());
            }
            if (mPreViewExtender.isExtensionAvailable(selector2)) {
                Log.d(TAG, "checkExtender: available  front " + mPreViewExtender.getClass().getSimpleName());
            } else {
                Log.d(TAG, "checkExtender:not available  front " + mPreViewExtender.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mImageAnalysisExecutor.shutdown();
        mImageAnalysisExecutor.shutdown();
        mVideoExecutor.shutdown();
        mFocusExecutor.shutdown();
    }


    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int eventAction = event.getAction();
        switch (eventAction) {
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 2) {
                    zoom(event.getX(0), event.getY(0), event.getX(1), event.getY(1));
                }
                break;
            case MotionEvent.ACTION_DOWN:
                if (event.getPointerCount() == 1) {
                    startFocus(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
                mLastDistance = 0;
                break;
        }
        return true;
    }

    private void startFocus(float x, float y) {
        MeteringPointFactory factory = mBinding.preview.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);

        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)//自动对焦
                .addPoint(point, FocusMeteringAction.FLAG_AE)//自动曝光
                .addPoint(point, FocusMeteringAction.FLAG_AWB)//自动白平衡
                .setAutoCancelDuration(5, TimeUnit.SECONDS)//持续时间
                .build();
        mFocusView.startFocus((int) x, (int) y);
        ListenableFuture<FocusMeteringResult> future = mCameraControl.startFocusAndMetering(action);//开始对焦
        future.addListener(() -> {
            try {
                FocusMeteringResult result = future.get();
                if (result.isFocusSuccessful()) {
                    Log.d(TAG, "Focus success ");
                    mHandler.post(() -> {
                        mFocusView.clear();
                    });
                } else {
                    Log.d(TAG, "Focus fail");
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

        }, mFocusExecutor);
    }

    private void zoom(float x1, float y1, float x2, float y2) {
        float offSetX = x1 - x2;
        float offSetY = y1 - y2;
        float distance = (float) Math.sqrt(offSetX * offSetX + offSetY * offSetY);
        if (distance > mLastDistance && mZoomNow < mMaxZoom) {
            mCameraControl.setZoomRatio(mZoomNow + 1);
        } else if (distance < mLastDistance && mZoomNow > mMinZoom) {
            mCameraControl.setZoomRatio(mZoomNow - 1);
        }
        mLastDistance = distance;
    }

}
