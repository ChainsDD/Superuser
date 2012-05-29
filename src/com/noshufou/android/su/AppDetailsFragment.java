/*******************************************************************************
 * Copyright (c) 2011 Adam Shanks (ChainsDD)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.noshufou.android.su;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.noshufou.android.su.preferences.Preferences;
import com.noshufou.android.su.provider.PermissionsProvider.Apps;
import com.noshufou.android.su.provider.PermissionsProvider.Logs;
import com.noshufou.android.su.service.ResultService;
import com.noshufou.android.su.util.Util;
import com.noshufou.android.su.util.Util.MenuId;
import com.noshufou.android.su.widget.LogAdapter;
import com.noshufou.android.su.widget.PinnedHeaderListView;

public class AppDetailsFragment extends SherlockListFragment
    implements LoaderManager.LoaderCallbacks<Cursor>, FragmentWithLog {
    private static final String TAG = "Su.AppDetailsFragment";

    private TextView mAppName = null;
    private ImageView mAppIcon = null;
    private ImageView mStatusIcon = null;
    private LinearLayout mDetailsContainer = null;
    private TextView mPackageNameText = null;
    private TextView mAppUidText = null;
    private TextView mRequestDetailText = null;
    private TextView mCommandText = null;
    private TextView mStatusText = null;

    private boolean mElitePresent = false;
    private boolean mUseAppSettings = true;
    private boolean mNotificationsEnabled = true;
    private boolean mLoggingEnabled = true;

    private Button mToggleButton = null;

    private static final int DETAILS_LOADER = 1;
    private static final int LOG_LOADER = 2;

    private long mShownIndex = -1;

    private boolean mReady = false;
    private boolean mDualPane = false;

    private int mAllow = -1;

    LogAdapter mAdapter = null;

    public static final String[] DETAILS_PROJECTION = new String[] {
        Apps._ID, Apps.UID, Apps.PACKAGE, Apps.NAME, Apps.EXEC_UID, Apps.EXEC_CMD, Apps.ALLOW,
        Apps.NOTIFICATIONS, Apps.LOGGING
    };

    private static final int DETAILS_COLUMN_UID = 1;
    private static final int DETAILS_COLUMN_PACKAGE = 2;
    private static final int DETAILS_COLUMN_NAME = 3;
    private static final int DETAILS_COLUMN_EXEC_UID = 4;
    private static final int DETAILS_COLUMN_EXEC_CMD = 5;
    private static final int DETAILS_COLUMN_ALLOW = 6;
    private static final int DETAILS_COLUMN_NOTIFICATIONS = 7;
    private static final int DETAILS_COLUMN_LOGGING = 8;

    private static final int MENU_GROUP_OPTIONS = 1;
    
    public static AppDetailsFragment newInstance(long index) {
        AppDetailsFragment fragment = new AppDetailsFragment();
        
        Bundle args = new Bundle();
        args.putLong("index", index);
        fragment.setArguments(args);
        
        return fragment;
    }
    
    public long getShownIndex() {
        return mShownIndex;
    }
    
    public void setShownIndex(long index) {
        mShownIndex = index;
        getLoaderManager().restartLoader(DETAILS_LOADER, null, this);
        getLoaderManager().restartLoader(LOG_LOADER, null, this);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (container == null) {
            return null;
        }
        View view = inflater.inflate(R.layout.fragment_app_details, container, false);

        mAppName = (TextView) view.findViewById(R.id.app_name);
        mAppIcon = (ImageView) view.findViewById(R.id.app_icon);
        mStatusIcon = (ImageView) view.findViewById(R.id.status_icon);
        mDetailsContainer = (LinearLayout) view.findViewById(R.id.details_container);
        mPackageNameText = (TextView) view.findViewById(R.id.package_name);
        mAppUidText = (TextView) view.findViewById(R.id.app_uid);
        mRequestDetailText = (TextView) view.findViewById(R.id.request_detail);
        mCommandText = (TextView) view.findViewById(R.id.command);
        mStatusText = (TextView) view.findViewById(R.id.status);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        FrameLayout fragmentContainer = (FrameLayout) getActivity()
                .findViewById(R.id.fragment_container);
        if (fragmentContainer != null) {
            mDualPane = true;
        }
        
        if (savedInstanceState != null && 
                savedInstanceState.containsKey("mShownIndex")) {
            mShownIndex = savedInstanceState.getLong("mShownIndex");
        } else if (getArguments() != null) {
            mShownIndex = getArguments().getLong("index", 0);
        } else {
            mShownIndex = 0;
        }
        
        setupListView();
        getLoaderManager().initLoader(DETAILS_LOADER, null, this);
        getLoaderManager().initLoader(LOG_LOADER, null, this);
        
        Log.d(TAG, "Before elite check, time is " + System.currentTimeMillis());
        mElitePresent = Util.elitePresent(getActivity(), true, 2);
        Log.d(TAG, "After elite check, time is " + System.currentTimeMillis());
    }
    
    private void setupListView() {
        final ListView list = getListView();
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        
        list.setDividerHeight(0);
        
        mAdapter = new LogAdapter(null, getActivity(), false);
        setListAdapter(mAdapter);
        
        if (list instanceof PinnedHeaderListView &&
                mAdapter.getDisplaySectionHeadersEnabled()) {
            PinnedHeaderListView pinnedHeaderListView =
                (PinnedHeaderListView) list;
            View pinnedHeader = inflater.inflate(R.layout.recent_list_section, list, false);
            pinnedHeaderListView.setPinnedHeaderView(pinnedHeader);
        }
        
        list.setOnScrollListener(mAdapter);
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mReady) return;

        menu.add(Menu.NONE, MenuId.TOGGLE, MenuId.TOGGLE, R.string.menu_toggle)
                .setIcon(R.drawable.ic_action_toggle)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, MenuId.FORGET, MenuId.FORGET, R.string.menu_forget)
                .setIcon(R.drawable.ic_action_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, MenuId.CLEAR_LOG, MenuId.CLEAR_LOG, R.string.menu_clear_log)
            .setIcon(R.drawable.ic_action_clear_log)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        if (mElitePresent) {
            menu.add(Menu.NONE, MenuId.USE_APP_SETTINGS, MenuId.USE_APP_SETTINGS, R.string.use_global_settings)
            .setCheckable(true).setChecked(mUseAppSettings)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            menu.add(MENU_GROUP_OPTIONS, MenuId.NOTIFICATIONS, MenuId.NOTIFICATIONS, R.string.notifications_enabled)
            .setCheckable(true).setChecked(mNotificationsEnabled);
            menu.add(MENU_GROUP_OPTIONS, MenuId.LOGGING, MenuId.LOGGING, R.string.logging_enabled)
            .setCheckable(true).setChecked(mLoggingEnabled);

            menu.setGroupEnabled(MENU_GROUP_OPTIONS, !mUseAppSettings);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case MenuId.TOGGLE:
                toggle(null);
                return true;
            case MenuId.FORGET:
                forget(null);
                return true;
            case MenuId.CLEAR_LOG:
                clearLog();
                return true;
            case MenuId.USE_APP_SETTINGS:
            case MenuId.NOTIFICATIONS:
            case MenuId.LOGGING:
                setOptions(itemId);
                return true;
            case R.id.abs__home:
            case android.R.id.home:
                if (mDualPane) {
                    closeDetails();
                } else {
                    Util.goHome(getActivity());
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mShownIndex != -1) {
            outState.putLong("mShownIndex", mShownIndex);
        }

        super.onSaveInstanceState(outState);
    }

    private void setOptions(int option) {
        ContentResolver cr = getActivity().getContentResolver();
        ContentValues values = new ContentValues();;
        switch (option) {
            case MenuId.USE_APP_SETTINGS:
                mUseAppSettings = !mUseAppSettings;
                values.put(Apps.NOTIFICATIONS, mUseAppSettings?null:mNotificationsEnabled);
                values.put(Apps.LOGGING, mUseAppSettings?null:mLoggingEnabled);
                if (mUseAppSettings) {
                    SharedPreferences prefs =
                            PreferenceManager.getDefaultSharedPreferences(getActivity());
                    mNotificationsEnabled = prefs.getBoolean(Preferences.NOTIFICATIONS, true);
                    mLoggingEnabled = prefs.getBoolean(Preferences.LOGGING, true);
                }
                break;
            case MenuId.NOTIFICATIONS:
                mNotificationsEnabled = !mNotificationsEnabled;
                values.put(Apps.NOTIFICATIONS, mNotificationsEnabled);
                break;
            case MenuId.LOGGING:
                mLoggingEnabled = !mLoggingEnabled;
                values.put(Apps.LOGGING, mLoggingEnabled);
                break;
        }
        cr.update(ContentUris.withAppendedId(Apps.CONTENT_URI, mShownIndex),
                values, null, null);
        getSherlockActivity().invalidateOptionsMenu();
    }
    
    public void toggle(View view) {
        if (!mReady) {
            return;
        }
        
        if (PreferenceManager.getDefaultSharedPreferences(getActivity())
                .getBoolean(Preferences.PIN, false)) {
            Intent intent = new Intent(getActivity(), PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHECK);
            startActivityForResult(intent, 0);
        } else {
            doToggle();
        }

    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            doToggle();
        }
    }

    private void doToggle() {
        ContentResolver cr = getActivity().getContentResolver();
        Uri uri = Uri.withAppendedPath(Apps.CONTENT_URI, String.valueOf(mShownIndex));

        ContentValues values = new ContentValues();
        values.put(Apps.ALLOW, mAllow == 1?0:1);
        cr.update(uri, values, null, null);
        
        // Update the log
        values.clear();
        values.put(Logs.DATE, System.currentTimeMillis());
        values.put(Logs.TYPE, Logs.LogType.TOGGLE);
        cr.insert(Uri.withAppendedPath(Logs.CONTENT_URI, String.valueOf(mShownIndex)), values);
        Intent intent = new Intent(getActivity(), ResultService.class);
        intent.putExtra(ResultService.EXTRA_ACTION, ResultService.ACTION_RECYCLE);
        getActivity().startService(intent);
    }
    
    public void forget(View view) {
        if (!mReady) {
            return;
        }

        ContentResolver cr = getActivity().getContentResolver();
        Uri uri = Uri.withAppendedPath(Apps.CONTENT_URI, String.valueOf(mShownIndex));

        cr.delete(uri, null, null);
        closeDetails();
    }
    
    public void clearLog(View view) {
        clearLog();
    }
    
    @Override
    public void clearLog() {
        if (mShownIndex != -1) {
            getActivity().getContentResolver().delete(ContentUris.withAppendedId(Logs.CONTENT_URI, mShownIndex), null, null);
        }
    }

    public void closeDetails() {
        if (mDualPane) {
            Fragment logFragment = LogFragment.newInstance();
            FragmentTransaction transaction = ((FragmentActivity)getActivity())
                    .getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, logFragment);
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.commit();
        } else {
            Util.goHome(getActivity());
        }
    }
    

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
        case DETAILS_LOADER:
            return new CursorLoader(getActivity(),
                    ContentUris.withAppendedId(Apps.CONTENT_URI, mShownIndex),
                    DETAILS_PROJECTION, null, null, null);
        case LOG_LOADER:
            return new CursorLoader(getActivity(),
                    ContentUris.withAppendedId(Logs.CONTENT_URI, mShownIndex),
                    LogAdapter.PROJECTION, null, null, null);
        default:
            throw new IllegalArgumentException("Unknown Loader: " + id);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        switch (loader.getId()) {
        case DETAILS_LOADER:
            if (data.moveToFirst()) {
                if (mDetailsContainer != null) {
                    mDetailsContainer.setVisibility(View.VISIBLE);
                }

                getSherlockActivity().getSupportActionBar().setTitle(data.getString(DETAILS_COLUMN_NAME));
                getSherlockActivity().getSupportActionBar().setSubtitle(data.getString((DETAILS_COLUMN_PACKAGE)));
                if (mAppName != null) {
                    mAppName.setText(data.getString(DETAILS_COLUMN_NAME));
                    mAppIcon.setImageDrawable(
                            Util.getAppIcon(getActivity(), data.getInt(DETAILS_COLUMN_UID)));
                }
                int allow = data.getInt(DETAILS_COLUMN_ALLOW);
                if (mStatusIcon != null) {
                    mStatusIcon.setImageDrawable(Util.getStatusIconDrawable(getActivity(), allow));
                    mStatusIcon.setVisibility(View.VISIBLE);
                }
                mPackageNameText.setText(data.getString(DETAILS_COLUMN_PACKAGE));
                mAppUidText.setText(data.getString(DETAILS_COLUMN_UID));
                mRequestDetailText.setText(
                        Util.getUidName(getActivity(), data.getInt(DETAILS_COLUMN_EXEC_UID), true));
                mCommandText.setText(data.getString(DETAILS_COLUMN_EXEC_CMD));
                mStatusText.setText(allow==1?
                        R.string.allowed:R.string.denied);
                if (mToggleButton != null) {
                    mToggleButton.setText(allow==1?R.string.deny:R.string.allow);
                }
                mAllow = allow;
                
                String notificationsStr = data.getString(DETAILS_COLUMN_NOTIFICATIONS);
                String loggingStr = data.getString(DETAILS_COLUMN_LOGGING);
                if (notificationsStr == null && loggingStr == null) {
                    mUseAppSettings = true;
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    mNotificationsEnabled = prefs.getBoolean(Preferences.NOTIFICATIONS, true);
                    mLoggingEnabled = prefs.getBoolean(Preferences.LOGGING, true);
                } else {
                    mUseAppSettings = false;
                    mNotificationsEnabled = notificationsStr.equals("1")?true:false;
                    mLoggingEnabled = loggingStr.equals("1")?true:false;
                }
            }
            mReady = true;
            getSherlockActivity().invalidateOptionsMenu();
            break;
        case LOG_LOADER:
            mAdapter.swapCursor(data);
            break;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (loader.getId() == LOG_LOADER) {
            mAdapter.swapCursor(null);
        }
    }
}
