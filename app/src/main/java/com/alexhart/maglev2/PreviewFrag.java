package com.alexhart.maglev2;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.alexhart.maglev2.AutoWhiteBalSeek.AutoWhiteBalSeekBar;
import com.alexhart.maglev2.AutoWhiteBalSeek.AutoWhiteBalSeekListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class PreviewFrag extends Fragment implements View.OnClickListener {

    private SharedPreferences mSharedPreferences;
    private ImageView mVideoButton;
    private boolean inPicturePreview = false;
    private boolean inVideoPreview = false;

    //UI members
    private ImageView mCameraButton;
    private AutoWhiteBalSeekBar mAutoWhiteBalSeek;
    private LinearLayout mLayoutIso;
    private TextView mSeekBarTextView;
    private AutoFitTextureView mTextureView;
    private AutoFitTextureView mTextureView2;
    private RelativeLayout mLayoutBottom;
    private LinearLayout mLayoutAutoFoc;
    private LinearLayout mLayoutExpTime;
    private LinearLayout mLayoutAutoExp;
    private RelativeLayout mLayoutCapture;
    private SeekBar mZoomSeek;
    private Switch mSwitchAutoFoc;
    private boolean showExpTimeFlag = false;
    private boolean showAutoFocFlag = false;
    private boolean showZoomFlag = false;
    private boolean showAutoExpFlag = false;
    private boolean showIsoFlag = false;
    private boolean showAutoWhitBalFlag = false;
    private List<View> mLayoutList;

    private static final int SHOW_AUTOFOC = 1;
    private static final int SHOW_AUTOEXP = 2;
    private static final int SHOW_EXPTIME = 3;
    private static final int SHOW_AUTOWHITBAL = 4;
    private static final int SHOW_ISO = 5;
    private static final int SHOW_ZOOM = 6;

    //camera members
    private Size mVideoPreviewSize;
    private MediaRecorder mMediaRecorder;
    boolean recording = false;
    private CameraCaptureSession mVideoCaptureSession;
    private boolean manualSupport;
    private int[] mCameraCapabilitiesList;
    private boolean mMediaPrepared = false;
    private boolean afterCreate = false;
    private boolean inMagLevPreview = false;
    private RelativeLayout mCameraSwapHolder;
    private RelativeLayout mCameraClosedHolder;
    private RelativeLayout mCameraOpenHolder;
    public static boolean mCameraConfig = false;
    public static final int MEDIA_TYPE_IMAGE = 10;
    public static final int MEDIA_TYPE_VIDEO = 20;
    public static String MEDIA_EXTENSION = "";
    final private static int STATE_OFF = 0;
    final private static int STATE_CAMERA = 1;
    final private static int STATE_VIDEO = 2;
    final private static int STATE_CAPTURE = 3;
    private int mCameraState = STATE_OFF;
    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice = null;
    //front facing camera has id 0, LOCKED on this
    private String mCameraId = "0";
    private CameraCaptureSession mCameraCaptureSession;
    private float valueAF;
    private int valueAE;
    private long valueAETime;
    private int valueISO;
    //TODO add in last vals to exposure pipeline
    private Float lastAF = null;
    private Rect lastZoom = null;

    private CameraCharacteristics mCameraCharacteristics;
    private List<Surface> mOutputSurfaces;
    private Size mPreviewSize;
    private Size mPreviewSize2;
    private File mVideoFile;
    private Surface mSurface;
    private Surface mSurface2;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private CaptureRequest.Builder mPreviewBuilder;
    private CaptureRequest.Builder mCaptureBuilder;

    private static final String TAG = "PreviewFrag";

    private float mFingerDist;
    private double mPinchCount;

    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.d(TAG, "OnImageAvail");
                    try {
                        File writtenFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
                        mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(),
                                writtenFile));
                        scanFile(writtenFile.getAbsolutePath());
                        makeToast("Photo saved: " + writtenFile.getAbsolutePath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

    private static class ImageSaver implements Runnable {
        private Image mImage;
        private File mFile;

        private ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            Log.d(TAG, "ImageSaverRun");
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;

            try {
                fileOutputStream = new FileOutputStream(mFile);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {

                mImage.close();
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.preview_frag, container, false);

        //check API level for compatability
//                    if (Integer.valueOf(android.os.Build.VERSION.SDK) < 21) {
//                        makeToast("Camera function not supported by your phone! :(");
//                        break;
//                    }
        mCameraState = STATE_CAMERA;
        setHasOptionsMenu(true);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        initUIListeners(v);
        afterCreate = true;
        return v;
    }


    private void initUIListeners(View v) {
        Log.d(TAG, "initUI");
        initializeSeekBarVals();

        mCameraClosedHolder = (RelativeLayout) v.findViewById(R.id.camera_closed_holder);
        mCameraOpenHolder = (RelativeLayout) v.findViewById(R.id.camera_holder);
        mCameraSwapHolder = (RelativeLayout) v.findViewById(R.id.camera_swap_preview);

        ImageView cameraClosedButton = (ImageView) v.findViewById(R.id.camera_closed_btn);
        Button cameraClosedSettings = (Button) v.findViewById(R.id.camera_closed_settings_btn);
        ImageView cameraSwapPreviewButton = (ImageView) v.findViewById(R.id.camera_preview_swap_btn);

        cameraSwapPreviewButton.setOnClickListener(this);
        cameraClosedButton.setOnClickListener(this);
        cameraClosedSettings.setOnClickListener(this);

        mTextureView = (AutoFitTextureView) v.findViewById(R.id.camera_preview);

        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        mSeekBarTextView = (TextView) v.findViewById(R.id.text_seekbar);
        mSeekBarTextView.setVisibility(View.INVISIBLE);

        mAutoWhiteBalSeek = (AutoWhiteBalSeekBar) v.findViewById(R.id.autowhitebal_seekbar);

        mLayoutBottom = (RelativeLayout) v.findViewById(R.id.bottom_layout);

        mLayoutAutoFoc = (LinearLayout) v.findViewById(R.id.focus_layout);
        mSwitchAutoFoc = (Switch) v.findViewById(R.id.focus_switch);
        SeekBar seekAutoFoc = (SeekBar) v.findViewById(R.id.focus_seekbar);

        mLayoutIso = (LinearLayout) v.findViewById(R.id.iso_layout);
        Switch switchIso = (Switch) v.findViewById(R.id.iso_switch);
        SeekBar seekIso = (SeekBar) v.findViewById(R.id.iso_seekbar);

        mLayoutExpTime = (LinearLayout) v.findViewById(R.id.expTime_layout);
        Switch switchExpTime = (Switch) v.findViewById(R.id.expTime_switch);
        SeekBar seekExpTime = (SeekBar) v.findViewById(R.id.expTime_seek);

        LinearLayout layoutZoom = (LinearLayout) v.findViewById(R.id.zoom_layout);
        mZoomSeek = (SeekBar) v.findViewById(R.id.zoom_seekbar);

        mLayoutAutoExp = (LinearLayout) v.findViewById(R.id.autoexp_layout);
        Switch switchAutoExp = (Switch) v.findViewById(R.id.autoexp_switch);
        SeekBar seekAutoExp = (SeekBar) v.findViewById(R.id.autoexp_seekbar);

        LinearLayout layoutAutoWhiteBal = (LinearLayout) v.findViewById(R.id.autowhitebal_layout);

        mLayoutCapture = (RelativeLayout) v.findViewById(R.id.capture_layout);
        mCameraButton = (ImageView) v.findViewById(R.id.camera_button);
        mVideoButton = (ImageView) v.findViewById(R.id.video_button);
        ImageView btnExpTime = (ImageView) v.findViewById(R.id.expTime_button);
        ImageView btnFocus = (ImageView) v.findViewById(R.id.focus_button);
        ImageView btnAutoExp = (ImageView) v.findViewById(R.id.autoExp_button);
        ImageView btnAutoWhiteBal = (ImageView) v.findViewById(R.id.autowhitebal_button);
        ImageView btnIso = (ImageView) v.findViewById(R.id.iso_button);
        ImageView btnZoom = (ImageView) v.findViewById(R.id.zoom_button);
        ImageView btnSettings = (ImageView) v.findViewById(R.id.options_button);

        mCameraButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        if (inPicturePreview) {
                            try {
                                takePicture();
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        } else {
                            mCameraState = STATE_CAMERA;
                            mCameraButton.setImageResource(R.drawable.camera_btn_ctrl);
                            mVideoButton.setImageResource(R.drawable.video_btn);
                            startPreview();
                            buttonDelay();
                        }
                        Log.i("setOnTouchListener", "MotionEvent.ACTION_DOWN");
                        break;
                    case MotionEvent.ACTION_UP:

                        if (inPicturePreview) {
                            continuePreview();
                        }
                        buttonDelay();

                        Log.i("setOnTouchListener", "MotionEvent.ACTION_UP");
                        break;
                }
                return true;
            }
        });

        mTextureView.setOnTouchListener(mTextureClickListener);

        btnSettings.setOnClickListener(this);
        btnFocus.setOnClickListener(this);
        btnZoom.setOnClickListener(this);
        btnIso.setOnClickListener(this);
        btnAutoExp.setOnClickListener(this);
        btnExpTime.setOnClickListener(this);
        btnAutoWhiteBal.setOnClickListener(this);
        mVideoButton.setOnClickListener(this);

        CheckListener mCheckListener = new CheckListener();
        mSwitchAutoFoc.setOnCheckedChangeListener(mCheckListener);
        switchExpTime.setOnCheckedChangeListener(mCheckListener);
        switchAutoExp.setOnCheckedChangeListener(mCheckListener);
        switchIso.setOnCheckedChangeListener(mCheckListener);

        mSwitchAutoFoc.setChecked(true);
        switchIso.setChecked(true);
        switchAutoExp.setChecked(true);
        switchExpTime.setChecked(true);

        SeekListener mSeekListener = new SeekListener();
        seekAutoFoc.setOnSeekBarChangeListener(mSeekListener);
        seekAutoExp.setOnSeekBarChangeListener(mSeekListener);
        seekExpTime.setOnSeekBarChangeListener(mSeekListener);
        mZoomSeek.setOnSeekBarChangeListener(mSeekListener);
        seekIso.setOnSeekBarChangeListener(mSeekListener);

        seekAutoFoc.setEnabled(false);
        seekIso.setEnabled(false);
        seekAutoExp.setEnabled(false);
        seekExpTime.setEnabled(false);

        seekAutoFoc.setMax(100);
        seekIso.setMax(100);
        seekAutoExp.setMax(100);
        mZoomSeek.setMax(100);

        seekAutoFoc.setProgress(50);
        seekAutoExp.setProgress(50);
        seekIso.setProgress(50);
        mZoomSeek.setProgress(0);

        //numbers up top
        mLayoutList = new ArrayList<>();
        mLayoutList.add(mLayoutBottom);//0
        mLayoutList.add(mLayoutAutoFoc);//1
        mLayoutList.add(mLayoutAutoExp);//2
        mLayoutList.add(mLayoutExpTime); //3
        mLayoutList.add(layoutAutoWhiteBal);//4
        mLayoutList.add(mLayoutIso);//5
        mLayoutList.add(layoutZoom);//6
    }

    private void buttonDelay() {
        //50% transparent
        String delayStr = mSharedPreferences.getString(getString(R.string.pref_camera_button_delay),"");
        int delay;
        if (delayStr.equals("")) {
            delay = 2000;
        }else {
            delay = Integer.parseInt(delayStr)*1000;
        }
        mCameraButton.setAlpha(128);
        mVideoButton.setAlpha(128);
        mCameraButton.setEnabled(false);
        mVideoButton.setEnabled(false);
        final Handler buttonDisableHandler = new Handler();
        buttonDisableHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCameraButton.setEnabled(true);
                mVideoButton.setEnabled(true);
                mCameraButton.setAlpha(255);
                mVideoButton.setAlpha(255);
            }}, delay);
    }

    private View.OnTouchListener mTextureClickListener = new View.OnTouchListener(){

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            int action = event.getAction();

            if (event.getPointerCount() > 1) {
                if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    mFingerDist = getFingerSpacing(event);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    handlePinch(event);
                }
            } else {
                mSeekBarTextView.setVisibility(View.GONE);
                if (action == MotionEvent.ACTION_DOWN){
                    if (mSwitchAutoFoc.isChecked()) {
                        handleFocus(event);
                    }
                }
            }
            return true;
        }
    };

    private void handlePinch(MotionEvent event) {
        mSeekBarTextView.setVisibility(View.VISIBLE);
        showZoomFlag = true;
        showLayout(SHOW_ZOOM, showZoomFlag);

        float newDist = getFingerSpacing(event);
        int prog = mZoomSeek.getProgress();
        mPinchCount++;

        if (newDist > mFingerDist) {
            if (mPinchCount >10) {
                prog++;
                mZoomSeek.setProgress(prog);
            }
        }else if (newDist < mFingerDist) {
            prog--;
            mZoomSeek.setProgress(prog);
            mPinchCount = 0;
        }
        mFingerDist = newDist;
    }

    /** Determine the space between the first two fingers */
    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float)Math.sqrt(x * x + y * y);
    }

    private void initializeSeekBarVals() {
        valueAF = 5.0f;
        valueAETime = (214735991 - 13231) / 2;
        valueISO = (800 - 50) / 2;
        valueAE = 0;
    }

    private void handleFocus(MotionEvent event) {
        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int areaSize = 200;
        int right = rect.right;
        int bottom = rect.bottom;
        int viewWidth = mTextureView.getWidth();
        int viewHeight = mTextureView.getHeight();
        int ll, rr;
        Rect newRect;
        int centerX = (int) event.getX();
        int centerY = (int) event.getY();
        ll = ((centerX * right) - areaSize) / viewWidth;
        rr = ((centerY * bottom) - areaSize) / viewHeight;
        int focusLeft = clamp(ll, 0, right);
        int focusBottom = clamp(rr, 0, bottom);
        newRect = new Rect(focusLeft, focusBottom, focusLeft + areaSize, focusBottom + areaSize);
        MeteringRectangle meteringRectangle = new MeteringRectangle(newRect, 500);
        MeteringRectangle[] meteringRectangleArr = {meteringRectangle};
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangleArr);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        updatePreview();
    }

    private int clamp(int x, int min, int max) {
        if (x < min) {
            return min;
        } else if (x > max) {
            return max;
        } else {
            return x;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.video_button:
                if (inPicturePreview && !recording) {
                    mCameraState = STATE_VIDEO;
                    mVideoButton.setImageResource(R.drawable.video_btn_active);
                    mCameraButton.setImageResource(R.drawable.camera_btn);
                    startPreview();
                    buttonDelay();
                } else if (inVideoPreview && !recording) {
                    startRecordingVideo();
                    mVideoButton.setImageResource(R.drawable.video_btn_stop_active);
                } else if (inVideoPreview && recording) {
                    mVideoButton.setImageResource(R.drawable.video_btn_active);
                    stopRecordingVideo();
                }
                break;

            case R.id.focus_button:
                if (!manualSupport) {
                    makeToast("Your device does not support manual focus, turning on auto focus...");
                    mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
                    updatePreview();
                    return;
                }
                showAutoFocFlag = !showAutoFocFlag;
                showLayout(SHOW_AUTOFOC, showAutoFocFlag);
                break;

            case R.id.expTime_button:
                showExpTimeFlag = !showExpTimeFlag;
                showLayout(SHOW_EXPTIME, showExpTimeFlag);

                break;

            case R.id.zoom_button:
                showZoomFlag = !showZoomFlag;
                showLayout(SHOW_ZOOM, showZoomFlag);
                break;
            case R.id.autoExp_button:
                if (!manualSupport) {
                    makeToast("Your device does not support manual control!");
                    return;
                }
                showAutoExpFlag = !showAutoExpFlag;
                showLayout(SHOW_AUTOEXP, showAutoExpFlag);
                break;
            case R.id.autowhitebal_button:
                if (!manualSupport) {
                    makeToast("Your device does not support manual control!");
                    return;
                }
                showAutoWhitBalFlag = !showAutoWhitBalFlag;
                showLayout(SHOW_AUTOWHITBAL, showAutoWhitBalFlag);
                break;
            case R.id.iso_button:
                if (!manualSupport) {
                    makeToast("Your device does not support manual control!");
                    return;
                }
                showIsoFlag = !showIsoFlag;
                showLayout(SHOW_ISO, showIsoFlag);
                break;
            case R.id.options_button:
                Intent i = new Intent(getActivity(), PreferencesFragment.class);
                i.putExtra("cameraID", mCameraId);
                startActivity(i);
                break;
            case R.id.camera_closed_settings_btn:
                Intent ii = new Intent(getActivity(), PreferencesFragment.class);
                if (mCameraId != null) {
                    ii.putExtra("cameraID", mCameraId);
                } else {
                    ii.putExtra("cameraID", "0");
                }
                startActivity(ii);
                break;
            case R.id.camera_closed_btn:

                if (MainActivity.surfaceTextInitiated) {
                    mCameraClosedHolder.setVisibility(View.GONE);
                    mCameraSwapHolder.setVisibility(View.GONE);
                    mCameraOpenHolder.setVisibility(View.VISIBLE);
                    if (mTextureView.isAvailable()) {
                        try {
                            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
                        } catch (CameraAccessException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    makeToast("Please swipe left first");
                }
                break;
            case R.id.camera_preview_swap_btn:
                mCameraSwapHolder.setVisibility(View.GONE);
                mCameraOpenHolder.setVisibility(View.VISIBLE);
                startPreview();
                MagLevControlFrag.setPreviewState(inMagLevPreview);
                break;
        }
    }

    private void showLayout(int showWhat, boolean showOrNot) {
        View v = mLayoutList.get(showWhat);
        if (showOrNot) {
            for (int i = 0; i < mLayoutBottom.getChildCount(); i++) {
                if (mLayoutBottom.getChildAt(i).getVisibility() == View.VISIBLE) {
                    mLayoutBottom.getChildAt(i).setVisibility(View.INVISIBLE);
                }
            }
            v.setVisibility(View.VISIBLE);
        } else {
            for (int i = 0; i < mLayoutBottom.getChildCount(); i++) {
                if (mLayoutBottom.getChildAt(i).getVisibility() == View.VISIBLE) {
                    mLayoutBottom.getChildAt(i).setVisibility(View.INVISIBLE);
                }
            }
            mLayoutCapture.setVisibility(View.VISIBLE);
        }
    }

    private class SeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            if (mPreviewBuilder == null || getView() == null) {
                return;
            }
            switch (seekBar.getId()) {
                case R.id.focus_seekbar:
                    float minimumLens = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                    if (minimumLens == 0) {
                        makeToast("Device does not support manual control");
                        return;
                    }
                    float num = (((float) i) * minimumLens / 100);
                    mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, num);
                    int showNum = (int) num;
                    String msg4 = "Focus:" + showNum;
                    mSeekBarTextView.setText(msg4);
                    break;
                case R.id.zoom_seekbar:
                    Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
//                    int maxZoom = mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM).intValue();

                    //forced max zoom, camera characteristics picked out 4x for some reason...!!!
                    int maxZoom = 8;

                    assert rect != null;
                    int minL = (rect.width() - (rect.width() / maxZoom)) / 2;
                    int minR = rect.width() - minL;
                    int minT = (rect.height() - (rect.height() / maxZoom)) / 2;
                    int minB = rect.height() - minT;
                    Rect checkRect = new Rect(minL, minT, minR, minB);

                    Rect newRect = new Rect(i * minL / 100,
                            i * minT / 100,
                            rect.width() - (rect.width() - minR) * i / 100,
                            rect.height() - (rect.height() - minB) * i / 100);

                    if (newRect.height() * newRect.width() < checkRect.width() * checkRect.height()) {
                        makeToast("Zoom Error!");
                        return;
                    }
                    mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, newRect);
                    String msg = "Zoom:" + i + "%";
                    mSeekBarTextView.setText(msg);
                    break;
                case R.id.autoexp_seekbar:
                    Range<Integer> range1 = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
                    if (range1 == null) {
                        makeToast("Device does not support exposure control");
                        return;
                    }
                    int maxAE = range1.getUpper();
                    int minAE = range1.getLower();
                    int all = (-minAE) + maxAE;
                    int time = 100 / all;
                    int ae = ((i / time) - maxAE) > maxAE ? maxAE : ((i / time) - maxAE) < minAE ? minAE : ((i / time) - maxAE);
                    mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ae);
                    String msg2 = "Exposure Compensation: " + ae;
                    mSeekBarTextView.setText(msg2);
                    valueAE = ae;
                    break;

                case R.id.expTime_seek:
                    Range<Long> range3 = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
                    if (range3 == null) {
                        makeToast("Device does not support exposure time control");
                        return;
                    }
                    long max = range3.getUpper();
                    long min = range3.getLower();
//                    Log.d("Exposure", "Exp max: " + max);
//                    Log.d("Exposure", "Exp min: " + min);
                    long ae2 = ((i * (max - min)) / 100 + min);
                    mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ae2);
                    String msg5 = "Exposure Time: " + ae2;
                    mSeekBarTextView.setText(msg5);
                    valueAETime = ae2;
                    break;
                case R.id.iso_seekbar:
                    Range<Integer> range = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
                    if (range == null) {
                        makeToast("Device does not support iso!");
                        return;
                    }
                    int max1 = range.getUpper();
                    int min1 = range.getLower();
                    int iso = ((i * (max1 - min1)) / 100 + min1);
                    mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                    valueISO = iso;
                    String msg3 = "Iso: " + iso;
                    mSeekBarTextView.setText(msg3);
                    break;
            }
            updatePreview();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mSeekBarTextView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mSeekBarTextView.setVisibility(View.INVISIBLE);
        }
    }

    //only checks manual focus compatibility!
    private boolean checkCompatibility() {
        for (int aMCameraCapabilitiesList : mCameraCapabilitiesList) {
            if (aMCameraCapabilitiesList == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) {
                return true;
            }
        }
        return false;
    }

    private class CheckListener implements CompoundButton.OnCheckedChangeListener {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mPreviewBuilder == null || getView() == null) {
                return;
            }
            Switch switchIso = (Switch) getView().findViewById(R.id.iso_switch);
            Switch switchExpTime = (Switch) getView().findViewById(R.id.expTime_switch);
            Switch switchAutoExp = (Switch) getView().findViewById(R.id.autoexp_switch);

            switch (buttonView.getId()) {
                case R.id.focus_switch:
                    if (isChecked) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
                        mLayoutAutoFoc.getChildAt(1).setEnabled(false);
                    } else {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                        mLayoutAutoFoc.getChildAt(1).setEnabled(true);
                    }
                    break;
                case R.id.autoexp_switch:
                    //exposure compensation
                    if (isChecked) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        mLayoutAutoExp.getChildAt(1).setEnabled(false);
                    } else {
//                        int lastValueAEComp = mPreviewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
//                        long lastValueAETime = mPreviewBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
                        switchExpTime.setChecked(true);
                        switchIso.setChecked(true);
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
//                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, lastValueAEComp);
//                        mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, lastValueAETime);
                        mLayoutAutoExp.getChildAt(1).setEnabled(true);
                    }
                    break;
                case R.id.iso_switch:
                    if (isChecked) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        mLayoutIso.getChildAt(1).setEnabled(false);
                        switchExpTime.setChecked(true);
                    } else {
                        switchAutoExp.setChecked(true);
                        switchExpTime.setChecked(false);
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                        //forced default 1/10s shutter
                        mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long)107361380);
                        mLayoutIso.getChildAt(1).setEnabled(true);
                    }
                    break;
                case R.id.expTime_switch:
                    if (isChecked) {
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                        mLayoutExpTime.getChildAt(1).setEnabled(false);
                        switchIso.setChecked(true);
                    } else {
                        switchAutoExp.setChecked(true);
                        switchIso.setChecked(false);
                        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                        mLayoutExpTime.getChildAt(1).setEnabled(true);
                    }
                    break;
            }
            updatePreview();
        }
    }

    //-------------------------------------------------------//
    //------------- CAMERA AND VIDEO MANAGEMENT--------------//
    //-------------------------------------------------------//

    private TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "onSurfaceTextAvailable");

                    if (!mCameraConfig) {
                        try {
                            openCamera(width, height);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "onSurfaceTextChanged");
//                    if (MainActivity.inPreview && !mCameraConfig) {
//                        try {
//                            openCamera(width, height);
//                        } catch (CameraAccessException e) {
//                            e.printStackTrace();
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//
//                    }else if (MainActivity.inPreview && mCameraConfig) {
//                        configureTransform(width, height);
//                    }
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    Log.d(TAG, "onSurfaceTextDestroyed");

                    closeCamera();
                    releaseMediaRecorder();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            };

    private void setUpCameraOutputs(int width, int height) throws CameraAccessException {
        mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
        mCameraCapabilitiesList = mCameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        //ONLY MANUAL CHECK!!
        manualSupport = checkCompatibility();
        if (!manualSupport) {
            makeToast("Your device doesn't support full functionality, use camera intent instead");
        }

        StreamConfigurationMap map = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size largestImageSize = Collections.max(
                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());

        // Don't want to have it as too large...bandwidth issues
        mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                width, height, largestImageSize);

        mTextureView2 = MagLevControlFrag.getTexture();
        mTextureView2.setAspectRatio(9, 16);

        mPreviewSize2 = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                mTextureView2.getWidth(), mTextureView2.getHeight(), largestImageSize);

        Size videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
        mVideoPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), width,
                height, videoSize);
    }

    /**
     * Input choices of size for video recording
     * Size must be less than 1080 because of issues we had with reading 4k,
     * Now its taking size from preferences!
     *
     * @param choices     List of sizes from media recorder that are supported
     *
     * @return Best size given parameters, or arbitrary size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 16 / 9 && size.getHeight() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Input choices of size from cameraChar, chooses the smallest one whose
     * width and height are at least as large as the desired values, while maintaining
     * the aspect ratio
     *
     * @param choices     List of sizes from camera characteristics supported
     * @param width       Minimum desired width
     * @param height      Minimum desired height
     * @param aspectRatio Aspect ratio
     * @return Best size given parameters, or arbitrary size
     */

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming found
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /**
     * Configures transform to mTextureView
     * Call this after preview size is found and mTextureView is set
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Log.d(TAG, "ConfigTrans");
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        //cycle through map sizes, width and height will be passed as if in landscape
        for (Size option : mapSizes) {
            if (width > height) {
                //check if bigger than texture view requested width
                if (option.getWidth() > width &&
                        option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getWidth() > height &&
                        option.getHeight() > width) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return mapSizes[0];
    }

    /**
     * Setup camera to be opened well as begin session to open camera based on
     * parameters. Handled on background
     *
     * @param width       Width of surface
     * @param height      Height of surface
     */
    private void openCamera(int width, int height) throws CameraAccessException, InterruptedException {
        Log.d(TAG, "Open Camera");
        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);

        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        initOutputSurface();

        mCameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);
        mCameraConfig = true;
    }

    private void initOutputSurface() {
        String picDims = mSharedPreferences.getString(getString(R.string.pref_picture_quality_key), "");
        String[] picDimsArray = picDims.split("x");

        if (picDimsArray.length == 1) {
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    ImageFormat.JPEG,2);
        } else {
            String picWidth = picDimsArray[0];
            String picHeight = picDimsArray[1];
            mImageReader = ImageReader.newInstance(Integer.parseInt(picWidth),Integer.parseInt(picHeight),
                    ImageFormat.JPEG,2);
        }

        mImageReader.setOnImageAvailableListener(
                mOnImageAvailableListener, mBackgroundHandler);

        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        SurfaceTexture texture2 = mTextureView2.getSurfaceTexture();
        texture2.setDefaultBufferSize(mPreviewSize2.getWidth(),mPreviewSize2.getHeight());

        mSurface = new Surface(texture);
        mSurface2 = new Surface(texture2);
        mOutputSurfaces = new ArrayList<>(3);
        mOutputSurfaces.add(mImageReader.getSurface());
        mOutputSurfaces.add(mSurface);
//        mOutputSurfaces.add(mSurface2);
    }

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            Log.i("CameraStateCallback", "onClosed");
        }

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.i("CameraStateCallback", "onOpened");
            mCameraDevice = cameraDevice;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.i("Camera=StateCallback", "onDisconnected");
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.i(TAG, "Camera=StateCallbackError");
            Toast.makeText(getActivity(), "onError", Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * Begins capture request for a preview from the camera onto selected surfaces
     * Capture session and callback handled on background
     */
    //TODO add in camera permission check
    //start camera preview
    private void startPreview() {
        Log.d(TAG, "startPreview");
        lastAF = null;
        lastZoom = null;
        setupPreviewSwitch();

        if (mCameraState == STATE_CAMERA) {
            try {
                releaseMediaRecorder();
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewBuilder.addTarget(mSurface);
                initPreviewBuilder();
                mCameraState = STATE_CAMERA;
                mCameraDevice.createCaptureSession(Arrays.asList(mSurface, mImageReader.getSurface()), mSessionPreviewStateCallback, mBackgroundHandler);

                MagLevControlFrag.setPreviewState(false);
                inMagLevPreview = false;
                inPicturePreview = true;
                inVideoPreview = false;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }else if (mCameraState == STATE_VIDEO) {
            try {
                if (!mMediaPrepared) {
                    setupMediaRecorder();
                }

                SurfaceTexture texture = mTextureView.getSurfaceTexture();
                assert texture != null;
                texture.setDefaultBufferSize(mVideoPreviewSize.getWidth(), mVideoPreviewSize.getHeight());
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                List<Surface> surfaces = new ArrayList<>();
                Surface previewSurface = new Surface(texture);
                surfaces.add(previewSurface);
                mPreviewBuilder.addTarget(previewSurface);
                Surface recorderSurface = mMediaRecorder.getSurface();
                surfaces.add(recorderSurface);
                mPreviewBuilder.addTarget(recorderSurface);

                initPreviewBuilder();

                mCameraDevice.createCaptureSession(surfaces, mSessionPreviewStateCallback, mBackgroundHandler);
            } catch (CameraAccessException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupPreviewSwitch() {
        //todo add previous whitebal, etc...
        if (mPreviewBuilder != null ) {
            lastAF = mPreviewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE);
            lastZoom = mPreviewBuilder.get(CaptureRequest.SCALER_CROP_REGION);
        }
    }

    private void startPreviewMagLev() {
        Log.d(TAG, "startMagLevPreview");
        setupPreviewSwitch();
        try {

            if (mCameraState == STATE_CAMERA) {
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewBuilder.addTarget(mSurface2);
                initPreviewBuilder();
                mCameraDevice.createCaptureSession(Arrays.asList(mSurface2, mImageReader.getSurface()), mSessionPreviewStateCallback, mBackgroundHandler);

            }else if (mCameraState == STATE_VIDEO) {
                SurfaceTexture texture = mTextureView2.getSurfaceTexture();
                assert texture != null;
                texture.setDefaultBufferSize(mTextureView2.getWidth(), mTextureView2.getHeight());
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                List<Surface> surfaces = new ArrayList<Surface>();
                Surface previewSurface = new Surface(texture);
                surfaces.add(previewSurface);
                mPreviewBuilder.addTarget(previewSurface);
//                Surface recorderSurface = mMediaRecorder.getSurface();
//                surfaces.add(recorderSurface);
//                mPreviewBuilder.addTarget(recorderSurface);

                initPreviewBuilder();

                mCameraDevice.createCaptureSession(surfaces, mSessionPreviewStateCallback, mBackgroundHandler);
            }

            MagLevControlFrag.setPreviewState(true);
            inMagLevPreview = true;
            inPicturePreview = true;
            inVideoPreview = false;
            mCameraOpenHolder.setVisibility(View.GONE);
            mCameraSwapHolder.setVisibility(View.VISIBLE);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final BroadcastReceiver mPreviewUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Preview Broadcast Received");

            switch (intent.getAction()) {

                case (MagLevControlFrag.CAMERA_PREVIEW):
                    inMagLevPreview = intent.getBooleanExtra(MagLevControlFrag.MAGLEV_PREVIEW_STATE, false);

                    if (inMagLevPreview) {
                        mCameraSwapHolder.setVisibility(View.GONE);
                        mCameraOpenHolder.setVisibility(View.VISIBLE);
                        mCameraClosedHolder.setVisibility(View.GONE);
                        startPreview();
                    }else {
                        startPreviewMagLev();
                    }
                    break;

                case (MainActivity.CAMERA_RESTART):
                    break;

                case (MainActivity.CAMERA_CLOSE):
                    if (inPicturePreview || inVideoPreview) {
                        releaseCameraPreview();
                        releaseVideoPreview();
                        closeCamera();
                        releaseMediaRecorder();
                        mCameraOpenHolder.setVisibility(View.GONE);
                        mCameraClosedHolder.setVisibility(View.VISIBLE);
                        mCameraSwapHolder.setVisibility(View.GONE);
                    }
            }
        }
    };

    private void initPreviewBuilder() {
        Log.d(TAG, "initPrevBuilder");
        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, valueAF);
        mPreviewBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, valueAETime);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, valueAE);
        mPreviewBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, valueISO);

        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        //TODO fix legacy auto focus
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);

        if (lastAF != null && lastZoom != null) {
            mPreviewBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, lastAF);
            mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, lastZoom);
        } else {
            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
        }
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
    }


    private CameraCaptureSession.StateCallback mSessionPreviewStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.i("Thread", "onConfigured---->" + Thread.currentThread().getName());
            Log.i(TAG, "mSessionStateCallback--->onConfigured");
            if (mCameraDevice == null) {
                return;
            }
            if (mCameraState == STATE_CAMERA) {
                try {
                    CaptureRequest previewCaptureRequest = mPreviewBuilder.build();
                    mCameraCaptureSession = cameraCaptureSession;
                    //continuous images from camera
                    mCameraCaptureSession.setRepeatingRequest(
                            previewCaptureRequest,
                            mSessionCaptureCallback,
                            mBackgroundHandler
                    );
                    mAutoWhiteBalSeek.setmOnAwbSeekBarChangeListener(new AutoWhiteBalSeekListener(getActivity(), mSeekBarTextView, mPreviewBuilder, mCameraCaptureSession,
                            mBackgroundHandler, mSessionCaptureCallback));
                    inVideoPreview = false;
                    inPicturePreview = true;
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            } else if (mCameraState == STATE_VIDEO){

                try {
                    mCameraCaptureSession = cameraCaptureSession;
                    mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
                    inVideoPreview = true;
                    inPicturePreview = false;
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }

            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.i(TAG, "mSessionStateCallback--->onConfigureFailed");
        }

        @Override
        public void onReady(@NonNull CameraCaptureSession session) {
            super.onReady(session);
            Log.i(TAG, "mSessionStateCallback--->onReady");
        }

        @Override
        public void onActive(@NonNull CameraCaptureSession session) {
            super.onActive(session);
            Log.i(TAG, "mSessionStateCallback--->onActive");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            super.onClosed(session);
            Log.i(TAG, "mSessionStateCallback--->onClosed");
        }
    };

    private void takePicture() throws CameraAccessException {
        Log.d(TAG, "TakePicture");
        //manual support check, zero shutter not supported on SDK <21
        if (!manualSupport) {
            mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        }else {
            mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        }
        mCaptureBuilder.addTarget(mImageReader.getSurface());
        mCaptureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
//        Range<Integer> fps[] = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
//        mCaptureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps[fps.length - 1]);
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
        previewBuilder2CaptureBuilder();
        mCameraState = STATE_CAPTURE;

        mCameraDevice.createCaptureSession(mOutputSurfaces, mSessionCaptureStateCallback, mBackgroundHandler);
    }

    private void previewBuilder2CaptureBuilder() {
        //AUTO WHITE BALENCE
        if (mPreviewBuilder.get(CaptureRequest.CONTROL_AWB_MODE) == CameraMetadata.CONTROL_AWB_MODE_OFF) {
            mCaptureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
            mCaptureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mPreviewBuilder.get(CaptureRequest.CONTROL_AWB_MODE));
        }
//        //AUTO EXPOSURE
        if (mPreviewBuilder.get(CaptureRequest.CONTROL_AE_MODE) == CameraMetadata.CONTROL_AE_MODE_OFF) {
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            mCaptureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mPreviewBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME));
        }else if (mPreviewBuilder.get(CaptureRequest.CONTROL_AE_MODE) == CameraMetadata.CONTROL_AE_MODE_ON) {
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, mPreviewBuilder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION));
        }
        //AUTO FOCUS
        if (mPreviewBuilder.get(CaptureRequest.CONTROL_AF_MODE) == CameraMetadata.CONTROL_AF_MODE_OFF) {
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            mCaptureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mPreviewBuilder.get(CaptureRequest.LENS_FOCUS_DISTANCE));
        }
//        mCaptureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, mPreviewBuilder.get(CaptureRequest.CONTROL_EFFECT_MODE));
        //ISO
        mCaptureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mPreviewBuilder.get(CaptureRequest.SENSOR_SENSITIVITY));
        mCaptureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, mPreviewBuilder.get(CaptureRequest.CONTROL_AF_REGIONS));
        mCaptureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, mPreviewBuilder.get(CaptureRequest.CONTROL_AE_REGIONS));
//        mCaptureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, mPreviewBuilder.get(CaptureRequest.CONTROL_SCENE_MODE));
//        //zoom
        mCaptureBuilder.set(CaptureRequest.SCALER_CROP_REGION, mPreviewBuilder.get(CaptureRequest.SCALER_CROP_REGION));
    }

    private void continuePreview() {
        mCameraState = STATE_CAMERA;
        try {
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface), mSessionPreviewStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
//            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            makeToast("Focus failed");
        }
    };

    private CameraCaptureSession.StateCallback mSessionCaptureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "CaptureStateConfigured");
            try {
                CameraCaptureSession.CaptureCallback captureCallback =
                        new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                            }
                        };

                session.capture(mCaptureBuilder.build(), captureCallback, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, "CaptureStateError");
                e.printStackTrace();
            } catch (Exception e) {
                Log.e(TAG, "CaptureStateError");
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "CaptureSessionStateConfigFailed");
//            Toast.makeText(getApplicationContext(), "onConfigureFailed---Capture", Toast.LENGTH_SHORT).show();
        }
    };

    private void updatePreview() {
        try {
            if (mCameraState == STATE_VIDEO) {
                mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(),null, mBackgroundHandler);
            }else {
                mCameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), mSessionCaptureCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mCameraConfig = false;
    }

    private void setupMediaRecorder() throws IOException {
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //uses H262, AAC, 1920x1080
//        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        //todo fix setup so temp file isn't created (could do all onClick, not efficient)

        switch (mSharedPreferences.getString(getString(R.string.pref_video_format_key), "0")) {

            case "0":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                MEDIA_EXTENSION = ".mp4";
                break;
            case "1":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
                MEDIA_EXTENSION = ".aac";
                break;
            case "2":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
                MEDIA_EXTENSION = ".amr";
                break;
            case "3":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
                MEDIA_EXTENSION = ".amr";
                break;
            case "4":
                //default
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                MEDIA_EXTENSION = ".mp4";
                break;
            case "5":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.RAW_AMR);
                MEDIA_EXTENSION = ".amr";
                break;
            case "6":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                MEDIA_EXTENSION = ".3gp";
                break;
            case "7":
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.WEBM);
                MEDIA_EXTENSION = ".webm";
                break;
            default:
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
                MEDIA_EXTENSION = ".mp4";
                break;
        }
        try {
            mVideoFile = getOutputMediaFile(MEDIA_TYPE_VIDEO);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        if(mVideoFile != null) {
            mMediaRecorder.setOutputFile(mVideoFile.getAbsolutePath());
        } else Log.d(TAG, "ErrorVideoFile");

        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        //returns val/not entry
        String vidDims = mSharedPreferences.getString(getString(R.string.pref_video_quality_key), "");
        String[] vidDimsArray = vidDims.split("x");

        String vidWidth = vidDimsArray[0];
        String vidHeight = vidDimsArray[1];
        mMediaRecorder.setVideoSize(Integer.parseInt(vidWidth),Integer.parseInt(vidHeight));

        String vidEncoder = mSharedPreferences.getString(getString(R.string.pref_video_encoding_key),"0");
        String audioEncoder = mSharedPreferences.getString(getString(R.string.pref_audio_encoding_key),"0");

        //hardcoded array
        switch (vidEncoder) {

            case "0":
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
                break;
            case "1":
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
                break;
            case "2":
                //default generally
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                break;
            case "3":
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
                break;
            case "4":
                mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.VP8);
                break;
        }

        switch (audioEncoder) {

            case "0":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
                break;
            case "1":
                //default generally
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                break;
            case "2":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC_ELD);
                break;
            case "3":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                break;
            case "4":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
                break;
            case "5":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
                break;
            case "6":
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.VORBIS);
                break;
        }

//        mMediaRecorder.setOutputFile(getVideoFile(getActivity()).getAbsolutePath());
//        mMediaRecorder.setVideoEncodingBitRate(10000000);
//        mMediaRecorder.setVideoFrameRate(30);
//        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
//        mMediaRecorder.setVideoSize(3840,2160);
//        mMediaRecorder.setVideoSize(1920, 1080);
//        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
        mMediaPrepared = true;
    }

    private void startRecordingVideo () {
        Log.d(TAG, "StartRecording");
        recording = true;
        mMediaRecorder.start();
    }

    private void stopRecordingVideo() {
        Log.d(TAG, "StopRecording");
        recording = false;
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaPrepared = false;
        scanFile(mVideoFile.getAbsolutePath());
        makeToast("Video saved: " + mVideoFile.getAbsolutePath());
//        sendCameraBroadcast();
//        releaseMediaRecorder();
        startPreview();
    }

    public static boolean getCameraConfig() {
        return PreviewFrag.mCameraConfig;
    }

    private void releaseMediaRecorder(){
        if (recording) {
            mMediaRecorder.stop();
            recording = false;
        }

        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            //mCameraDevice.lock();           // lock camera for later use
            recording = false;
            mMediaPrepared = false;
        }
    }

    private void releaseVideoPreview () {
        if (mVideoCaptureSession != null) {
            mVideoCaptureSession.close();
            mVideoCaptureSession = null;
        }
        inVideoPreview = false;
    }

    private void releaseCameraPreview () {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader =  null;
        }
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        inPicturePreview = false;
    }

    private void closeBackgroundThread() {
        //block UI thread until this is shutdown
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera2 background thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }


    private void scanFile(String path) {
        MediaScannerConnection.scanFile(getActivity(),
                new String[]{path}, null,
                new MediaScannerConnection.OnScanCompletedListener() {

                    public void onScanCompleted(String path, Uri uri) {
                        Log.i("TAG", "Finished scanning " + path);
                    }
                });
    }

    public static Uri getOutputMediaFileUri(int type){
        try {
            return Uri.fromFile(getOutputMediaFile(type));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Create a File for saving an image or video */
    public static File getOutputMediaFile(int type) throws IOException{
        // using Environment.getExternalStorageState() before doing this.
        File directory = new File(Environment.getExternalStorageDirectory() + File.separator +
                "Maglev");
        // Create the storage directory if it does not exist
        if (! directory.exists()){
            if (! directory.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(directory.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(directory.getPath() + File.separator +
                    "VID_"+ timeStamp + MEDIA_EXTENSION);
        } else {
            return null;
        }
        return mediaFile;
    }

    private void makeToast(String msg) {
        Toast.makeText(getActivity(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(TAG, "OnAttach");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeCamera();
        closeBackgroundThread();
        releaseMediaRecorder();
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mPreviewUpdateReceiver);
        closeCamera();
        closeBackgroundThread();
        releaseMediaRecorder();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (afterCreate) {
            if (mSharedPreferences.getBoolean(getString(R.string.pref_camera_startup_key),true)){
                mCameraClosedHolder.setVisibility(View.GONE);
                mCameraOpenHolder.setVisibility(View.VISIBLE);
                mCameraSwapHolder.setVisibility(View.GONE);
            }else {
                mCameraClosedHolder.setVisibility(View.VISIBLE);
                mCameraOpenHolder.setVisibility(View.GONE);
                mCameraSwapHolder.setVisibility(View.GONE);
            }
        }else {
            mCameraClosedHolder.setVisibility(View.VISIBLE);
            mCameraOpenHolder.setVisibility(View.GONE);
            mCameraSwapHolder.setVisibility(View.GONE);
        }
        IntentFilter filter = new IntentFilter(MagLevControlFrag.CAMERA_PREVIEW);
        filter.addAction(MainActivity.CAMERA_CLOSE);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mPreviewUpdateReceiver,
                filter);
        openBackgroundThread();
        afterCreate = false;
    }
}
