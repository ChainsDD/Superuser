/*
 *	Copyright (c) 2011 Adam Shanks, Daniel Huckaby
 *
 *	Licensed under the Apache License, Version 2.0 (the "License");
 *	you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS,
 * 	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *	See the License for the specific language governing permissions and
 *	limitations under the License.
 */

package com.noshufou.android.su.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.TextView;

import com.noshufou.android.su.R;

public class PagerHeader extends ViewGroup {
    private static final String TAG = "Su.PagerHeader";

    private Context mContext;
    private int mDisplayedPage = 0;

    private static final int LEFT_ZONE = -1;
    private static final int MIDDLE_ZONE = 0;
    private static final int RIGHT_ZONE = 1;

    private int mLeftZoneEdge;
    private int mRightZoneEdge;
    private boolean mTouchZonesAccurate;

    private int mTouchSlopSquare;
    private boolean mAlwaysInTapRegion;
    private MotionEvent mCurrentDownEvent;

    private ShapeDrawable mTabDrawable;
    private ShapeDrawable mBottomBar;
    private GradientDrawable mShadow;
    private GradientDrawable mFadingEdgeLeft;
    private GradientDrawable mFadingEdgeRight;

    private OnHeaderClickListener mOnHeaderClickListener = null;

    private boolean mChangeOnClick = true;

    private ColorSet mActiveTextColor;
    private ColorSet mInactiveTextColor;
    private ColorSet mTabColor;
    private int mTabHeight;
    private int mTabPadding;
    private int mPaddingPush;
    private int mFadingEdgeLength;
    private int mShadowHeight;
    private int mBottomBarHeight;
    private boolean mShowTopShadow;
    private boolean mShowBottomBar;
    private boolean mShowTab;

    private static DisplayMetrics mDisplayMetrics;

    public interface OnHeaderClickListener {
        public void onHeaderClicked(int position);
        public void onHeaderSelected(int position);
    }

    public PagerHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        Resources resources = context.getResources();
        mDisplayMetrics = resources.getDisplayMetrics();

        // Get attributes from the layout xml
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagerHeader, 0, 0);
        mActiveTextColor = new ColorSet(
                a.getColor(R.styleable.PagerHeader_activeTextColor, Color.BLACK));
        mInactiveTextColor = new ColorSet(
                a.getColor(R.styleable.PagerHeader_inactiveTextColor, Color.DKGRAY));
        mTabColor = new ColorSet(
                a.getColor(R.styleable.PagerHeader_tabColor, mActiveTextColor.getColor()));
        mTabHeight = a.getDimensionPixelSize(R.styleable.PagerHeader_tabHeight, dipToPixels(4));
        mTabPadding = a.getDimensionPixelSize(R.styleable.PagerHeader_tabPadding, dipToPixels(10));
        mPaddingPush = a.getDimensionPixelSize(
                R.styleable.PagerHeader_paddingPush, dipToPixels(50));
        mFadingEdgeLength = a.getDimensionPixelSize(
                R.styleable.PagerHeader_fadingEdgeLength, dipToPixels(30));
        mShowTopShadow = a.getBoolean(R.styleable.PagerHeader_showTopShadow, true);
        mShowBottomBar = a.getBoolean(R.styleable.PagerHeader_showBottomBar, true);
        mShowTab = a.getBoolean(R.styleable.PagerHeader_showTab, true);

        ColorSet fadingEdgeColorHint = new ColorSet(0);
        if (a.hasValue(R.styleable.PagerHeader_backgroundColor)) {
            int backgroundColor = a.getColor(R.styleable.PagerHeader_backgroundColor, 0);
            setBackgroundColor(backgroundColor);
            fadingEdgeColorHint.setColor(backgroundColor);
        } else if (a.hasValue(R.styleable.PagerHeader_fadingEdgeColorHint)) {
            fadingEdgeColorHint.setColor(
                    a.getColor(R.styleable.PagerHeader_fadingEdgeColorHint, 0));
        } else {
            Log.w(TAG, "Either backgroundColor or fadingEdgeColorHint must be specified to " +
                    "enable fading edges");
            fadingEdgeColorHint.setColor(0x00000000);
        }

        mTabDrawable = new ShapeDrawable(new RectShape());
        mTabDrawable.getPaint().setColor(mTabColor.getColor());

        mBottomBar = new ShapeDrawable(new RectShape());
        mBottomBar.getPaint().setColor(mTabColor.getColor());
        mBottomBarHeight = dipToPixels(2);

        mShadow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x88000000, 0x00000000});
        mShadowHeight = dipToPixels(3);

        int[] fadingEdgeGradient = new int[] { fadingEdgeColorHint.getColor(),
                fadingEdgeColorHint.getColor(0) };
        mFadingEdgeLeft = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                fadingEdgeGradient);
        mFadingEdgeRight = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                fadingEdgeGradient);

        final ViewConfiguration config = ViewConfiguration.get(context);
        int touchSlop = config.getScaledTouchSlop();
        mTouchSlopSquare = touchSlop * touchSlop;
    }

    public void add(int index, String label) {
        TextView textView = new TextView(mContext);
        textView.setTextColor(mInactiveTextColor.getColor());
        textView.setTextSize(16);
        textView.setText(label);
        addView(textView);
    }

    public void setDisplayedPage(int index) {
        mDisplayedPage = index;
    }

    public void setOnHeaderClickListener(OnHeaderClickListener listener) {
        mOnHeaderClickListener = listener;
    }

    public void setChangeOnClick(boolean changeOnClick) {
        mChangeOnClick = changeOnClick;
    }

    public boolean getChangeOnClick() {
        return mChangeOnClick;
    }

    public void setPosition(int position, float positionOffset, int positionOffsetPixels) {
        mTouchZonesAccurate = false;
        int width = getWidth();
        int center = width / 2;

        // Move the view at position. This will be the label for the left
        // of the two fragments that may be on the screen
        if (position >= 0 && position < getChildCount()) {
            TextView view = (TextView) getChildAt(position);
            int viewWidth = view.getWidth();
            int leftMin = 0;
            if (position + 1 < getChildCount()) {
                int nextViewWidth = getChildAt(position + 1).getWidth();
                leftMin = Math.min(0,
                        center - (nextViewWidth / 2) - mPaddingPush - viewWidth);
            }
            int leftMax = center - (viewWidth / 2);
            int newLeft = map(positionOffset, 1, 0, leftMin, leftMax);
            view.layout(newLeft, view.getTop(), newLeft + viewWidth, view.getBottom());
            view.setTextColor(Color.rgb(
                    map(positionOffset, 1, 0, mInactiveTextColor.red, mActiveTextColor.red),
                    map(positionOffset, 1, 0, mInactiveTextColor.green, mActiveTextColor.green),
                    map(positionOffset, 1, 0, mInactiveTextColor.blue, mActiveTextColor.blue)));
        }

        // Move the view at position + 1. This will be the label for the
        // right of the two fragments that may be visible on screen
        if ((position + 1) < getChildCount()) {
            TextView view = (TextView) getChildAt(position + 1);
            int viewWidth = view.getWidth();
            int prevViewWidth = getChildAt(position).getWidth();
            int leftMin = center - (viewWidth / 2);
            int leftMax = Math.max(width - viewWidth,
                    center + (prevViewWidth / 2) + mPaddingPush);
            int newLeft = map(positionOffset, 1, 0, leftMin, leftMax);
            view.layout(newLeft, view.getTop(), newLeft + viewWidth, view.getBottom());
            view.setTextColor(Color.rgb(
                    map(positionOffset, 1, 0, mActiveTextColor.red, mInactiveTextColor.red),
                    map(positionOffset, 1, 0, mActiveTextColor.green, mInactiveTextColor.green),
                    map(positionOffset, 1, 0, mActiveTextColor.blue, mInactiveTextColor.blue)));
        }

        // Move the view at position - 1. This will be the label for the
        // fragment that is off the screen to the left, if it exists
        if (position > 0) {
            TextView view = (TextView) getChildAt(position - 1);
            int plusOneLeft = getChildAt(position).getLeft();
            int newLeft = view.getLeft();
            int viewWidth = view.getWidth();
            if (plusOneLeft < newLeft + viewWidth + mPaddingPush || newLeft < 0) {
                newLeft = Math.min(0, plusOneLeft - viewWidth - mPaddingPush);
                view.layout(newLeft, view.getTop(), newLeft + viewWidth, view.getBottom());
                int alpha = map(positionOffset, 1, 0, 0, 255);
                view.setTextColor(mInactiveTextColor.getColor(alpha));
            }
        }

        // Move the view at position + 2. This will be the label for the
        // fragment that is off the screen to the right, if it exists
        if ((position + 2) < getChildCount()) {
            TextView view = (TextView) getChildAt(position + 2);
            int minusOneRight = getChildAt(position + 1).getRight();
            int newLeft = view.getLeft();
            int viewWidth = view.getWidth();
            if (minusOneRight > (newLeft - mPaddingPush) || newLeft + viewWidth > width) {
                newLeft = Math.max(minusOneRight + mPaddingPush, width - viewWidth);
                view.layout(newLeft, view.getTop(), newLeft + viewWidth, view.getBottom());
                int alpha = map(positionOffset, 0, 1, 0, 255);
                view.setTextColor(mInactiveTextColor.getColor(alpha));
            }
        }

        // Draw the tab under the active or oncoming TextView based on the
        // positionOffset
        View view = getChildAt(positionOffset < 0.5f?position:position + 1);
        int viewLeft = view.getLeft();
        int viewRight = view.getRight();
        float percent = (Math.abs(positionOffset - 0.5f)/0.5f);
        int tabHeight =  (int) (mTabHeight * percent);
        int alpha = (int) (255 * percent);
        mTabDrawable.setBounds(viewLeft - mTabPadding,
                getHeight() - tabHeight,
                viewRight + mTabPadding,
                getHeight());
        mTabDrawable.setAlpha(alpha);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int textHeight = 0;
        int textWidth = 0;
        for (int i = 0; i < getChildCount(); i++ ) {
            View view = getChildAt(i);
            view.measure(0, 0);
            textHeight = Math.max(textHeight, view.getMeasuredHeight());
            textWidth = Math.max(textWidth, view.getMeasuredWidth());
        }

        int desiredWidth = textWidth + (mFadingEdgeLength *2);
        int width = resolveSize(desiredWidth, widthMeasureSpec);

        int desiredHeight = textHeight + getPaddingTop() + getPaddingBottom()
                + mShadowHeight + mTabHeight;
        int height = resolveSize(desiredHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int right = r - l;
        int height = b - t;

        for (int i = 0; i < getChildCount(); i++) {
            // Put all the children outside of the view, then use setPosition
            // to put the ones that need to be seen back. This will be where
            // we set the top and bottom of every view though.
            TextView view = (TextView) getChildAt(i);
            int viewWidth = view.getMeasuredWidth();
            int viewHeight = view.getMeasuredHeight();
            int textTop = (height/2) - (viewHeight -
                    ((view.getLineHeight()*view.getLineCount())/2));
            view.layout(right,
                    textTop,
                    right + viewWidth,
                    textTop + viewHeight);
        }
        setPosition(mDisplayedPage, 0, 0);

        mShadow.setBounds(0, 0, right, mShadowHeight);
        mBottomBar.setBounds(0, height - mBottomBarHeight, right, height);

        // Set up the fading edges
        mFadingEdgeLeft.setBounds(0, mShadowHeight, mFadingEdgeLength, height - mBottomBarHeight);
        mFadingEdgeRight.setBounds(right - mFadingEdgeLength, mShadowHeight,
                right, height - mBottomBarHeight);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        mFadingEdgeLeft.draw(canvas);
        mFadingEdgeRight.draw(canvas);
        if (mShowTopShadow) mShadow.draw(canvas);
        if (mShowBottomBar) mBottomBar.draw(canvas);
        if (mShowTab) mTabDrawable.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            if (mCurrentDownEvent != null) {
                mCurrentDownEvent.recycle();
            }
            mCurrentDownEvent = MotionEvent.obtain(event);
            mAlwaysInTapRegion = true;
            showPress(mCurrentDownEvent, true);
            break;
        case MotionEvent.ACTION_MOVE:
            if (mAlwaysInTapRegion) {
                final int deltaX = (int) (x - mCurrentDownEvent.getX());
                final int deltaY = (int) (y - mCurrentDownEvent.getY());
                int distance = (deltaX * deltaX) + (deltaY * deltaY);
                if (distance > mTouchSlopSquare) {
                    mAlwaysInTapRegion = false;
                    showPress(mCurrentDownEvent, false);
                }
            }
            break;
        case MotionEvent.ACTION_UP:
            showPress(mCurrentDownEvent, false);
            if (mAlwaysInTapRegion) {
                onTap(mCurrentDownEvent);
            }
        }

        return true;
    }

    private void showPress(MotionEvent event, boolean pressed) {
        int touchZone = getTouchZone(event);
        if (touchZone != MIDDLE_ZONE) {
            TextView view = (TextView) getChildAt(mDisplayedPage + getTouchZone(event));
            view.setTextColor(pressed?mActiveTextColor.getColor():mInactiveTextColor.getColor());
        }
    }

    private void onTap(MotionEvent event) {
        int touchZone = getTouchZone(event);
        if (mOnHeaderClickListener != null) {
            mOnHeaderClickListener.onHeaderClicked(mDisplayedPage + touchZone);
        }

        if (mChangeOnClick && touchZone != MIDDLE_ZONE) {
            setDisplayedPage(mDisplayedPage + touchZone);
            if (mOnHeaderClickListener != null) {
                mOnHeaderClickListener.onHeaderSelected(mDisplayedPage);
            }
        }
    }

    private int getTouchZone(MotionEvent event) {
        if (mTouchZonesAccurate) {
            int x = (int) event.getX();
            if (x < mLeftZoneEdge && mDisplayedPage > 0) {
                return LEFT_ZONE;
            } else if (x > mLeftZoneEdge && x < mRightZoneEdge) {
                return MIDDLE_ZONE;
            } else if (x > mRightZoneEdge && mDisplayedPage < getChildCount() -1) {
                return RIGHT_ZONE;
            }
        } else {
            View middleChild = getChildAt(mDisplayedPage);
            int mLeft = middleChild.getLeft();
            int mRight = middleChild.getRight();

            View leftChild = getChildAt(mDisplayedPage - 1);
            int lRight = leftChild!=null?leftChild.getRight():-1;

            View rightChild = getChildAt(mDisplayedPage + 1);
            int rLeft = rightChild!=null?rightChild.getLeft():-1;

            mLeftZoneEdge = lRight < 0 ? 0 : lRight + ((mLeft - lRight)/2);
            mRightZoneEdge = rLeft < 0 ? getWidth() : rLeft - ((rLeft - mRight)/2);
            mTouchZonesAccurate = true;
            return getTouchZone(event);
        }
        return MIDDLE_ZONE;
    }

    private static int map(float value, float fromLow, float fromHigh, int toLow, int toHigh) {
        return (int) ((value - fromLow) * (toHigh - toLow) / (fromHigh - fromLow) + toLow);
    }

    /**
     * Converts density independent pixel value to raw pixel value
     */
    private static int dipToPixels(float dipValue) {
        return (int) (mDisplayMetrics.density * dipValue + 0.5f);
    }

    private class ColorSet {
        public int alpha;
        public int red;
        public int green;
        public int blue;

        ColorSet(int color) {
            setColor(color);
        }

        public void setColor(int color) {
            alpha = Color.alpha(color);
            red = Color.red(color);
            green = Color.green(color);
            blue = Color.blue(color);
        }

        public int getColor() {
            return Color.argb(alpha, red, green, blue);
        }

        public int getColor(int alphaOverride) {
            return Color.argb(alphaOverride, red, green, blue);
        }
    }
}
