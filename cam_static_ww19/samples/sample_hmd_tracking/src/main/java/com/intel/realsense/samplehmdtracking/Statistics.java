package com.intel.realsense.samplehmdtracking;

class Statistics {
    private static final String TAG = "TrackingSampleApp STAT";

    private long mBaseTime;
    private float mFps;
    private long mPoseCount;

    public void reset(){
        mBaseTime = System.currentTimeMillis();
    }

    public float getFps(){
        return mFps;
    }

    public void onPose(){
        mPoseCount++;
        long curr = System.currentTimeMillis();
        float diffInSeconds = (float) ((curr - mBaseTime) * 0.001);
        if(diffInSeconds > 2){
            mFps = mPoseCount / diffInSeconds;
            mPoseCount = 0;
            mBaseTime = curr;
        }
    }
}
