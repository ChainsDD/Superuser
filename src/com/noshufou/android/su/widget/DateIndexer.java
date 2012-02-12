package com.noshufou.android.su.widget;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import android.content.Context;
import android.database.Cursor;
import android.widget.SectionIndexer;

import com.noshufou.android.su.util.Util;

public class DateIndexer implements SectionIndexer {
//    private static final String TAG = "Su.DateIndexer";
    
    private Cursor mCursor;
    private int mColumnIndex;
    private int mSectionCount;
    private String[] mSections;
    private int[] mSectionDates;
    private int[] mSectionPositions;
    private SimpleDateFormat mIntFormat;
    
    public DateIndexer(Context context, Cursor cursor, int sortedColumnIndex) {
        mCursor = cursor;
        mColumnIndex = sortedColumnIndex;
        mIntFormat = new SimpleDateFormat("yyyyDDD");
        GregorianCalendar calendar = new GregorianCalendar();
        
        mCursor.moveToFirst();
        long firstDateLong = mCursor.getLong(mColumnIndex);
        mCursor.moveToLast();
        long lastDateLong = mCursor.getLong(mColumnIndex);

        Calendar startDate = Calendar.getInstance();
        startDate.setTimeInMillis(lastDateLong);
        Calendar endDate = Calendar.getInstance();
        endDate.setTimeInMillis(firstDateLong);
        mSectionCount = daysBetween(startDate, endDate);

        mSections = new String[mSectionCount];
        mSectionDates = new int[mSectionCount];
        mSectionPositions = new int[mSectionCount];

        calendar.setTimeInMillis(firstDateLong);
        for (int i = 0; i < mSectionCount; i++) {
            mSections[i] = Util.formatDate(context, calendar.getTimeInMillis());
            mSectionDates[i] = Integer.parseInt(mIntFormat.format(calendar.getTime()));
            mSectionPositions[i] = -1;
            calendar.add(GregorianCalendar.DATE, -1);
        }
    }

    @Override
    public int getPositionForSection(int section) {
        if (mCursor == null) {
            return 0;
        }
        
        if (section <= 0) {
            return 0;
        }
        
        if (section >= mSectionCount) {
            return mCursor.getCount();
        }
        
        if (mSectionPositions[section] > 0) {
        	return mSectionPositions[section];
        }
        
        int start = section;
        int end = mCursor.getCount();
        
        for (int i = section - 1; i > 0; i--) {
            if (mSectionPositions[i] > 0) {
                start = mSectionPositions[i];
                break;
            }
        }
        
        int savedCursorPos = mCursor.getPosition();
        long date;
        int dateInt;
        int result = 0;
        for (int i = start; i < end; i++) {
            if (mCursor.moveToPosition(i)) {
                date = mCursor.getLong(mColumnIndex);
                dateInt = Integer.parseInt(mIntFormat.format(date));
                if (mSectionDates[section] >= dateInt) {
                    mSectionPositions[section] = i;
                    result = i;
                    break;
                }
            }
        }
        mCursor.moveToPosition(savedCursorPos);
        return result;
    }

    @Override
    public int getSectionForPosition(int position) {
        // Find the date for the position
        int savedCursorPos = mCursor.getPosition();
        mCursor.moveToPosition(position);
        long date = mCursor.getLong(mColumnIndex);
        mCursor.moveToPosition(savedCursorPos);

        int dateInt = Integer.parseInt(mIntFormat.format(date));
        for (int i = 0; i < mSectionCount; i++) {
        	if (dateInt == mSectionDates[i]) {
        		return i;
        	}
        }
        return 0;
    }

    @Override
    public Object[] getSections() {
        return mSections;
    }
    
    public static int daysBetween(final Calendar startDate, final Calendar endDate) {
    	Calendar sDate = (Calendar) startDate.clone();
    	int daysBetween = 0;
    	
    	int y1 = sDate.get(Calendar.YEAR);
    	int y2 = endDate.get(Calendar.YEAR);
    	int m1 = sDate.get(Calendar.MONTH);
    	int m2 = endDate.get(Calendar.MONTH);
    	
    	// Year optimization
    	while (((y2 - y1) * 12 + (m2 - m1)) > 12) {
    		
    		// Move to Jan 01
    		if (sDate.get(Calendar.MONTH) == Calendar.JANUARY
    				&& sDate.get(Calendar.DAY_OF_MONTH) ==
    					sDate.getActualMinimum(Calendar.DAY_OF_MONTH)) {
    			daysBetween += sDate.getActualMaximum(Calendar.DAY_OF_YEAR);
    			sDate.add(Calendar.YEAR, 1);
    		} else {
    			int diff = 1 + sDate.getActualMaximum(Calendar.DAY_OF_YEAR)
    					- sDate.get(Calendar.DAY_OF_YEAR);
    			sDate.add(Calendar.DAY_OF_YEAR, diff);
    			daysBetween += diff;
    		}
    		y1 = sDate.get(Calendar.YEAR);
    	}
    	
    	// Month optimization
    	while ((m2 - m1) % 12 > 1) {
    		daysBetween += sDate.getActualMaximum(Calendar.DAY_OF_MONTH);
    		sDate.add(Calendar.MONTH, 1);
    		m1 = sDate.get(Calendar.MONTH);
    	}

    	// process the remainder date
    	while (sDate.before(endDate)) {
    		sDate.add(Calendar.DAY_OF_MONTH, 1);
    		daysBetween++;
    	}
    	
    	// Add one more day to include the end date
    	daysBetween++;

    	return daysBetween;
    }
    
}
