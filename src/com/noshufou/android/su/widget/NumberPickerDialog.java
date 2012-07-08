/*
 * Copyright (c) 2011 Adam Shanks (@ChainsDD)
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.noshufou.android.su.widget;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.noshufou.android.su.R;

/**
 * A dialog that prompts the user for the message deletion limits.
 */
@TargetApi(11)
public class NumberPickerDialog extends AlertDialog implements OnClickListener {

    private static final String NUMBER = "number";

    /**
     * The callback interface used to indicate the user is done filling in
     * the time (they clicked on the 'Set' button).
     */
    public interface OnNumberSetListener {

        /**
         * @param number The number that was set.
         */
        void onNumberSet(int dialogId, int number);
    }

    private final NumberPicker mNumberPicker;
    private final OnNumberSetListener mCallback;
    private final int mDialogId;

    /**
     * @param context Parent.
     * @param callBack How parent is notified.
     * @param number The initial number.
     */
    public NumberPickerDialog(Context context,
            OnNumberSetListener callBack,
            int number,
            int rangeMin,
            int rangeMax,
            int title,
            int units,
            int dialogId) {
        this(context, R.style.Theme_Dialog_Alert,
                callBack, number, rangeMin, rangeMax, title, units, dialogId);
    }

    /**
     * @param context Parent.
     * @param theme the theme to apply to this dialog
     * @param callBack How parent is notified.
     * @param number The initial number.
     */
    public NumberPickerDialog(Context context,
            int theme,
            OnNumberSetListener callBack,
            int number,
            int rangeMin,
            int rangeMax,
            int title,
            int units,
            int dialogId) {
        super(context, theme);
        mCallback = callBack;
        mDialogId = dialogId;

        setTitle(title);

        setButton(DialogInterface.BUTTON_POSITIVE, context.getText(R.string.set), this);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.number_picker_dialog, null);
        setView(view);
        mNumberPicker = (NumberPicker) view.findViewById(R.id.number_picker);

        if (units != 0) {
            TextView unit = (TextView) view.findViewById(R.id.unit);
            unit.setText(units);
            unit.setVisibility(View.VISIBLE);
        }

        // initialize state
        mNumberPicker.setMinValue(rangeMin);
        mNumberPicker.setMaxValue(rangeMax);
        mNumberPicker.setValue(number);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallback != null) {
            mNumberPicker.clearFocus();
            mCallback.onNumberSet(mDialogId, mNumberPicker.getValue());
            dialog.dismiss();
        }
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(NUMBER, mNumberPicker.getValue());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int number = savedInstanceState.getInt(NUMBER);
        mNumberPicker.setValue(number);
    }
}