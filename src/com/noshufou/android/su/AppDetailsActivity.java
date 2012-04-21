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

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.View;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.noshufou.android.su.util.Util;

public class AppDetailsActivity extends SherlockFragmentActivity {
//    private static final String TAG = "Su.AppDetailsActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE
                && (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK)
                        == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            finish();
            return;
        }
        
        setContentView(R.layout.activity_app_details);
        if (savedInstanceState == null) {
            Fragment fragment = Fragment.instantiate(this, AppDetailsFragment.class.getName(),
                    getIntent().getExtras());
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment).commit();
        }
    }

    public void goHome(View view) {
        Util.goHome(this);
    }
}
