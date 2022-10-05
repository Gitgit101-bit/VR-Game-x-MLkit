package com.nuwarobotics.app.posedetectorsample.util;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import android.os.Handler;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = CameraPreview.class.getSimpleName();

    private SurfaceHolder mHolder;
    private Camera mCamera;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        mCamera = camera;
        init();
    }

    private void init() {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        if (mCamera == null) {
            return;
        }

        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        if (mCamera == null) {
            return;
        }

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        new Handler(getContext().getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // stop preview before making changes
                try {
                    Log.d(TAG, "-----------C");
                    mCamera.stopPreview();
                } catch (Exception e) {
                    Log.e(TAG, "-----------C:" + e.getMessage());
                    // ignore: tried to stop a non-existent preview
                }

                // set preview size and make any resize, rotate or
                // reformatting changes here

                // start preview with new settings
                try {
                    mCamera.setPreviewDisplay(mHolder);
                    mCamera.startPreview();
                } catch (Exception e) {
                    Log.d(TAG, "Error starting camera preview: " + e.getMessage());
                }
            }
        }, 500);
    }
}
