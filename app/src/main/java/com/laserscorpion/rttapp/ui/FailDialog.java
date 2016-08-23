package com.laserscorpion.rttapp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

public class FailDialog extends AbstractDialog {
    QuitDialogListener quitter;

    public static FailDialog newInstance(String message) {
        FailDialog dialog = new FailDialog();
        return (FailDialog) newInstance(message, dialog);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        quitter = (QuitDialogListener)activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Oops, sorry");
        builder.setMessage(message);
        builder.setNegativeButton("Back", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                quitter.dialogFail();
            }
        });
        return builder.create();
    }

    public interface QuitDialogListener {
        void dialogFail();
    }

}
