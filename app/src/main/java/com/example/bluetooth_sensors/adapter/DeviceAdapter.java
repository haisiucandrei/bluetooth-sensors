package com.example.bluetooth_sensors.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bluetooth_sensors.R;
import com.example.bluetooth_sensors.model.Device;

import java.util.ArrayList;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    /**
     * The underlying array structure for this adapter
     * The adapter will basically take the objects inside the array and populate the
     * UI recycler view using the data inside, as described in this class
     */
    private final ArrayList<Device> deviceList;

    public DeviceAdapter(@NonNull Context context, int resource, @NonNull ArrayList<Device> deviceList) {
        this.deviceList = deviceList;
    }

    /**
     * The ViewHolder is responsible for "taking care of" each item in the list
     * Basically it's a wrapper object for a list item layout??
     * Will be used by the adapter to generate each list item (the extent of my knowledge 21.03.22)
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textDeviceName;
        private final TextView textDeviceAddress;
        private final TextView textDeviceStatus;
        private final TextView textDeviceReceivedTemperature;
        private final TextView textDeviceReceivedPressure;
        private final TextView textDeviceReceivedCH4;
        private final TextView textDeviceReceivedCO;
        private final TextView textDeviceReceivedHumidity;

        public ViewHolder(View view) {
            super(view);

            textDeviceName = view.findViewById(R.id.text_name);
            textDeviceAddress = view.findViewById(R.id.text_addr);
            textDeviceStatus = view.findViewById(R.id.text_status);
            textDeviceReceivedTemperature = view.findViewById(R.id.text_temp);
            textDeviceReceivedPressure = view.findViewById(R.id.text_pressure);
            textDeviceReceivedCH4 = view.findViewById(R.id.text_ch4);
            textDeviceReceivedCO = view.findViewById(R.id.text_co);
            textDeviceReceivedHumidity = view.findViewById(R.id.text_humidity);
        }

        public TextView getTextDeviceName() {
            return textDeviceName;
        }

        public TextView getTextDeviceAddress() {
            return textDeviceAddress;
        }

        public TextView getTextDeviceStatus() {
            return textDeviceStatus;
        }

        public TextView getTextDeviceReceivedTemperature() {
            return textDeviceReceivedTemperature;
        }

        public TextView getTextDeviceReceivedPressure() {
            return textDeviceReceivedPressure;
        }

        public TextView getTextDeviceReceivedCH4() {
            return textDeviceReceivedCH4;
        }

        public TextView getTextDeviceReceivedCO() {
            return textDeviceReceivedCO;
        }

        public TextView getTextDeviceReceivedHumidity() {
            return textDeviceReceivedHumidity;
        }
    }

    /**
     * Create new views (invoked by the layout manager)
     * Inside this method, you specify which layout the items must use
     * @param viewGroup
     * @param viewType
     * @return
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.listitem_device, viewGroup, false);

        return new ViewHolder(view);
    }

    /**
     * Replace the contents of a view (invoked by the layout manager)
     * Basically you take the view (an item in the list) and specify its data
     * This data can then be updated by:
     *  - modifying the underlying corresponding object in the array
     *  - calling the appropiate notify function from the UI thread
     * @param viewHolder
     * @param position
     */
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {

        // Get element from your dataset at this position and replace the
        // default contents of the view with that element
        viewHolder.getTextDeviceName().setText(deviceList.get(position).getName());
        viewHolder.getTextDeviceAddress().setText(deviceList.get(position).getAddress());
        viewHolder.getTextDeviceStatus().setText(deviceList.get(position).getStatus());
        String string_temp = String.valueOf(
                deviceList.get(position).getTemperature()
        ) + "°C";
        viewHolder.getTextDeviceReceivedTemperature().setText(string_temp);
        String string_pres = String.valueOf(
                deviceList.get(position).getPressure()
        ) + "hPa";
        viewHolder.getTextDeviceReceivedPressure().setText(string_pres);
        String string_ch4 = deviceList.get(position).getCh4() + "ppm";
        viewHolder.getTextDeviceReceivedCH4().setText(string_ch4);
        String string_co = deviceList.get(position).getCo() + "ppm";
        viewHolder.getTextDeviceReceivedCO().setText(string_co);
        String string_humidity = deviceList.get(position).getHumidity() + "%";
        viewHolder.getTextDeviceReceivedHumidity().setText(string_humidity);

    }

    /**
     * Return the size of your dataset (invoked by the layout manager)
     * @return
     */
    @Override
    public int getItemCount() {
        return deviceList.size();
    }

}
