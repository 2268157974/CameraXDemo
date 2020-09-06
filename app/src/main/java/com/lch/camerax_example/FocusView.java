package com.lch.camerax_example;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class FocusView extends View {

    public FocusView(Context context) {
        super(context);
    }

    public FocusView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FocusView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    private Paint mPaint;
    private Rect mRect;


    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mRect != null) {
            canvas.drawRect(mRect, mPaint);
        }
    }

    public void startFocus(int x, int y) {
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(4);
        mRect = new Rect(x - 100, y - 100, x + 50, y + 50);
        invalidate();
    }

    public void clear() {
        mRect = null;
        mPaint.reset();
        invalidate();
    }
}
