package neopixelvoicecommand;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import neopixelvoicecommand.ble.BleDevicesScanner;
import neopixelvoicecommand.ble.BleManager;
import neopixelvoicecommand.ble.BleUtils;

public class MainActivity extends AppCompatActivity implements BleManager.BleManagerListener, BleUtils.ResetBluetoothAdapterListener, NavigationView.OnNavigationItemSelectedListener {

    //Service Constant
    private static final String BLUEFRUIT_BORARD_NAME = "Adafruit Bluefruit LE";
    private static final String UUID_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1001;
    private static final int kTxMaxCharacters = 20;

    //Bluetooth
    private BluetoothGattService mUartService;
    private BleDevicesScanner mScanner;
    private BleManager mBleManager;

    //UI
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private boolean mIsScanPaused = true;
    private AlertDialog mConnectingDialog;

    @Override
    protected void onDestroy() {
        Log.d(TAG, "OnDestroy called");

        // Stop ble adapter reset if in progress
        BleUtils.cancelBluetoothAdapterReset();

        // Clean
        if (mConnectingDialog != null) {
            mConnectingDialog.cancel();
        }

        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                speak();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        mBleManager = BleManager.getInstance(this);
        // Request Bluetooth scanning persmissions
        requestLocationPermissionIfNeeded();
    }

    // region Permissions
    @TargetApi(Build.VERSION_CODES.M)
    private void requestLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can scan for Bluetooth peripherals");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                    }
                });
                builder.show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Location permission granted");
                    // Autostart scan
                    autostartScan();
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Bluetooth Scanning not available");
                    builder.setMessage("Since location access has not been granted, the app will not be able to scan for Bluetooth peripherals");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                break;
            }
            default:
                break;
        }
    }

    // endregion

    @Override
    public void onResume() {
        super.onResume();

        // Set listener
        mBleManager.setBleListener(this);

        // Autostart scan
        autostartScan();
    }

    @Override
    public void onPause() {
        // Stop scanning
        if (mScanner != null && mScanner.isScanning()) {
            mIsScanPaused = true;
            stopScanning();
        }

        super.onPause();
    }

    private void autostartScan() {
        if (BleUtils.getBleStatus(this) == BleUtils.STATUS_BLE_ENABLED) {
            // If was connected, disconnect
            mBleManager.disconnect();
            startScan();
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    // region BleManagerListener
    @Override
    public void onConnected() {
    }

    @Override
    public void onConnecting() {
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "MainActivity onDisconnected");
    }

    @Override
    public void onServicesDiscovered() {
        Log.d(TAG, "onServicesDiscovered");
        mUartService = mBleManager.getGattService(UUID_SERVICE);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showConnectionStatus(false);
                Snackbar.make(findViewById(R.id.fab),
                            String.format("Connected to %s", mBleManager.getDeviceName()),
                            Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

            }
        });
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }

    // region ResetBluetoothAdapterListener
    @Override
    public void resetBluetoothCompleted() {
        Log.d(TAG, "Reset completed -> Resume scanning");
        resumeScanning();
    }
    // endregion

    private void resumeScanning() {
        if (mIsScanPaused) {
            startScan();
            mIsScanPaused = mScanner == null;
        }
    }

    // region Scan
    private void startScan() {
        Log.d(TAG, "startScan");

        // Stop current scanning (if needed)
        stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        if (BleUtils.getBleStatus(this) != BleUtils.STATUS_BLE_ENABLED) {
            Log.w(TAG, "startScan: BluetoothAdapter not initialized or unspecified address.");
        } else {
            mScanner = new BleDevicesScanner(bluetoothAdapter, new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    final String deviceName = device.getName();
                    if (BLUEFRUIT_BORARD_NAME.equals(deviceName)) {
                        if (mBleManager.canConnectToDevice()) {
                            connectToDevice(device);
                        }
                    } else {
                        Log.d(TAG, "Discovered device: " + (deviceName != null ? deviceName : "<unknown>"));
                    }
                }
            });

            // Start scanning
            mScanner.start();
        }
    }

    private void stopScanning() {
        // Stop scanning
        if (mScanner != null) {
            mScanner.stop();
            mScanner = null;
        }
    }
    // endregion

    public void connectToDevice(BluetoothDevice device) {
        stopScanning();
        mBleManager.setBleListener(MainActivity.this);           // Force set listener (could be still checking for updates...)
        connect(device);
    }

    private void connect(BluetoothDevice device) {
        boolean isConnecting = mBleManager.connect(this, device.getAddress());
        if (isConnecting) {
            showConnectionStatus(true);
        }
    }

    private void showConnectionStatus(boolean enable) {
        showStatusDialog(enable, R.string.scan_connecting);
    }

    private void showStatusDialog(boolean show, int stringId) {
        if (show) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(stringId);

            // Show dialog
            mConnectingDialog = builder.create();
            mConnectingDialog.setCanceledOnTouchOutside(false);

            mConnectingDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        mBleManager.disconnect();
                        mConnectingDialog.cancel();
                    }
                    return true;
                }
            });
            mConnectingDialog.show();
        } else {
            if (mConnectingDialog != null) {
                mConnectingDialog.cancel();
            }
        }
    }

    // region Send Data to UART
    protected void sendData(String text) {
        final byte[] value = text.getBytes(Charset.forName("UTF-8"));
        sendData(value);
    }

    protected void sendData(byte[] data) {
        if (mUartService != null) {
            // Split the value into chunks (UART service has a maximum number of characters that can be written )
            for (int i = 0; i < data.length; i += kTxMaxCharacters) {
                final byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + kTxMaxCharacters, data.length));
                mBleManager.writeService(mUartService, UUID_TX, chunk);
            }
        } else {
            Log.w(TAG, "Uart Service not discovered. Unable to send data");
        }
    }

    // Send data to UART and add a byte with a custom CRC
    protected void sendDataWithCRC(byte[] data) {

        // Calculate checksum
        byte checksum = 0;
        for (byte aData : data) {
            checksum += aData;
        }
        checksum = (byte) (~checksum);       // Invert

        // Add crc to data
        byte dataCrc[] = new byte[data.length + 1];
        System.arraycopy(data, 0, dataCrc, 0, data.length);
        dataCrc[data.length] = checksum;

        // Send it
        Log.d(TAG, "Send to UART: " + BleUtils.bytesToHexWithSpaces(dataCrc));
        sendData(dataCrc);
    }
    // endregion

    public void sendColorToDevice(int color) {
        // Send selected color !Crgb
        byte r = (byte) ((color >> 16) & 0xFF);
        byte g = (byte) ((color >> 8) & 0xFF);
        byte b = (byte) ((color >> 0) & 0xFF);

        ByteBuffer buffer = ByteBuffer.allocate(2 + 3 * 1).order(java.nio.ByteOrder.LITTLE_ENDIAN);

        // prefix
        String prefix = "!C";
        buffer.put(prefix.getBytes());

        // values
        buffer.put(r);
        buffer.put(g);
        buffer.put(b);

        byte[] result = buffer.array();
        sendDataWithCRC(result);
    }

    /* The following colors are supported:
     * 'red', 'blue', 'green', 'black', 'white', 'gray', 'cyan', 'magenta',
     * 'yellow', 'lightgray', 'darkgray', 'grey', 'lightgrey', 'darkgrey',
     * 'aqua', 'fuchsia', 'lime', 'maroon', 'navy', 'olive', 'purple',
     * 'silver', 'teal'.
     */
    private void changeColor(String color) {
        try {
            int rgb = Color.parseColor(color);
            Integer rgbInt = new Integer(rgb);
            String hex = BleUtils.bytesToHexWithSpaces(new byte[]{rgbInt.byteValue()});
            Log.d(TAG, String.format("Send to device: %s", hex));
            sendColorToDevice(rgb);
        } catch (IllegalArgumentException iae) {
            Log.d(TAG, iae.getLocalizedMessage());
            Snackbar.make(findViewById(R.id.fab),
                            iae.getLocalizedMessage(),
                            Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
    }

    public void speak() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                getClass().getPackage().getName());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "NeoPixelVoiceCommand");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                List<String> list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                String color = list.get(0);
                changeColor(color);
                Snackbar.make(findViewById(R.id.fab),
                            String.format("Color: %s", color),
                            Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }

        } else if(resultCode == RecognizerIntent.RESULT_AUDIO_ERROR){
            Snackbar.make(findViewById(R.id.fab), "Audio Error", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }else if(resultCode == RecognizerIntent.RESULT_CLIENT_ERROR){
            Snackbar.make(findViewById(R.id.fab), "Client Error", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }else if(resultCode == RecognizerIntent.RESULT_NETWORK_ERROR){
            Snackbar.make(findViewById(R.id.fab), "Network Error", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }else if(resultCode == RecognizerIntent.RESULT_NO_MATCH){
            Snackbar.make(findViewById(R.id.fab), "No Match", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }else if(resultCode == RecognizerIntent.RESULT_SERVER_ERROR){
            Snackbar.make(findViewById(R.id.fab), "Server Error", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}