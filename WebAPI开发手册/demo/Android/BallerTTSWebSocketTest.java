package com.baller.test;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

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
import java.util.Locale;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// 添加依赖：org.java-websocket:Java-WebSocket:1.3.0

public class BallerTTSWebSocketTest extends BallerBase {

    private static String mLogTag = "BallerTTSWebSocketTest";
    private static String mUrl = "ws://api.baller-tech.com/v1/service/ws/v1/tts";
    private static String mHost = "api.baller-tech.com";
    private static float speed = 1.0f;

    private WebSocketClient mWSClient = null;
    private String mLanguage;
    private String mTxt;

    private AudioTrack mAudioTrack = null;

    BallerTTSWebSocketTest(String strLanguage, String strTxt) {
        this.mLanguage = strLanguage;
        this.mTxt = strTxt;
    }

    private void sendNetDisconnect() {
        return;
    }

    private static String HMACSHA256AndBase64(byte[] data, byte[] key)
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

    private static String makeFrame(String strLanguage, String strText)
    {
        JSONObject jsonParams = new JSONObject();
        JSONObject jsonBusiness = new JSONObject();
        try {
            jsonBusiness.put("language", strLanguage);
            jsonBusiness.put("sample_format", "audio/L16;rate=16000");
            jsonBusiness.put("audio_encode", "raw");
            jsonBusiness.put("speed",speed);
            jsonParams.put("business", jsonBusiness);
        } catch (JSONException e) {
            Log.e(mLogTag, e.toString());
            return "";
        }

        JSONObject jsonData = new JSONObject();
        try {
            jsonData.put("txt", Base64.encodeToString(strText.getBytes(), Base64.NO_WRAP));
            jsonParams.put("data", jsonData);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return jsonParams.toString();
    }

    @Override
    public void run() {
        int iAudioBuff = AudioTrack.getMinBufferSize(16000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                iAudioBuff * 8, AudioTrack.MODE_STREAM);
        mAudioTrack.play();

        final String strLanguage = this.mLanguage;
        final String strText = this.mTxt;
        String strUrl = mUrl + "?" + URLEncoder.encode(makeAuthorization());
        try
        {
            mWSClient = new WebSocketClient(new URI(strUrl), new Draft_17(),
                    new HashMap<String, String>(), 3000) {

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Thread sendThread = new Thread(new Runnable() {
                        @Override
                        public
                        void run() {
                            try {
                                mWSClient.send(BallerTTSWebSocketTest.makeFrame(strLanguage, strText));
                            } catch (Exception e) {
                                sendNetDisconnect();

                                try {
                                    closeBlocking();
                                } catch ( InterruptedException ex ) {
                                    ex.printStackTrace();
                                }
                            }
                            Log.e(mLogTag, "send data finish");
                        }
                    });
                    sendThread.start();
                }

                @Override
                public void onMessage(String message) {
                    JSONObject jsonResult;
                    byte[] audio = new byte[0];
                    try {
                        jsonResult = new JSONObject(message);
                    } catch (JSONException e) {
                        Log.i(mLogTag, e.toString());
                        return;
                    }

                    boolean isEnd = false;
                    String strData = "";
                    int error_code = 0 ;
                    try {
                        isEnd = 1 == jsonResult.getInt("is_end");
                        strData = jsonResult.getString("data");
                        error_code = jsonResult.getInt("code");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    if (error_code != 0)
                    {
                        Log.i(mLogTag, "tts failed: " + error_code);
                    } else {
                        audio =  Base64.decode(strData, Base64.NO_WRAP);
                    }

                    if (0 < audio.length)
                    {
                        mAudioTrack.write(audio, 0, audio.length);
                    }

                    if (isEnd) {
                        try {
                            mWSClient.close();
                        } catch (Exception e) {
                            Log.i(mLogTag, e.toString());
                        }
                        Log.e(mLogTag, "get result finish");
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    mWSClient = null;
                }

                @Override
                public void onError(Exception ex) {
                    Log.i(mLogTag, "on error");
                }
            };
        } catch (URISyntaxException e)
        {
            e.printStackTrace();
            return;
        }

        boolean bConnectSucc;
        try {
            bConnectSucc = mWSClient.connectBlocking();
        } catch (InterruptedException e) {
            bConnectSucc = false;
            e.printStackTrace();
        }

        if (!bConnectSucc) {
            sendNetDisconnect();
        }

        Log.e(mLogTag, "thread 1 leave");
    }
}

