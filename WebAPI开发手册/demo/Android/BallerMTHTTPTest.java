package com.baller.demo.asr_tts_websocket;

import android.util.Base64;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.security.MessageDigest;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.json.JSONObject;

// 添加依赖：org.jbundle.util.osgi.wrapped:org.jbundle.util.osgi.wrapped.org.apache.http.client:4.1.2
// 添加依赖：commons-logging:commons-logging:1.2


public class BallerMTHTTPTest extends Thread {
    private static String mLogTag = "BallerMTHTTP";
    private static String mUrl = "http://api.baller-tech.com/v1/service/v1/mt";
    private static long mAppId = 0L;
    private static String mAppkey = "";

    private String mLanguage;
    private String mTxt;

    BallerMTHTTPTest(String strLanguage, String strTxt) {
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

        //连接超时
        int error_code = 0;
        httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
        //请求超时
        httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 30000);
        try {
            // 获取HttpResponse实例
            HttpResponse httpResp = httpClient.execute(httpPost);
            // 判断是够请求成功
            if (httpResp.getStatusLine().getStatusCode() == 200) {
                // 获取返回的数据
                String result = EntityUtils.toString(httpResp.getEntity(), "UTF-8");
                if (!result.isEmpty()) {
                    JSONObject responseContent = new JSONObject(result);
                    error_code = responseContent.getInt("code");
                }

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

    private static boolean getResult(String requestId) {
        JSONObject paramMap = new JSONObject();
        try {
            paramMap.put("request_id", requestId);
        }catch (Exception e) {
            Log.i(mLogTag, "mt get failed. " + e.toString());
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

        httpClient.getParams().setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000);
        httpClient.getParams().setParameter(CoreConnectionPNames.SO_TIMEOUT, 30000);
        try {
            HttpResponse httpResp = httpClient.execute(httpGet);
            if (httpResp.getStatusLine().getStatusCode() == 200) {
                String result = EntityUtils.toString(httpResp.getEntity(), "UTF-8");
                JSONObject jsonResult = new JSONObject(result);
                isEnd = 1 == jsonResult.getInt("is_end");
                String strData = jsonResult.getString("data");
                int error_code = jsonResult.getInt("code");

                if (error_code != 0)
                {
                    Log.i(mLogTag, "mt failed: " + error_code);
                } else if (!strData.isEmpty()) {
                    Log.i(mLogTag, "mt result: " + strData);
                }
                return isEnd;
            } else {
                Log.i("HttpPost", "HttpPost方式请求失败");
            }
        } catch (Exception e) {
            return true;
        }
        return false;
    }
    @Override
    public void run() {
        String requestId = UUID.randomUUID().toString();

        try {
            boolean putSuccess = postData(requestId, mTxt.getBytes("utf-8"));
            if (!putSuccess) {
                Log.e(mLogTag, "post failed");
                return;
            }

            while (!isInterrupted()) {
                if (getResult(requestId)) {
                    break;
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
