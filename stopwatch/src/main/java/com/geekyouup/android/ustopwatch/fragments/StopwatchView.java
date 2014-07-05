package com.geekyouup.android.ustopwatch.fragments;

import com.geekyouup.android.ustopwatch.AlarmUpdater;
import com.geekyouup.android.ustopwatch.R;
import com.geekyouup.android.ustopwatch.SoundManager;
import com.geekyouup.android.ustopwatch.UltimateStopwatchActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

/**
 * Animated view that draws the stopwatch, takes keystrokes, etc.
 */
public class StopwatchView extends SurfaceView implements SurfaceHolder.Callback {
	public class StopwatchThead extends Thread implements OnTouchListener {
		/*
		 * State-tracking constants
		 */
		public static final int STATE_PAUSE = 2;
		public static final int STATE_READY = 3;
		public static final int STATE_RUNNING = 4;

		private static final String KEY_STATE = "state";
		private static final String KEY_LASTTIME = "lasttime";
		private static final String KEY_NOWTIME = "currenttime";
        private static final String KEY_COUNTDOWN_SUFFIX = "_cd";
		private double mScaleFactor = 1; //how much to scale the images up or down by

		/** The drawable to use as the background of the animation canvas */
		private Bitmap mBackgroundImage;
		private int mBackgroundStartY;
		private int mAppOffsetX = 0;
		private int mAppOffsetY = 0;
		private double mMinsAngle = 0;
		private double mSecsAngle = 0;
		private double mDisplayTimeMillis = 0;
		private final double twoPI = Math.PI * 2.0;
		private boolean mStopwatchMode = true;

		private int mCanvasWidth = 320;
		private int mCanvasHeight = 480;
		private int mSecsCenterX = 156;
		private int mSecsCenterY = 230;
		private int mMinsCenterX = 156;
		private int mMinsCenterY = 185;

		private int mSecsHalfWidth = 0;
		private int mSecsHalfHeight = 0;
		private int mMinsHalfWidth = 0;
		private int mMinsHalfHeight = 0;

		/** Used to figure out elapsed time between frames */
		private long mLastTime;

		private Drawable mSecHand;
		private Drawable mMinHand;

		/** The state of the game. One of READY, RUNNING, PAUSE, LOSE, or WIN */
		private int mMode = STATE_READY;

		/** Indicate whether the surface has been created & is ready to draw */
		private boolean mRun = false;
		private boolean mSkipDraw = false;
        private long mTouching = 0L;

		/** Handle to the surface manager object we interact with */
		private SurfaceHolder mSurfaceHolder;
		private Handler mHandler;
		private Context mContext;
        private int mBGColor = 0xff000000;

		public StopwatchThead(SurfaceHolder surfaceHolder, Context context, boolean isStopwatchMode) {
			// get handles to some important objects
			mSurfaceHolder = surfaceHolder;
			mContext = context;
			mStopwatchMode=isStopwatchMode;

			Resources res = mContext.getResources();
			//loadGraphics(res, isStopwatchMode());

            mBGColor = getResources().getColor(isStopwatchMode?R.color.stopwatch_background:R.color.countdown_background);

            //fix the background colour shearing when swiping by setting the surface on top of the window
            //and fixing the window bg color
            setZOrderOnTop(true);
		}

        private void loadGraphics(Resources res, boolean isStopwatch)
        {
            if(res==null) res = mContext.getResources();
            mSkipDraw=true;

            int minDim = Math.min(mCanvasHeight,mCanvasWidth);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled=false;
            double handsScaleFactor = 1;

            if(minDim >= 1000){
                mBackgroundImage = BitmapFactory.decodeResource(res, isStopwatch?R.drawable.background1000:R.drawable.background1000_cd,options);
                handsScaleFactor=1.388;
            }else if(minDim >= 720){
                mBackgroundImage = BitmapFactory.decodeResource(res, isStopwatch?R.drawable.background720:R.drawable.background720_cd,options);
                handsScaleFactor=1;
            }else if(minDim >= 590){
                mBackgroundImage = BitmapFactory.decodeResource(res, isStopwatch?R.drawable.background590:R.drawable.background590_cd,options);
                handsScaleFactor=0.82;
            }else if(minDim >= 460){
                mBackgroundImage = BitmapFactory.decodeResource(res, isStopwatch?R.drawable.background460:R.drawable.background460_cd,options);
                handsScaleFactor= 0.64;
            }else if(minDim >= 320){
                mBackgroundImage = BitmapFactory.decodeResource(res, isStopwatch?R.drawable.background320:R.drawable.background320_cd,options);
                handsScaleFactor=0.444;
            }else if(minDim >= 240){
                mBackgroundImage = BitmapFactory.decodeResource(res, isStopwatch?R.drawable.background240:R.drawable.background240_cd,options);
                handsScaleFactor= 0.333;
            }else{
                mBackgroundImage = BitmapFactory.decodeResource(res, isStopwatch?R.drawable.background150:R.drawable.background150_cd,options);
                handsScaleFactor= 0.208;
            }

            mSecHand = res.getDrawable(isStopwatch?R.drawable.sechand:R.drawable.sechand_cd);
            mMinHand = res.getDrawable(isStopwatch?R.drawable.minhand:R.drawable.minhand_cd);

            mSecsHalfWidth = mSecHand.getIntrinsicWidth()/2;
            mSecsHalfHeight = mSecHand.getIntrinsicHeight()/2;

            mMinsHalfWidth = mMinHand.getIntrinsicWidth()/2;
            mMinsHalfHeight = mMinHand.getIntrinsicHeight()/2;

            mMinsHalfHeight = (int) ((double) mMinsHalfHeight * handsScaleFactor);
            mMinsHalfWidth = (int) ((double) mMinsHalfWidth * handsScaleFactor);
            mSecsHalfHeight= (int) ((double) mSecsHalfHeight * handsScaleFactor);
            mSecsHalfWidth= (int) ((double) mSecsHalfWidth * handsScaleFactor);

            mBackgroundStartY = (mCanvasHeight - mBackgroundImage.getHeight()) / 2;
            mAppOffsetX = (mCanvasWidth - mBackgroundImage.getWidth()) / 2;

            if (mBackgroundStartY < 0)
                mAppOffsetY = -mBackgroundStartY;

            mSecsCenterY = mBackgroundStartY + (mBackgroundImage.getHeight() / 2); //new graphics have watch center in center
            mMinsCenterY = mBackgroundStartY + (mBackgroundImage.getHeight() * 314 / 1000);//mSecsCenterY - 44;
            mSecsCenterX = mCanvasWidth/2;
            mMinsCenterX = mCanvasWidth/2;

            mSkipDraw=false;
        }

		public void setHandler(Handler handler) {
			this.mHandler = handler;
		}

		/**
		 * Starts the game, setting parameters for the current difficulty.
		 */
		public void doStart() {
			synchronized (mSurfaceHolder) {
				// First set the game for Medium difficulty
				mLastTime = System.currentTimeMillis();
				setState(STATE_RUNNING);
			}
		}

		/**
		 * Pauses the physics update & animation.
		 */
		public void pause() {
			synchronized (mSurfaceHolder) {
				if (mMode == STATE_RUNNING)
					setState(STATE_PAUSE);
			}
		}

		public void reset() {
            resetVars();
		}

		private void resetVars() {
			synchronized (mSurfaceHolder) {
				setState(STATE_PAUSE);
				mLastTime = 0;
				mMinsAngle = 0;
				mSecsAngle = 0;
				mDisplayTimeMillis = 0;
				
				broadcastClockTime(0);
			}
		}

		public void setTime(int hour, int minute, int seconds, boolean start) {
			synchronized (mSurfaceHolder) {
				setState(STATE_READY);
				mLastTime = System.currentTimeMillis();
				mMinsAngle = (Math.PI * 2 * ((double) minute / 30.0));
				mSecsAngle = (Math.PI * 2 * ((double) seconds / 60.0));
				mDisplayTimeMillis = hour * 3600000 + minute * 60000 + seconds * 1000;
				
				if(start) doStart();
                else
                {
                    updatePhysics(false);
                }
			}
		}

		@Override
		public void run() {
			while (mRun) {
				Canvas c = null;
				try {
					//auto double buffer by locking canvas
					c = mSurfaceHolder.lockCanvas(null);
					if(c!= null)
					{
						synchronized (mSurfaceHolder) {
							if (mMode == STATE_RUNNING) updatePhysics(false);
							if(!mSkipDraw) doDraw(c);
						}
					}
				} finally {
					if (c != null) {
						mSurfaceHolder.unlockCanvasAndPost(c);
					}
					try {
						sleep(30);
					} catch (InterruptedException e) {
					}
				}
			}
		}
		
		/**
		 * Update the time
		 */
		private void updatePhysics(boolean appResuming) {
			long now = System.currentTimeMillis();

			if(mMode == STATE_RUNNING)
			{
				if (isStopwatchMode())
					mDisplayTimeMillis += (now - mLastTime);
				else
					mDisplayTimeMillis -= (now - mLastTime);
			}else
			{
				mLastTime=now;
			}

			// mins is 0 to 30
			mMinsAngle = twoPI * (mDisplayTimeMillis / 1800000.0);
			mSecsAngle = twoPI * (mDisplayTimeMillis / 60000.0);

			if(mDisplayTimeMillis<0) mDisplayTimeMillis=0;
			
			// send the time back to the Activity to update the other views
			broadcastClockTime(isStopwatchMode()?mDisplayTimeMillis:-mDisplayTimeMillis);
			mLastTime = now;

			// stop timer at end
			if (mMode == STATE_RUNNING && !isStopwatchMode() && mDisplayTimeMillis <= 0) {
				resetVars(); // applies pause state
                notifyCountdownComplete(appResuming);
			}
		}		
		
		/**
		 * Draws the background and hands on the Canvas.
		 */
		private void doDraw(Canvas canvas) {
			// Draw the background image. Operations on the Canvas accumulate
            canvas.drawColor(mBGColor);
			if(mBackgroundImage!=null)
                canvas.drawBitmap(mBackgroundImage, mAppOffsetX, mBackgroundStartY + mAppOffsetY, null);

            // draw the mins hand with its current rotatiom
            if(mMinHand!=null && mSecHand !=null)
            {
                canvas.save();
                canvas.rotate((float) Math.toDegrees(mMinsAngle), mMinsCenterX, mMinsCenterY + mAppOffsetY);
                mMinHand.setBounds(mMinsCenterX - mMinsHalfWidth, mMinsCenterY - mMinsHalfHeight + mAppOffsetY,
                        mMinsCenterX + mMinsHalfWidth, mMinsCenterY + mAppOffsetY + mMinsHalfHeight);
                mMinHand.draw(canvas);
                canvas.restore();

                // Draw the secs hand with its current rotation
                canvas.save();
                canvas.rotate((float) Math.toDegrees(mSecsAngle), mSecsCenterX, mSecsCenterY + mAppOffsetY);
                mSecHand.setBounds(mSecsCenterX - mSecsHalfWidth, mSecsCenterY - mSecsHalfHeight + mAppOffsetY,
                        mSecsCenterX + mSecsHalfWidth, mSecsCenterY + mAppOffsetY + mSecsHalfHeight);
                mSecHand.draw(canvas);
                canvas.restore();
            }
		}

		/**
		 * Dump state to the provided Bundle. Typically called when the
		 * Activity is being suspended.
		 */
		public void saveState(SharedPreferences.Editor map) {
			synchronized (mSurfaceHolder) {
				if (!isStopwatchMode() || mDisplayTimeMillis > 0) {
					if (!isStopwatchMode() && mDisplayTimeMillis > 0 && mMode == STATE_RUNNING) {
						AlarmUpdater.setCountdownAlarm(mContext, (long) mDisplayTimeMillis);
					}else
					{
						AlarmUpdater.cancelCountdownAlarm(mContext); //just to be sure
					}

					map.putInt(KEY_STATE+(mStopwatchMode?"":KEY_COUNTDOWN_SUFFIX), mMode);
					map.putLong(KEY_LASTTIME+(mStopwatchMode?"":KEY_COUNTDOWN_SUFFIX), mLastTime);
					map.putLong(KEY_NOWTIME+(mStopwatchMode?"":KEY_COUNTDOWN_SUFFIX), (long) mDisplayTimeMillis);
				} else {
					map.clear();
				}
			}
		}

		/**
		 * Restores state from the indicated Bundle. Called when
		 * the Activity is being restored after having been previously
		 * destroyed.
		 */
		private synchronized void restoreState(SharedPreferences savedState) {
			synchronized (mSurfaceHolder) {
				if (savedState != null) {
					setState(savedState.getInt(KEY_STATE+(mStopwatchMode?"":KEY_COUNTDOWN_SUFFIX), STATE_PAUSE));
					mLastTime = savedState.getLong(KEY_LASTTIME+(mStopwatchMode?"":KEY_COUNTDOWN_SUFFIX), System.currentTimeMillis());
					mDisplayTimeMillis = savedState.getLong(KEY_NOWTIME+(mStopwatchMode?"":KEY_COUNTDOWN_SUFFIX), 0);
					//loadGraphics(null, mStopwatchMode);
					updatePhysics(true);
				}
				notifyStateChanged();
				AlarmUpdater.cancelCountdownAlarm(mContext); //just to be sure
			}
		}

		/**
		 * Used to signal the thread whether it should be running or not.
		 * Passing true allows the thread to run; passing false will shut it
		 * down if it's already running. Calling start() after this was most
		 * recently called with false will result in an immediate shutdown.
		 */
		public void setRunning(boolean b) {
			mRun = b;
		}

		public boolean isRunning() {
			return mRun;
		}

        public boolean isPaused()
        {
            return (mMode != STATE_RUNNING);
        }

		/**
		 * Sets the  mode. That is, whether we are running, paused, in the
		 * failure state etc.
		 */
		private void setState(int mode) {
			synchronized (mSurfaceHolder) {
				mMode = mode;
			}
		}

		/**
		 * Resumes from a pause.
		 */
		public void unpause() {
			// stop timer at end
			if (!isStopwatchMode() && mDisplayTimeMillis <= 0) {
				resetVars(); // applies pause state
				//requestCountdownDialog();
			} else {
				// Move the real time clock up to now
				synchronized (mSurfaceHolder) {
					mLastTime = System.currentTimeMillis();
				}
				setState(STATE_RUNNING);
			}
		}

        private void broadcastClockTime(double mTime)
		{
			if (mHandler != null) {
				Message msg = mHandler.obtainMessage();
				Bundle b = new Bundle();
				b.putBoolean(UltimateStopwatchActivity.MSG_UPDATE_COUNTER_TIME, true);
				b.putDouble(UltimateStopwatchActivity.MSG_NEW_TIME_DOUBLE, mTime);
				msg.setData(b);
				mHandler.sendMessage(msg);
			}
		}

        private void notifyCountdownComplete(boolean appResuming)
        {
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage();
                Bundle b = new Bundle();
                b.putBoolean(CountdownFragment.MSG_COUNTDOWN_COMPLETE, true);
                b.putBoolean(CountdownFragment.MSG_APP_RESUMING, appResuming);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }
        }

        private void notifyStateChanged()
        {
            if (mHandler != null) {
                Message msg = mHandler.obtainMessage();
                Bundle b = new Bundle();
                b.putBoolean(UltimateStopwatchActivity.MSG_STATE_CHANGE, true);
                msg.setData(b);
                mHandler.sendMessage(msg);
            }
        }


		public boolean startStop() {
			if (mMode == STATE_PAUSE) {
				unpause();
			} else if (mMode == STATE_RUNNING) {
				pause();
			} else {
				doStart();
			}

            notifyStateChanged();
            return (mMode == STATE_RUNNING);
		}

		public boolean isStopwatchMode() {
			return mStopwatchMode;
		}

		public void setIsStopwatchMode(boolean isStopwatchMode) {
            this.mStopwatchMode = isStopwatchMode;
			//resetVars(); //why was this being called here? Relic from when we had one clock face?
			loadGraphics(null, isStopwatchMode);
		}

		public boolean onTouch(View v, MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
                SoundManager sm = SoundManager.getInstance(mContext);
                if(sm.isEndlessAlarmSounding())
                {
                   sm.stopEndlessAlarm();
                }else
                {
    				mTouching = System.currentTimeMillis();
                }
			}else if(event.getAction() == MotionEvent.ACTION_MOVE)
            {
                if(mTouching>0 && System.currentTimeMillis()-mTouching > 1000)
                    mTouching=0L;   //reset touch if user is swiping
            }
            else if(event.getAction() == MotionEvent.ACTION_UP)
            {
                if(mTouching>0)// && System.currentTimeMillis()-mTouching < 500)
                    startStop();

                mTouching=0L;
            }
			return true;
		}
		
		// none trackball devices
		public boolean doKeypress(int keyCode) {
			if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
				startStop();
				return true;
			}
			return false;
		}

		//trackball device
		public boolean doTrackBall(MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				startStop();
				return true;
			}
			return false;
		}

		/* Callback invoked when the surface dimensions change. */
		public void setSurfaceSize(int width, int height) {
			// synchronized to make sure these all change atomically
			synchronized (mSurfaceHolder) {
				mCanvasWidth = width;
				mCanvasHeight = height;

				loadGraphics(null,isStopwatchMode());
			}
		}

	}

	
	/** Handle to the application context, used to e.g. fetch Drawables. */
	private StopwatchThead thread;
	private SurfaceHolder sHolder;
	private Context mContext;
	private SharedPreferences mRestoreState;
    private boolean mStopwatchMode=true;

	public StopwatchView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;

		// register our interest in hearing about changes to our surface
		sHolder = getHolder();
		sHolder.addCallback(this);

		// create thread only; it's started in surfaceCreated()
		setFocusableInTouchMode(true);
		setFocusable(true);
	}

	/**
	 * Fetches the animation thread corresponding to this LunarView.
	 * 
	 * @return the animation thread
	 */
	public StopwatchThead getThread() {
		return thread;
	}

	public StopwatchThead createNewThread(boolean isStopwatchMode) {
		this.mStopwatchMode=isStopwatchMode;
        if(thread==null) thread = new StopwatchThead(sHolder, mContext, isStopwatchMode);
		setOnTouchListener(thread); //touching the stopwatch no longer starts and stops it.
		return thread;
	}

	@Override
	public boolean onTrackballEvent(MotionEvent event) {
		return thread.doTrackBall(event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		boolean consumed = thread.doKeypress(keyCode);
		if (!consumed)
			return super.onKeyDown(keyCode, event);
		else
			return consumed;
	}

	/* Callback invoked when the surface dimensions change. */
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		thread.setSurfaceSize(width, height);
	}

	/*
	 * Callback invoked when the Surface has been created and is ready to be
	 * used.
	 */
	public void surfaceCreated(SurfaceHolder holder) {
		// start the thread here so that we don't busy-wait in run()
		// waiting for the surface to be created
		Log.d("USW", "Surface Created");
		try {
			if(thread==null) createNewThread(mStopwatchMode);
			thread.setRunning(true);
			thread.start();
		
			if (mRestoreState != null) {
				thread.restoreState(mRestoreState);
			}
		} catch (Exception e) {
			Log.e("USW", "StopwatchView error", e);
		}
	}

	/*
	 * Callback invoked when the Surface has been destroyed and must no longer
	 * be touched. WARNING: after this method returns, the Surface/Canvas must
	 * never be touched again!
	 */
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d("USW", "Surface DESTROYED");

		// we have to tell thread to shut down & wait for it to finish, or else
		// it might touch the Surface after we return and explode
		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
			}
		}
		thread=null;
	}

	public void restoreState(SharedPreferences savedState) {
		mRestoreState = savedState;
	}

    public double getWatchTime()
    {
        return thread.mDisplayTimeMillis;
    }

}
