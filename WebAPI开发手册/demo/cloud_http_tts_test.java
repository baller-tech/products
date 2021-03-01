package com.baller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;


public class cloud_http_tts_test {
  //地址配置
  public static String mUrl = "http://api.baller-tech.com/v1/service/v1/tts";

  // 账号信息 由北京市大牛儿科技发展有限公司统一分配;
  public static long mAppId = 0L;
  public static String mAppkey = "";

  // 语种
  // 请查考《语音识别（TTS）HTTP协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
  public static String mLanguage = "tib_ad";
  // 音频文件
  public static String mTxtFile = "";
  // 结果保存文件
  public static String mSaveResultFile = mTxtFile + "_out.pcm";
  // 合成的音频文件格式
  // 请查考《语音识别（TTS）HTTP协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
  public static String mSampleFormat = "audio/L16;rate=16000";
  // 推送结果的地址，该地址为调用者自己搭建的接收推送结果的Web服务地址
  public static String mCallbackUrl = "";

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
 
  public static String getGmtTime() {
    SimpleDateFormat sdf3 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    sdf3.setTimeZone(TimeZone.getTimeZone("GMT"));
    return sdf3.format(new Date());
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static boolean postData(String requestId, byte[] testData) {

    Map<String, String> businessParams = new HashMap<>();
    businessParams.put("request_id", requestId);
    businessParams.put("language", mLanguage);
    businessParams.put("sample_format", mSampleFormat);
    if (!mCallbackUrl.isEmpty()) {
      businessParams.put("callback_url", mCallbackUrl);
    }
    String businessParamsBase64 = Base64.getEncoder().encodeToString(
        JSON.toJSONString(businessParams).getBytes());

    String requestTime = getGmtTime();
    String checkSum = mAppkey + requestTime + businessParamsBase64;
    String md5 = DigestUtils.md5DigestAsHex(checkSum.getBytes());

    MultiValueMap headers = new LinkedMultiValueMap<String, String>();
    headers.set("B-AppId", String.valueOf(mAppId));
    headers.set("B-CurTime", requestTime);
    headers.set("B-Param", businessParamsBase64);     
    headers.set("B-CheckSum", md5);
    headers.set("Content-Type", "application/octet-stream");

    HttpEntity<String> param = new HttpEntity(testData, headers);
    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<String> response = restTemplate.exchange(
        mUrl, HttpMethod.POST, param, String.class);
    JSONObject responseContent = JSONObject.parseObject(response.getBody());

    if (responseContent.getInteger("code") != 0) {
      System.out.println(requestId + " POST failed. " + responseContent.getInteger("code") + " " 
          +  responseContent.get("message"));
      return false;
    }
    return true;
  }

  @SuppressWarnings({ "rawtypes", "unchecked"})
  public static boolean getResult(String requestId, DataOutputStream fout) {

    Map<String, String> paramMap = new HashMap<>();
    paramMap.put("request_id", requestId);
    String businessParamsBase64 = Base64.getEncoder().encodeToString(
        JSON.toJSONString(paramMap).getBytes());

    String requestTime = getGmtTime();
    String checkSum = mAppkey + requestTime + businessParamsBase64;
    String md5 = DigestUtils.md5DigestAsHex(checkSum.getBytes());

    MultiValueMap headerMap = new LinkedMultiValueMap<String, String>();
    headerMap.set("B-AppId", String.valueOf(mAppId));
    headerMap.set("B-CurTime", requestTime);
    headerMap.set("B-Param", businessParamsBase64);     
    headerMap.set("B-CheckSum", md5);

    HttpEntity<String> param = new HttpEntity(headerMap);
    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<Resource> exchange = restTemplate.exchange(
        mUrl, HttpMethod.GET, param, Resource.class);

    MultiValueMap<String, String> headers = exchange.getHeaders();
    List<String> codes = headers.get("B-Code");
    int code = Integer.valueOf(codes.get(0)).intValue();
    List<String> messages = headers.get("B-Message");
    String message = messages.get(0);

    if (code != 0) {
      System.out.println(requestId + " Get failed. " + String.valueOf(code) + " " +  message);
      return true;
    }

    InputStream stream = null;
    try {
      if (exchange.getBody() != null) {
        stream = exchange.getBody().getInputStream();
      }
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    try {
      if (stream != null && stream.available() > 0) {
        try {
          byte[] byt = new byte[stream.available()];
          stream.read(byt);
          fout.write(byt);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    List<String> isEnds = headers.get("B-Is-End");
    int isEnd = Integer.valueOf(isEnds.get(0)).intValue();
    return isEnd == 1;
  }

  public static void testOnce() {
    String requestId = UUID.randomUUID().toString();

    DataOutputStream fileOut = null;
    try {
      fileOut = new DataOutputStream(new FileOutputStream(mSaveResultFile, true));
    } catch (FileNotFoundException e1) {
      e1.printStackTrace();
    }

    // 读取并发送数据
    // 一次语音合成任务需调用一次POST接口，和多次GET接口。
    // 调用POST接口将文本数据发送给服务器，服务端会将传入的文本分为不同的子句，GET接口的响应报文中为一个子句的合成结果，当收到一个子句的合成结果时，应用就可以开始播放，下一个子句的合成结果会在当前子句播放完成前返回。
    boolean putSuccess = postData(requestId, readFile(mTxtFile));
    if (!putSuccess) {
      return;
    }

    if (mCallbackUrl.isEmpty()) {
      // 获取识别结果
      while (!getResult(requestId, fileOut)) {
        try {
          // 停留40ms的作用：
          // 1. 避免线程一直被占用，导致CPU利用率高。40ms为经验值，使用时可根据实际的需求调大或调小。
          Thread.sleep(40);
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }

    if (fileOut != null) {
      try {
        fileOut.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    System.out.println(requestId + " GET result finished");
  }   

  public static void main(String[] args) {
    testOnce();
  }
}
