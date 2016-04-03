package com.example.wear.wearable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

/**
 * Created by DivyaM on 4/3/16.
 */
public class WeatherWatchFaceService extends CanvasWatchFaceService {
    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }
    private class Engine extends CanvasWatchFaceService.Engine{

        Paint mTextPaint;
        Float mTextXOffset;
        Float mTextYOffset;
        boolean mIsRound;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            //Create Paint for later use
            mTextPaint = new Paint();
            mTextPaint.setTextSize(40);
            mTextPaint.setColor(Color.WHITE);
            mTextPaint.setAntiAlias(true);


            //Make text in Center
            mTextXOffset = mTextPaint.measureText("12:00")/2;
            mTextYOffset = (mTextPaint.ascent() + mTextPaint.descent())/2;

        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            super.onDraw(canvas, bounds);
            canvas.drawText("12:00",
                    bounds.centerX()-mTextXOffset,
                    bounds.centerY()-mTextYOffset,
                    mTextPaint);
        }


    }
}
