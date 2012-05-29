package com.noshufou.android.su.preferences;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.Switch;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockPreferenceActivity;
import com.actionbarsherlock.view.MenuItem;
import com.noshufou.android.su.R;
import com.noshufou.android.su.util.Util;

public class PreferencesActivityHC extends SherlockPreferenceActivity {
    
    private List<Header> mHeaders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.prefs_headers, target);
        
        updateHeaderList(target);
        
        mHeaders = target;
    }

    private void updateHeaderList(List<Header> target) {
        int i = 0;
        while (i < target.size()) {
            Header header = target.get(i);
            int id = (int) header.id;
            if (id == R.id.nfc_settings && NfcAdapter.getDefaultAdapter(this) == null) {
                target.remove(header);
            }
            
            if (target.get(i) == header)
                i++;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        
        ListAdapter listAdapter = getListAdapter();
        if (listAdapter instanceof HeaderAdapter) {
            ((HeaderAdapter) listAdapter).resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        
        ListAdapter listAdapter = getListAdapter();
        if (listAdapter instanceof HeaderAdapter) {
            ((HeaderAdapter) listAdapter).pause();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Util.goHome(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class HeaderAdapter extends ArrayAdapter<Header> {
        static final int HEADER_TYPE_NORMAL = 0;
        static final int HEADER_TYPE_SWITCH = 2;
        private static final int HEADER_TYPE_COUNT = HEADER_TYPE_SWITCH + 1;
        
        private final PreferenceEnabler mLoggingEnabler;
        private final PreferenceEnabler mNotifsEnabler;
        
        private static class HeaderViewHolder {
            ImageView icon;
            TextView title;
            TextView summary;
            Switch switch_;
        }
        
        private LayoutInflater mInflater;
        
        static int getHeaderType(Header header) {
            if (header.id == R.id.log_settings || header.id == R.id.notification_settings) {
                return HEADER_TYPE_SWITCH;
            } else {
                return HEADER_TYPE_NORMAL;
            }
        }
        
        @Override
        public int getItemViewType(int position) {
            Header header = getItem(position);
            return getHeaderType(header);
        }
        
        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }
        
        @Override
        public boolean isEnabled(int position) {
            return true;
        }
        
        @Override
        public int getViewTypeCount() {
            return HEADER_TYPE_COUNT;
        }
        
        @Override
        public boolean hasStableIds() {
            return true;
        }
        
        public HeaderAdapter(Context context, List<Header> objects) {
            super(context, 0, objects);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            
            mLoggingEnabler = new PreferenceEnabler(context, new Switch(context), Preferences.LOGGING, true);
            mNotifsEnabler = new PreferenceEnabler(context, new Switch(context), Preferences.NOTIFICATIONS, true);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HeaderViewHolder holder;
            Header header = getItem(position);
            int headerType = getHeaderType(header);
            View view = null;
            
            if (convertView == null) {
                holder = new HeaderViewHolder();
                switch (headerType) {
                    case HEADER_TYPE_SWITCH:
                        view = mInflater.inflate(R.layout.preference_header_switch, parent, false);
                        holder.icon = (ImageView) view.findViewById(R.id.icon);
                        holder.title = (TextView) view.findViewById(android.R.id.title);
                        holder.summary = (TextView) view.findViewById(android.R.id.summary);
                        holder.switch_ = (Switch) view.findViewById(R.id.switchWidget);
                        break;
                    case HEADER_TYPE_NORMAL:
                        view = mInflater.inflate(R.layout.preference_header_item, parent, false);
                        holder.icon = (ImageView) view.findViewById(R.id.icon);
                        holder.title = (TextView) view.findViewById(android.R.id.title);
                        holder.summary = (TextView) view.findViewById(android.R.id.summary);
                        break;
                }
                view.setTag(holder);
            } else {
                view = convertView;
                holder = (HeaderViewHolder) view.getTag();
            }
            
            switch (headerType) {
                case HEADER_TYPE_SWITCH:
                    if (header.id == R.id.log_settings) {
                        mLoggingEnabler.setSwitch(holder.switch_);
                    } else {
                        mNotifsEnabler.setSwitch(holder.switch_);
                    }
                case HEADER_TYPE_NORMAL:
                    holder.icon.setImageResource(header.iconRes);
                    holder.title.setText(header.getTitle(getContext().getResources()));
                    CharSequence summary = header.getSummary(getContext().getResources());
                    if (!TextUtils.isEmpty(summary)) {
                        holder.summary.setVisibility(View.VISIBLE);
                        holder.summary.setText(summary);
                    } else {
                        holder.summary.setVisibility(View.GONE);
                    }
                    break;
            }
            
            return view;
        }
        
        public void pause() {
            mLoggingEnabler.pause();
            mNotifsEnabler.pause();
        }
        
        public void resume() {
            mLoggingEnabler.resume();
            mNotifsEnabler.resume();
        }
    }
    
    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (mHeaders == null) {
            mHeaders = new ArrayList<Header>();
            for (int i = 0; i < adapter.getCount(); i++) {
                mHeaders.add((Header) adapter.getItem(i));
            }
        }
        
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.HONEYCOMB_MR2) {
            super.setListAdapter(new HeaderAdapter(this, mHeaders));
        } else {
            super.setListAdapter(adapter);
        }
    }

}
