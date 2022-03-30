package com.baller.demo.asr_tts_websocket;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Base64;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;


import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// 添加依赖：org.jbundle.util.osgi.wrapped:org.jbundle.util.osgi.wrapped.org.apache.http.client:4.1.2
// 添加依赖：commons-logging:commons-logging:1.2


class BallerASRHTTPTest extends Thread {

    private BallerASRHTTP mAsrhttp;
    private LinkedList<byte[]> mRecordPCM = new LinkedList<>();
    private static int iAudioBuff = AudioTrack.getMinBufferSize(16000,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

    BallerASRHTTPTest(String strLanguage) {
        mAsrhttp = new BallerASRHTTP(strLanguage);
    }

    private class BallerASRHTTP extends Thread {
        private String mLogTag = "BallerASRHTTP";
        private String mUrl = "http://api.baller-tech.com/v1/service/v1/asr";
        private long mAppId = 0L;
        private String mAppkey = "";
        private String mLanguage;

        private String requestId;
        private AudioTrack mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                iAudioBuff, AudioTrack.MODE_STREAM);
        private AtomicBoolean has_start = new AtomicBoolean(false);

        BallerASRHTTP(String strLanguage) {
            this.mLanguage = strLanguage;
            this.requestId = UUID.randomUUID().toString();
        }

        void addAudio(byte[] audio) {
            if (mRecordPCM.size() > 0) {
                byte [] audio_tmp = mRecordPCM.pop();
                if (audio_tmp != null) {
                    if (!postData(requestId, audio, "continue")) {
                        Log.e(mLogTag, "post failed");
                    }
                    has_start.set(true);
                }
            }
            mRecordPCM.addLast(audio);
        }

        void setFinish() {
            byte [] audio_tmp = mRecordPCM.pop();
            if (audio_tmp != null) {
                postData(requestId, audio_tmp, "end");
            }
        }

        private String getGmtTime() {
            SimpleDateFormat sdf3 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            sdf3.setTimeZone(TimeZone.getTimeZone("GMT"));
            return sdf3.format(new Date());
        }

        private String MD5(String sourceStr) {
            try {
                MessageDigest mdInst = MessageDigest.getInstance("MD5");
                mdInst.update(sourceStr.getBytes());
                byte[] md = mdInst.digest();
                StringBuilder buffer = new StringBuilder();
                for (int i = 0; i < md.length; i++) {
                    int tmp = md[i];
                    if (tmp < 0)
                        tmp += 256;
                    if (tmp < 16)
                        buffer.append("0");
                    buffer.append(Integer.toHexString(tmp));
                }
                return buffer.toString();

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        private boolean postData(String requestId_, byte[] testData, String inputMode) {
            JSONObject businessParams = new JSONObject();
            try {
                businessParams.put("request_id", requestId_);
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

            HttpPost httpPost = new HttpPost(mUrl);
            httpPost.setHeader("B-AppId", String.valueOf(mAppId));
            httpPost.setHeader("B-CurTime", requestTime);
            httpPost.setHeader("B-Param", businessParamsBase64);
            httpPost.setHeader("B-CheckSum", md5);
            httpPost.setHeader("Content-Type", "application/octet-stream");

            httpPost.setEntity(new ByteArrayEntity(testData));

            HttpClient httpClient = new DefaultHttpClient();

            httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
            httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 30000);
            try {
                HttpResponse httpResp = httpClient.execute(httpPost);
                int  status_code = httpResp.getStatusLine().getStatusCode();
                if (status_code == 200) {
                    String result = EntityUtils.toString(httpResp.getEntity(), "UTF-8");
                    JSONObject responseContent = new JSONObject(result);
                    int error_code = responseContent.getInt("code");
                    if (error_code != 0)
                    {
                        Log.i(mLogTag, "asr post failed: " + error_code);
                        return false;
                    }

                } else {
                    Log.i(mLogTag, "asr post failed. " + status_code);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        private boolean getResult(String requestId_) {
            boolean isEnd = false;
            JSONObject paramMap = new JSONObject();
            try {
                paramMap.put("request_id", requestId_);
            }catch (Exception e) {
                e.printStackTrace();
                return true;
            }

            String businessParamBase64 = Base64.encodeToString(paramMap.toString().getBytes(), Base64.NO_WRAP);

            String requestTime = getGmtTime();
            String checkSum = mAppkey + requestTime + businessParamBase64;
            String md5 = MD5(checkSum);

            HttpGet httpGet = new HttpGet(mUrl);
            httpGet.setHeader("B-AppId", String.valueOf(mAppId));
            httpGet.setHeader("B-CurTime", requestTime);
            httpGet.setHeader("B-Param", businessParamBase64);
            httpGet.setHeader("B-CheckSum", md5);

            HttpClient httpClient = new DefaultHttpClient();


            httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
            httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 30000);
            try {
                HttpResponse httpResp = httpClient.execute(httpGet);
                if (httpResp.getStatusLine().getStatusCode() == 200) {
                    String result = EntityUtils.toString(httpResp.getEntity(), "UTF-8");

                    if (!result.isEmpty()) {
                        JSONObject jsonResult = new JSONObject(result);
                        isEnd = 1 == jsonResult.getInt("is_end");
                        String strData = jsonResult.getString("data");
                        int error_code = jsonResult.getInt("code");
                        int isComplete = jsonResult.getInt("is_complete");
                        if (error_code != 0)
                        {
                            String message = jsonResult.getString("message");
                            Log.i(mLogTag, "asr get failed: " + error_code + " " + message + " " + requestId_);
                        } else if (isComplete == 1){
                            Log.i(mLogTag, "asr result: " + strData);
                        }
                    }
                } else {
                    Log.i(mLogTag, "asr get failed");
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return true;
            }
            return isEnd;
        }

        @Override
        public void run() {
            mAudioTrack.play();
            try {
                while (true) {
                    if (has_start.get())  {
                        if (getResult(requestId)) {
                            break;
                        }
                    }
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

        while (!this.isInterrupted()) {
            int iReadSize = mWakeRecorder.read(data, 0, data.length);
            if (0 < iReadSize)
            {
                mAsrhttp.addAudio(data);
            }
        }

        mAsrhttp.setFinish();
        try {
            mAsrhttp.join();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mWakeRecorder.stop();
        mWakeRecorder.release();
    }
}

