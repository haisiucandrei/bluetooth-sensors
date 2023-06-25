package com.example.bluetooth_sensors.activity;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.util.JsonWriter;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bluetooth_sensors.R;
import com.example.bluetooth_sensors.adapter.DeviceAdapter;
import com.example.bluetooth_sensors.fragment.GasLevelAlertDialogFragment;
import com.example.bluetooth_sensors.model.Device;
import com.example.bluetooth_sensors.model.LogEntry;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataTransferActivity extends AppCompatActivity implements GasLevelAlertDialogFragment.GasLevelAlertDialogListener {

    /**
     * SPP UUID for any HC-05 bluetooth adapter
     */
    public static final String HC05_UUID = "00001101-0000-1000-8000-00805f9b34fb";

    /**
     * used to keep references to all the device threads running
     */
    private final List<BluetoothConnectThread> deviceThreads = new ArrayList<>(10);

    /**
     * list of devices we will TRY to connect to (name is misleading)
     */
    private final ArrayList<Device> connectedDevices = new ArrayList<>(10);
    private DeviceAdapter deviceListAdapter;
    private int currentPosition = 0;

    /**
     * firebase database reference for remote backing up
     */
    private DatabaseReference databaseReference;

    /**
     * flags for local/remote backing up of the data
     */
    private boolean saveLocally;
    private boolean saveRemotely;

    /**
     * Variable used for storing the sound played when the gas concentration is too high
     */
    private Ringtone alertSound;

    private final GasLevelAlertDialogFragment dialog = new GasLevelAlertDialogFragment();

    /**
     * Creates the layout of the activity
     * Basically generates the list of available devices and generates a recycler view for them
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_datatransfer);

        databaseReference = FirebaseDatabase.getInstance(
                this.getString(R.string.database_url)).getReference().child("logs");

        // check whether you have to save your data locally, remotely, or bot
        saveLocally = getIntent().getBooleanExtra(MainActivity.INTENT_EXTRA_SAVE_LOCALLY, false);
        saveRemotely = getIntent().getBooleanExtra(MainActivity.INTENT_EXTRA_SAVE_REMOTELY, false);
        Log.d(getString(R.string.log_tag), "Save locally? " + saveLocally);
        Log.d(getString(R.string.log_tag), "Save remotely? " + saveRemotely);

        // initialize the alert sound
        alertSound = RingtoneManager.getRingtone(getApplicationContext(), RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));

        // get list of devices registered in the app
        CharSequence[] registeredDeviceAddresses = getIntent().getCharSequenceArrayExtra(MainActivity.INTENT_EXTRA_ADDRESSES);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {

            // get set of available devices (registered in app + paired)
            // by finding all the bluetooth devices that have addresses registered in the app
            Set<BluetoothDevice> availableDevices =
                    MainActivity.bluetoothAdapter.getBondedDevices().stream().
                            filter(bondedDevice -> Arrays.stream(registeredDeviceAddresses).
                                    anyMatch(deviceAddress -> deviceAddress.equals(
                                            bondedDevice.getAddress())
                                    )
                            ).collect(Collectors.toSet());

            // create a new thread for each available device
            for (BluetoothDevice device : availableDevices) {
                connectedDevices.add(new Device(device.getAddress(), device.getName()));
                BluetoothConnectThread newThread = new BluetoothConnectThread(device, currentPosition++);
                deviceThreads.add(newThread);
                newThread.start();
            }

            // populate the list - each available device gets an entry in the list
            deviceListAdapter = new DeviceAdapter(getApplicationContext(), R.layout.listitem_device, connectedDevices);
            RecyclerView connectedDevicesList = findViewById(R.id.device_list);
            connectedDevicesList.setLayoutManager(new LinearLayoutManager(this));
            connectedDevicesList.setAdapter(deviceListAdapter);
        } else {
            Log.e("INIT", "Bluetooth permission denied");
        }
    }

    /**
     * Called before this activity exits
     * Will signal each thread to stop and close its corresponding sockets
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (BluetoothConnectThread thread : deviceThreads) {
            thread.signalStop();
            thread.cancel();
            Log.d(getString(R.string.log_tag), "Thread " + thread.getName() + " is alive " + thread.isAlive());
        }

        if (alertSound.isPlaying()) {
            alertSound.stop();
        }
    }

    public void testGasAlert(View view) {
        fireDialog();
    }

    private void fireDialog() {
        if (!dialog.isVisible() && GasLevelAlertDialogFragment.armed) {
            dialog.show(getSupportFragmentManager(), "GasLevelAlertDialogFragment");
            alertSound.play();
        }
    }

    /**
     * Used to connect to each bluetooth device in a separate thread
     */
    private class BluetoothConnectThread extends Thread {

        /**
         * the index in the device list corresponding to the current thread
         */
        private final int positionInList;

        /**
         * bluetooth related fields
         */
        private final BluetoothSocket deviceSocket;
        private final BluetoothDevice device;

        /**
         * flag for stopping the loop inside the thread
         */
        private boolean shouldStop = false;

        private final File deviceLogFile;

        /**
         * reference to the node of the current device in the database
         */
        private final DatabaseReference currentDeviceDatabaseReference;

        private final float CH4_ALERT_LEVEL = 100;
        private final float CO_ALERT_LEVEL = 75;

        public BluetoothConnectThread(BluetoothDevice device, int positionInList) {
            this.positionInList = positionInList;
            this.device = device;
            this.deviceLogFile = new File(getFilesDir(),
                    device.getAddress() + "_" + LogEntry.getMeasurementDate() + "_log.json");
            this.currentDeviceDatabaseReference = databaseReference.child(
                    "node_" + device.getAddress()
            );

            // the bluetooth related fields are final, so we need a temporary reference to work with
            // before setting the deviceSocket field
            BluetoothSocket temp = null;

            // create socket for the required device
            try {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                    temp = this.device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(HC05_UUID));
                } else {
                    Log.e(getString(R.string.log_tag), "Bluetooth permission denied");
                }
            } catch (IOException e) {
                Log.e(getString(R.string.log_tag), "Failed to create socket");
            }

            deviceSocket = temp;
        }

        private void writeCurrentLogEntryAsJson(JsonWriter deviceLogFileStream, LogEntry entry) {
            try {
                deviceLogFileStream.beginObject();

                // first write the measurement time
                deviceLogFileStream.name("time");
                deviceLogFileStream.value(LogEntry.getMeasurementTime());

                for (Field field : LogEntry.class.getDeclaredFields()) {
                    deviceLogFileStream.name(field.getName());
                    deviceLogFileStream.value(field.get(entry).toString());
                }
                deviceLogFileStream.endObject();
            } catch (IOException ioe) {
                Log.e(getString(R.string.log_tag), "Failed to write to logfile");
            } catch (IllegalAccessException iae) {
                Log.e(getString(R.string.log_tag), iae.getMessage());
            }
        }



        /**
         * Main data transfer function
         *
         * @param deviceLogFileStream
         */
        private void getDataFromDevice(@Nullable JsonWriter deviceLogFileStream) {
            float temperature;
            float pressure;
            float ch4;
            float co;
            float humidity;
            byte[] payload = new byte[20];

            try {

                InputStream socketStream = deviceSocket.getInputStream();

                // if we save locally, start the array json structure
                if (saveLocally) {
                    // beginning JSON array (top-level in log file)
                    assert deviceLogFileStream != null;
                    deviceLogFileStream.beginArray();
                }

                // main reading loop
                while (true) {

                    if (shouldStop) {
                        // thread should exit, so we break the loop
                        break;
                    } else if (socketStream.available() >= 20) {
                        // read 8 bytes (4 temperature, 4 pressure)
                        socketStream.read(payload, 0, 20);

                        temperature = (float) ByteBuffer.wrap(payload, 0, 4).getInt() / 10;
                        pressure = (float) ByteBuffer.wrap(payload, 4, 4).getInt() / 100;
                        ch4 = (float) ByteBuffer.wrap(payload, 8, 4).getInt() / 100;
                        co = (float) ByteBuffer.wrap(payload, 12, 4).getInt() / 100;
                        humidity = (float) ByteBuffer.wrap(payload, 16, 4).getInt() / 100;


                        // update the underlying array of the list
                        connectedDevices.get(positionInList).setTemperature(temperature);
                        connectedDevices.get(positionInList).setPressure(pressure);
                        connectedDevices.get(positionInList).setCh4(ch4);
                        connectedDevices.get(positionInList).setCo(co);
                        connectedDevices.get(positionInList).setHumidity(humidity);

                        // signal the recycler view that its data was updated
                        // have to update the view from the UI thread, not from the current thread
                        runOnUiThread(() -> deviceListAdapter.notifyItemChanged(positionInList));

                        // activate alert dialog if levels exceed threshold
                        if (ch4 >= CH4_ALERT_LEVEL || co >= CO_ALERT_LEVEL) {
                            fireDialog();
                        }

                        // if necessary, save your data locally or remotely
                        LogEntry currentLogEntry = new LogEntry(temperature, pressure, ch4, co, humidity);
                        if (saveLocally) {
                            // write the read data to the corresponding device log
                            assert deviceLogFileStream != null;
                            writeCurrentLogEntryAsJson(deviceLogFileStream, currentLogEntry);
                        }

                        if (saveRemotely) {
                            // write the data to the database
                            currentDeviceDatabaseReference.child(LogEntry.getMeasurementDateAndTime()).setValue(currentLogEntry);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(getString(R.string.log_tag), "Failed to read from socket to " + device.getAddress());
            }

            try {
                // in case you had to save locally, close the json array in the file
                if (saveLocally) {
                    // end json array (close all brackets in logfile)
                    assert deviceLogFileStream != null;
                    deviceLogFileStream.endArray();

                    // close log file stream
                    deviceLogFileStream.close();
                }

            } catch (IOException e) {
                Log.e(getString(R.string.log_tag), "Failed to close log file for " + device.getAddress());
            }
        }

        /**
         * Used to set the flag for breaking the loop
         * When the current activity exits, all running threads should stop, so the onDestroy()
         * method of the view should call this method on all device threads
         */
        public void signalStop() {
            this.shouldStop = true;
        }

        /**
         * Runs the thread
         * Will try to connect to the device
         * If the connection is successful, it will call the function which reads the data
         */
        public void run() {
            try {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                    deviceSocket.connect();

                    if (deviceSocket.isConnected()) {
                        Log.d(getString(R.string.log_tag), "Node " + device.getAddress() + " connected successfully");
                        connectedDevices.get(positionInList).setStatus("connected");
                        runOnUiThread(() -> deviceListAdapter.notifyItemChanged(positionInList));

                        // open the log file only if the device successfully connected
                        // and the user requested to save the logs locally
                        if (saveLocally) {
                            JsonWriter deviceLogFileStream = new JsonWriter(new FileWriter(deviceLogFile, false));
                            Log.d(getString(R.string.log_tag), "The path for the logfile is " +
                                    deviceLogFile.getAbsolutePath());

                            // call method to get data from device
                            getDataFromDevice(deviceLogFileStream);
                            // close the log file
                            deviceLogFileStream.close();
                        } else {
                            // call method to get data from device
                            getDataFromDevice(null);
                        }
                    }

                } else {
                    Log.e(getString(R.string.log_tag), "Bluetooth permission denied");
                }
            } catch (IOException connectException) {
                try {
                    deviceSocket.close();
                    Log.e(getString(R.string.log_tag), "Failed to connect to node " + device.getAddress());
                } catch (IOException closeException) {
                    Log.e(getString(R.string.log_tag), "Failed to close socket.");
                }
            }
        }

        /**
         * Closes the bluetooth socket (if necessary) before the thread object is recycled
         */
        public void cancel() {
            try {
                if (deviceSocket.isConnected()) {
                    connectedDevices.get(positionInList).setStatus("disconnected");
                    runOnUiThread(() -> deviceListAdapter.notifyItemChanged(positionInList));
                    deviceSocket.close();
                    Log.d(getString(R.string.log_tag), "Closed socket to " + device.getAddress());
                }
            } catch (IOException e) {
                Log.e(getString(R.string.log_tag), "Could not close socket to " + device.getAddress());
            }
        }
    }
}