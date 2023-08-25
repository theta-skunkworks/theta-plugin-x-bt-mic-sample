/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.pluginapplication;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;


public class MainActivity extends PluginActivity implements BluetoothUtil.IBluetoothConnectListener {

    private String TAG="BluetoothMicTest";

    final static int SAMPLING_RATE = 44100;
    private AudioRecord audioRec = null;
    private boolean isRecording = false;
    private int bufSize;
    private AudioTrack audioTrack;

    private BluetoothUtil mUtil = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //録音用バッファの計算
        bufSize=AudioRecord.getMinBufferSize(
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        Log.d(TAG, "bufSize=" + String.valueOf(bufSize));

        //再生開始 ※Bluetooth機器を接続せず本体スピーカーを利用した場合、HW特性の都合で再生音が聞こえません
        audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, SAMPLING_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufSize, AudioTrack.MODE_STREAM);
        audioTrack.play();


        // Set enable to close by pluginlibrary, If you set false, please call close() after finishing your end processing.
        setAutoClose(true);
        // Set a callback when a button operation event is acquired.
        setKeyCallback(new KeyCallback() {
            @Override
            public void onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyReceiver.KEYCODE_CAMERA) {
                    /*
                     * To take a static picture, use the takePicture method.
                     * You can receive a fileUrl of the static picture in the callback.
                     */

                    //シャッターボタン押下時の処理
                    if (isRecording) {
                        //録音終了
                        stopRecord();
                        mUtil.closeSco();
                    } else {
                        //録音開始
                        //SCO接続を試し 結果により onSuccess() or onError()が呼ばれる
                        //今回のサンプルは接続結果をログに残し、どちらもそこでstartRecord()する。
                        mUtil.openSco(MainActivity.this);
                    }

                }
            }

            @Override
            public void onKeyUp(int keyCode, KeyEvent event) {
                /**
                 * You can control the LED of the camera.
                 * It is possible to change the way of lighting, the cycle of blinking, the color of light emission.
                 * Light emitting color can be changed only LED3.
                 */
                //notificationLedBlink(LedTarget.LED3, LedColor.BLUE, 1000);
            }

            @Override
            public void onKeyLongPress(int keyCode, KeyEvent event) {

            }
        });

        //BluetoothUtilのインスタンス生成
        mUtil = BluetoothUtil.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //THETA X needs to open WebAPI camera before camera.takePicture
        notificationWebApiCameraOpen();

        if (isApConnected()) {

        }

    }

    @Override
    protected void onPause() {
        // Do end processing
        //close();

        //停止操作忘れ防止
        stopRecord();

        //再生終了と開放
        audioTrack.stop();
        audioTrack.release();

        //THETA X needs to close WebAPI camera before finishing plugin
        notificationWebApiCameraClose();

        super.onPause();
    }

    private void startRecord(){
        //AudioRecordの作成
        audioRec = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLING_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 2); // ２倍にしないと処理落ちするらしい。

        audioRec.startRecording();
        isRecording = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "START RECORD");
                short buf[] = new short[bufSize];
                while (isRecording) {
                    int numRead = audioRec.read(buf, 0, buf.length);

                    //キャプチャした音声データを再生に流す
                    if (numRead > 0) {
                        audioTrack.write(buf, 0, numRead);
                    }
                }
                // 録音停止
                Log.d(TAG, "STOP RECORD");
                audioRec.stop();
                audioRec.release();
            }
        }).start();
    }

    private void stopRecord() {
        if (isRecording) {
            isRecording = false;
        }
    }

    @Override
    public void onError(String error) {
        Log.d(TAG,"Bluetooth mic is not found. Use the built-in mic.");
        startRecord();
    }

    @Override
    public void onSuccess() {
        Log.d(TAG,"Use the bluetooth mic.");
        startRecord();
    }


}
