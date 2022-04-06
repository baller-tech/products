package com.baller.test;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import java.util.UUID;

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

public class BallerTTSHTTPTest extends BallerBase {
    private static String mLogTag = "BallerTTSHTTPTest";
    private static String mUrl = "http://api.baller-tech.com/v1/service/v1/tts";

    private String mLanguage = "";
    private String mTxt = "";
    private AudioTrack mAudioTrack = null;

    private int iAudioBuff = AudioTrack.getMinBufferSize(16000,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

    BallerTTSHTTPTest(String strLanguage, String strTxt) {
        this.mLanguage = strLanguage;
        this.mTxt = strTxt;
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
                    Log.i(mLogTag, "tts failed: " + error_code);
                    return false;
                }
            } else {
                Log.i(mLogTag, "tts post failed");
                return false;
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

        boolean isEnd;
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
                    return true;
                } else {
                    HttpEntity entity = httpResp.getEntity();
                    byte[] audio = EntityUtils.toByteArray(entity);
                    if (audio.length > 0) {
                        mAudioTrack.write(audio, 0, audio.length);
                    }
                }
                isEnd =  1== Integer.parseInt(httpResp.getFirstHeader("B-Is-End").getValue());
                return isEnd;
            } else {
                Log.i(mLogTag, "tts get failed");
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
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, iAudioBuff * 4,
                AudioTrack.MODE_STREAM);
        mAudioTrack.play();

        try {
            boolean putSuccess = postData(requestId, mTxt.getBytes("utf-8"));
            if (!putSuccess) {
                Log.e(mLogTag, "post data failed");
                return;
            }
            Log.e(mLogTag, "post data finish");

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
            Log.e(mLogTag, "get result finish");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.e(mLogTag, "thread 1 leave");
    }
}
