// Original source code: https://github.com/StevenRudenko/BleSensorTag. MIT License (Steven Rudenko)

package neopixelvoicecommand.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public class BleManager implements BleGattExecutor.BleExecutorListener {
    // Log
    private final static String TAG = BleManager.class.getSimpleName();

    // Enumerations
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;

    // Singleton
    private static BleManager mInstance = null;

    // Data
    private final BleGattExecutor mExecutor = BleGattExecutor.createExecutor(this);
    private BluetoothAdapter mAdapter;
    private BluetoothGatt mGatt;
    private Context mContext;

    private BluetoothDevice mDevice;
    private String mDeviceAddress;
    private int mConnectionState = STATE_DISCONNECTED;

    private BleManagerListener mBleListener;

    public static BleManager getInstance(Context context) {
        if(mInstance == null)
        {
            mInstance = new BleManager(context);
        }
        return mInstance;
    }

    public boolean canConnectToDevice() {
        return (mConnectionState == STATE_DISCONNECTED);
    }

    public String getDeviceName() {
        if (mDevice != null) {
            return mDevice.getName();
        }
        return "<Unknown>";
    }

    public void setBleListener(BleManagerListener listener) {
        mBleListener = listener;

    }

    public BleManager(Context context) {
        // Init Adapter
        mContext = context.getApplicationContext();
        if (mAdapter == null) {
            mAdapter = BleUtils.getBluetoothAdapter(mContext);
        }

        if (mAdapter == null || !mAdapter.isEnabled()) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
        }
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result is reported asynchronously through the {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} callback.
     */
    public boolean connect(Context context, String address) {
        if (mAdapter == null || address == null) {
            Log.w(TAG, "connect: BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Get preferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean reuseExistingConnection = sharedPreferences.getBoolean("pref_recycleconnection", false);

        if (reuseExistingConnection) {
            // Previously connected device.  Try to reconnect.
            if (mDeviceAddress != null && address.equalsIgnoreCase(mDeviceAddress) && mGatt != null) {
                Log.d(TAG, "Trying to use an existing BluetoothGatt for connection.");
                if (mGatt.connect()) {
                    mConnectionState = STATE_CONNECTING;
                    if (mBleListener != null)
                        mBleListener.onConnecting();
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            final boolean forceCloseBeforeNewConnection = sharedPreferences.getBoolean("pref_forcecloseconnection", true);

            if (forceCloseBeforeNewConnection) {
                close();
            }
        }

        mDevice = mAdapter.getRemoteDevice(address);
        if (mDevice == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        Log.d(TAG, "Trying to create a new connection.");
        mDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        if (mBleListener != null) {
            mBleListener.onConnecting();
        }

        final boolean gattAutoconnect = sharedPreferences.getBoolean("pref_gattautoconnect", false);
        mGatt = mDevice.connectGatt(mContext, gattAutoconnect, mExecutor);

        return true;
    }

    public void clearExecutor() {
        if (mExecutor != null) {
            mExecutor.clear();
        }
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} callback.
     */
    public void disconnect() {
        mDevice = null;

        if (mAdapter == null || mGatt == null) {
            Log.w(TAG, "disconnect: BluetoothAdapter not initialized");
            return;
        }

        // Disconnect
        mGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are  released properly.
     */
    public void close() {
        if (mGatt != null) {
            mGatt.close();
            mGatt = null;
            mDeviceAddress = null;
            mDevice = null;
        }
    }

    public void writeService(BluetoothGattService service, String uuid, byte[] value)
    {
        if (service != null) {
            if (mAdapter == null || mGatt == null) {
                Log.w(TAG, "writeService: BluetoothAdapter not initialized");
                return;
            }

            mExecutor.write(service, uuid, value);
            mExecutor.execute(mGatt);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mGatt != null) {
            return mGatt.getServices();
        } else {
            return null;
        }
    }

    public BluetoothGattService getGattService(String uuid) {
        if (mGatt != null) {
            final UUID serviceUuid = UUID.fromString(uuid);
            return mGatt.getService(serviceUuid);
        } else {
            return null;
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

        // Log.d(TAG, "onConnectionStateChange status: "+status+ " newState: "+newState);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            mConnectionState = STATE_CONNECTED;

            if (mBleListener != null) {
                mBleListener.onConnected();
            }

            // Attempts to discover services after successful connection.
            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            mConnectionState = STATE_DISCONNECTED;

            if (mBleListener != null) {
                mBleListener.onDisconnected();
            }
        } else if (newState == BluetoothProfile.STATE_CONNECTING) {
            mConnectionState = STATE_CONNECTING;

            if (mBleListener != null) {
                mBleListener.onConnecting();
            }
        }
    }

    // region BleExecutorListener
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
       // if (status == BluetoothGatt.GATT_SUCCESS) {
            // Call listener
            if (mBleListener != null)
                mBleListener.onServicesDiscovered();
       // }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onServicesDiscovered status: "+status);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
       // if (status == BluetoothGatt.GATT_SUCCESS) {
            if (mBleListener != null) {
                mBleListener.onDataAvailable(characteristic);
            }
       // }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onCharacteristicRead status: "+status);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (mBleListener != null) {
            mBleListener.onDataAvailable(characteristic);
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
     //   if (status == BluetoothGatt.GATT_SUCCESS) {
            if (mBleListener != null) {
                mBleListener.onDataAvailable(descriptor);
            }
     //   }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onDescriptorRead status: "+status);
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        if (mBleListener != null) {
            mBleListener.onReadRemoteRssi(rssi);
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onReadRemoteRssi status: "+status);
        }

    }
    //endregion

    public interface BleManagerListener {

        void onConnected();
        void onConnecting();
        void onDisconnected();
        void onServicesDiscovered();

        void onDataAvailable(BluetoothGattCharacteristic characteristic);
        void onDataAvailable(BluetoothGattDescriptor descriptor);

        void onReadRemoteRssi(int rssi);
    }
}
