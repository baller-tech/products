package com.baller;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

/**
 * 
 * 信息配置类
 */
class Common {
    // 地址配置
    public static String mUrl = "ws://api.baller-tech.com/v1/service/ws/v1/tts";
    public static String mHost = "api.baller-tech.com";

    // 账号信息 由北京市大牛儿科技发展有限公司统一分配
    public static long mOrgId = 0L;
    public static long mAppId = 0L;
    public static String mAppkey = "";

    // 语种
    // 请查考《语音识别（TTS）WebSocket协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
    public static String mLanguage = "";
    // 测试使用的文本文件
    public static String mTxtFile = "";
    // 合成的音频文件格式
    // 请查考《语音识别（TTS）WebSocket协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
    public static String mSampleFormat = "audio/L16;rate=16000";
	// 合成语速
	// 请查考《语音识别（TTS）WebSocket协议WebAPI开发文档.pdf》中“语速的取值范围”章节
	public static float mSpeed = 1.0f;
    // 合成的音频的压缩类型，
    // 请查考《语音识别（TTS）WebSocket协议WebAPI开发文档.pdf》中“支持的音频编码”章节
    public static String mAudioEncode = "raw";
    // 结果保存文件
    public static String mSaveResultFile = mTxtFile + "_out." + mAudioEncode;

    // 读取文件
    @SuppressWarnings("resource")
    public static byte[] readFile(String strFile) {
        byte[] fileData = null;

        BufferedInputStream inputStream;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(strFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return fileData;
        }

        try {
            fileData = new byte[inputStream.available()];
        } catch (IOException e) {
            e.printStackTrace();
            return fileData;
        }

        try {
            inputStream.read(fileData);
        } catch (IOException e) {
            e.printStackTrace();
            return fileData;
        }

        return fileData;
    }
}

/**
 * 
 * 发送数据的线程类
 */
class SendFrameThread extends Thread {
    private WebSocketClient mClient;
    private boolean mIsFirstFrame = true;

    SendFrameThread(WebSocketClient wsClient) {
        this.mClient = wsClient;
    }

    public String makeFrame(byte[] data) {
        JSONObject jsonParams = new JSONObject();
        if (mIsFirstFrame) {
            JSONObject jsonBusiness = new JSONObject();
            try {
                jsonBusiness.put("language", Common.mLanguage);
                jsonBusiness.put("sample_format", Common.mSampleFormat);
                jsonBusiness.put("audio_encode", Common.mAudioEncode);
				jsonBusiness.put("speed", Common.mSpeed);
                jsonParams.put("business", jsonBusiness);
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }

            mIsFirstFrame = false;
        }

        JSONObject jsonData = new JSONObject();
        try {
            jsonData.put("txt", Base64.getEncoder().encodeToString(data));
            jsonParams.put("data", jsonData);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return jsonParams.toString();
    }

    public void run() {
        // 读取测试文件
        byte[] imageData = Common.readFile(Common.mTxtFile);
        if (imageData == null || imageData.length == 0) {
            System.out.println("读取测试文件出错");
            return;
        }

        String strSendFrame = this.makeFrame(imageData);
        if (strSendFrame != null) {
            if (mClient.isClosed() || mClient.isClosing()) {
                return;
            }
            mClient.send(strSendFrame);
        } else {
            return;
        }
    }
}

public class cloud_websocket_tts_test {

    private static DataOutputStream mOutFile = null;

    private static String HMACSHA256AndBase64(byte[] data, byte[] key) {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return Base64.getEncoder().encodeToString(mac.doFinal(data));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String makeAuthorization() {
        Calendar cd = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String strRequestDateTime = sdf.format(cd.getTime());

        String strSignatureOrg = "app_id:" + Common.mAppId + "\n";
        strSignatureOrg += "date:" + strRequestDateTime + "\n";
        strSignatureOrg += "host:" + Common.mHost;
        String strSignatureFinal = HMACSHA256AndBase64(strSignatureOrg.getBytes(), Common.mAppkey.getBytes());

        JSONObject jsonAuthorization = new JSONObject();
        try {
            jsonAuthorization.put("app_id", Long.toString(Common.mAppId));
            jsonAuthorization.put("signature", strSignatureFinal);
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }

        String strAuthorizationBase64 = Base64.getEncoder().encodeToString(jsonAuthorization.toString().getBytes());
        String strAuthorizationFinal = "";
        try {
            strAuthorizationFinal = "authorization=" + URLEncoder.encode(strAuthorizationBase64, "UTF-8") + "&host="
                    + URLEncoder.encode(Common.mHost, "UTF-8") + "&date="
                    + URLEncoder.encode(strRequestDateTime, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return strAuthorizationFinal;
    }

    public static void main(String[] args) {
        // 检查运行条件
        if (Common.mAppId == 0L || Common.mAppkey == "") {
            System.out.println("请填写账号信息");
            return;
        }

        try {
            mOutFile = new DataOutputStream(new FileOutputStream(Common.mSaveResultFile, true));
        } catch (FileNotFoundException e1) {
            System.out.println("打开结果文件" + Common.mSaveResultFile + "失败");
            e1.printStackTrace();
            return;
        }

        // 开始连接
        WebSocketClient wsClient = null;
        try {
            String strUrl = Common.mUrl + "?" + makeAuthorization();
            wsClient = new WebSocketClient(new URI(strUrl), new Draft_6455()) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    // 启动数据发送线程
                    SendFrameThread sendThread = new SendFrameThread(this);
                    sendThread.start();
                }

                // 一次语音合成任务只需发送一次文本数据，但合成的结果会分多次返回。
                // 服务端会将传入的文本分为不同的子句，每次onMessage触发时，返回的是一个子句的合成结果，当收到一个子句的合成结果时，应用就可以开始播放，下一个子句的合成结果会在当前子句播放完成前返回。
                @Override
                public void onMessage(String message) {
                    JSONObject jsonObject = JSONObject.parseObject(message);
                    int iCode = jsonObject.getInteger("code");

                    if (0 == iCode) {
                        // 正常流程
                        boolean isEnd = jsonObject.getBoolean("is_end");
                        String strDataAfterBase64 = jsonObject.getString("data");
                        byte[] arData = Base64.getDecoder().decode(strDataAfterBase64);

                        try {
                            mOutFile.write(arData);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        if (isEnd) {
                            // 处理完毕主动关闭连接
                            this.close();
                            System.out.println("语音合成完毕");
                        }
                    } else {
                        // 出错流程
                        String strMessage = jsonObject.getString("message");
                        System.out.println("服务器报错 code: " + iCode + " message: " + strMessage);
                        this.close();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (reason.length() > 0) {
                        System.out.println("[websocket] 关闭(" + reason + ")");
                    }
                }

                @Override
                public void onError(Exception e) {
                    System.out.println("[websocket] 出错 ");
                    e.printStackTrace();
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        // 开始建立连接
        if (wsClient != null) {
            wsClient.connect();
        }
    }
}
