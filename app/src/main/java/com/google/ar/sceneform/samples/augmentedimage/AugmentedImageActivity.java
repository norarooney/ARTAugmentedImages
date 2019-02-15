/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.sceneform.samples.augmentedimage;


import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.Frame;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.samples.common.helpers.SnackbarHelper;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import android.media.MediaPlayer;



// Example of screen recording class forked from https://github.com/chinmoyp/screenrecorder - Robert Brodin 2018, ART
// Using mostly MediaProjection and MediaProjectionManager to make this work.
// Follow my comments below for how to apply this to any code.
// NOTE: To edit Codec and recording settings, go to initRecorder().
// NOTE: To edit file location, go to getFilePath(). (by default goes to sdcard/recordings)

import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

// Need to import all of these Classes to make the Screen recording work.
import android.os.Environment;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjection.Callback;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.media.projection.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
/**
 * This application demonstrates using augmented images to place anchor nodes. app to include image
 * tracking functionality.
 */
public class AugmentedImageActivity extends AppCompatActivity {

    //Remove TAG in an augmented reality file
    public static final String TAG = "AugmentedImageActivity";

    // DO NOT CHANGE PERMISSION CODE (messes up onActivityResult() method)
    private static final int PERMISSION_CODE = 1;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;

    // DISPLAY_WIDTH and DISPLAY_HEIGHT are set default to 480 by 640, but are changed relative to the size of the phone being used in the onCreate() method.
    private static int DISPLAY_WIDTH = 480;
    private static int DISPLAY_HEIGHT = 640;

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjection.Callback mMediaProjectionCallback;
    private ToggleButton mToggleButton;
    private MediaRecorder mMediaRecorder;

    private MediaPlayer mediaPlayer;
    private Integer currentSongIndex = null;

    private ArFragment arFragment;
    private ImageView fitToScanView;
    // Augmented image and its associated center pose anchor, keyed by the augmented image in
    // the database.
    private final Map<AugmentedImage, AugmentedImageNode> augmentedImageMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        fitToScanView = findViewById(R.id.image_view_fit_to_scan);

        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        DISPLAY_HEIGHT = metrics.heightPixels;
        DISPLAY_WIDTH = metrics.widthPixels;

        // inits the recorder (settings for recording)
        initRecorder();
        prepareRecorder();

        // Finds button with ID toggle. This can be changed to any button, you will just need to cast it to (RadioButton) or (Button) instead of (ToggleButton).
        mToggleButton = (ToggleButton) findViewById(R.id.toggle);

        // Creates an event listener to see when the button is clicked.
        mToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onToggleScreenShare(v);
            }
        });

        mMediaProjectionCallback = new MediaProjectionCallback();
    }

    // onDestroy makes sure the recording stops (failsafe if .stop() doesn't work the first time).
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (augmentedImageMap.isEmpty()) {
            fitToScanView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "Screen Cast Permission Denied", Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    // Toggles Screen recording on and off.
    public void onToggleScreenShare(View view) {
        if (((ToggleButton) view).isChecked()) {
            initRecorder();
            prepareRecorder();
            mProjectionManager = (MediaProjectionManager) getSystemService
                    (Context.MEDIA_PROJECTION_SERVICE);


            mToggleButton.setBackgroundColor(getResources().getColor(R.color.colorPrimaryDark));
            mToggleButton.setText("   ");
            //shareScreen();
        } else {
            //mMediaRecorder.stop();
            mMediaRecorder.reset();
            Log.v(TAG, "Recording Stopped");
            stopScreenSharing();
            mToggleButton.setText("Off");
            mToggleButton.setVisibility(View.VISIBLE);
            mToggleButton.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
        }
    }
    // Stops screen recording.
    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mMediaRecorder.reset();
        //mMediaRecorder.release();
    }

    // Creates a VirtualDisplay object using the parameters set at the start.
    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay("MainActivity",
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null /*Handler*/);
    }

    // Checks if mToggleButton is clicked. Will have to change mToggleButton to the new button variable if a mToggleButton is not being used.
    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            if (mToggleButton.isChecked()) {
                mToggleButton.setChecked(false);
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                Log.v(TAG, "Recording Stopped");
            }
            mMediaProjection = null;
            stopScreenSharing();
            Log.i(TAG, "MediaProjection Stopped");
        }
    }

    // Prepares recorder (checks for exceptions)
    private void prepareRecorder() {
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
            finish();
        }
    }

    // Used to create the desired filePath that the file will be saved in. By default it goes to /sdcard/recordings, but could be changed to anything.
    public String getFilePath() {
        // Can change the folder which the video is saved into.
        final String directory = Environment.getExternalStorageDirectory() + File.separator + "Recordings";
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            // Toast is just a fancy class that helps print custom error messages.
            Toast.makeText(this, "Failed to get External Storage", Toast.LENGTH_SHORT).show();
            return null;
        }
        // Using final because the folder variable should NEVER be changed.
        final File folder = new File(directory);
        boolean success = true;

        // If the folder doesn't exist, it is created using mkdir().
        if (!folder.exists()) {
            success = folder.mkdir();
        }
        String filePath;

        // Once the folder exists (if it didn't already), the filePath is set to the directory + capture_date.mp4. Capture can be changed quite easily.
        if (success) {
            String videoName = ("capture_" + getCurSysDate() + ".mp4");
            filePath = directory + File.separator + videoName;
        } else {
            Toast.makeText(this, "Failed to create Recordings directory", Toast.LENGTH_SHORT).show();
            return null;
        }
        return filePath;
    }

    // Gets the date (relative to the device). Will be used for the file name in getFilePath().
    public String getCurSysDate() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    }

    // Used for Codec and recording settings!
    private void initRecorder() {
        int YOUR_REQUEST_CODE = 200; // could be something else..
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    YOUR_REQUEST_CODE);


            if (mMediaRecorder == null) {
                mMediaRecorder = new MediaRecorder();
            }

            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            // Bitrate is set relative to the screen, so it is just the width of the device * the height of the device.
            mMediaRecorder.setVideoEncodingBitRate(10000000);
            // Framerate crashed at 60 when testing.
            mMediaRecorder.setVideoFrameRate(30);
            // Sets video size relative to the phone.
            mMediaRecorder.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT);
            // Sets file path using the getFilePath() method.
            mMediaRecorder.setOutputFile(getFilePath());
        }
    }


        /**
         * Registered with the Sceneform Scene object, this method is called at the start of each frame.
         *
         * @param frameTime - time since last frame.
         */
        private void onUpdateFrame (FrameTime frameTime){
            Frame frame = arFragment.getArSceneView().getArFrame();

            // If there is no frame or ARCore is not tracking yet, just return.
            if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                return;
            }


            Collection<AugmentedImage> updatedAugmentedImages =
                    frame.getUpdatedTrackables(AugmentedImage.class);
            for (AugmentedImage augmentedImage : updatedAugmentedImages) {

                switch (augmentedImage.getTrackingState()) {
                    case PAUSED:
                        // When an image is in PAUSED state, but the camera is not PAUSED, it has been detected,
                        // but not yet tracked.
                        String text = "Detected Image " + augmentedImage.getIndex();
                        SnackbarHelper.getInstance().showMessage(this, text);

                        break;

                    case TRACKING:

                        // Have to switch to UI Thread to update View.
                        fitToScanView.setVisibility(View.GONE);

                        if (currentSongIndex == null) {
                            // If there is no song playing, set currentSongIndex to the song that aligns with
                            // the detected image
                            currentSongIndex = augmentedImage.getIndex();

                            mediaPlayer = MediaPlayer.create(this, AugmentedImageFragment.Music_list[currentSongIndex]);
                            mediaPlayer.start();
                            // Create a new mediaPlayer object with the correct song
                            // Start the song
                        } else if ((currentSongIndex != augmentedImage.getIndex())) {

                            // If the song that is playing does not match up with the song the image it detects
                            // is assigned to:
                            // Assign the correct song to currentSongIndex
                            // Stop playing the song, reset the mediaPlayer object
                            currentSongIndex = augmentedImage.getIndex();
                            mediaPlayer.stop();
                            mediaPlayer.release();

                            mediaPlayer = MediaPlayer.create(this, AugmentedImageFragment.Music_list[currentSongIndex]);
                            mediaPlayer.start();
                            // Recreate the mediaPlayer object with the correct song
                            // Start the song

                        }

                        // Create a new anchor for newly found images.
                        if (!augmentedImageMap.containsKey(augmentedImage)) {
                            AugmentedImageNode node = new AugmentedImageNode(this);
                            node.setImage(augmentedImage, this);
                            augmentedImageMap.put(augmentedImage, node);
                            arFragment.getArSceneView().getScene().addChild(node);
                        }
                        break;

                    case STOPPED:

                        augmentedImageMap.remove(augmentedImage);

                        break;

                    default:

                }
            }
        }
    }
