package com.baller.demo.asr_tts_websocket;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.security.MessageDigest;
import java.io.InputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.json.JSONObject;
import org.apache.http.HttpEntity;

// 添加依赖：org.jbundle.util.osgi.wrapped:org.jbundle.util.osgi.wrapped.org.apache.http.client:4.1.2
// 添加依赖：commons-logging:commons-logging:1.2

public class BallerTTSHTTPTest extends Thread {
    private static String mLogTag = "BallerTTSHTTPTest";
    private static String mUrl = "http://api.baller-tech.com/v1/service/v1/tts";
    private static long mAppId = 0L;
    private static String mAppkey = "";

    private String mLanguage = "";
    private String mTxt = "";
    private AudioTrack mAudioTrack = null;

    private int iAudioBuff = AudioTrack.getMinBufferSize(16000,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

    BallerTTSHTTPTest(String strLanguage, String strTxt) {
        this.mLanguage = strLanguage;
        this.mTxt = strTxt;
    }

    private static String getGmtTime() {
        SimpleDateFormat sdf3 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf3.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf3.format(new Date());
    }

    private static String MD5(String sourceStr) {
        try {
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            mdInst.update(sourceStr.getBytes());
            byte[] md = mdInst.digest();
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < md.length; i++) {
                int tmp = md[i];
                if (tmp < 0)
                    tmp += 256;
                if (tmp < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(tmp));
            }
            return buf.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean postData(String requestId, byte[] testData) {
        JSONObject businessParams = new JSONObject();
        try {
            businessParams.put("request_id", requestId);
            businessParams.put("language", mLanguage);
            businessParams.put("sample_format", "audio/L16;rate=16000");
            businessParams.put("audio_encode", "raw");
            businessParams.put("speed", 1.0f);
        } catch (Exception e) {
            e.printStackTrace();
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
            if (httpResp.getStatusLine().getStatusCode() == 200) {
                String result = EntityUtils.toString(httpResp.getEntity(), "UTF-8");
                JSONObject responseContent = new JSONObject(result);
                int error_code = responseContent.getInt("code");
                if (error_code != 0)
                {
                    Log.i(mLogTag, "mt failed: " + error_code);
                }
            } else {
                Log.i("HttpPost", "HttpPost方式请求失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private  boolean getResult(String requestId) {
        JSONObject paramMap = new JSONObject();
        try {
            paramMap.put("request_id", requestId);
        }catch (Exception e) {
            e.printStackTrace();
            return false;
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

        boolean isEnd;
        InputStream stream;
        int error_code;

        httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
        httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 30000);
        try {
            HttpResponse httpResp = httpClient.execute(httpGet);
            if (httpResp.getStatusLine().getStatusCode() == 200) {
                error_code = Integer.parseInt(httpResp.getFirstHeader("B-Code").getValue());
                if (error_code != 0)
                {
                    String message = httpResp.getFirstHeader("B-Message").getValue();
                    Log.i(mLogTag, "tts failed: " + error_code +  " " + message);
                } else {
                    HttpEntity entity = httpResp.getEntity();
                    byte[] audio = EntityUtils.toByteArray(entity);
                    if (audio.length > 0) {
                        mAudioTrack.write(audio, 0, audio.length);
                    }
                }
                isEnd = Boolean.parseBoolean(httpResp.getFirstHeader("B-Is-End").getValue());
                return isEnd;
            } else {
                Log.i("HttpPost", "HttpPost方式请求失败");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }
    @Override
    public void run() {
        String requestId = UUID.randomUUID().toString();
        Log.e(mLogTag, requestId);
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, iAudioBuff,
                AudioTrack.MODE_STREAM);
        mAudioTrack.play();

        try {
            boolean putSuccess = postData(requestId, mTxt.getBytes("utf-8"));
            if (!putSuccess) {
                System.out.println(requestId + " POST data failed");
                return;
            }

            // 获取结果
            while (!isInterrupted()) {
                if(getResult(requestId)) {
                    break;
                }
                try {
                    Thread.sleep(40);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
