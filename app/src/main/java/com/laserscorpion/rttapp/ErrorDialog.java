package com.laserscorpion.rttapp;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;

public abstract class ErrorDialog extends DialogFragment {
    protected static final String KEY = "error dialog arg";
    protected String message;
    protected DialogListener listener;

    public static ErrorDialog newInstance(String message, ErrorDialog subClassInstance) {
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

    public interface DialogListener {
        void dialogDismissed();
    }
}
