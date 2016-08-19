package com.laserscorpion.rttapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

public class DismissableDialog extends ErrorDialog {

    public static DismissableDialog newInstance(String message) {
        DismissableDialog dialog = new DismissableDialog();
        return (DismissableDialog)ErrorDialog.newInstance(message, dialog);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Oops");
        builder.setMessage(message);
        builder.setNeutralButton("Dang", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                listener.dialogDismissed();
            }
        });
        return builder.create();
    }

}
