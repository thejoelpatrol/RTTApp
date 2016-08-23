package com.laserscorpion.rttapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;


public class CallEndDialog extends AbstractDialog {
    SaveDialogListener saver;

    public static CallEndDialog newInstance(String message) {
        CallEndDialog dialog = new CallEndDialog();
        return (CallEndDialog) newInstance(message, dialog);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        saver = (SaveDialogListener)activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Call ended");
        builder.setMessage(message);
        builder.setNeutralButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                saver.dialogDismissed();
            }
        });
        builder.setPositiveButton("Save Text", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                saver.dialogSaveText();
            }
        });
        return builder.create();
    }

    public interface SaveDialogListener extends DialogListener {
        void dialogSaveText();
    }
}

