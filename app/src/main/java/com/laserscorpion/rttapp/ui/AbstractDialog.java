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
