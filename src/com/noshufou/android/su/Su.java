package com.noshufou.android.su; 

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TabHost;
import android.widget.Toast;

public class Su extends TabActivity {
    private static final String TAG = "Su";
    
    private static final int PACKAGE_UNINSTALL = 1;
    private static final int SEND_REPORT = 2;
    
    private Context mContext;
    private String mMaliciousAppPackage = "";
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mContext = this;
		
		setContentView(R.layout.main);
		
		Resources res = getResources();
		TabHost tabHost = getTabHost();
		TabHost.TabSpec spec;
		Intent intent;
		
		intent = new Intent().setClass(this, AppListActivity.class);
		spec = tabHost.newTabSpec("apps").setIndicator(getString(R.string.tab_apps),
				res.getDrawable(R.drawable.ic_tab_permissions))
				.setContent(intent);
		tabHost.addTab(spec);
		
		intent = new Intent().setClass(this, LogActivity.class);
		spec = tabHost.newTabSpec("log").setIndicator(getString(R.string.tab_log),
				res.getDrawable(R.drawable.ic_tab_log))
				.setContent(intent);
		tabHost.addTab(spec);
		
		intent = new Intent().setClass(this, SuPreferences.class);
		spec = tabHost.newTabSpec("settings").setIndicator(getString(R.string.tab_settings),
				res.getDrawable(R.drawable.ic_tab_settings))
				.setContent(intent);
		tabHost.addTab(spec);
		
		tabHost.setCurrentTab(0);
		
		firstRun();
		
		new CheckForMaliciousApps().execute();
	}
	
	private void firstRun() {
		int versionCode = 0;
		try {
			versionCode = getPackageManager()
					.getPackageInfo("com.noshufou.android.su", PackageManager.GET_META_DATA)
					.versionCode;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Package not found... Odd, since we're in that package...", e);
		}

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		int lastFirstRun = prefs.getInt("last_run", 0);
		
		if (lastFirstRun >= versionCode) {
			Log.d(TAG, "Not first run");
			return;
		}
		Log.d(TAG, "First run for version " + versionCode);
		
		String suVer = getSuVersion();
		Log.d(TAG, "su version: " + suVer);
		new Updater(this, suVer).doUpdate();
		
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt("last_run", versionCode);
		editor.commit();
	}
	
	private void maliciousAppFound(final String packageName) {
	    new AlertDialog.Builder(mContext).setTitle(R.string.warning)
	            .setMessage(getString(R.string.malicious_app_found, packageName))
	            .setPositiveButton(R.string.uninstall, new DialogInterface.OnClickListener() {
                    
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri packageUri = Uri.parse("package:" + packageName);
                        Intent intent = new Intent(Intent.ACTION_DELETE, packageUri);
                        startActivityForResult(intent, PACKAGE_UNINSTALL);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        new CheckForMaliciousApps().execute();
                    }
                }).show();
	}

	@Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
	    switch (requestCode) {
	    case PACKAGE_UNINSTALL:
	        // We should check to see if the resultCode == 1, but it's always 0
	        // perhaps it's a mistake in PackageInstaller.apk
	        new AlertDialog.Builder(mContext).setTitle(R.string.uninstall_successful)
	        .setMessage(R.string.report_msg)
	        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

	            @Override
	            public void onClick(DialogInterface dialog, int which) {
	                Intent email = new Intent(Intent.ACTION_SEND);
	                email.setType("plain/text");
	                email.putExtra(Intent.EXTRA_EMAIL,
	                        new String[] {"superuser.android@gmail.com"});
	                email.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject));
	                email.putExtra(Intent.EXTRA_TEXT,
	                        getString(R.string.report_body, mMaliciousAppPackage));
	                startActivityForResult(email, SEND_REPORT);
	            }
	        })
	        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    new CheckForMaliciousApps().execute();
                }
            }).show();
	        break;
	    case SEND_REPORT:
	        new CheckForMaliciousApps().execute();
	        break;
	    }
    }

    public static String getSuVersion() {
        Process process = null;

        try {
            process = Runtime.getRuntime().exec("su -v");
            BufferedReader is = new BufferedReader(new InputStreamReader(
                    new DataInputStream(process.getInputStream())), 64);

            int i = 0;
            while (i < 150 && !is.ready()) {
                    Thread.sleep(5);
                i++;
            }

            if (is.ready()) {
                return is.readLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "Problems reading current version.", e);
            return null;
        } catch (InterruptedException e) {
            Log.w(TAG, "Sleep timer got interrupted...");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return null;
    }
	
    public static int getSuVersionCode() {
        Process process = null;

        try {
            process = Runtime.getRuntime().exec("su -V");
            BufferedReader is = new BufferedReader(new InputStreamReader(
                    new DataInputStream(process.getInputStream())), 64);

            int i = 0;
            while (i < 150 && !is.ready()) {
                    Thread.sleep(5);
                i++;
            }

            if (is.ready()) {
                return Integer.parseInt(is.readLine());
            }
        } catch (IOException e) {
            Log.e(TAG, "Problems reading current version.", e);
            return 0;
        } catch (InterruptedException e) {
            Log.w(TAG, "Sleep timer got interrupted...");
        } catch (NumberFormatException e) {
            return 0;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return 0;
    }

    public class CheckForMaliciousApps extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... params) {
            PackageManager pm = mContext.getPackageManager();
            try {
                // Check for packages implicitly granted respond permissions
                // No app shall be allowed to share a UID with Superuser
                String[] pkgsWithSuUID = pm.getPackagesForUid(
                        pm.getApplicationInfo(mContext.getPackageName(), 0).uid);
                for (String pkg : pkgsWithSuUID) {
                    if (!pkg.equals(getPackageName()) && !pkg.equals(mMaliciousAppPackage)) {
                        mMaliciousAppPackage = pkg;
                        return pkg;
                    }
                }

                // Check for packages explicitly granted respond permissions
                List<PackageInfo> pkgs = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
                for (PackageInfo pkg : pkgs) {
                    if (pkg.requestedPermissions != null) {
                        for (String s : pkg.requestedPermissions) {
                            if (!pkg.applicationInfo.packageName.equals(getPackageName()) &&
                                    s.equals("com.noshufou.android.su.RESPOND") &&
                                    pm.checkPermission("com.noshufou.android.su.RESPOND",
                                            pkg.applicationInfo.packageName) ==
                                                PackageManager.PERMISSION_GRANTED) {
                                mMaliciousAppPackage = pkg.applicationInfo.packageName;
                                return pkg.applicationInfo.packageName;
                            }
                        }
                    }
                }
            } catch (NameNotFoundException e) {
                // This won't happen
                Log.e(TAG, "You divided by zero...", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                maliciousAppFound(result);
            }
        }
	}
}
