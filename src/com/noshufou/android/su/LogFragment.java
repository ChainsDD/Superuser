package com.noshufou.android.su;

import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.noshufou.android.su.provider.PermissionsProvider.Logs;
import com.noshufou.android.su.util.Util.MenuId;
import com.noshufou.android.su.widget.LogAdapter;
import com.noshufou.android.su.widget.PinnedHeaderListView;

public class LogFragment extends SherlockListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>, FragmentWithLog {
    private static final String TAG = "Su.LogFragment";
    
    private LogAdapter mAdapter = null;
    private TextView mLogCountTextView = null;
    private boolean mDualPane;

    public static LogFragment newInstance() {
        return new LogFragment();
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDualPane = ((HomeActivity)getActivity()).isDualPane();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_log, container, false);

        mLogCountTextView = (TextView) view.findViewById(R.id.log_count);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(TAG, "onActivityCreated()");

        if (mDualPane) {
            Log.d(TAG, "is dual pane");
            ListFragment appList = (ListFragment) getActivity().getSupportFragmentManager()
                    .findFragmentById(R.id.app_list);
            appList.getListView().clearChoices();
            appList.getListView().invalidateViews();
        }

        setupListView();
        getLoaderManager().initLoader(0, null, this);
    }

    private void setupListView() {
        final ListView list = getListView();
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        
        list.setDividerHeight(0);
        
        mAdapter = new LogAdapter(null, getActivity());
        setListAdapter(mAdapter);
        
        if (list instanceof PinnedHeaderListView &&
                mAdapter.getDisplaySectionHeadersEnabled()) {
            PinnedHeaderListView pinnedHeaderListView =
                (PinnedHeaderListView) list;
            View pinnedHeader = inflater.inflate(R.layout.log_list_section, list, false);
            pinnedHeaderListView.setPinnedHeaderView(pinnedHeader);
        }
        
        list.setOnScrollListener(mAdapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.d(TAG, "onCreateOptionsMenu()");
        if (mDualPane) {
            Log.d(TAG, "is dual pane");
            menu.add(Menu.NONE, MenuId.INFO, MenuId.INFO,
                    R.string.page_label_info)
                    .setIcon(R.drawable.ic_action_info)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        menu.add(Menu.NONE, MenuId.CLEAR_LOG, MenuId.CLEAR_LOG, R.string.menu_clear_log)
                .setIcon(R.drawable.ic_action_clear_log)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MenuId.CLEAR_LOG:
                getActivity().getContentResolver().delete(Logs.CONTENT_URI, null, null);
                return true;
            case MenuId.INFO:
                ((HomeActivity)getActivity()).showInfo();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(), Logs.CONTENT_URI, LogAdapter.PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        mAdapter.swapCursor(cursor);
        mLogCountTextView.setText(getString(R.string.log_count, cursor!=null?cursor.getCount():0));
        mLogCountTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mAdapter.swapCursor(null);
    }

    public void clearLog(View view) {
        clearLog();
    }
    
    @Override
    public void clearLog() {
        getActivity().getContentResolver().delete(Logs.CONTENT_URI, null, null);
    }

}
