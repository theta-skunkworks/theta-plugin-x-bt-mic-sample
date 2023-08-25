package com.theta360.pluginapplication;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

public class BluetoothUtil {

    private final String TAG = "BluetoothUtil";

    @SuppressLint("StaticFieldLeak")
    private static BluetoothUtil mBluetoothUtil;

    // The first time you open sco is not successful, the number of consecutive connections
    private static final int SCO_CONNECT_TIME = 5;
    private int mConnectIndex = 0;

    private AudioManager mAudioManager = null;
    @SuppressLint("StaticFieldLeak")
    static Context mContext;

    private BluetoothUtil() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        }
    }

    public static BluetoothUtil getInstance(Context context) {
        mContext = context;
        if (mBluetoothUtil == null) {
            mBluetoothUtil = new BluetoothUtil();
        }
        return mBluetoothUtil;
    }

    public void openSco(final IBluetoothConnectListener listener) {
        if (!mAudioManager.isBluetoothScoAvailableOffCall()) {
            Log.e (TAG, "openSco() - The system does not support Bluetooth recording");
            listener.onError("openSco() - Your device no support bluetooth record!");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Start SCO connection, Bluetooth headset microphone works
                mAudioManager.stopBluetoothSco();
                mAudioManager.startBluetoothSco();
                // Bluetooth SCO connection establishment takes time.
                mConnectIndex = 0;
                mContext.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,
                                -1);
                        boolean bluetoothScoOn = mAudioManager.isBluetoothScoOn();
                        Log.i(TAG, "onReceive() - state = " + state + ", bluetoothScoOn = " +
                                bluetoothScoOn);
                        if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                            Log.e(TAG, "onReceive() - SCO established successfully!");
                            mAudioManager.setBluetoothScoOn(true); // Open SCO
                            listener.onSuccess();
                            mContext.unregisterReceiver(this); // Cancel the broadcast
                        } else {// After waiting for a second, try to start SCO again
                            Log.e(TAG, "onReceive() - SCO establishment failed index = " +
                                    mConnectIndex);
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (mConnectIndex < SCO_CONNECT_TIME) {
                                Log.d(TAG, "onReceive() - Retry SCO establishment");
                                mAudioManager.startBluetoothSco();//Try connecting again
                            } else {
                                Log.e(TAG, "onReceive() - Failed to establish SCO!");
                                listener.onError("Open SCO failed, please retry");

                                mContext.unregisterReceiver(this); //Cancel the broadcast
                            }
                            mConnectIndex++;
                        }
                    }
                }, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
            }
        }).start();
    }

    public void closeSco() {
        boolean bluetoothScoOn = mAudioManager.isBluetoothScoOn();
        Log.i(TAG, "closeSco() - bluetoothScoOn = " + bluetoothScoOn);
        if (bluetoothScoOn) {
            mAudioManager.setBluetoothScoOn(false);
            mAudioManager.stopBluetoothSco();
        }
        mBluetoothConnectListener = null;
    }

    public interface IBluetoothConnectListener {
        void onError(String error);
        void onSuccess();
    }

    IBluetoothConnectListener mBluetoothConnectListener;
}
