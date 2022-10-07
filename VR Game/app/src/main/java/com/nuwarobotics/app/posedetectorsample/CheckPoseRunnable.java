package com.nuwarobotics.app.posedetectorsample;

import android.util.Log;

import java.text.MessageFormat;
import java.util.concurrent.CountDownLatch;

public abstract class CheckPoseRunnable<TImageRequest, TImageResponse> implements Runnable {
    public static final String TAG = CheckPoseRunnable.class.getSimpleName();

    private Throwable failed;

    private boolean running = true;
    private MainActivity mainActivity;

    private long lastResultTimestamp;

    private CountDownLatch finishLatch;
    private StringBuffer logs = new StringBuffer();

    public CheckPoseRunnable(MainActivity mainViewModel) {
        this.mainActivity = mainViewModel;
    }

    @Override
    public void run() {
        try {
            start();
            String log = checkPoseFromImageStream();
            Log.i(TAG, log);

            if (running) {
                mainActivity.getCheckPoseHandler().postDelayed(this, 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (mainActivity.getDetectResultCallback() != null) {
                mainActivity.getDetectResultCallback().onDetectInterrupt(e);
            }
        }
    }

    private String checkPoseFromImageStream()
            throws InterruptedException, RuntimeException {
        Log.i(TAG, "checkPoseFromImageStream()...");
        final StringBuffer logs = new StringBuffer();
        appendLogs(logs, "*** checkPoseFromImageStream");
        finishLatch = new CountDownLatch(1);

        if (mainActivity.imageQueue.size() <= 0) {
            Log.i(TAG, "no image request");
            return logs.toString();
        }
        TImageRequest request = (TImageRequest) mainActivity.imageQueue.take();
        requestProcess(request);

        // Receiving happens asynchronously
//            if (!finishLatch.await(1, TimeUnit.MINUTES)) {
//                throw new RuntimeException(
//                        "Could not finish rpc within 1 minute, the server is likely down");
//            }
        finishLatch.await();

        if (failed != null) {
            throw new RuntimeException(failed);
        }
        return logs.toString();
    }

    public void stop() {
        running = false;
        lastResultTimestamp = 0;
    }

    public void start() {

        running = true;
    }

    protected void onSuccess(TImageResponse imageResponse) {
        Log.i(TAG, "onSuccess()...");
        if (lastResultTimestamp == 0) {
            lastResultTimestamp = System.currentTimeMillis();
        } else {
            long delta = System.currentTimeMillis() - lastResultTimestamp;
            lastResultTimestamp = System.currentTimeMillis();
            float fps = 1000.f / delta;
            mainActivity.responseFps = fps;
            Log.i(TAG, "image response fps: " + fps);
        }
        if (mainActivity.getDetectResultCallback() != null) {
            mainActivity.getDetectResultCallback().onImageResponse(imageResponse);
        }
    }

    protected void onError(Throwable t) {
        failed = t;
        if (finishLatch != null) {
            finishLatch.countDown();
        }
    }

    protected void onCompleted() {
        appendLogs(logs, "Finished ImageResponse");
        if (finishLatch != null) {
            finishLatch.countDown();
        }
    }

    private static void appendLogs(StringBuffer logs, String msg, Object... params) {
        if (params.length > 0) {
            logs.append(MessageFormat.format(msg, params));
        } else {
            logs.append(msg);
        }
        logs.append("\n");
        Log.i(TAG, MessageFormat.format(msg, params));
    }

    protected abstract void requestProcess(TImageRequest imageRequest);
}
