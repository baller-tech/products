package com.baller.test;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Base64;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.UUID;

import org.json.JSONObject;

public class BallerASRHTTPTest extends BallerBase {
    private static String mLogTag = "BallerASRHTTP";
    private static String mUrl = "http://" + mHost + "/v1/service/v1/asr";
    private String mLanguage;

    private BallerASRHTTP mAsrhttp;
    private LinkedList<byte[]> mRecordPCM = new LinkedList<>();
    private static int iAudioBuff = AudioTrack.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT);

    BallerASRHTTPTest(String strLanguage) {
        this.mLanguage = strLanguage;
        mAsrhttp = new BallerASRHTTP();
    }

    private boolean postData(String requestId, byte[] testData, String inputMode) {
        JSONObject businessParams = new JSONObject();
        try {
            businessParams.put("request_id", requestId);
            businessParams.put("language", mLanguage);
            businessParams.put("sample_format", "audio/L16;rate=16000");
            businessParams.put("audio_format", "raw");
            businessParams.put("service_type", "sentence");
            businessParams.put("input_mode", inputMode);
            businessParams.put("vad", "on");
        } catch (Exception e) {
            Log.i(mLogTag, "asr post failed. " + e.getMessage());
            return false;
        }
        String businessParamsBase64 =
                Base64.encodeToString(businessParams.toString().getBytes(), Base64.NO_WRAP);

        String requestTime = getGmtTime();
        String checkSum = mAppkey + requestTime + businessParamsBase64;
        String md5 = MD5(checkSum);

        try {

            URL url = new URL(mUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // 设置连接超时为5秒
            connection.setConnectTimeout(5000);
            // 设置请求类型为Get类型
            connection.setRequestMethod("POST");
            connection.setRequestProperty("B-AppId", String.valueOf(mAppId));
            connection.setRequestProperty("B-CurTime", requestTime);
            connection.setRequestProperty("B-Param", businessParamsBase64);
            connection.setRequestProperty("B-CheckSum", md5);
            connection.setRequestProperty("Content-Type", "application/octet-stream");

            OutputStream out = connection.getOutputStream();
            out.write(testData);
            out.flush();
            out.close();

            int  status_code = connection.getResponseCode();

            if (status_code == 200) {
                InputStream inStream = connection.getInputStream();
                byte[] bt = readStream(inStream);

                String content = new String(bt, StandardCharsets.UTF_8);
                com.alibaba.fastjson.JSONObject responseContent = com.alibaba.fastjson.JSONObject.parseObject(content);
                int error_code = responseContent.getIntValue("code");
                String message = responseContent.getString("message");
                if (error_code != 0)
                {
                    Log.i(mLogTag, "asr failed: " + error_code + " " +message);
                }
                inStream.close();
            } else {
                Log.i(mLogTag, "asr post failed");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean getResult(String requestId) {
        boolean isEnd;
        JSONObject paramMap = new JSONObject();
        try {
            paramMap.put("request_id", requestId);
        }catch (Exception e) {
            e.printStackTrace();
            return true;
        }

        String businessParamBase64 = Base64.encodeToString(paramMap.toString().getBytes(), Base64.NO_WRAP);

        String requestTime = getGmtTime();
        String checkSum = mAppkey + requestTime + businessParamBase64;
        String md5 = MD5(checkSum);

        try {

            URL url = new URL(mUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // 设置连接超时为5秒
            connection.setConnectTimeout(5000);
            // 设置请求类型为Get类型
            connection.setRequestMethod("GET");
            connection.setRequestProperty("B-AppId", String.valueOf(mAppId));
            connection.setRequestProperty("B-CurTime", requestTime);
            connection.setRequestProperty("B-Param", businessParamBase64);
            connection.setRequestProperty("B-CheckSum", md5);

            int  status_code = connection.getResponseCode();

            if (status_code == 200) {
                InputStream inStream = connection.getInputStream();
                byte[] bt = readStream(inStream);

                String content = new String(bt, StandardCharsets.UTF_8);
                com.alibaba.fastjson.JSONObject responseContent = com.alibaba.fastjson.JSONObject.parseObject(content);
                isEnd = 1 == responseContent.getIntValue("is_end");
                int is_complete = responseContent.getIntValue("is_complete");
                String strData = responseContent.getString("data");
                int error_code = responseContent.getIntValue("code");
                String message = responseContent.getString("message");
                if (error_code != 0)
                {
                    Log.i(mLogTag, "asr failed: " + error_code + " " +message);
                } else if (!strData.isEmpty() && is_complete == 1) {
                    Log.i(mLogTag, "asr result: " + strData);
                    sendResult(strData);
                }
            } else {
                Log.i(mLogTag, "asr get failed");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return isEnd;
    }


    private class BallerASRHTTP extends Thread {
        private AudioTrack mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                16000, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                iAudioBuff, AudioTrack.MODE_STREAM);

        BallerASRHTTP() {}


        void addAudio(byte[] audio) {
            mRecordPCM.addLast(audio);
        }

        @Override
        public void run() {
            mAudioTrack.play();
            String requestId = UUID.randomUUID().toString();
            try {
                while (!finish.get()) {
                    if (mRecordPCM.size() == 0) {
                        continue;
                    }

                    byte [] audio = mRecordPCM.pop();
                    if (audio != null) {
                        boolean putSuccess =postData(requestId, audio, "continue");
                        if (!putSuccess) {
                            Log.e(mLogTag, "post failed");
                        }
                    }
                }
                byte [] audio;
                if (mRecordPCM.size() > 0) {
                    audio = mRecordPCM.pop();
                } else {
                    audio= new byte[32];
                }

                if (!postData(requestId, audio, "end")) {
                    Log.e(mLogTag, "post failed");
                }

                while (!getResult(requestId)) {
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {

        byte[] data = new byte[iAudioBuff];

        AudioRecord mWakeRecorder = null;
        try {
            mWakeRecorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, iAudioBuff);
            mWakeRecorder.startRecording();
        } catch (Exception e) {
            if (mWakeRecorder != null) {
                mWakeRecorder.stop();
                mWakeRecorder.release();
            }
            return;
        }

        // 检测是否在录音中,6.0以下会返回此状态
        if (mWakeRecorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            mWakeRecorder.stop();
            mWakeRecorder.release();
            return;
        } else {
            int readSize = mWakeRecorder.read(data, 0, 2);
            if (readSize <= 0) {
                mWakeRecorder.stop();
                mWakeRecorder.release();
                return;
            }
        }

        mAsrhttp.start();

        while (!this.finish.get()) {
            int iReadSize = mWakeRecorder.read(data, 0, data.length);
            if (0 < iReadSize)
            {
                mAsrhttp.addAudio(data);
            }
        }

        try {
            mAsrhttp.join();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mWakeRecorder.stop();
        mWakeRecorder.release();
    }
}

