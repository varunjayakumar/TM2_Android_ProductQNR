package com.intel.realsense.samplehmdtracking;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.renderscript.Float3;
import android.renderscript.Float4;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.intel.realsense.tracking.ErrorListener;
import com.intel.realsense.tracking.PoseListener;
import com.intel.realsense.tracking.TrackingDevice;
import com.intel.realsense.tracking.TrackingError;
import com.intel.realsense.tracking.TrackingException;
import com.intel.realsense.tracking.TrackingManager;
import com.intel.realsense.tracking.TrackingPose;
import com.intel.realsense.trackingdevicedetector.Enumerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import au.com.bytecode.opencsv.CSVWriter;

public class SampleHmdTracking extends AppCompatActivity {
    private static final String TAG = "SampleHmd";
    private final Context mContext = this;

    private TrackingDevice mTrackingDevice;
    private TextView mPoseTextView = null;
    private Button mStartStopButton = null;

    private Enumerator mEnumerator;

    // VJ added
    int total_cycle = 3;
    int cur_cycle = 1;
    ArrayList<String> sixDOFList;
    private File libTMLogFolder;
    private File errorLogs;
    private FileWriter errorLogsWriter;
    private File[] logFiles;
    private CSVWriter[] mCSVWriters = null;
    private final Statistics statistics = new Statistics();
    private final DecimalFormat mDF = new DecimalFormat("###.####");
    private  int CSV_NO_OF_COLUMNS ;
    public static String path_to_csv;
    public static boolean new_cycle;
    public static int cyc_count;
    final String[] ALL_COLUMNS = new String[]{"TIMESTAMP",
            "CONFIDENCE",
            "TRANSLATION.x",
            "TRANSLATION.y",
            "TRANSLATION.z",
            "ROTATION.i",
            "ROTATION.j",
            "ROTATION.k",
            "ROTATION.r",
            "ACCELERATION.x",
            "ACCELERATION.y",
            "ACCELERATION.z",
            "VELOCITY.x",
            "VELOCITY.y",
            "VELOCITY.z",
            "ANGULAR_VELOCITY.yaw",
            "ANGULAR_VELOCITY.pitch",
            "ANGULAR_VELOCITY.roll",
            "ANGULAR_ACCELERATION.yaw",
            "ANGULAR_ACCELERATION.pitch",
            "ANGULAR_ACCELERATION.roll","FPS"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_hmd_tracking);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }

        TextView trackingLibVersion = (TextView) findViewById(R.id.tracking_lib_version);
        trackingLibVersion.setText("Tracking lib version: " + TrackingManager.getVersion());

        mPoseTextView = (TextView) findViewById(R.id.tm_data_text_view);
        mPoseTextView.setText("TM device disconnected");
        mPoseTextView.setMovementMethod(new ScrollingMovementMethod());


        mStartStopButton = (Button) findViewById(R.id.start_stop_button);
        mStartStopButton.setEnabled(false);

        //Original code
        mStartStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                synchronized (this) {
                    if (mStartStopButton.getText().equals("Stop")) {
                        stop();
                    } else {
                        start();
                    }
                }
            }
        });

        initLog();

        //VJ added code
        /*mStartStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                while (cur_cycle <= total_cycle)
                {
                    start();
                    *//*try {
                        wait(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        System.out.print("Failed to execute wait() inside onClick()");
                    }*//*
                    *//*try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        System.out.print("Failed to execute sleep() inside onClick()");
                    }*//*
                    *//*new CountDownTimer(2000, 2) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            //start();
                        }
                        public void onFinish() {
                            stop();
                        }
                    }.start();*//*
                    stop();
                    cur_cycle++;
                }
            }
        });*/

        logLibTM();
        mEnumerator = new Enumerator(mContext, mEnumeratorListener);
    }

    private void start(){
        cyc_count = cyc_count+1;
        new_cycle = true;
        try {
            mTrackingDevice.enableTracking(mHmdPoseListener);
            mTrackingDevice.start();
            mStartStopButton.setText("Stop");
        } catch (Exception e) {
            Log.e(TAG, "start streaming failed, error: " + e.getMessage());
        }
    }

    private void stop(){
        new_cycle = false;
        try {
            mTrackingDevice.stop();
        } catch (Exception e) {
            Log.e(TAG, "stop streaming failed, error: " + e.getMessage());
        }
        mStartStopButton.setText("Start");
        try {
            TrackingManager.resetTrackingDevice(mContext);    // resets fw/TM2
        } catch (TrackingException e) {
            e.printStackTrace();
        }
    }

    private PoseListener mHmdPoseListener = new PoseListener() {
        @Override
        public void onPose(TrackingPose pose) {
            if (pose != null) {
                final String poseStr = pose.toFormattedString();
                statistics.onPose();

                if (new_cycle) {
                    new_cycle = false;
                    logFiles = new File[1];
                    logFiles[0] = new File(path_to_csv,"Cycle_"+String.valueOf(cyc_count)+".csv");
                    try {
                        mCSVWriters = new CSVWriter[1];
                        mCSVWriters[0] = new CSVWriter(new FileWriter(logFiles[0]));
                        mCSVWriters[0].writeNext(ALL_COLUMNS);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                try {
                    if (mCSVWriters != null) {
                        Float4 rot = pose.getRotation();   //float4 return type
                        Float3 trans = pose.getTranslation();    // float3 return type
                        Float3 axisAcceleration = pose.getAcceleration();
                        Float3 axisVelocity = pose.getVelocity();
                        Float3 eulerAnglesVelocity = pose.getAngularVelocity();
                        Float3 eulerAnglesAcceleration = pose.getAngularAcceleration();
                        int trkConfidence = pose.getTrackerConfidence();

                        sixDOFList.add(String.valueOf(pose.getTimestamp()));
                        sixDOFList.add(mDF.format(trkConfidence));
                        sixDOFList.add(mDF.format(trans.x));
                        sixDOFList.add(mDF.format(trans.y));
                        sixDOFList.add(mDF.format(trans.z));
                        sixDOFList.add(mDF.format(rot.x));
                        sixDOFList.add(mDF.format(rot.y));
                        sixDOFList.add(mDF.format(rot.z));
                        sixDOFList.add(mDF.format(rot.w));
                        sixDOFList.add(mDF.format(axisAcceleration.x));
                        sixDOFList.add(mDF.format(axisAcceleration.y));
                        sixDOFList.add(mDF.format(axisAcceleration.z));
                        sixDOFList.add(mDF.format(axisVelocity.x));
                        sixDOFList.add(mDF.format(axisVelocity.y));
                        sixDOFList.add(mDF.format(axisVelocity.z));
                        sixDOFList.add(mDF.format(eulerAnglesVelocity.x));
                        sixDOFList.add(mDF.format(eulerAnglesVelocity.y));
                        sixDOFList.add(mDF.format(eulerAnglesVelocity.z));
                        sixDOFList.add(mDF.format(eulerAnglesAcceleration.x));
                        sixDOFList.add(mDF.format(eulerAnglesAcceleration.y));  //originally roll (z). I think there was a typo
                        sixDOFList.add(mDF.format(eulerAnglesAcceleration.z));
                        sixDOFList.add(mDF.format(statistics.getFps()));
                        CSV_NO_OF_COLUMNS = sixDOFList.size();
                        String[] data = new String[CSV_NO_OF_COLUMNS];

                        sixDOFList.toArray(data);

                        mCSVWriters[0].writeNext(data);
                        sixDOFList.clear();

                        // update UI
                        runOnUiThread(new Runnable() {
                            public void run() {
                                mPoseTextView.setText(poseStr);
                            }
                        });
                    }
                }
                catch (Exception e) {
                    Log.e(TAG + "csv", e.getMessage());
                }
            }
        }
    };

    private ErrorListener mErrorListener = new ErrorListener() {
        @Override
        public void onError(final TrackingError error) {
            Log.e(TAG, "onError: " + error);
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(mContext, "Tracking device error: " + error, Toast.LENGTH_LONG).show();
                    if(error == TrackingError.DEVICE_IN_ERROR_STATE){
                        mPoseTextView.setText("Tracking device is in error state, hard reset is required");
                        mStartStopButton.setEnabled(false);
                    }
                }
            });
        }
    };

    @Override
    public void onDestroy() {
        if (mStartStopButton.getText().equals("Stop")) {
            stop();
        }
        try {
            mEnumerator.close();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: " + e.getMessage());
        }
        if(mTrackingDevice != null){
            mTrackingDevice.close();
        }

        super.onDestroy();
    }

    private Enumerator.Listener mEnumeratorListener = new Enumerator.Listener() {
        @Override
        public void onTrackingDeviceAvailable() {
            try {
                mTrackingDevice = TrackingManager.getTrackingDevice(mContext, mErrorListener);
                runOnUiThread(new Runnable() {
                    public void run() {
                        mStartStopButton.setEnabled(true);
                        mPoseTextView.setText("TM device connected");
                        mStartStopButton.setText("Start");
                        TextView trackingFwVersion = (TextView) findViewById(R.id.tracking_fw_version);
                        trackingFwVersion.setText("Tracking firmware version: " + mTrackingDevice.getInfo().getFirmwareVersion());
                    }
                });
            }
            catch (TrackingException e){
                Log.e(TAG, e.getMessage());
                mErrorListener.onError(e.getError());
            }
            catch (Exception e){
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onTrackingDeviceUnavailable() {
            if(mTrackingDevice != null)
                mTrackingDevice.close();
            mTrackingDevice = null;
            runOnUiThread(new Runnable() {
                public void run() {
                    mStartStopButton.setEnabled(false);
                    mPoseTextView.setText("TM device disconnected");
                }
            });
        }
    };

    private void initLog()
    {
        sixDOFList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String currentDateAndTime = sdf.format(new Date());

        File folder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "pose_recs_static");
        folder.mkdir();

        libTMLogFolder = new File(Environment.getExternalStorageDirectory()+"/pose_recs_static/libtm_"+currentDateAndTime);
        libTMLogFolder.mkdir();

        errorLogs = new File(libTMLogFolder.getAbsolutePath()+"/error_list_"+currentDateAndTime+".log");
        try {
            errorLogsWriter = new FileWriter(errorLogs);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File logFileFolder = new File(folder, currentDateAndTime );
        logFileFolder.mkdir();

        path_to_csv = folder + "/" + currentDateAndTime;
        System.out.println("Path to CSV : " + path_to_csv);
        System.out.println("Folder : " + folder);
        System.out.println("Current Date And Time : " + currentDateAndTime);
    }

    private void logLibTM()
    {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String currentDateAndTime = sdf.format(new Date());

        File logfile = new File(libTMLogFolder.getAbsolutePath()+"/libtm_"+currentDateAndTime+".log");
        try {
            Runtime.getRuntime().exec(new String[]{"logcat","-f",logfile.getAbsolutePath(),"libtm"});
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}