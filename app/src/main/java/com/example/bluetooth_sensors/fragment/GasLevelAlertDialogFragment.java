package com.example.bluetooth_sensors.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class GasLevelAlertDialogFragment extends DialogFragment {

    public static boolean armed = true;

    public interface GasLevelAlertDialogListener {

    }

    GasLevelAlertDialogListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        try {
            listener = (GasLevelAlertDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("The data transfer activity must implement GasLevelAlertDialogListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Gas concentration too high!");
        builder.setMessage("Leave the room quickly!");
        builder.setPositiveButton("Dismiss", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                GasLevelAlertDialogFragment.armed = false;
            }
        });

        return builder.create();
    }
}
