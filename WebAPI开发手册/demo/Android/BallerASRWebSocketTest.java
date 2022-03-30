package com.baller.demo.asr_tts_websocket;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Base64;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// 添加依赖：org.java-websocket:Java-WebSocket:1.3.0

public class BallerASRWebSocketTest extends Thread {

    private BallerASR mAsrWebSocket;

    public BallerASRWebSocketTest(String strLanguage) {
        mAsrWebSocket = new BallerASR(strLanguage);
    }

    public class BallerASR extends Thread {

        private String mLogTag = "BallerASRWebSocketTest";
        private String mUrl = "ws://api.baller-tech.com/v1/service/ws/v1/asr";
        private String mHost = "api.baller-tech.com";
        private long mAppId = 0L;
        private String mAppkey = "";

        WebSocketClient mWSClient = null;
        LinkedList<byte[]> mRecordPCM = new LinkedList<>();
        boolean mFirstFrame = true;
        String mLanguage;
        AtomicBoolean mFinish = new AtomicBoolean(false);

        BallerASR(String strLanguage) {
            this.mLanguage = strLanguage;
        }

        private String HMACSHA256AndBase64(byte[] data, byte[] key)
        {
            try  {
                SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA256");
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(signingKey);
                return Base64.encodeToString(mac.doFinal(data), Base64.NO_WRAP);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
        void sendNetDisconnect() {

        }

        void addAudio(byte[] audio) {
            mRecordPCM.addLast(audio);
        }

        void setFinish() {
            mFinish.set(true);
        }

        private String makeAuthorization() {
            Calendar cd = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String strRequestDateTime = sdf.format(cd.getTime());

            String strSignatureOrg = "app_id:" + mAppId + "\n";
            strSignatureOrg += "date:" + strRequestDateTime + "\n";
            strSignatureOrg += "host:" + mHost;
            String strSignatureFinal = HMACSHA256AndBase64(strSignatureOrg.getBytes(), mAppkey.getBytes());

            JSONObject jsonAuthorization = new JSONObject();
            try {
                jsonAuthorization.put("app_id", Long.toString(mAppId));
                jsonAuthorization.put("signature", strSignatureFinal);
            } catch (JSONException e) {
                e.printStackTrace();
                return "";
            }

            String strAuthorizationFinal = Base64.encodeToString(jsonAuthorization.toString().getBytes(), Base64.NO_WRAP);
            return "authorization=" + strAuthorizationFinal + "&host=" + mHost + "&date=" + strRequestDateTime;
        }

        private String makeFrame(byte[] audio, String strInputMode)
        {
            JSONObject jsonParams = new JSONObject();
            if (this.mFirstFrame)
            {
                JSONObject jsonBusiness = new JSONObject();
                try {
                    jsonBusiness.put("language", this.mLanguage);
                    jsonBusiness.put("audio_format", "audio/L16;rate=16000");
                    jsonBusiness.put("service_type", "sentence");
                    jsonParams.put("business", jsonBusiness);
                } catch (Exception e) {
                    e.printStackTrace();
                    return "";
                }
                this.mFirstFrame = false;
            }

            JSONObject jsonData = new JSONObject();
            try {
                jsonData.put("input_mode", strInputMode);
                if (null != audio) {
                    jsonData.put("audio", Base64.encodeToString(audio, Base64.NO_WRAP));
                }
                jsonParams.put("data", jsonData);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return jsonParams.toString();
        }

        @Override
        public void run() {
            String strUrl = mUrl + "?" + URLEncoder.encode(makeAuthorization());
            try
            {
                mWSClient = new WebSocketClient(new URI(strUrl), new Draft_17(), new HashMap<String, String>(), 3000) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                    }

                    @Override
                    public void onMessage(String message) {
                        JSONObject jsonResult;
                        try {
                            jsonResult = new JSONObject(message);
                        } catch (Exception e) {
                            Log.i(mLogTag, e.toString());
                            return;
                        }

                        boolean isEnd = false;
                        boolean isComplete = false;
                        int errorCode = 0 ;
                        String strText = "";
                        try {
                            isEnd = 1 == jsonResult.getInt("is_end");
                            isComplete = 1 == jsonResult.getInt("is_complete");
                            strText = jsonResult.getString("data");
                            errorCode = jsonResult.getInt("code");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (errorCode != 0)
                        {
                            Log.i(mLogTag, "asr failed. error code: " + String.valueOf(errorCode));
                        } else if (isComplete) {
                            Log.i(mLogTag, "asr result: " + strText);
                        }

                        if (isEnd) {
                            try {
                                mWSClient.closeBlocking();
                            } catch (Exception e) {
                                Log.i(mLogTag, e.toString());
                            }
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        Log.i(mLogTag, "on close: " + reason);
                        mWSClient = null;
                    }

                    @Override
                    public void onError(Exception ex) {
                        Log.i(mLogTag, "on error: " + ex.getMessage());
                    }
                };
            } catch (Exception e)
            {
                e.printStackTrace();
                return;
            }

            boolean bConnectSucc = true;
            try {
                bConnectSucc = mWSClient.connectBlocking();
            } catch (InterruptedException e) {
                bConnectSucc = false;
                e.printStackTrace();
            }

            if (!bConnectSucc) {
                sendNetDisconnect();
                return;
            }

            while (!this.mFinish.get() & mWSClient!=null)
            {
                if (this.mRecordPCM.size() > 0)
                {
                    {
                        byte[] audio = this.mRecordPCM.pop();
                        if (null != audio)
                        {
                            try {
                                mWSClient.send(this.makeFrame(audio, "continue"));
                            } catch (Exception e)
                            {
                                sendNetDisconnect();
                                try {
                                    mWSClient.closeBlocking();
                                } catch (Exception eConnect) {
                                    Log.i(mLogTag, eConnect.toString());
                                }
                                return;
                            }
                        }
                    }
                }

                // 等待10毫秒在继续
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Log.e(mLogTag, e.toString());
                }
            }
            try {
                mWSClient.send(this.makeFrame(null, "end"));
            } catch (Exception e) {
                sendNetDisconnect();
                try {
                    mWSClient.closeBlocking();
                } catch (Exception eConnect) {
                    Log.i(mLogTag, eConnect.toString());
                }
            }
        }
    }

    @Override
    public void run() {

        int recordBufSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);  //audioRecord能接受的最小的buffer大小
        byte[] data = new byte[recordBufSize];

        AudioRecord mWakeRecorder = null;
        try {
            mWakeRecorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufSize);
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
            //可能情况二
            mWakeRecorder.stop();
            mWakeRecorder.release();
            return;
        } else {
            int readSize = mWakeRecorder.read(data, 0, 2);
            if (readSize <= 0) {
                //可能情况三
                mWakeRecorder.stop();
                mWakeRecorder.release();
                return;
            }
        }

        mAsrWebSocket.start();

        while (!this.isInterrupted()) {
            int iReadSize = mWakeRecorder.read(data, 0, data.length);
            if (0 < iReadSize)
            {
                mAsrWebSocket.addAudio(data);
            }
        }
        mAsrWebSocket.setFinish();
        mWakeRecorder.stop();
        mWakeRecorder.release();
    }
}

