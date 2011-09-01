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

import com.noshufou.android.su.R;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class PagerHeader extends ViewGroup {

	private final int HEIGHT;
	private final int PADDING_TOP;
	private final int PADDING_BOTTOM;
	private final int PADDING_PUSH;
	private final int TAB_HEIGHT;
	private final int TAB_PADDING;
	private final int FADING_EDGE_LENGTH;
	private final float TEXT_SIZE = 16;

	private int mTextTop = 0;
	private int mTextHeight = 0;

	private Context mContext;
	private int mDisplayedPage = 0;

	private int mLastMotionAction;
	private float mLastMotionX;

	private Drawable mTabDrawable;
	private Drawable mFadingEdgeLeft;
	private Drawable mFadingEdgeRight;

	private OnHeaderChangeListener mOnHeaderChangeListener = null;
	private OnHeaderClickListener mOnHeaderClickListener = null;
	
	private int selectedTextColor, selectedTextColorRed, selectedTextColorGreen, selectedTextColorBlue;
	private int unselectedTextColor, unselectedTextColorRed, unselectedTextColorGreen, unselectedTextColorBlue;
	
	private static DisplayMetrics displayMetrics;

	public interface OnHeaderChangeListener {
		public void onHeaderSelected(int position);
	}
	
	public interface OnHeaderClickListener {
		public void onHeaderClicked(int position);
	}

	public PagerHeader(Context context, AttributeSet attrs) {
		super(context, attrs);
		mContext = context;
		
		Resources resources = context.getResources();
		displayMetrics = resources.getDisplayMetrics();
		
		selectedTextColor = resources.getColor(R.color.pager_header_selected_text_color);
		selectedTextColorRed = Color.red(selectedTextColor);
		selectedTextColorGreen = Color.green(selectedTextColor);
		selectedTextColorBlue = Color.blue(selectedTextColor);
		
		unselectedTextColor = resources.getColor(R.color.pager_header_unselected_text_color);
		unselectedTextColorRed = Color.red(unselectedTextColor);
		unselectedTextColorGreen = Color.green(unselectedTextColor);
		unselectedTextColorBlue = Color.blue(unselectedTextColor);

		HEIGHT = dipToPixels(32);
		PADDING_TOP = dipToPixels(3);
		PADDING_BOTTOM = dipToPixels(5);
		PADDING_PUSH = dipToPixels(50);
		TAB_HEIGHT = dipToPixels(4);
		TAB_PADDING = dipToPixels(10);
		FADING_EDGE_LENGTH = dipToPixels(30);

		// TODO Do this programmatically
		mTabDrawable = resources.getDrawable(R.drawable.pager_header_tab);
		mFadingEdgeLeft = resources.getDrawable(R.drawable.pager_header_fading_edge_left);
		mFadingEdgeRight = resources.getDrawable(R.drawable.pager_header_fading_edge_right);
	}

	public void add(int index, String label) {
		TextView textView = new TextView(mContext);
		textView.setText(label);
		textView.setTextColor(unselectedTextColor);
		textView.setTextSize(TEXT_SIZE);
		textView.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (mOnHeaderClickListener != null) {
					mOnHeaderClickListener.onHeaderClicked(mDisplayedPage);
				}
			}
		});
		addView(textView);
	}

	public void setOnHeaderChangeListener(OnHeaderChangeListener listener) {
		mOnHeaderChangeListener = listener;
	}
	
	public void setOnHeaderClickListener(OnHeaderClickListener listener) {
		mOnHeaderClickListener = listener;
	}

	public void setDisplayedPage(int index) {
		mDisplayedPage = index;
	}

	public void setPosition(int position, float positionOffset,
			int positionOffsetPixels) {
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
                        center - (nextViewWidth / 2) - PADDING_PUSH - viewWidth);
            }
            int leftMax = center - (viewWidth / 2);
            int newLeft = map(positionOffset, 1, 0, leftMin, leftMax);
			view.layout(newLeft, view.getTop(), newLeft + viewWidth, view.getBottom());
			view.setTextColor(Color.rgb(
					Math.max(unselectedTextColorRed, (int) ((-(selectedTextColorRed * 2) * (float) positionOffset) + selectedTextColorRed)),
					Math.max(unselectedTextColorGreen, (int) ((-(selectedTextColorGreen * 2) * (float) positionOffset) + selectedTextColorGreen)),
					Math.max(unselectedTextColorBlue, (int) ((-(selectedTextColorBlue * 2) * (float) positionOffset) + selectedTextColorBlue))));
		}

		// Move the view at position + 1. This will be the label for the
		// right of the two fragments that may be visible on screen
		if ((position + 1) < getChildCount()) {
			TextView view = (TextView) getChildAt(position + 1);
			int viewWidth = view.getWidth();
			int prevViewWidth = getChildAt(position).getWidth();
            int leftMin = center - (viewWidth / 2);
            int leftMax = Math.max(width - viewWidth,
                    center + (prevViewWidth / 2) + PADDING_PUSH);
            int newLeft = map(positionOffset, 1, 0, leftMin, leftMax);
			view.layout(newLeft, view.getTop(), newLeft + viewWidth, view.getBottom());
			view.setTextColor(Color.rgb(
					Math.max(unselectedTextColorRed, (int) (((selectedTextColorRed * 2) * positionOffset) - selectedTextColorRed)),
					Math.max(unselectedTextColorGreen, (int) (((selectedTextColorGreen * 2) * positionOffset) - selectedTextColorGreen)),
					Math.max(unselectedTextColorBlue, (int) (((selectedTextColorBlue * 2) * positionOffset) - selectedTextColorBlue))));
		}

		// Move the view at position - 1. This will be the label for the
		// fragment that is off the screen to the left, if it exists
		if (position > 0) {
			TextView view = (TextView) getChildAt(position - 1);
			int plusOneLeft = getChildAt(position).getLeft();
			int newLeft = view.getLeft();
			int viewWidth = view.getWidth();
			if (plusOneLeft < newLeft + viewWidth + PADDING_PUSH || newLeft < 0) {
				newLeft = Math.min(0, plusOneLeft - viewWidth - PADDING_PUSH);
				view.layout(newLeft, view.getTop(), newLeft + viewWidth,
						view.getBottom());
				int alpha = map(positionOffset, 0, 1, 0, 255);
				view.setTextColor(Color.argb(alpha, 0, 0, 0));
			}
		}

		// Move the view at position + 2. This will be the label for the
		// fragment that is off the screen to the right, if it exists
		if ((position + 2) < getChildCount()) {
			TextView view = (TextView) getChildAt(position + 2);
			int minusOneRight = getChildAt(position + 1).getRight();
			int newLeft = view.getLeft();
			int viewWidth = view.getWidth();
			if (minusOneRight > (newLeft - PADDING_PUSH)
					|| newLeft + viewWidth > width) {
				newLeft = Math.max(minusOneRight + PADDING_PUSH, width
						- viewWidth);
				view.layout(newLeft, view.getTop(), newLeft + viewWidth,
						view.getBottom());
				int alpha = (int) (255 * ((float) (width - newLeft) / (float) viewWidth));
				view.setTextColor(Color.argb(alpha, 0, 0, 0));
			}
		}

		// Draw the tab under the active or oncoming TextView based the
		// positionOffset
		View view = getChildAt(positionOffset < 0.5f ? position : position + 1);
		int viewLeft = view.getLeft();
		int viewRight = view.getRight();
		float percent = (float) (Math.abs(positionOffset - 0.5f) / 0.5f);
		int tabHeight = (int) (TAB_HEIGHT * percent);
		int alpha = (int) (255 * percent);
		mTabDrawable.setBounds(viewLeft - TAB_PADDING, getHeight() - tabHeight,
				viewRight + TAB_PADDING, getHeight());
		mTabDrawable.setAlpha(alpha);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		for (int i = 0; i < getChildCount(); i++) {
			View view = getChildAt(i);
			view.measure(0, 0);
			mTextHeight = Math.max(mTextHeight, view.getMeasuredHeight());
		}

		int width = resolveSize(0, widthMeasureSpec);

		int textHeight = getChildAt(0).getMeasuredHeight();
		mTextTop = PADDING_TOP;
		int height = Math
				.max(HEIGHT, textHeight + PADDING_TOP + PADDING_BOTTOM);

		setMeasuredDimension(width, height);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		int left = 0;
		int right = r - l;
		int center = (r - l) / 2;

		for (int i = 0; i < getChildCount(); i++) {
			TextView view = (TextView) getChildAt(i);
			int viewWidth = view.getMeasuredWidth();
			int viewHeight = view.getMeasuredHeight();
			int viewLeft = 0;
			if (i == mDisplayedPage - 1) {
				int nextViewWidth = getChildAt(mDisplayedPage).getWidth();
				viewLeft = Math.min(0, center - (nextViewWidth / 2) - PADDING_PUSH - viewWidth);
			} else if (i == mDisplayedPage) {
				viewLeft = center - (viewWidth / 2);
				view.setTextColor(selectedTextColor);
				mTabDrawable
						.setBounds(viewLeft - TAB_PADDING, b - t - TAB_HEIGHT,
								viewLeft + viewWidth + TAB_PADDING, b - t);
			} else if (i == mDisplayedPage + 1) {
				int prevViewWidth = getChildAt(mDisplayedPage).getWidth();
				viewLeft = Math.max(right - viewWidth,
						center + (prevViewWidth / 2) + PADDING_PUSH);
			} else if (i < (mDisplayedPage - 1)) {
				viewLeft = left - viewWidth - 5;
			} else if (i > (mDisplayedPage + 1)) {
				viewLeft = right + 5;
			}
			view.layout(viewLeft, mTextTop, viewLeft + viewWidth, mTextTop
					+ viewHeight);
		}

		// Set up the fading edges
		mFadingEdgeLeft.setBounds(0, mTextTop, FADING_EDGE_LENGTH, mTextTop
				+ mTextHeight);
		mFadingEdgeRight.setBounds(right - FADING_EDGE_LENGTH, mTextTop, right,
				mTextTop + mTextHeight);
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		mTabDrawable.draw(canvas);
		mFadingEdgeLeft.draw(canvas);
		mFadingEdgeRight.draw(canvas);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		int action = event.getAction();
		if (action == MotionEvent.ACTION_DOWN) {

		}

		if (action == MotionEvent.ACTION_UP
				&& mLastMotionAction == MotionEvent.ACTION_DOWN
				&& event.getX() == mLastMotionX) {
			int width = getWidth();
			int pageSize = width / 4;
			if ((int) mLastMotionX < pageSize && mDisplayedPage > 0) {
				setDisplayedPage(mDisplayedPage - 1);
				if (mOnHeaderChangeListener != null) {
					mOnHeaderChangeListener.onHeaderSelected(mDisplayedPage);
				}
			} else if ((int) mLastMotionX > width - pageSize
					&& mDisplayedPage < getChildCount() - 1) {
				setDisplayedPage(mDisplayedPage + 1);
				if (mOnHeaderChangeListener != null) {
					mOnHeaderChangeListener.onHeaderSelected(mDisplayedPage);
				}
			}
		}

		mLastMotionAction = event.getAction();
		mLastMotionX = event.getX();
		return true;
	}
	
	private static int map(float value, float fromLow, float fromHigh, int toLow, int toHigh) {
		return (int) ((value - fromLow) * (toHigh - toLow) / (fromHigh - fromLow) + toLow);
	}
	
	/**
	 * Converts density independent pixel value to raw pixel value
	 */
	private static int dipToPixels(float dipValue) {
		return (int) (displayMetrics.density * dipValue + 0.5f);
	}
}