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
import android.content.DialogInterface;
import android.os.Bundle;

public class FailDialog extends AbstractDialog {
    FailDialogListener listener;

    public static FailDialog newInstance(String message) {
        FailDialog dialog = new FailDialog();
        return (FailDialog) newInstance(message, dialog);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (FailDialogListener)activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Oops, sorry");
        builder.setMessage(message);
        builder.setNegativeButton("Back", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                listener.dialogFail();
            }
        });
        return createDialog(builder);
    }

    public interface FailDialogListener {
        void dialogFail();
    }

}
