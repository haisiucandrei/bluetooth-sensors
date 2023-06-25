package com.example.bluetooth_sensors.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import com.example.bluetooth_sensors.R;
import com.example.bluetooth_sensors.fragment.PrintDeviceListDialogFragment;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity
        implements PrintDeviceListDialogFragment.PrintDeviceListDialogListener {

    public static final String INTENT_EXTRA_ADDRESSES = "devices";
    public static final String INTENT_EXTRA_SAVE_LOCALLY = "save_locally";
    public static final String INTENT_EXTRA_SAVE_REMOTELY = "save_remotely";
    public static final BluetoothAdapter bluetoothAdapter= BluetoothAdapter.getDefaultAdapter();

    private static final int REQUEST_ENABLE_BT = 5;
    private static final String DEVICE_LIST_FILE = "sensor_devices.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (bluetoothAdapter.isEnabled()) {
            findViewById(R.id.buttonConnect).setEnabled(true);
        }
    }

    /**
     * For checking Bluetooth Permissions
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Bluetooth permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * On-click callback for Enable Bluetooth button
     * @param view
     */
    public void enableBluetooth(View view) {
        if (bluetoothAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "Bluetooth already on.", Toast.LENGTH_SHORT).show();
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(intent);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[] { Manifest.permission.BLUETOOTH }, REQUEST_ENABLE_BT);
            }
        }
    }

    /**
     * On-click callback for Connect button
     * @param view
     */
    public void connectToDevices(View view) {
        if (bluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(this, DataTransferActivity.class);
            // I can add key-value data to intents
            intent.putExtra(INTENT_EXTRA_ADDRESSES, getDeviceList());

            // check the state of the log save switches
            Switch switchSaveLocally = findViewById(R.id.switch_save_locally);
            Switch switchSaveRemotely = findViewById(R.id.switch_save_remotely);

            intent.putExtra(INTENT_EXTRA_SAVE_LOCALLY, switchSaveLocally.isChecked());
            intent.putExtra(INTENT_EXTRA_SAVE_REMOTELY, switchSaveRemotely.isChecked());

            startActivity(intent);
        } else {
            Toast.makeText(MainActivity.this, "Turn Bluetooth on first.", Toast.LENGTH_SHORT).show();
        }

    }

    /**
     * On-click callback for Help button
     * @param view
     */
    public void getHelp(View view) {
        Uri uri = Uri.parse("https://github.com/haisiucandrei/bluetooth-sensors");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }

    /**
     * Utility method for checking MAC address formats using regex
     * @param address
     * @return
     */
    private boolean checkAddressFormat(@NonNull String address) {
        String regex = "^([0-9A-Fa-f]{2}[:-])"
                + "{5}([0-9A-Fa-f]{2})|"
                + "([0-9a-fA-F]{4}\\."
                + "[0-9a-fA-F]{4}\\."
                + "[0-9a-fA-F]{4})$";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(address);

        return m.matches();
    }

    /**
     * On-click callback for Add New Device button
     * @param view
     */
    public void addNewDevice(View view) {
        EditText deviceAddressField = findViewById(R.id.editTextDeviceAddr);
        File deviceFile = new File(getFilesDir(), DEVICE_LIST_FILE);
        String deviceAddress = deviceAddressField.getText().toString();
        BufferedWriter deviceFileStream;

        // for debugging the file
        Log.d(getString(R.string.log_tag), deviceFile.getAbsolutePath());

        if (deviceAddress.isEmpty()) {
            Toast.makeText(MainActivity.this, "Please input an address", Toast.LENGTH_SHORT).show();
        }
        else if (!checkAddressFormat(deviceAddress)) {
            Toast.makeText(MainActivity.this, "Invalid MAC address format", Toast.LENGTH_SHORT).show();
        } else {
            try {
                deviceFileStream = new BufferedWriter(
                        new FileWriter(deviceFile, true)
                );
                deviceFileStream.write(deviceAddress);
                deviceFileStream.newLine();

                deviceFileStream.close();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "File not found", Toast.LENGTH_SHORT).show();
            } finally {
                deviceAddressField.getText().clear();
            }
        }
    }

    /**
     * On-click callback for Print Device List button
     * @param view
     */
    public void printDeviceList(View view) {
        DialogFragment dialog = new PrintDeviceListDialogFragment();
        dialog.show(getSupportFragmentManager(), "PrintDeviceListDialogFragment");
    }

    /**
     * Callback for dialog list population
     * @return
     */
    @Override
    public CharSequence[] getDeviceList() {
        File deviceFile = new File(getFilesDir(), DEVICE_LIST_FILE);
        try {
            if (!deviceFile.exists()) {
                deviceFile.createNewFile();
            }
            return Files.lines(deviceFile.toPath()).toArray(CharSequence[]::new);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Error reading file.", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Callback for dialog list element click
     * @param addressToDelete
     */
    @Override
    public void deleteDevice(String addressToDelete) {
        File deviceFile = new File(getFilesDir(), DEVICE_LIST_FILE);
        try {
            List<String> lines = Files.lines(deviceFile.toPath()).collect(Collectors.toList());
            List<String> updatedLines = lines.stream().
                    filter(line -> !line.contains(addressToDelete)).
                    collect(Collectors.toList());
            Files.write(deviceFile.toPath(), updatedLines);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, "Error deleting device.", Toast.LENGTH_SHORT).show();
        }
    }
}
