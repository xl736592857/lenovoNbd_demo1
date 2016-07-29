package nbd.lenovo.ar;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.os.StatFs;
import android.util.Log;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements RecognizerHelper.CallbackRecognizer, TextureView.SurfaceTextureListener, Camera.PictureCallback, MediaRecorder.OnErrorListener, MediaPlayer.OnCompletionListener {
    private String TAG = "MainActivity";
    private boolean isPause = false;
    private Camera mCamera;//相机
    private TextureView mPreview;//预览
    private MediaRecorder mMediaRecorder;//录像
    private File mOutputFile;//视频录制的文件
    private RecognizerHelper mRecognizerHelper;//负责语音识别
    private String[] arrayRecognition;//需要识别的内容
    private String[] arrayPyRecognition;//识别内容的拼音
    private int recognitionIndex = 0;
    private boolean isRecording = false;//是否在录制
    private boolean isCapture = false;//是否在拍照
    private TextView tvPromt;//提示
    private CamcorderProfile profile;
    private boolean isSurfaceTexureAvaiable = false;
    private boolean isPreviewing = false;//是否在预览
    private int minMB = 50;//视频录制要求最小的内存
    private int delayCheckCacheTime = 1000;//视频录制时多长时间检测一次内存
    private String cacheError = "内存不足" + minMB + "MB无法录制";
    private Handler mHanler = new Handler();
    private TextView tvResult = null, tvRecordTime = null;
    private MediaPlayer player;
    private AssetFileDescriptor[] arrayFileDescriptor = null;
    private final int ErrorRecognition = 1, ErrorMismatching = 2, ErrorTimout = 3;
    private final long RecognitionRecordTime = 3000, RecognitiontimeOut = 2000;//RecognitionRecordTime 录音识别的时间 RecognitiontimeOut识别的超时时间
    private long currentRecordingSecond = 0;//语音识别倒计时
    private boolean isRecognitionError = false;//是否识别错误
    private long recordTime = 0;//录像的时间

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE); //设置无标题
        setContentView(R.layout.activity_main);
        initView();
        initInfo();
    }

    /**
     * 初始化view的信息
     */
    private void initView() {
        mPreview = (TextureView) findViewById(R.id.surface_view);
        tvPromt = (TextView) findViewById(R.id.tv_promt);
        tvRecordTime = ((TextView) findViewById(R.id.tv_record_time));
        tvResult = (TextView) findViewById(R.id.result);
    }
    /**
     * 初始化需要使用的信息
     */
    private void initInfo() {
        arrayRecognition = getResources().getStringArray(R.array.recognition_array);
        arrayPyRecognition = new String[arrayRecognition.length];
        for (int i = 0; i < arrayRecognition.length; i++) {
            arrayPyRecognition[i] = SpellUtil.converterToSpell(arrayRecognition[i]);
        }
        int[] soudResource = new int[]{R.raw.capture, R.raw.recordvideo, R.raw.remotesensing};
        arrayFileDescriptor = new AssetFileDescriptor[soudResource.length];
        for (int i = 0; i < arrayFileDescriptor.length; i++) {
            arrayFileDescriptor[i] = this.getResources()
                    .openRawResourceFd(soudResource[i]);
        }
        mPreview.setSurfaceTextureListener(this);
        profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        mRecognizerHelper = RecognizerHelper.getInstance(this, this);
    }

    /**
     * 销毁播放器
     */
    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    /**
     * 开始播放声音
     */
    private void playSound() {
        try {
            tvRecordTime.setText("等待显示数字开始说话");
            Intent i = new Intent("com.android.music.musicservicecommand");
            i.putExtra("command", "pause");
            sendBroadcast(i);
            releasePlayer();
            player = new MediaPlayer();
            player.setOnCompletionListener(this);
            player.setDataSource(arrayFileDescriptor[recognitionIndex].getFileDescriptor(), (long) (arrayFileDescriptor[recognitionIndex].getStartOffset()), (long) (arrayFileDescriptor[recognitionIndex].getLength()));
            player.prepare();
            player.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_CAMERA || event.getKeyCode() == KeyEvent.KEYCODE_BACK)) {
            if (isRecording) {
                stopVideoRecord();
                recognitionIndex = 2;
                playSound();
                return true;
            }

        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPause = false;
        if (isSurfaceTexureAvaiable && !isPreviewing && recognitionIndex < 2) {
            preparedCamera();
        } else if (recognitionIndex == 2) {
            playSound();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRecording) {
            stopVideoRecord();
            recognitionIndex = 2;
        }
        mHanler.removeCallbacks(null);
        isPause = true;
        mRecognizerHelper.stopRecognizerRecord();
        mRecognizerHelper.cancleRecognition();
        releasePlayer();
        // if we are using MediaRecorder, release it first
        releaseMediaRecorder();
        // release the camera immediately on pause event
        releaseCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecognizerHelper.release();
        for (int i = 0; i < arrayFileDescriptor.length; i++) {
            try {
                arrayFileDescriptor[i].close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Process.killProcess(Process.myPid());
    }


    @Override
    public void onReadyForSpeech(Bundle bundle) {
        isRecognitionError = false;
        currentRecordingSecond = RecognitionRecordTime;
        tvPromt.setText(arrayRecognition[recognitionIndex]);
        tvPromt.setVisibility(View.VISIBLE);
        tvRecordTime.setText(currentRecordingSecond / 1000 + "");
        mHanler.postDelayed(RecognitionStop, 1000);
    }


    @Override
    public void onError(int i) {
        LogUtil.logE(TAG, "error:" + i);
        if (i == 9 || i == 3) {
            ToastUtil.showToast(getApplicationContext(), "无法获得录音权限");
            finish();
        } else {
            onRecognitionError(ErrorRecognition, i);
        }

    }

    /**
     * @param error error[0] 1,识别出错 2,识别不匹配 3,超时未识别出结果
     *              error[1]  1,ERROR_NETWORK_TIMEOUT	网络超时
     *              2	ERROR_NETWORK	网络错误
     *              3	ERROR_AUDIO	录音错误
     *              4	ERROR_SERVER	服务端错误
     *              5	ERROR_CLIENT	客户端调用错误
     *              6	ERROR_SPEECH_TIMEOUT	超时
     *              7	ERROR_NO_MATCH	没有识别结果
     *              8	ERROR_RECOGNIZER_BUSY	引擎忙
     *              9	ERROR_INSUFFICIENT_PERMISSIONS	缺少权限
     */
    private void onRecognitionError(int... error) {
        mHanler.removeCallbacks(RecognitionTimeoutCheck);
        mHanler.removeCallbacks(RecognitionStop);
        isRecognitionError = true;
        if (!isPause) {
            tvRecordTime.setText("声音无法识别");
            playSound();
        }
    }

    /**
     * 拍照
     */
    private void capture() {
        if (!isCapture) {
            isCapture = false;
            tvPromt.setVisibility(View.GONE);
            mCamera.takePicture(null, null, this);
        }

    }

    /**
     * 录制视频
     */
    private void startVideoRecord() {
        tvRecordTime.setText("");
        if (!isRecording) {
            if (getSDAvailableSize() < minMB) {
                onRecordError(cacheError);
                return;
            }
            isRecording = true;
            new MediaPrepareTask().execute(null, null, null);
        }
    }

    /**
     * 停止录像
     */
    private void stopVideoRecord() {
        mHanler.removeCallbacks(RecordCheckThread);
        if (mMediaRecorder != null) {
            // stop recording and release camera
            try {
                mMediaRecorder.stop();  // stop the recording
            } catch (RuntimeException e) {
                // RuntimeException is thrown when stop() is called immediately after start().
                // In this case the output file is not properly constructed ans should be deleted.
                Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                //noinspection ResultOfMethodCallIgnored
                mOutputFile.delete();
            }
            releaseMediaRecorder(); // release the MediaRecorder object
            mCamera.lock();         // take camera access back from MediaRecorder
            // inform the user that recording has stopped
            isRecording = false;
            releaseCamera();
            ToastUtil.showToast(getApplicationContext(), "录像存储至" + mOutputFile.getAbsolutePath());
            recognitionIndex = 2;
            playSound();
        }
    }

    /**
     * 跳转至远程交互
     */
    private void fowardRemoteSensing() {
        Intent intent = new Intent();
        PackageManager packageManager = this.getPackageManager();
        intent = packageManager.getLaunchIntentForPackage("sinofloat.wvp");
        if (intent != null) {
            tvRecordTime.setText("");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else {
            ToastUtil.showToast(getApplicationContext(), "请确认是否安装了WVP客户端");
            playSound();
        }

    }
    /**
     * 获取到识别结果
     *
     * @param arrayList 识别结果
     */
    private void onGetRecognitionResult(ArrayList<String> arrayList) {
        StringBuffer sb=new StringBuffer();
        if (!isRecognitionError) {
            mHanler.removeCallbacks(RecognitionTimeoutCheck);
            mHanler.removeCallbacks(RecognitionStop);
            if (arrayList != null && arrayList.size() > 0) {
                boolean isMatch = false;
                for (String result : arrayList) {
                    sb.append(result+",");
                    LogUtil.logV(TAG, result);
                    isMatch = result.equals(arrayRecognition[recognitionIndex]);
                    if (!isMatch) {
                        String spell = SpellUtil.converterToSpell(result);
                        isMatch = spell.equals(arrayPyRecognition[recognitionIndex]);
                    }
                    if (isMatch) {
                        break;
                    }
                }
                String result=sb.toString();
                if (isMatch) {
                    tvResult.setText("识别为:"+result.substring(0,result.length()-1));
                    switch (recognitionIndex) {
                        case 0:
                            capture();
                            break;
                        case 1:
                            startVideoRecord();
                            break;
                        case 2:
                            fowardRemoteSensing();
                            break;
                    }
                } else {

                    tvResult.setText("识别为:"+result.substring(0,result.length()-1));
                    onRecognitionError(ErrorMismatching);
                }
            }
        }
    }
    @Override
    public void onResults(Bundle bundle) {
        ArrayList<String> listValue = bundle.getStringArrayList("results_recognition");
        onGetRecognitionResult(listValue);
    }

    @Override
    public void onBeginningOfSpeech() {
        mHanler.removeCallbacks(RecognitionStop);
        mHanler.removeCallbacks(RecognitionTimeoutCheck);
        tvRecordTime.setText("检测到声音...");
    }

    @Override
    public void onEndOfSpeech() {
        mRecognizerHelper.stopRecognizerRecord();
        tvRecordTime.setText("正在识别中...");
    }
    /**
     * 释放mediaRecorder
     */
    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            mCamera.lock();
        }
    }

    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }
    /**
     * 开始预览
     *
     * @return true succ false fail
     */
    private boolean startPreview() {
        // BEGIN_INCLUDE (configure_preview)
        mCamera = CameraHelper.getDefaultBackFacingCameraInstance();
        if (mCamera == null) {
            return false;
        }
        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, mPreview.getWidth(), mPreview.getHeight());
        // Use the same size for recording profile.
        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;
        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mCamera.setParameters(parameters);
        try {
            // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
            // with {@link SurfaceView}
            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
            mCamera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }
        return true;
    }

    private void preparedCamera() {
        if (recognitionIndex < 2) {
            isPreviewing = startPreview();
            if (isPreviewing) {
                playSound();
            } else {
                ToastUtil.showToast(getApplicationContext(), "请确认是否开启相机权限");
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean startVideoRecorder() {
        if (!isPreviewing) {
            return false;
        }
        // BEGIN_INCLUDE (configure_media_recorder)
        mMediaRecorder = new MediaRecorder();
        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);
        mMediaRecorder.setOnErrorListener(this);
        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(profile);

        // Step 4: Set output file
        mOutputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
        if (mOutputFile == null) {
            return false;
        }
        mMediaRecorder.setOutputFile(mOutputFile.getPath());
        // END_INCLUDE (configure_media_recorder)

        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        isSurfaceTexureAvaiable = true;
        preparedCamera();
    }
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
    }
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        isSurfaceTexureAvaiable = false;
        isPreviewing = false;
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }
    @Override
    public void onPictureTaken(byte[] bytes, Camera camera) {
        mCamera.stopPreview();
        boolean saveResult = false;
        File imgFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_IMAGE);
        if (imgFile != null) {
            try {
                saveResult = FileUtil.writeDataToFile(bytes, imgFile);
            } catch (IOException e) {
                e.printStackTrace();
                saveResult = false;
            }
        } else {
            saveResult = false;
        }
        if (!saveResult) {
            ToastUtil.showToast(getApplicationContext(), "拍照存储失败");
            mCamera.startPreview();
            recognitionIndex = 0;
            mRecognizerHelper.startRecognition();
        } else {
            ToastUtil.showToast(getApplicationContext(), "照片存储至" + imgFile.getAbsolutePath());
            mHanler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCamera.startPreview();
                    recognitionIndex = 1;
                    playSound();
                }
            }, 1000);
        }
        isCapture = false;

    }
    /**
     * 获得sd卡剩余容量，即可用大小 单位(MB)
     *
     * @return
     */
    private long getSDAvailableSize() {
        File path = Environment.getExternalStorageDirectory();
        StatFs stat = new StatFs(path.getPath());
        long blockSize = stat.getBlockSize();
        long availableBlocks = stat.getAvailableBlocks();
        return blockSize * availableBlocks / 1024 / 1024;
    }

    private void onRecordError(String error) {
        ToastUtil.showToast(getApplicationContext(), error);
        recognitionIndex = 2;
        mRecognizerHelper.startRecognition();
    }

    @Override
    public void onError(MediaRecorder mediaRecorder, int i, int i1) {
        stopVideoRecord();
        onRecordError("error:" + i);
    }
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mRecognizerHelper.startRecognition();
    }
    /**
     * 录像线程
     */
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            if (!startVideoRecorder()) {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                return false;
            }
            return true;
        }
        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                ToastUtil.showToast(getApplicationContext(), "录制失败");
                MainActivity.this.finish();
            } else {
                tvPromt.setText("录像中 00:00");
                mHanler.postDelayed(RecordCheckThread, delayCheckCacheTime);
            }

        }
    }
    /**
     * 停止录音线程
     */
    Thread RecognitionStop = new Thread() {
        @Override
        public void run() {
            super.run();
            currentRecordingSecond -= 1000;
            if (currentRecordingSecond <= 0) {
                mRecognizerHelper.stopRecognizerRecord();
                tvRecordTime.setText("正在识别中...");
                mHanler.postDelayed(RecognitionTimeoutCheck, RecognitiontimeOut);
            } else {
                tvRecordTime.setText(currentRecordingSecond / 1000 + "");
                mHanler.postDelayed(RecognitionStop, 1000);
            }

        }
    };
    /**
     * 检测内存并更新录制时间
     */
    Thread RecordCheckThread = new Thread() {
        @Override
        public void run() {
            super.run();
            if (isRecording) {
                if (getSDAvailableSize() < minMB) {
                    stopVideoRecord();
                    onRecordError(cacheError);
                } else {
                    recordTime += 1;
                    long seconds = recordTime % 60;
                    long minus = recordTime / 60;
                    String secondStr = seconds > 9 ? (seconds + "") : ("0" + seconds);
                    String minusStr = minus > 9 ? (minus + "") : ("0" + minus);
                    String time = minusStr + ":" + secondStr;
                    tvPromt.setText("录像中 " + time);
                    mHanler.postDelayed(RecordCheckThread, delayCheckCacheTime);
                }
            }

        }
    };
    /**
     * 识别超时检测
     */
    Thread RecognitionTimeoutCheck = new Thread() {
        @Override
        public void run() {
            super.run();
            mRecognizerHelper.cancleRecognition();
            onRecognitionError(ErrorTimout);

        }
    };
}
