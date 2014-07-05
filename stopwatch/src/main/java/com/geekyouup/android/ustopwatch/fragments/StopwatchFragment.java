package com.geekyouup.android.ustopwatch.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.geekyouup.android.ustopwatch.AlarmUpdater;
import com.geekyouup.android.ustopwatch.R;
import com.geekyouup.android.ustopwatch.SoundManager;
import com.geekyouup.android.ustopwatch.StopwatchStateEndpointHelper;
import com.geekyouup.android.ustopwatch.TimeUtils;
import com.geekyouup.android.ustopwatch.UltimateStopwatchActivity;
import com.google.android.gcm.GCMRegistrar;


public class StopwatchFragment extends SherlockFragment {

    private StopwatchCustomView mStopwatchView;
    private Button mResetButton;
    private Button mStartButton;
    private View mSaveLapTimeButton;
    private TextView mTimerText;
    private double mCurrentTimeMillis = 0;
    private SoundManager mSoundManager;
    private boolean mRunningState = false;

    private static final String PREFS_NAME = "USW_SWFRAG_PREFS";
    private static final String PREF_IS_RUNNING = "key_stopwatch_is_running";
    private int mLastSecond = 0;
    private volatile boolean needSync = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mSoundManager = SoundManager.getInstance(getSherlockActivity());

        View swView = inflater.inflate(R.layout.stopwatch_fragment, null);
        mTimerText = (TextView) swView.findViewById(R.id.counter_text);
        mStopwatchView = (StopwatchCustomView) swView.findViewById(R.id.swview);

        mStartButton = (Button) swView.findViewById(R.id.startButton);
        mStartButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startStop();
                    return false;
                }
                return false;
            }
        });

        mResetButton = (Button) swView.findViewById(R.id.resetButton);
        mResetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LapTimeRecorder.getInstance().stopwatchReset();
                reset();
            }
        });

        mSaveLapTimeButton = swView.findViewById(R.id.saveButton);
        mSaveLapTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning()) {
                    LapTimeRecorder.getInstance().recordLapTime(mStopwatchView.getWatchTime(), (UltimateStopwatchActivity) getSherlockActivity());
                    mSoundManager.playSound(SoundManager.SOUND_LAPTIME);
                }
            }
        });


        return swView;
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences settings = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean(PREF_IS_RUNNING, mRunningState);
        mStopwatchView.saveState(editor);
        editor.commit();

        try {
            if (isRunning() && mCurrentTimeMillis > 0)
                AlarmUpdater.showChronometerNotification(getSherlockActivity(), (long) mCurrentTimeMillis);
        } catch (Exception ignored) {}
    }

    @Override
    public void onResume() {
        super.onResume();

        mStopwatchView.setHandler(new Handler() {
            @Override
            public void handleMessage(Message m) {
                if (m.getData().getBoolean(UltimateStopwatchActivity.MSG_UPDATE_COUNTER_TIME, false)) {
                    mCurrentTimeMillis = m.getData().getDouble(UltimateStopwatchActivity.MSG_NEW_TIME_DOUBLE);
                    setTime(mCurrentTimeMillis);

                    int currentSecond = (int) mCurrentTimeMillis / 1000;
                    if (currentSecond > mLastSecond) {
                        mSoundManager.doTick();
                        mLastSecond = currentSecond;
                    }

                } else if (m.getData().getBoolean(UltimateStopwatchActivity.MSG_STATE_CHANGE, false)) {
                    setUIState();
                }
            }
        });

        AlarmUpdater.cancelChronometerNotification(getSherlockActivity());

        SharedPreferences settings = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mRunningState = settings.getBoolean(PREF_IS_RUNNING, false);
        mStopwatchView.restoreState(settings);

        ((UltimateStopwatchActivity) getSherlockActivity()).registerStopwatchFragment(this);


        //center the timer text in a fixed position, stops wiggling numbers
        Paint paint = new Paint();
        Rect bounds = new Rect();
        paint.setTypeface(Typeface.SANS_SERIF);// your preference here
        paint.setTextSize(getResources().getDimension(R.dimen.counter_font));// have this the same as your text size
        String text = getString(R.string.default_time); //00:00:00.000
        paint.getTextBounds(text, 0, text.length(), bounds);
        int text_width = bounds.width();
        int width = getResources().getDisplayMetrics().widthPixels;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) width = width / 2;

        mTimerText.setPadding((width - text_width) / 2, 0, 0, 0);

    }

    public void startStop() {
        mStopwatchView.startStop();
        setUIState();
        syncState(mStopwatchView.getWatchTime(), isRunning());
    }

    private void syncState(double time, boolean isRunning) {
        StopwatchStateEndpointHelper helper = StopwatchStateEndpointHelper.getInstance();
        helper.sendStateToEndpoint(getSherlockActivity(),
                time, isRunning,
                GCMRegistrar.getRegistrationId(getSherlockActivity()));
    }

    private void setUIState() {
        boolean stateChanged = (mRunningState != isRunning());
        mRunningState = isRunning();
        mResetButton.setEnabled(mRunningState || (mCurrentTimeMillis != 0));
        mSaveLapTimeButton.setEnabled(mRunningState || (mCurrentTimeMillis != 0));

        if (isAdded()) mStartButton.setText(mRunningState ? getString(R.string.pause) : getString(R.string.start));

        if (stateChanged)
            mSoundManager.playSound(mRunningState ? SoundManager.SOUND_START : SoundManager.SOUND_STOP);
    }

    public void reset() {
        mStopwatchView.setTime(0,0,0,true);
        mSoundManager.playSound(SoundManager.SOUND_RESET);

        mResetButton.setEnabled(false);
        mSaveLapTimeButton.setEnabled(false);
        mStartButton.setText(getString(R.string.start));
        syncState(0.0, isRunning());
    }

    private void setTime(double millis) {
        if (mTimerText != null)
            mTimerText.setText(TimeUtils.createStyledSpannableString(getSherlockActivity(), millis, true));
    }

    public double getTime() {
        return mCurrentTimeMillis;
    }

    // this is the callback function
    public void setState(double time, boolean makeRunning) {
        mStopwatchView.setState((int)time, makeRunning);
        mCurrentTimeMillis = time;
        setTime(time);
    }

    public boolean isRunning() {
        return (mStopwatchView != null && mStopwatchView.isRunning());
    }

}
