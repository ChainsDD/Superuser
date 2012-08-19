package com.noshufou.android.su;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.widget.AbsListView;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.noshufou.android.su.preferences.Preferences;
import com.noshufou.android.su.util.Util;
import com.noshufou.android.su.util.Util.MenuId;
import com.noshufou.android.su.widget.ChangeLog;
import com.noshufou.android.su.widget.PagerHeader;

public class HomeActivity extends SherlockFragmentActivity implements DialogInterface.OnClickListener {
    private static final String TAG = "Su.HomeActivity";

    private static final String STATE_SHOW_DETAILS = "show_details";

    public boolean mDualPane = false;
    private boolean mLoggingEnabled = true;
    private boolean mElite = false;
    
    private MenuItem mTempUnrootItem = null;
    private MenuItem mOtaSurviveItem = null;

    private ViewPager mPager;
    
    private static final String CM_VERSION = SystemProperties.get("ro.cm.version", "");
    private static final String ROOT_ACCESS_PROPERTY = "persist.sys.root_access";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate()");

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_home);
        setSupportProgressBarIndeterminateVisibility(false);
        Log.d(TAG, "after setContentView()");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mLoggingEnabled = prefs.getBoolean(Preferences.LOGGING, true);

        if (findViewById(R.id.fragment_container) != null) {
            mDualPane = true;
            ((AppListFragment)getSupportFragmentManager().findFragmentById(R.id.app_list))
                    .getListView().setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            if (savedInstanceState == null) {
                if (mLoggingEnabled) {
                    showLog();
                } else {
                    showInfo();
                }
            }
        } else {
            mPager = (ViewPager)findViewById(R.id.pager);
            mPager.setPageMargin(getResources().getDimensionPixelSize(R.dimen.page_margin));
//            mPager.setPageMarginDrawable(new ColorDrawable(0xff5e5e5e));
            PagerHeader pagerHeader = (PagerHeader) findViewById(R.id.pager_header);
            PagerAdapter pagerAdapter = new PagerAdapter(this, mPager, pagerHeader);

            pagerAdapter.addPage(InfoFragment.class, R.string.page_label_info);
            pagerAdapter.addPage(AppListFragment.class, R.string.page_label_apps);
            if (mLoggingEnabled) {
                pagerAdapter.addPage(LogFragment.class, R.string.page_label_log);
            }
            mPager.setCurrentItem(1);
        }

        new EliteCheck().execute();

        ChangeLog cl = new ChangeLog(this);
        if (cl.firstRun()) {
            cl.getLogDialog().show();
            Util.writeDefaultStoreFile(this);
        }
        
        // Check for root enabled on CyanogenMod 9
        if (CM_VERSION.length() > 0) {
            String root = SystemProperties.get(ROOT_ACCESS_PROPERTY, "1");
            // 0: off, 1: apps, 2:adb, 3:both
            if ("0".equals(root) || "2".equals(root)) {
                new AlertDialog.Builder(this).setMessage(R.string.root_disabled_summary)
                        .setTitle(R.string.root_disabled_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, this)
                        .setNegativeButton(android.R.string.no, this)
                        .show();
            }
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Intent settings = new Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS");
            settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(settings);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mElite = Util.elitePresent(this, false, 0);
        menu.add(Menu.NONE, MenuId.ELITE,
                MenuId.ELITE, mElite?R.string.menu_extras:R.string.menu_get_elite)
                .setIcon(R.drawable.ic_action_extras)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        menu.add(Menu.NONE, MenuId.PREFERENCES,
                MenuId.PREFERENCES, R.string.menu_preferences)
                .setIcon(R.drawable.ic_action_settings)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MenuId.ELITE:
            Intent eliteIntent = new Intent();
            if (mElite) {
                eliteIntent.setComponent(new ComponentName("com.noshufou.android.su.elite",
                        "com.noshufou.android.su.elite.FeaturedAppsActivity"));
            } else {
                eliteIntent = new Intent(Intent.ACTION_VIEW);
                eliteIntent.setData(Uri.parse("market://details?id=com.noshufou.android.su.elite"));
            }
            startActivity(eliteIntent);
            break;
        case MenuId.PREFERENCES:
            Util.launchPreferences(this);
            break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showDetails(long id) {
        if (mDualPane) {
            Fragment fragment = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);
            if (fragment instanceof AppDetailsFragment) {
                ((AppDetailsFragment)fragment).setShownIndex(id);
            } else {
                Bundle bundle = new Bundle();
                bundle.putLong("index", id);
                Fragment detailsFragment = 
                        Fragment.instantiate(this, AppDetailsFragment.class.getName(), bundle);
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                transaction.replace(R.id.fragment_container, detailsFragment);
                transaction.addToBackStack(STATE_SHOW_DETAILS);
                transaction.commit();
            }
        } else {
            Intent intent = new Intent(this, AppDetailsActivity.class);
            intent.putExtra("index", id);
            startActivity(intent);
        }
    }

    public void closeDetails() {
        if (mDualPane) {
            getSupportFragmentManager()
                    .popBackStack(STATE_SHOW_DETAILS, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
    }

    public void showLog() {
        if (mDualPane) {
            Fragment logFragment = Fragment.instantiate(this, LogFragment.class.getName());
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            transaction.replace(R.id.fragment_container, logFragment);
            transaction.commit();
        }
    }
    
    public void showInfo() {
        if (mDualPane) {
            Fragment infoFragment = Fragment.instantiate(this, InfoFragment.class.getName());
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            transaction.replace(R.id.fragment_container, infoFragment);
            transaction.commit();
        }
    }

    public boolean isDualPane() {
        return mDualPane;
    }

    public static class PagerAdapter extends FragmentPagerAdapter
            implements ViewPager.OnPageChangeListener, PagerHeader.OnHeaderClickListener {

        private final Context mContext;
        private final ViewPager mPager;
        private final PagerHeader mHeader;
        private final ArrayList<PageInfo> mPages = new ArrayList<PageInfo>();

        static final class PageInfo {
            private final Class<?> clss;
            private final Bundle args;

            PageInfo(Class<?> _clss, Bundle _args) {
                clss = _clss;
                args = _args;
            }
        }

        public PagerAdapter(FragmentActivity activity, ViewPager pager,
                PagerHeader header) {
            super(activity.getSupportFragmentManager());
            mContext = activity;
            mPager = pager;
            mHeader = header;
            mHeader.setOnHeaderClickListener(this);
            mPager.setAdapter(this);
            mPager.setOnPageChangeListener(this);
        }

        public void addPage(Class<?> clss, int res) {
            addPage(clss, null, res);
        }

        public void addPage(Class<?> clss, String title) {
            addPage(clss, null, title);
        }

        public void addPage(Class<?> clss, Bundle args, int res) {
            addPage(clss, null, mContext.getResources().getString(res));
        }

        public void addPage(Class<?> clss, Bundle args, String title) {
            PageInfo info = new PageInfo(clss, args);
            mPages.add(info);
            mHeader.add(0, title);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mPages.size();
        }

        @Override
        public Fragment getItem(int position) {
            PageInfo info = mPages.get(position);
            return Fragment.instantiate(mContext, info.clss.getName(), info.args);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            mHeader.setPosition(position, positionOffset, positionOffsetPixels);
        }

        @Override
        public void onPageSelected(int position) {
            mHeader.setDisplayedPage(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onHeaderClicked(int position) {

        }

        @Override
        public void onHeaderSelected(int position) {
            mPager.setCurrentItem(position);
        }

    }

    private class EliteCheck extends AsyncTask<Void, Void, Drawable> {

        @Override
        protected Drawable doInBackground(Void... params) {
            if (Util.elitePresent(HomeActivity.this, false, 0)) {
                return new TransitionDrawable(
                        new Drawable[] { getResources().getDrawable(R.drawable.ic_logo),
                                getResources().getDrawable(R.drawable.ic_logo_elite) });
            } else {
                return getResources().getDrawable(R.drawable.ic_logo);
            }
        }

        @Override
        protected void onPostExecute(Drawable result) {
            getSupportActionBar().setLogo(result);
            if (result instanceof TransitionDrawable) {
                ((TransitionDrawable)result).startTransition(1000);
            }
        }
    }

}
