package neopixelvoicecommand.ble;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BleDevicesScanner {
    private static final String TAG = BleDevicesScanner.class.getSimpleName();
    private static final long kScanPeriod = 20 * 1000; // scan period in milliseconds

    // Data
    private final BluetoothAdapter mBluetoothAdapter;
    private volatile boolean mIsScanning = false;
    private Handler mHandler;
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final LeScansPoster mLeScansPoster;

    //
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    synchronized (mLeScansPoster) {
                        mLeScansPoster.set(device, rssi, scanRecord);
                        mMainThreadHandler.post(mLeScansPoster);
                    }
                }
            };

    public BleDevicesScanner(BluetoothAdapter adapter, BluetoothAdapter.LeScanCallback callback) {
        mBluetoothAdapter = adapter;
        mLeScansPoster = new LeScansPoster(callback);

        mHandler = new Handler();
    }

    public void start() {
        if (kScanPeriod > 0) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mIsScanning) {
                        Log.d(TAG, "Scan timer expired. Restart scan");
                        stop();
                        start();
                    }
                }
            }, kScanPeriod);
        }

        mIsScanning = true;
        Log.d(TAG, "start scanning");
        mBluetoothAdapter.startLeScan(mLeScanCallback);

    }

    public void stop() {
        if (mIsScanning) {
            mHandler.removeCallbacksAndMessages(null);      // cancel pending calls to stop
            mIsScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            Log.d(TAG, "stop scanning");
        }
    }

    public boolean isScanning() {
        return mIsScanning;
    }

    private static class LeScansPoster implements Runnable {
        private final BluetoothAdapter.LeScanCallback leScanCallback;

        private BluetoothDevice device;
        private int rssi;
        private byte[] scanRecord;

        private LeScansPoster(BluetoothAdapter.LeScanCallback leScanCallback) {
            this.leScanCallback = leScanCallback;
        }

        public void set(BluetoothDevice device, int rssi, byte[] scanRecord) {
            this.device = device;
            this.rssi = rssi;
            this.scanRecord = scanRecord;
        }

        @Override
        public void run() {
            leScanCallback.onLeScan(device, rssi, scanRecord);
        }
    }
}