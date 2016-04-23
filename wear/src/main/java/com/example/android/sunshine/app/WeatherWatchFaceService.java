package com.example.android.sunshine.app;

/**
 * Created by DivyaM on 4/22/16.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class WeatherWatchFaceService extends CanvasWatchFaceService {

    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);


    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final Typeface BOLD_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener {
        private static final String TAG = "WeatherWatchFaceService";
        private static final String WEATHER_PATH = "/WeatherWatchFace/Config";

        private static final String HIGH_TEMPERATURE = "HIGH_TEMPERATURE";
        private static final String LOW_TEMPERATURE = "LOW_TEMPERATURE";

        private static final String SYNC_NOW = "/sync_now";


        /** Alpha value for drawing time when in mute mode. */
        static final int MUTE_ALPHA = 100;

        /** Alpha value for drawing time when not in mute mode. */
        static final int NORMAL_ALPHA = 255;

        /** How often {@link #mUpdateTimeHandler} ticks in milliseconds. */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;


        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTimePaint;

        boolean mAmbient;
        //Time mTime;
        Calendar mCalendar;

        int mTapCount;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        static final String COLON_STRING = ":";


        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(WeatherWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        private Paint mDatePaint;
        private Paint mTemperaturePaint;
        private float mColonWidth;
        private float mTimeOffset;
        private float mDateOffset;
        private float mTempOffset;
        private float mInternalDistance;
        private boolean mMute;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;
        Date mDate;
        private float mLineHeight;
        private Paint mColonPaint;
        private String mAmString;
        private String mPmString;
        private String mHightTemp;
        private String mLowTemp;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WeatherWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = WeatherWatchFaceService.this.getResources();
            AssetManager assets = WeatherWatchFaceService.this.getAssets();

            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mAmString = resources.getString(R.string.digital_am);
            mPmString = resources.getString(R.string.digital_pm);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text),BOLD_TYPEFACE);
            mColonPaint = createTextPaint(resources.getColor(R.color.digital_colons),BOLD_TYPEFACE);
            mDatePaint = createTextPaint(resources.getColor(R.color.date_bg_color), NORMAL_TYPEFACE);
            mTemperaturePaint = createTextPaint(resources.getColor(R.color.temp_bg_color), NORMAL_TYPEFACE);
            //, Typeface.createFromAsset(assets,resources.getString(R.string.weather_date_font)));


//            mTemperaturePaint = new Paint();
//
//            mTemperaturePaint = createTextPaint(resources.getColor(R.color.weather_temperature_color));//,
//              //      Typeface.createFromAsset(assets,resources.getString(R.string.weather_temprature_font)));

            mInternalDistance = resources.getDimension(R.dimen.weather_internal_distance);

            // mTime = new Time();
            mDate = new Date();
            mCalendar = Calendar.getInstance();

            sendMessage(SYNC_NOW);

            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                //mTime.clear(TimeZone.getDefault().getID());
                //mTime.setToNow();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                    Log.d(TAG, "Google API Client disconnected");
                }

            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in am  bient mode), so we may need to start or stop the timer.
            updateTimer();
        }



        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WeatherWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WeatherWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = WeatherWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float timeTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_text_size_round : R.dimen.digital_date_text_size);

            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_highlow_text_size_round: R.dimen.digital_highlow_text_Size);


            mTimePaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mTemperaturePaint.setTextSize(tempTextSize);
            mColonPaint.setTextSize(timeTextSize);

            mColonWidth = mColonPaint.measureText(COLON_STRING);

            mTimeOffset = (mTimePaint.ascent() + mTimePaint.descent());
            mDateOffset = (mDatePaint.ascent()+ mDatePaint.descent());
            mTempOffset = (mTemperaturePaint.ascent()+ mTemperaturePaint.descent());
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mTemperaturePaint.setAntiAlias(!inAmbientMode);
                    mColonPaint.setAntiAlias(!mAmbient);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onInterruptionFilterChanged: " + interruptionFilter);
            }
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mDatePaint.setAlpha(alpha);
                mTemperaturePaint.setAlpha(alpha);
                mTimePaint.setAlpha(alpha);
                mColonPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }



        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Show colons for the first half of each second so the colons blink on when the time
            // updates.
            boolean mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            boolean is24Hour = DateFormat.is24HourFormat(WeatherWatchFaceService.this);
            String hourString;
            String minString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            float AMPMStringWidth = mTimePaint.measureText(mAmString);

            float minWidth = mTimePaint.measureText(minString);


            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            float hourWidth = mTimePaint.measureText(hourString);

            float x = (bounds.width() - (hourWidth +minWidth + mColonWidth +mColonWidth+AMPMStringWidth))/2;
            float y = ((canvas.getHeight()-mTimeOffset)/2);

            canvas.drawText(hourString, x, mYOffset, mTimePaint);
            x += hourWidth;
            if (isInAmbientMode() || mMute|| mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
            }
            x += mColonWidth;
            canvas.drawText(minString, x, mYOffset, mTimePaint);
            x += minWidth;

            y += mInternalDistance - mDateOffset / 2;

            // Draw the background.
            // In unmuted interactive mode, draw a second blinking colon followed by the seconds.
            // Otherwise, if we're in 12-hour mode, draw AM/PM

            // In unmuted interactive mode, draw a second blinking colon followed by the seconds.
            // Otherwise, if we're in 12-hour mode, draw AM/PM
            if (!is24Hour) {
                x += mColonWidth;
                canvas.drawText(getAmPmString(
                        mCalendar.get(Calendar.AM_PM)), x, mYOffset, mTimePaint);
            }

            // Only render the day of week and date if there is no peek card, so they do not bleed
            // into each other in ambient mode.
            if (getPeekCardPosition().isEmpty()) {

                String dateString = mDayOfWeekFormat.format(mDate);
                float dateWidth = mDatePaint.measureText(dateString);


                x = (bounds.width() - dateWidth)/2;
                Log.d(TAG,"bounds Width, dateWidth , x " + bounds.width() + ", " + dateWidth + ", " + x);
                // Day of week
                canvas.drawText(
                        mDayOfWeekFormat.format(mDate),
                        x, mYOffset + mLineHeight, mDatePaint);

                Log.d(TAG, "dayofWeek " + mDayOfWeekFormat.format(mDate));
                // Date
//                canvas.drawText(
//                        mDateFormat.format(mDate),
//                        mXOffset, mYOffset + mLineHeight * 2, mDatePaint);
            }

            // if(mHightTemp != null && mLowTemp != null){
            String high = String.format("%s", mHightTemp);
            float highWidth = mTemperaturePaint.measureText(high);
            String low = String.format("%s", mLowTemp);
            float lowWidth = mTemperaturePaint.measureText(low);

            String temphi = String.format(getResources().getString(R.string.format_temperature), 26.0);
            String templo = String.format(getResources().getString(R.string.format_temperature), 23.0);
            //String.format("%s %s", mHightTemp, mLowTemp)
            float tempWidth = mTemperaturePaint.measureText(temphi) + mTemperaturePaint.measureText(templo);

            Log.d(TAG, "temphi tempLo: " + high + "\t" + low);
            x  = (bounds.width() - (tempWidth))/2;
            y = mYOffset + 2*mLineHeight;


            canvas.drawText(temphi, x,y, mTemperaturePaint);
            // }
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }



        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEE MMM dd", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = DateFormat.getDateFormat(WeatherWatchFaceService.this);
            mDateFormat.setCalendar(mCalendar);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {

            Wearable.DataApi.addListener(mGoogleApiClient,Engine.this);
            Log.d(TAG, "GoogleApiClient is Connected");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "GoogleApiClient Connection is Suspended");

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(TAG, "Data is changed");

            for(DataEvent event:dataEventBuffer){
                if(event.getType() == DataEvent.TYPE_CHANGED){
                    String path = event.getDataItem().getUri().getPath();
                    if(WEATHER_PATH.equals(path)){
                        Log.e("log", "Data Changed for " + WEATHER_PATH);
                        try{
                            DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                            mHightTemp = dataMapItem.getDataMap().getString(HIGH_TEMPERATURE);
                            mLowTemp = dataMapItem.getDataMap().getString(LOW_TEMPERATURE);
                            Log.d(TAG, "Data time : " + dataMapItem.getDataMap().getLong("time"));

                            Log.e(TAG,"From Phone: highTemp : " + mHightTemp + "\t LowTemp : " + mLowTemp );


                            invalidate();
                        }catch (Exception e){
                            Log.e(TAG, "Exception  " + e);
                        }
                    }else{
                        Log.e(TAG, " Unrecognized path : \"" + path + "\" \"" +    WEATHER_PATH + "\"" );
                    }
                }else{
                    Log.e("LOG", "Unknown data event type   " + event.getType());
                }
            }
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(TAG, "The connection of GoogleApiClient is failed");
        }

        private void sendMessage(final String path){
            if(!mGoogleApiClient.isConnected())
                mGoogleApiClient.connect();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                    for(Node node: nodes.getNodes()){
                        Log.i(TAG, "sending Message");

                        Wearable.MessageApi.sendMessage(
                                mGoogleApiClient,node.getId(),path,null).setResultCallback(
                                new ResultCallback<MessageApi.SendMessageResult>() {
                                    @Override
                                    public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                                        if(!sendMessageResult.getStatus().isSuccess()){
                                            Log.d(TAG, " Failed to send Message");
                                        }
                                    }
                                }
                        );

                    }
                }
            }).start();
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<Engine> mWeakReference;

        public EngineHandler(WeatherWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WeatherWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}