package com.noshufou.android.su.widget;

import android.content.Context;
import android.os.Build;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class ViewPagerCompat extends ViewPager {

    public ViewPagerCompat(Context context) {
        super(context);
    }

    public ViewPagerCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.DONUT) {
            return super.onInterceptTouchEvent(ev);
        } else {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.DONUT) {
            return super.onTouchEvent(ev);
        } else {
            return false;
        }
    }
}
