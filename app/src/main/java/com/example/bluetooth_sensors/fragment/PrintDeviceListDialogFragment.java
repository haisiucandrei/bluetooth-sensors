package com.example.bluetooth_sensors.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.bluetooth_sensors.R;

public class PrintDeviceListDialogFragment extends DialogFragment {

    /**
     * Interface for implementing callbacks inside the host of this dialog
     */
    public interface PrintDeviceListDialogListener {
        CharSequence[] getDeviceList();
        void deleteDevice(String addressToDelete);
    }

    /**
     * the listener will be the host of the dialog
     */
    PrintDeviceListDialogListener listener;

    /**
     * Called when the dialog fragment is associated with the activity
     * I override this so I can set the host of the dialog using the listener field
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            listener = (PrintDeviceListDialogListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException("The main activity must implement NoticeDialogListener");
        }
    }

    /**
     * Called on the creation of the dialog
     * @param savedInstanceState
     * @return
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        CharSequence[] deviceList = listener.getDeviceList();
        builder.setTitle(R.string.title_device_list)
                .setItems(deviceList,
                        (dialogInterface, i) -> listener.deleteDevice(deviceList[i].toString()));

        return builder.create();
    }
}
