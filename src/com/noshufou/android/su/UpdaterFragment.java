package com.noshufou.android.su;

import android.app.ListFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.noshufou.android.su.service.UpdaterService;
import com.noshufou.android.su.service.UpdaterService.Step;
import com.noshufou.android.su.service.UpdaterService.UpdaterBinder;
import com.noshufou.android.su.widget.ConsoleAdapter;

public class UpdaterFragment extends ListFragment 
        implements UpdaterService.UpdaterListener, View.OnClickListener {
    private static final String TAG = "UpdaterFragment";

    private int mLastStep = 0;
    UpdaterService mService;
    private boolean mBound = false;
    private Button mButton;

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            UpdaterBinder binder = (UpdaterBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.registerUpdaterListener(UpdaterFragment.this);
            if (!mService.isRunning())
                mService.getManifest();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.updater, container, false);
        mButton = (Button) view.findViewById(R.id.action_button);
        mButton.setOnClickListener(this);

        setListAdapter(new ConsoleAdapter(getActivity()));

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (!mBound) {
            Intent intent = new Intent(getActivity(), UpdaterService.class);
            getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
        
        getActivity().getActionBar().setTitle(R.string.updater_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBound) {
            mService.registerUpdaterListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mBound) {
            mService.unregisterUpdaterListener();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mBound) {
            getActivity().unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    public void onStepsChanged(Step step) {
        if (step.descRes != mLastStep) {
            getListAdapter().addEntry(step.descRes);
            mLastStep = step.descRes;
        } else if (step.state != Step.STATE_IN_PROGRESS) {
            getListAdapter().addStatusToLastEntry(step.result,
                    (step.state == Step.STATE_SUCCESSFUL) ? ConsoleAdapter.CONSOLE_GREEN : ConsoleAdapter.CONSOLE_RED );
        }
    }

    @Override
    public void onFinishTask(int task) {
        switch (task) {
            case UpdaterService.TASK_DOWNLOAD_MANIFEST:
                mButton.setText(R.string.updater_update);
                mButton.setEnabled(true);
                break;
            case UpdaterService.TASK_UPDATE:
                mButton.setText(R.string.updater_cool);
                mButton.setEnabled(true);
                break;
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "UpdaterTask cancelled");
        }

        private boolean downloadFile(String urlStr, String localName) {
            BufferedInputStream bis = null;
            
            try {
                URL url = new URL(urlStr);
                
                URLConnection urlCon = url.openConnection();
                bis = new BufferedInputStream(urlCon.getInputStream());

                ByteArrayBuffer baf = new ByteArrayBuffer(50);
                int current = 0;
                while (((current = bis.read()) != -1)) {
                    baf.append((byte) current);
                    if (isCancelled()) {
                        return false;
                    }
                }
                bis.close();
                
                if (isCancelled()) {
                    return false;
                } else if (localName.equals("manifest")) {
                    try {
                        mManifest = new Manifest();
                        return mManifest.populate(new JSONArray(new String(baf.toByteArray())));
                    } catch (JSONException e) {
                        Log.e(TAG, "Malformed manifest file", e);
                        return false;
                    }
                } else {
                    FileOutputStream outFileStream = getActivity().openFileOutput(localName, 0);
                    outFileStream.write(baf.toByteArray());
                    outFileStream.close();
                    if (localName.equals("busybox")) {
                        mBusyboxPath = getActivity().getFilesDir().getAbsolutePath().concat("/busybox");
                    }
                }

            } catch (MalformedURLException e) {
                Log.e(TAG, "Bad URL: " + urlStr, e);
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Problem downloading file: " + localName, e);
                return false;
            }
            return true;
        }
    }

    @Override
    public void onClick(View v) {
        if (!mBound)
            return;

        mService.update();
        mButton.setText(R.string.updater_working);
        mButton.setEnabled(false);
    }

    @Override
    public ConsoleAdapter getListAdapter() {
        return (ConsoleAdapter) super.getListAdapter();
    }
    
}
