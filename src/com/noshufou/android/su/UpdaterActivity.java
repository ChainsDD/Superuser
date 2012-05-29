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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Window;
import com.noshufou.android.su.service.UpdaterService;
import com.noshufou.android.su.service.UpdaterService.Step;
import com.noshufou.android.su.service.UpdaterService.UpdaterBinder;
import com.noshufou.android.su.widget.ConsoleAdapter;

public class UpdaterActivity extends SherlockListActivity
        implements UpdaterService.UpdaterListener, View.OnClickListener {
    private static final String TAG = "Su.UpdaterActivity";

    private int mLastStep = 0;
    private UpdaterService mService;
    private boolean mBound = false;
    private Button mButton;
    
    private ServiceConnection mConnection = new ServiceConnection() {
        
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            UpdaterBinder binder = (UpdaterBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.registerUpdaterListener(UpdaterActivity.this);
            if (!mService.isRunning())
                mService.getManifest();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.updater);
        
        mButton = (Button) findViewById(R.id.action_button);
        mButton.setOnClickListener(this);
        
        setListAdapter(new ConsoleAdapter(this));
        
        if (!mBound) {
            Intent intent = new Intent(this, UpdaterService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
        
        getSupportActionBar().setTitle(R.string.updater_title);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBound) {
            mService.registerComponentCallbacks(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBound) {
            mService.unregisterUpdaterListener();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBound) {
            unbindService(mConnection);
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
                    (step.state == Step.STATE_SUCCESSFUL) ? ConsoleAdapter.CONSOLE_GREEN : ConsoleAdapter.CONSOLE_RED);
        }
    }

    @Override
    public void onFinishTask(int task) {
        switch(task) {
            case UpdaterService.TASK_DOWNLOAD_MANIFEST:
                mButton.setText(R.string.updater_update);
                mButton.setEnabled(true);
                break;
            case UpdaterService.TASK_UPDATE:
                mButton.setText(R.string.updater_cool);
                mButton.setEnabled(true);
                break;
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
