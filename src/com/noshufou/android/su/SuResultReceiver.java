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

import com.noshufou.android.su.service.ResultService;
import com.noshufou.android.su.util.Util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SuResultReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // Notify the user if their su binary is outdated. Note this doesn't
        // check for the absolute latest binary, just the latest required
        // to work properly
        if (intent.getIntExtra(SuRequestReceiver.EXTRA_VERSION_CODE, 0) < 6) {
            Util.showOutdatedNotification(context);
        }
        
        Intent serviceIntent = new Intent(context, ResultService.class);
        serviceIntent.putExtras(intent);
        serviceIntent.putExtra(ResultService.EXTRA_ACTION, ResultService.ACTION_RESULT);
        context.startService(serviceIntent);
    }

}
