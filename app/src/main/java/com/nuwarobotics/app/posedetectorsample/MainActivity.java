package com.nuwarobotics.app.posedetectorsample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.nuwarobotics.app.posedetectorsample.util.CameraPreview;
import com.nuwarobotics.app.posedetectorsample.util.GrpcSurfaceView;
import com.nuwarobotics.posedetect.ImageRequest;
import com.nuwarobotics.posedetect.Pose;
import com.nuwarobotics.posedetect.PoseDetector;
import com.nuwarobotics.posedetect.PoseDetectorFactory;
import com.nuwarobotics.posedetect.PoseListener;
import com.nuwarobotics.posedetect.Result;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.nuwarobotics.app.posedetectorsample.util.Utility.getNV21;
import static com.nuwarobotics.app.posedetectorsample.util.Utility.newImageRequest;
import static com.nuwarobotics.app.posedetectorsample.util.Utility.rotateYUV420Degree180;
import static com.nuwarobotics.app.posedetectorsample.util.Utility.scale;

public class MainActivity extends AppCompatActivity {
    public static final int PERMISSIONS_REQUEST = 1001;
    public static final int PICK_ACTION_SKELETON_PIC_RESULT_CODE = 1002;
    private final String TAG = this.getClass().getSimpleName();
    //===preview
    Config config;
    Camera camera;
    float requestFps;
    float responseFps;
    BlockingQueue<ImageRequest> imageQueue = new LinkedBlockingQueue<ImageRequest>();
    Handler checkPoseHandler;
    int camid = Camera.getNumberOfCameras() > 1 ? 1 : 0;
    long lastPreviewTimestamp = 0;
    private int POSE_ID_SAMPLE = 96;
    private int pushup = 102;
    private PoseDetector mPoseDetector = null;
    private ImageView mIvSkeletonImage;
    private TextView mTvPoseScore;
    private TextView mTvPoseJson;
    private TextView timeview;
    private TextView countview;
    private TextView Lfhd;
    private TextView Rthd;
    private TextView txt;
    private Uri mSourceBitmap = null;
    private String mPoseListJsonData = null;
    private String PushUpJsonData = null;
    private int count = 0;


    private Button mBtnPreviewSwitcher;
    private Button mBtnCameraSwitcher;
    private FrameLayout framePreview;
    private ScrollView scrollView;
    private CameraPreview cameraPreview = null;
    Timer timer;
    TimerTask timerTask;
    boolean timeStarted = false;
    Double time = 0.0;

    int Left_hand = 4;
    int Right_hand = 7;
    float ball_x = new Random().nextInt(320-20)+20;
    float ball_y = new Random().nextInt(240-20)+20;
    int s_width;
    int s_height;
    float ball_radius = 20;
    private static final DecimalFormat df = new DecimalFormat("0.00");

    private boolean previewOn = false;
    private CheckPoseRunnable checkPoseRunnable = null;
    private GrpcSurfaceView resultView;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private PoseResultCallback mPoseResultCallback = new PoseResultCallback<Pose>() {
        @Override
        public void onPoseResponse(Pose pose) {
            Log.d(TAG, "Id: " + pose.getId());
            //Log.d(TAG, "PoseSkeleton: " + Arrays.toString(pose.getPoseSkeleton()));
            //Log.d(TAG, "Likelihood: " + Arrays.toString(pose.getLikelihood()));
            //Log.d(TAG, "PoseWeight: " + Arrays.toString(pose.getPoseWeight()));
            //Log.d(TAG, "Score: " + pose.getScore());

            float[] Skeleton;
            Skeleton = pose.getPoseSkeleton();
            if (Skeleton[0] != 0.0) {
                Rthd.setText("Rthd: " + Math.round(Skeleton[Left_hand*2]*100.0)/100 + ", "+ Math.round(Skeleton[Left_hand*2+1]*100.0)/100 + ", " + get_distance(Skeleton[Left_hand*2],Skeleton[Left_hand*2+1]));
                Lfhd.setText("Lfhd: " + Math.round(Skeleton[Right_hand*2]*100.0)/100 + ", "+ Math.round(Skeleton[Right_hand*2+1]*100.0)/100 + ", " + get_distance(Skeleton[Right_hand*2],Skeleton[Right_hand*2+1]));
                }
            if (hit_ball(get_distance(Skeleton[Left_hand*2],Skeleton[Left_hand*2+1])) || hit_ball(get_distance(Skeleton[Right_hand*2],Skeleton[Right_hand*2+1]))){
                count++ ;
                countview.setText("Count: "+String.valueOf(count));
                ball_x = new Random().nextInt(s_width-20)+20;
                ball_y = new Random().nextInt(s_height-20)+20;

            }
        }
    };

    private double get_distance(double x1, double y1) {
        return Math.round(Math.sqrt(Math.pow(x1 - ball_x, 2) + Math.pow(y1 - ball_y, 2))*100)/100-ball_radius;
    }


    private boolean hit_ball(double real_distance){
        if (real_distance<=10){
            return true;
        }
        return false;
    }
    private Bitmap renderedBmp;
    private DetectResultCallback detectResultCallback = new DetectResultCallback() {
        @Override
        public void onDetectInterrupt(Exception e) {

        }

        @Override
        public void onImageResponse(Object object) {
            Result result = (Result) object;
            Log.d(TAG, "onImageResponse:" + result.toString());
            Bitmap bmp = Bitmap.createBitmap(result.image.getWidth(), result.image.getHeight(), Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(ByteBuffer.wrap(result.image.getData()));
            MainActivity.this.onImageResponse(result.postId, result.score, result.image.getWidth(), result.image.getHeight(), bmp);
        }
    };

    public Handler getCheckPoseHandler() {
        return checkPoseHandler;
    }

    private void initPoseChecker() {
        this.config = new Config();
        HandlerThread handlerThread = new HandlerThread("checkPose");
        handlerThread.start();
        checkPoseHandler = new Handler(handlerThread.getLooper());
    }

    public DetectResultCallback getDetectResultCallback() {
        return detectResultCallback;
    }

    private void onImageResponse(int poseId, int score, int width, int height, Bitmap bmp) {
        Log.i(TAG, MessageFormat.format(
                "Got image poseId: {0} , score: {1}, width: {2}, height: {3}",
                poseId,
                score,
                width,
                height));

        s_width = width;
        s_height = height;

        if (resultView == null) {
            return;
        }

        drawSkeleton(bmp);

        checkPose(pushup, false);
    }

    public boolean checkPose(int poseId, boolean isFirst) {
        stopCheck();
        if (checkPoseRunnable == null) {
            return false;
        }

        startCaptureFrame(poseId);
        if (isFirst) {
            checkPoseHandler.postDelayed(checkPoseRunnable, 3000);
        } else {
            checkPoseHandler.post(checkPoseRunnable);
        }

        return true;
    }

    protected CheckPoseRunnable createCheckPoseRunnable() {
        return new CheckPoseRunnable<ImageRequest, Result>(this) {
            @Override
            protected void requestProcess(ImageRequest imageRequest) {
                Log.i(TAG, "requestProcess()...");
                Log.i(TAG, "image ticket:" + imageRequest.image.getTicketId());
                mPoseDetector.compare(imageRequest, new PoseListener.OnCompareListener() {
                    @Override
                    public void onCompare(Result result) {
                        Log.i(TAG, "onCompare()...");
                        onSuccess(result);
                        onCompleted();
                    }
                });
            }
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_DENIED) {
                    finish();
                    return;
                }
            }
            goAfterCheckedPermission();
        }
    }

    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            goAfterCheckedPermission();
        } else {
            requestPermissions(
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST
            );
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case PICK_ACTION_SKELETON_PIC_RESULT_CODE:
                if (data == null) {
                    Log.e(TAG, "onActivityResult: data is null");
                    return;
                }

                Log.i(TAG, "Result Action skeleton PICTURE URI " + data.getData());
                updatePose(data.getData());
            default:
                Log.d(TAG, "requestCode: " + requestCode);
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermission();
        setContentView(R.layout.activity_main);
        initPoseChecker();
        timer = new Timer();
        PushUpJsonData = LoadJsonFromAsset("PushUp");
        Log.d("PushUpJsonData",PushUpJsonData);
    }

    public String LoadJsonFromAsset(String name) {
        String json = null;
        try {
            InputStream in = this.getAssets().open(name + ".json");
            int size = in.available();
            byte[] bbuffer= new byte[size];
            in.read(bbuffer);
            in.close();
            json = new String(bbuffer, "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return json;
    }

    private void goAfterCheckedPermission() {
        //initial PoseDetector
        mPoseDetector = PoseDetectorFactory.newInstance(PoseDetectorFactory.MLKIT, getApplication());
        mPoseDetector.initialize();
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mIvSkeletonImage = this.findViewById(R.id.ivSkeletonImage);
        mTvPoseJson = this.findViewById(R.id.tvPoseJson);
        framePreview = this.findViewById(R.id.camera_preview);
        scrollView = this.findViewById(R.id.ScrollView);
        resultView = this.findViewById(R.id.result_view);

        timeview = this.findViewById(R.id.time);
        countview = this.findViewById(R.id.Count);
        Lfhd = this.findViewById(R.id.Lfhd);
        Rthd = this.findViewById(R.id.Rthd);
        txt = this.findViewById(R.id.txt);
        mBtnPreviewSwitcher = this.findViewById(R.id.btnPreviewSwitcher);
        mBtnPreviewSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (previewOn) {
                    timeStarted = false;
                    stopPreview();
                    mBtnCameraSwitcher.setVisibility(View.INVISIBLE);
                    mBtnPreviewSwitcher.setText("start");
                    count = 0;
                    time = 0.0;
                    timerTask.cancel();
                    countview.setText("Count");
                    timeview.setText("Time");
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            Lfhd.setText("Lfhd :");
                            Rthd.setText("Rthd :");
                        }
                    }, 500);   //0.5 seconds
                } else {
                    timeStarted = true;
                    startPreview((Button) view, camid);
                    scrollView.setVisibility(View.INVISIBLE);
                    mBtnPreviewSwitcher.setText("end");
                    mBtnCameraSwitcher.setVisibility(View.VISIBLE);
                    mBtnPreviewSwitcher.setEnabled(false);
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            startTimer();
                            mBtnPreviewSwitcher.setEnabled(true);
                        }
                    }, 3000);   //3 seconds
                }
                previewOn = !previewOn;
            }
        });

        mBtnCameraSwitcher = this.findViewById(R.id.btnCameraSwitcher);
        mBtnCameraSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (camid == 1){
                    camid = 0 ;
                    mBtnCameraSwitcher.setText("FRONT");
                } else {
                    camid = 1 ;
                    mBtnCameraSwitcher.setText("REAR");
                }
                stopPreview();
                startPreview((Button) view, camid);
            }
        });
    }

    private void startTimer() {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        time++ ;
                        timeview.setText(getTimeText());
                        Log.d("Time: ", getTimeText());
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask,0,1000);
    }
    private String getTimeText() {
        int rounded = (int) Math.round(time);

        int seconds = ((rounded % 86400) % 3600) % 60;
        int minutes = ((rounded % 86400) % 3600) / 60;
        int hours = ((rounded % 86400) / 60);
        return formatTime(seconds, minutes, hours);
    }
    private String formatTime(int seconds, int minutes, int hours){
        return String.format("%02d",hours) + ":" + String.format("%02d",minutes) + ":" + String.format("%02d",seconds);
    }

    private void startPreview(Button view, int id) {
        loadPoseJson(PushUpJsonData);
        checkPoseRunnable = createCheckPoseRunnable();
        startPreview(camid);
        checkPose(pushup, true);
    }

    private void stopPreview() {
        endPreview();

        checkPoseRunnable.stop();
        if (checkPoseRunnable != null) {
            checkPoseHandler.removeCallbacks(checkPoseRunnable);
        }
        //view.setText("start");
    }

    public void stopCheck() {
        if (checkPoseRunnable == null) {
            return;
        }
        stopCaptureFrame();
        checkPoseRunnable.stop();
    }

    private void updateSkeletonImage(Pose pose) {
        Bitmap srcBitmap = getSampleBitmap(mSourceBitmap);
        Bitmap tempImage = srcBitmap.copy(Bitmap.Config.ARGB_8888, true);
        mPoseDetector.renderPoseToBitmap(pose, tempImage, false);     //paint skeleton on image
        mIvSkeletonImage.setImageBitmap(Bitmap.createBitmap(tempImage));       // update image

        List<Pose> poseList = new ArrayList<>();
        pose.setScore(1.0f);    // set score level -
        poseList.add(pose);
        mPoseListJsonData = transferPoseJSON(poseList); // transfer pose data
        if (previewOn) {
            stopPreview();
            previewOn = false;
        }

        if (mPoseListJsonData != null && mPoseListJsonData.length() > 10) {
            Log.d("mPoseListJsonData",mPoseListJsonData);
            //Log.d("PaperJsonData",PaperJsonData);
            Log.d("PushUpJsonData",PushUpJsonData);
            //Log.d("Gym2JsonData",Gym2JsonData);
            mBtnPreviewSwitcher.setEnabled(true);
            mTvPoseJson.setText(mPoseListJsonData);
        }
    }

    private String transferPoseJSON(List<Pose> poseList) {
        Gson gson = new Gson();
        String jsonPoseSkeleton = gson.toJson(poseList);
        return jsonPoseSkeleton;
    }

    private void loadPoseJson(String jsonPose) {
        if (jsonPose != null) {
            mPoseDetector.loadPoses(jsonPose);
        }
    }

    private void uploadActionResource(String s, String s2, int pickActionHintPicResultCode) {
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType(s);
        chooseFile = Intent.createChooser(chooseFile, s2);
        startActivityForResult(chooseFile, pickActionHintPicResultCode);
    }

    public void updatePose(Uri image) {
        if (image != null) {
            mSourceBitmap = image;
            float rate = getRate(image);
            Bitmap bitmapSrc = getSampleBitmap(image);
            Bitmap bitmapSample = scale(bitmapSrc, rate);   //scale the source image

            Bitmap processedBitmap = bitmapSample.copy(Bitmap.Config.ARGB_8888, true);
            ImageRequest request = newImageRequest(POSE_ID_SAMPLE, processedBitmap.getWidth(), processedBitmap.getHeight(), getNV21(processedBitmap.getWidth(), processedBitmap.getHeight(), processedBitmap), ImageFormat.NV21);
            mIvSkeletonImage.setImageBitmap(bitmapSample);// update image

            //detect skeleton from image
            mPoseDetector.detect(request, new PoseListener.OnDetectListener() {
                @Override
                public void onDetect(Pose pose) {
                    Log.i(TAG, "onDetect()...");
                    mPoseResultCallback.onPoseResponse(pose);
                }
            });
        } else {
            Log.e(TAG, " SkeletonImage is null");
        }
    }

    private Bitmap getSampleBitmap(Uri source) {
        try {
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;  //optional
            InputStream inputStr = getBaseContext().getContentResolver().openInputStream(source);
            Bitmap bitmapSrc = BitmapFactory.decodeStream(inputStr, null, bitmapOptions);
            Log.i(TAG, "[" + bitmapOptions.outWidth + "," + bitmapOptions.outHeight + "]===");
            return bitmapSrc;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private float getRate(Uri source) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream inputStream = getBaseContext().getContentResolver().openInputStream(source);
            BitmapFactory.decodeStream(inputStream, null, options);
            int imageHeight = options.outHeight;
            int imageWidth = options.outWidth;
            Log.i(TAG, "[" + imageWidth + "," + imageHeight + "]===");
            //source image is 640X480
            float hh = 480f; // height
            float ww = 640f; // width
            float rate = 1;
            if (imageWidth > imageHeight && imageWidth > ww) { // Scale base on width if width bigger than height
                rate = ww / imageWidth;
            } else if (imageWidth < imageHeight && imageHeight > hh) {// Scale base on height if height bigger than width
                rate = hh / imageHeight;
            }
            return rate;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    //--------------------------
    private void startPreview(int id) {
        Camera camera = openCameraAndPreview(id);
        cameraPreview = new CameraPreview(getApplicationContext(), camera);
        framePreview.addView(cameraPreview);
        resultView.setVisibility(View.VISIBLE);
    }

    private void endPreview() {
        if (cameraPreview != null) {
            stopCheck();
            framePreview.removeView(cameraPreview);
            cameraPreview = null;
            resultView.setVisibility(View.INVISIBLE);
        }
    }

    //------------------------------------
    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;

        try {
            releaseCamera();
            camera = Camera.open(id);
            qOpened = (camera != null);

            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> list = parameters.getSupportedPictureSizes();
            Camera.Size previewSize = null;
            for (Camera.Size size : list) {
                if (size.width <= config.MAX_FRAME_WIDTH && size.height <= config.MAX_FRAME_HEIGHT) {
                    previewSize = size;
                    break;
                }
            }
            if (previewSize == null) {
                previewSize = list.get(0);
            }
            Log.i(TAG, "previewSize: " + previewSize.width + ", " + previewSize.height);
            parameters.setPreviewSize(previewSize.width, previewSize.height);

            //Add auto focus setting
            List<String> allFocus = parameters.getSupportedFocusModes();
            if (allFocus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (allFocus.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            camera.setParameters(parameters);
        } catch (Exception e) {
            Log.e("MainViewModel", "failed to open Camera");
            e.printStackTrace();
        }

        return qOpened;
    }

    public void releaseCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    public Camera openCameraAndPreview(int id) {
        //int id = Camera.getNumberOfCameras() > 1 ? 1 : 0;
        if (safeCameraOpen(id)) {
            return camera;
        }
        return null;
    }

    private void startCaptureFrame(int poseId) {
        if (camera != null) {
            camera.setPreviewCallback(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    if (lastPreviewTimestamp == 0) {
                        lastPreviewTimestamp = System.currentTimeMillis();
                    } else {
                        long delta = System.currentTimeMillis() - lastPreviewTimestamp;
                        Log.i(TAG, "preview frame delta: " + delta);
                        if (responseFps != 0 && delta < config.PREVIEW_FRAME_SAMPLING_TIME) {
                            Log.i(TAG, "drop preview frame...");
                            return;
                        }
                    }
                    lastPreviewTimestamp = System.currentTimeMillis();

                    if (imageQueue.size() >= config.MAX_QUEUE_IMAGE_SIZE) {
                        return;
                    }

                    long current = System.currentTimeMillis();
                    Camera.Parameters parameters = camera.getParameters();
                    int width = parameters.getPreviewSize().width;
                    int height = parameters.getPreviewSize().height;

                    float scaleWidth = ((float) config.MAX_FRAME_WIDTH) / width;
                    float scaleHeight = ((float) config.MAX_FRAME_HEIGHT) / height;
                    float scaleFactor = Math.min(scaleWidth, scaleHeight);

                    byte[] cvtData = data;
                    if (Build.MODEL.equals("801LV")) {
                        cvtData = rotateYUV420Degree180(cvtData, width, height);
                    }

                    ImageRequest request = newImageRequest(poseId, width, height, cvtData, ImageFormat.NV21);

                    //detect skeleton from image
                    mPoseDetector.detect(request, new PoseListener.OnDetectListener() {
                        @Override
                        public void onDetect(Pose pose) {
                            Log.i(TAG, "onDetect()...");
                            mPoseResultCallback.onPoseResponse(pose);
                        }
                    });

                    long delta = System.currentTimeMillis() - current;
                    float fps = 1000.f / delta;
                    requestFps = fps;
                    Log.i(TAG, "image request fps: " + fps);
                    try {
                        imageQueue.put(request);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void stopCaptureFrame() {
        lastPreviewTimestamp = 0;
        if (camera != null) {
            camera.setPreviewCallback(null);
        }
    }

    private void drawSkeleton(Bitmap bmp) {
        Bitmap drawBmp = flipImage(bmp);
        if (!bmp.isRecycled()) {
            bmp.recycle();
        }
        resultView.drawBitmap(drawBmp, ball_x, ball_y, ball_radius);
        if (renderedBmp != null && !renderedBmp.isRecycled()) {
            renderedBmp.recycle();
            renderedBmp = drawBmp;
        }
    }

    private Bitmap flipImage(Bitmap image_bitmap) {
        // create new matrix for transformation
        Matrix matrix = new Matrix();
        matrix.preScale(-1.0f, 1.0f);

        Bitmap flipped_bitmap = Bitmap.createBitmap(image_bitmap, 0, 0, image_bitmap.getWidth(), image_bitmap.getHeight(), matrix, true);
        return flipped_bitmap;
    }

    public interface DetectResultCallback<Result> {
        void onDetectInterrupt(Exception e);

        void onImageResponse(Result result);
    }

    public interface PoseResultCallback<Pose> {
        void onPoseResponse(Pose pose);
    }

    public static class Config {
        public int MAX_QUEUE_IMAGE_SIZE = 1;
        public int MAX_FRAME_WIDTH = 320;
        public int MAX_FRAME_HEIGHT = 240;
        public int PREVIEW_FRAME_SAMPLING_TIME = 200;

        public static class Builder {
            Config config = new Config();

            public Config build() {
                return config;
            }

            public Builder setMaxQueueImageSize(int size) {
                config.MAX_QUEUE_IMAGE_SIZE = size;
                return this;
            }

            public Builder setMaxFrameWidth(int width) {
                config.MAX_FRAME_WIDTH = width;
                return this;
            }

            public Builder setMaxFrameHeight(int height) {
                config.MAX_FRAME_HEIGHT = height;
                return this;
            }

            public Builder setPreviewFrameSamplingTime(int time) {
                config.PREVIEW_FRAME_SAMPLING_TIME = time;
                return this;
            }
        }
    }

}