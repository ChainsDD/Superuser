package com.noshufou.android.su;

import com.noshufou.android.su.preferences.Preferences;
import com.noshufou.android.su.provider.PermissionsProvider.Logs;
import com.noshufou.android.su.util.Util;
import com.noshufou.android.su.widget.ChangeLog;
import com.noshufou.android.su.widget.PagerHeader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuCompat;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageView;

import java.util.ArrayList;

public class HomeActivity extends FragmentActivity {
//    private static final String TAG = "Su.HomeActivity";

    private static final int MENU_ELITE = 0;
    private static final int MENU_GET_ELITE = 1;
    private static final int MENU_CLEAR_LOG = 2;
    private static final int MENU_PREFERENCES = 3;

    private static final String STATE_SHOW_DETAILS = "show_details";

    public boolean mDualPane = false;
    private boolean mLoggingEnabled = true;

    private ViewPager mPager;
    private TransitionDrawable mTitleLogo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

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
                    Fragment detailsFragment =
                            Fragment.instantiate(this, AppDetailsFragment.class.getName());
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                    transaction.replace(R.id.fragment_container, detailsFragment);
                    transaction.commit();
                }
            }
        } else {
            mPager = (ViewPager)findViewById(R.id.pager);
            mPager.setPageMargin(getResources().getDimensionPixelSize(R.dimen.page_margin));
//            mPager.setPageMarginDrawable(new ColorDrawable(0xff5e5e5e));
            PagerHeader pagerHeader = (PagerHeader) findViewById(R.id.pager_header);
            PagerAdapter pagerAdapter = new PagerAdapter(this, mPager, pagerHeader);

            pagerAdapter.addPage(AppListFragment.class, R.string.page_label_apps);
            if (mLoggingEnabled) {
                pagerAdapter.addPage(LogFragment.class, R.string.page_label_log);
            } else {
                pagerHeader.setVisibility(View.GONE);

            }

            // DEBUG
//            pagerAdapter.addPage(AppListFragment.class, null, "APPS");
//            pagerAdapter.addPage(LogFragment.class, null, "LOGS");
//            pagerAdapter.addPage(AppListFragment.class, null, "more apps");
//            pagerAdapter.addPage(LogFragment.class, null, "more logs");
            // END DEBUG
        }

        mTitleLogo = 
                (TransitionDrawable) ((ImageView)findViewById(android.R.id.home)).getDrawable();
        new EliteCheck().execute();

        ChangeLog cl = new ChangeLog(this);
        if (cl.firstRun()) {
            cl.getLogDialog().show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        if (Util.elitePresent(this, false, 0)) {
            item = menu.add(Menu.NONE, MENU_ELITE,
                    MENU_ELITE, R.string.menu_extras);
            item.setIcon(R.drawable.ic_menu_star);
            MenuCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        } else {
            item = menu.add(Menu.NONE, MENU_GET_ELITE,
                    MENU_GET_ELITE, R.string.pref_get_elite_title);
            item.setIcon(R.drawable.ic_menu_star);
            MenuCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        if (mLoggingEnabled) {
            item = menu.add(Menu.NONE, MENU_CLEAR_LOG,
                    MENU_CLEAR_LOG, R.string.menu_clear_log);
            item.setIcon(R.drawable.ic_menu_clear_log);
            MenuCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        item = menu.add(Menu.NONE, MENU_PREFERENCES,
                MENU_PREFERENCES, R.string.menu_preferences);
        item.setIcon(R.drawable.ic_menu_preferences);
        MenuCompat.setShowAsAction(item, MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ELITE:
            Intent eliteIntent = new Intent();
            eliteIntent.setComponent(new ComponentName("com.noshufou.android.su.elite",
                    "com.noshufou.android.su.elite.FeaturedAppsActivity"));
            startActivity(eliteIntent);
            break;
        case MENU_GET_ELITE:
            Intent mktIntent = new Intent(Intent.ACTION_VIEW);
            mktIntent.setData(Uri.parse("market://details?id=com.noshufou.android.su.elite"));
            startActivity(mktIntent);
            break;
        case MENU_CLEAR_LOG:
            getContentResolver().delete(Logs.CONTENT_URI, null, null);
            break;
        case MENU_PREFERENCES:
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

    private class EliteCheck extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            return Util.elitePresent(HomeActivity.this, false, 0);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                mTitleLogo.startTransition(1000);
            }
        }
    }
}
