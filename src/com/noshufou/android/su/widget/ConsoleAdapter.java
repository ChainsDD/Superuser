package com.noshufou.android.su.widget;

import android.content.Context;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.noshufou.android.su.R;
import com.noshufou.android.su.widget.ConsoleAdapter.ConsoleEntry;

public class ConsoleAdapter extends ArrayAdapter<ConsoleEntry> {
    
    private Context mContext;

    public static final int CONSOLE_GREY = 0xffeeeeec;
    public static final int CONSOLE_RED = 0xffef2929;
    public static final int CONSOLE_GREEN = 0xff8ae234;
    
    public ConsoleAdapter(Context context) {
        super(context, R.layout.console_item);
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) super.getView(position, convertView, parent);
        ConsoleEntry entry = getItem(position);
        
        Spannable str = (Spannable) view.getText();
        str.setSpan(new ForegroundColorSpan(entry.statusColor),
                entry.entry.length(),
                entry.toString().length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        return view;
    }

    public void addEntry(int res) {
        ConsoleEntry entry = new ConsoleEntry(mContext.getString(res));
        add(entry);
    }

    public void addStatusToLastEntry(CharSequence status, int color) {
        ConsoleEntry entry = getItem(getCount() - 1);
        entry.status = status;
        entry.statusColor = color;
        notifyDataSetChanged();
    }

    class ConsoleEntry {
        public String entry;
        public CharSequence status = "";
        public int statusColor = CONSOLE_GREY;
        
        public ConsoleEntry(String text) {
            entry = text;
        }

        @Override
        public String toString() {
            return entry + status;
        }
    }
}
