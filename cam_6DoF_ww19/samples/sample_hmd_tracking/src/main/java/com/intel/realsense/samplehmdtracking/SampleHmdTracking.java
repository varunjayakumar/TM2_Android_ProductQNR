package com.intel.realsense.samplehmdtracking;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.renderscript.Float3;
import android.renderscript.Float4;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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
    //default
    private static final String TAG = "SampleHmd";
    private final Context mContext = this;

    private TrackingDevice mTrackingDevice;
    private TextView mPoseTextView = null;
    private Button mStartStopButton = null;

    private Enumerator mEnumerator;

    //Porting
    /**
     * No of columns in the CSV file. Corresponds to number of data values given by Pose info.
     */
    private  int CSV_NO_OF_COLUMNS ;
    /**
     * Total number of cycles to run the tests. Default is 500
     */
    private static  int TOTAL_NO_OF_CYCLES =575;
    /**
     * Describes the pattern of one cycle of the robot arm. Numbers 1-6 are the directions.
     */
    final int[] PATTERN = {1 ,2 ,1 ,3 ,1 ,4 ,5 ,4 ,6 ,4};
    private static int HARD_FAILURE_THRESHOLD = 10;
    private int mConsecutiveFailureCount =0;
    private int mPrevFailurePatternIndex =0;
    private int mTotalFailures =0;
    private boolean isFirstFailure = true;

    /**
     * Separator symbol used to separate two cycles in csv files
     */
    private static final String CYCLE_SEPARATOR = "#";
    int currentPatternIndex = -1;
    int reset_count = 0;

    final String[] ALL_COLUMNS = new String[]{"TIMESTAMP",
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

    //private final TrackingDevice.Listener mTrackingDeviceListener = this;     //removing
    //private final TrackingDeviceManager.Listener mTrackingDeviceManagerListener = this;   //removing
    private TextView mCycleNumberTextView =null;
    private CheckBox mPrintPose = null;
    private CheckBox mRecordPose = null;
    private boolean isTestStarted = false;
    int count=0;
    int mCycleNumber=1;
    private CSVWriter[] mCSVWriters = null;
    private final DecimalFormat mDF = new DecimalFormat("###.####");
    private final Statistics statistics = new Statistics();

    float global_d=0;
    int startButtonEnableCount=0;

    UsbManager usbManager;
    UsbDevice device;

    UsbDeviceConnection usbConnection;
    ArrayList<String> sixDOFList;

    boolean writeToLog = false;
    private File[] logFiles;
    private File libTMLogFolder;
    private File errorLogs;
    private FileWriter errorLogsWriter;
    private boolean mCycle = false;

    boolean isTM2attached =false;

    boolean isTM2attachedFirstTime = true;
    private boolean mArduioConnected =false;

    boolean mErrorOccured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_hmd_tracking);

        //Porting
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }

        //Porting
        initUI();
        TOTAL_NO_OF_CYCLES = getIntent().getIntExtra("cycles",575);
        HARD_FAILURE_THRESHOLD = getIntent().getIntExtra("hard",10);
        Log.d(TAG,"HARD_FAILURE_THRESHOLD "+HARD_FAILURE_THRESHOLD);
        initLog();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RobotArmProtocol.ROBOT_CONNECTED);
        intentFilter.addAction(RobotArmProtocol.ROBOT_MOVING);
        intentFilter.addAction(RobotArmProtocol.ROBOT_PAUSED);
        intentFilter.addAction(RobotArmProtocol.ROBOT_DISCONNECTED);
        registerReceiver(arduinoReceiver,intentFilter);
        logLibTM();

        mEnumerator = new Enumerator(mContext, mEnumeratorListener);
    }

    private void start(){
        try {
            mTrackingDevice.enableTracking(mHmdPoseListener);
            mTrackingDevice.start();
            mStartStopButton.setText("Stop");
            //Porting
            mRecordPose.setEnabled(false);
        } catch (Exception e) {
            Log.e(TAG, "start streaming failed, error: " + e.getMessage());
        }
    }

    private void stop(){
        try {
            mTrackingDevice.stop();
        } catch (Exception e) {
            Log.e(TAG, "stop streaming failed, error: " + e.getMessage());
        }
        mStartStopButton.setText("Start");
        //Porting
        mRecordPose.setEnabled(true);
    }

    private PoseListener mHmdPoseListener = new PoseListener() {
        @Override
        public void onPose(final TrackingPose pose) {
            if (pose != null) {
                final String poseStr = pose.toFormattedString();
                statistics.onPose();
                mErrorOccured = false;
                mConsecutiveFailureCount = 0;

                try {
                    if (mCSVWriters != null && writeToLog) {
                        Float4 rot = pose.getRotation();   //float4 return type
                        Float3 trans = pose.getTranslation();    // float3 return type
                        Float3 axisAcceleration = pose.getAcceleration();
                        Float3 axisVelocity = pose.getVelocity();
                        Float3 eulerAnglesVelocity = pose.getAngularVelocity();
                        Float3 eulerAnglesAcceleration = pose.getAngularAcceleration();

                        sixDOFList.add(String.valueOf(pose.getTimestamp()));
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

                        mCSVWriters[PATTERN[currentPatternIndex] - 1].writeNext(data);
                        sixDOFList.clear();

                        // update UI
                        runOnUiThread(new Runnable() {
                            public void run() {
                                mPoseTextView.setText("FPS: " + statistics.getFps() + "\n" + poseStr);
                            }
                        });
                    }
                } catch (Exception e) {
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
            //Porting
            try {
                errorLogsWriter.append("Tracking device error: " + error +" Cycle = " + mCycleNumber+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
            mTotalFailures++;
            mErrorOccured = true;
        }
    };

    @Override
    public void onDestroy() {
        releaseAllResources();
        /*//Removing
        if (mStartStopButton.getText().equals("Stop")) {
            stop();
        }
        */

        super.onDestroy();
    }

    private Enumerator.Listener mEnumeratorListener = new Enumerator.Listener() {
        @Override
        public void onTrackingDeviceAvailable() {
            startButtonEnableCount++;
            if (startButtonEnableCount==2) {
                mStartStopButton.setEnabled(true);
                startButtonEnableCount=0;
            }

            try {
                mTrackingDevice = TrackingManager.getTrackingDevice(mContext, mErrorListener);
            } catch (TrackingException e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    //mStartStopButton.setEnabled(true);    //Removing as this is mentioned above
                    mPoseTextView.setText("TM device connected");
                    mStartStopButton.setText("Start");
                    TextView trackingFwVersion = (TextView) findViewById(R.id.tracking_fw_version);
                    trackingFwVersion.setText("Tracking firmware version: " + mTrackingDevice.getInfo().getFirmwareVersion());
                }
            });

            isTM2attached = true;
            if(!isTM2attachedFirstTime) {
                start();
            }
            isTM2attachedFirstTime = false;
        }

        @Override
        public void onTrackingDeviceUnavailable() {
            mStartStopButton.setEnabled(false);
            if(mTrackingDevice != null)
                mTrackingDevice.close();
            mTrackingDevice = null;
            mPoseTextView.setText("TM device disconnected");

            try {
                errorLogsWriter.append("TM Device disconnected\n Cycle = " + mCycleNumber+"\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    //Porting
    private void initUI()
    {
        TextView trackingLibVersion = (TextView) findViewById(R.id.tracking_lib_version);
        trackingLibVersion.setText(getString(R.string.tracking_lib_version_label) + TrackingManager.getVersion());

        mCycleNumberTextView = (TextView) findViewById(R.id.textViewCycleNumber);
        mPrintPose = (CheckBox) findViewById(R.id.show_pose_data);
        mPrintPose.setChecked(true);
        mRecordPose = (CheckBox) findViewById(R.id.record_pose_data);
        mRecordPose.setChecked(true);
        mPoseTextView = (TextView) findViewById(R.id.tm_data_text_view);
        mPoseTextView.setText(R.string.status_disconnected);
        mPoseTextView.setMovementMethod(new ScrollingMovementMethod());

        mStartStopButton = (Button) findViewById(R.id.start_stop_button);
        mStartStopButton.setEnabled(false);
        mStartStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                synchronized (mContext) {
                    if(mArduioConnected)
                    {
                        mCycleNumberTextView.setText("Cycle #: " + mCycleNumber +"/"+TOTAL_NO_OF_CYCLES);
                        if (mStartStopButton.getText().equals("Stop")) {
                            stop();
                        } else {
                            start();
                        }
                    }
                }
            }
        });
    }

    private void initLog()
    {
        sixDOFList = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String currentDateAndTime = sdf.format(new Date());

        File folder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "pose_recs");
        folder.mkdir();

        libTMLogFolder = new File(Environment.getExternalStorageDirectory()+"/pose_recs/libtm_"+currentDateAndTime);
        libTMLogFolder.mkdir();

        errorLogs = new File(libTMLogFolder.getAbsolutePath()+"/error_list_"+currentDateAndTime+".log");
        try {
            errorLogsWriter = new FileWriter(errorLogs);
        } catch (IOException e) {
            e.printStackTrace();
        }

        File logFileFolder = new File(folder, currentDateAndTime );
        logFileFolder.mkdir();
        logFiles= new File[6];
        logFiles[0] = new File(logFileFolder,"dir_1.csv");
        logFiles[1] = new File(logFileFolder,"dir_2.csv");
        logFiles[2] = new File(logFileFolder,"dir_3.csv");
        logFiles[3] = new File(logFileFolder,"dir_4.csv");
        logFiles[4] = new File(logFileFolder,"dir_5.csv");
        logFiles[5] = new File(logFileFolder,"dir_6.csv");

        try {
            mCSVWriters = new CSVWriter[6];
            for(int i=0;i<logFiles.length;i++) {
                mCSVWriters[i] = new CSVWriter(new FileWriter(logFiles[i]));
                mCSVWriters[i].writeNext(ALL_COLUMNS);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    /**
     * Receives broadcasts from the host regarding robot rotation to control logging
     */
    BroadcastReceiver arduinoReceiver = new BroadcastReceiver() {
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mCycleNumberTextView.setText("Cycle #: "+ mCycleNumber+"/"+TOTAL_NO_OF_CYCLES);
            }
        };

        @Override
        public void onReceive(Context context, Intent intent) {
            // code to reset tm2 fw at a certain interval everytime
            reset_count++;
            if (reset_count == 30) {       //675 = 45 mins. 675 is the number of times binary signal is sent by robot. There are 15 signals in 1 min
                try {
                    TrackingManager.resetTrackingDevice(mContext);    // resets fw/TM2
                } catch (TrackingException e) {
                    e.printStackTrace();
                }
                reset_count = 0;
            }

            if(intent.getAction().equals(RobotArmProtocol.ROBOT_MOVING))
            {
                Log.d("recvd","Data_0");
                writeToLog=false;

            }

            if(intent.getAction().equals(RobotArmProtocol.ROBOT_PAUSED))
            {
                if(!isTM2attached) {
                    mTotalFailures++;
                    mErrorOccured = true;
                }
                if (mErrorOccured)
                {
                    mConsecutiveFailureCount++;
                    if(mConsecutiveFailureCount >= HARD_FAILURE_THRESHOLD) {

                        try {
                            errorLogsWriter.append("===FAILURES===\n"+"Total failures = "+mTotalFailures+"\nTotal hard failures = 1\nTotal robot disconnection = 0\nTotal soft failures= "+(mTotalFailures-1));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        finish();
                    }
                }
                Log.d("recvd","Data_1");
                currentPatternIndex++;    //check for robot connection update here
                Log.d(TAG,"Pattern idx "+currentPatternIndex);
                count=0;
                if(currentPatternIndex >= 10)
                {
                    currentPatternIndex=0;
                    mCycleNumber++;

                    if(mCycleNumber>TOTAL_NO_OF_CYCLES) {
                        try {
                            errorLogsWriter.append("===FAILURES===\n"+"Total failures = "+mTotalFailures+"\nTotal hard failures = 0\nTotal robot disconnection = 0\nTotal soft failures= "+(mTotalFailures));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        finish();
                    }
                    else {
                        runOnUiThread(runnable);


                        String[] temp = new String[CSV_NO_OF_COLUMNS];
                        Log.d(TAG, "no of columns " + CSV_NO_OF_COLUMNS);
                        for (int i = 0; i < CSV_NO_OF_COLUMNS; i++)
                            temp[i] = CYCLE_SEPARATOR;
                        temp[0] = temp[0] + " " + mCycleNumber;
                        for (CSVWriter writer : mCSVWriters) {
                            writer.writeNext(temp);
                            Log.d(TAG, "writing #");
                        }
                    }
                    // VJ added for logging failure count after every cycle
                    try {
                        errorLogsWriter.append("===FAILURES===\n"+"Total failures = "+mTotalFailures+"\nTotal hard failures = 0\nTotal robot disconnection = 0\nTotal soft failures= "+(mTotalFailures));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                writeToLog=true;
                Log.d(TAG,"writing to file "+ logFiles[PATTERN[currentPatternIndex] - 1].getName());
            }

            if(intent.getAction().equals(RobotArmProtocol.ROBOT_CONNECTED))
            {

                Log.d("recvd","Data_o");
                mArduioConnected=true;
                if(isTM2attached)
                {
                    mStartStopButton.setEnabled(true);

                }
                startButtonEnableCount++;
                if(startButtonEnableCount==2) {
                    mStartStopButton.setEnabled(true);
                    startButtonEnableCount=0;
                }
            }

            if(intent.getAction().equals(RobotArmProtocol.ROBOT_DISCONNECTED))
            {
                Log.d(TAG,"Robot disconnected");
                mTotalFailures++;
                Toast.makeText(mContext, "Robot disconnected", Toast.LENGTH_SHORT).show();

                try {

                    errorLogsWriter.append("Robot  disconnected Cycle = " + mCycleNumber+"\n");
                    errorLogsWriter.append("===FAILURES===\n"+"Total failures = "+mTotalFailures+"\nTotal hard failures = 0\nTotal robot disconnection = 1\nTotal soft failures= "+(mTotalFailures-1));

                } catch (IOException e) {
                    e.printStackTrace();
                }
                finish();
            }

        }
    };

    /**
     * @author Ghanshyam
     * Release the CSVWriters, TM2 device, broadcast receiver
     */
    private void releaseAllResources()
    {
        try {
            String[] temp = new String[CSV_NO_OF_COLUMNS];
            Log.d(TAG, "no of columns " + CSV_NO_OF_COLUMNS);
            for (int i = 0; i < CSV_NO_OF_COLUMNS; i++)
                temp[i] = CYCLE_SEPARATOR;
            temp[0] = temp[0] + " " + mCycleNumber;
            for (CSVWriter writer : mCSVWriters) {
                writer.writeNext(temp);
                Log.d(TAG, "writing #");
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "onDestroy: " + e.getMessage());
        }
        if (mStartStopButton.getText().equals("Stop")) {
            //stop();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stop();
                }
            });
        }

        try {
            mEnumerator.close();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: " + e.getMessage());
        }

        if(mTrackingDevice != null){
            mTrackingDevice.close();
        }

        try {
            for(CSVWriter o: mCSVWriters) {
                if (o != null)
                    o.close();
            }
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }

        try {
            errorLogsWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            unregisterReceiver(arduinoReceiver);
        }
        catch (IllegalArgumentException e)
        {
            Log.d(TAG,"reciever not registered");
        }
    }

}