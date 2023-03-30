package com.baller.test;

import android.util.Base64;
import android.util.Log;

import java.util.UUID;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.alibaba.fastjson.JSONObject;


public class BallerMTHTTPTest extends BallerBase {
    private static String mLogTag = "BallerMTHTTP";
    private static String mUrl = "http://" + mHost + "/v1/service/v1/mt";
    private String mLanguage;

    private String mTxt;

    BallerMTHTTPTest(String strLanguage, String strTxt) {
        this.mLanguage = strLanguage;
        this.mTxt = strTxt;
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
                JSONObject responseContent = JSONObject.parseObject(content);
                int error_code = responseContent.getIntValue("code");
                String message = responseContent.getString("message");
                if (error_code != 0)
                {
                    Log.i(mLogTag, "mt failed: " + error_code + " " +message);
                }
                inStream.close();
            } else {
                Log.i(mLogTag, "mt post failed");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean getResult(String requestId) {
        JSONObject paramMap = new JSONObject();
        try {
            paramMap.put("request_id", requestId);
        }catch (Exception e) {
            Log.i(mLogTag, "mt get failed. " + e.toString());
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

            boolean isEnd;
            int  status_code = connection.getResponseCode();

            if (status_code == 200) {
                InputStream inStream = connection.getInputStream();
                byte[] bt = readStream(inStream);

                String content = new String(bt, StandardCharsets.UTF_8);
                JSONObject responseContent = JSONObject.parseObject(content);
                isEnd = 1 == responseContent.getIntValue("is_end");
                String strData = responseContent.getString("data");
                int error_code = responseContent.getIntValue("code");
                String message = responseContent.getString("message");
                if (error_code != 0)
                {
                    Log.i(mLogTag, "mt failed: " + error_code + " " +message);
                } else if (!strData.isEmpty()) {
                    Log.i(mLogTag, "mt result: " + strData);
                    sendResult(strData);
                }
                return isEnd;
            } else {
                Log.i(mLogTag, "mt get failed");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void run() {
        String requestId = UUID.randomUUID().toString();

        try {
            boolean putSuccess = postData(requestId, mTxt.getBytes(StandardCharsets.UTF_8));
            if (!putSuccess) {
                Log.e(mLogTag, "post failed");
                return;
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
