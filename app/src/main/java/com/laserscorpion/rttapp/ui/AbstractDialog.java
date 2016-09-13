/* © 2016 Joel Cretan
 *
 * This is part of RTTAPP, an Android RFC 4103 real-time text app
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.laserscorpion.rttapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

/**
 * A base class for all types of popup dialogs, with some common functionality.
 */
public abstract class AbstractDialog extends DialogFragment {
    protected static final String KEY = "error dialog arg";
    protected String message;
    protected DialogListener listener;

    public static AbstractDialog newInstance(String message, AbstractDialog subClassInstance) {
        Bundle args = new Bundle();
        args.putString(KEY, message);
        subClassInstance.setArguments(args);
        return subClassInstance;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (DialogListener)activity;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        message = args.getString(KEY);
    }

    protected Dialog createDialog(AlertDialog.Builder builder) {
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        return dialog;
    }

    public interface DialogListener {
        void dialogDismissed();
    }
}
