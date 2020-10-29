package com.baller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class cloud_http_asr_test {

  //地址配置
  public static String mUrl = "http://api.baller-tech.com/v1/service/v1/asr";

  // 账号信息 由北京市大牛儿科技发展有限公司统一分配;
  public static long mAppId = 0L;
  public static String mAppkey = "";
  
  // 测试语种
  // 请查考《语音识别（ASR）HTTP协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
  public static String mLanguage = "tib_ad";
  // 测试使用的文件
  public static String mTxtFile = "";
  // 音频文件格式
  // 请查考《语音识别（ASR）HTTP协议WebAPI开发文档.pdf》中“支持的音频格式”章节
  public static String mAudioFormat = "raw";
  // 音频采样率
  // 请查考《语音识别（ASR）HTTP协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
  public static int mSampleRate = 16000;
  public static String mSampleFormat = "audio/L16;rate=" + String.valueOf(mSampleRate);
  // 结果保存文件
  public static String mSaveResultFile = mTxtFile + "_out.txt";
  // 服务类型
  // sentence: 整句识别 结果实时返回 每个任务限制时长
  // realtime: 实时识别 结果实时返回 每个任务无时长限制
  public static String mServiceType = "sentence";

  // 推送结果的地址，该地址为调用者自己搭建的接收推送结果的Web服务地址
  public static String mCallbackUrl = "";
  // 结果是否保存到文件中
  public static boolean mSaveToFile = true;
  // 是否显示子句的位移信息
  public static boolean mShowOffset = false;

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
  public static boolean postData(String requestId, String inputMode, byte[] testData) {

    Map<String, String> businessParams = new HashMap<>();
    businessParams.put("request_id", requestId);
    businessParams.put("language", mLanguage);
    businessParams.put("sample_format", mSampleFormat);
    businessParams.put("audio_format", mAudioFormat);
    businessParams.put("service_type", mServiceType);
    businessParams.put("input_mode", inputMode);
    businessParams.put("vad", "on");
    if (!mCallbackUrl.isEmpty()) {
      businessParams.put("callback_url", mCallbackUrl);
    }
    String businessParamsBase64 = Base64.getEncoder().encodeToString(
        JSON.toJSONString(businessParams).getBytes());

    String requestTime = getGmtTime();
    String checkSum = mAppkey + requestTime + businessParamsBase64;
    String md5 = DigestUtils.md5DigestAsHex(checkSum.getBytes());

    MultiValueMap headers = new LinkedMultiValueMap<String, String>();
    headers.set("Content-Type", "application/octet-stream");
    headers.set("B-AppId", String.valueOf(mAppId));
    headers.set("B-CurTime", requestTime);
    headers.set("B-Param", businessParamsBase64);     
    headers.set("B-CheckSum", md5);

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
  public static boolean getResult(String requestId, FileOutputStream outFile) {

    Map<String, String> paramMap = new HashMap<>();
    paramMap.put("request_id", requestId);
    String businessParamBase64 = Base64.getEncoder().encodeToString(
        JSON.toJSONString(paramMap).getBytes());

    String requestTime = getGmtTime();
    String checkSum = mAppkey + requestTime + businessParamBase64;
    String md5 = DigestUtils.md5DigestAsHex(checkSum.getBytes());

    MultiValueMap headerMap = new LinkedMultiValueMap<String, String>();
    headerMap.add("B-AppId", String.valueOf(mAppId));
    headerMap.add("B-CurTime", requestTime);
    headerMap.add("B-Param", businessParamBase64);
    headerMap.set("B-CheckSum", md5);
      
    HttpEntity<String> param = new HttpEntity(headerMap);
    RestTemplate restTemplate = new RestTemplate();
    ResponseEntity<String> exchange = restTemplate.exchange(
        mUrl, HttpMethod.GET, param, String.class);
    JSONObject jsonObject = JSONObject.parseObject(exchange.getBody());

    if (jsonObject.getInteger("code") != 0) {
      System.out.println(requestId + " GET failed. " + jsonObject.getInteger("code") + " " 
          +  jsonObject.get("message"));
      return true;
    }
    
    // 语音识别时，会将传入的语音根据一定的规则分为不同的子句，每次GET请求返回的一个子句的识别结果
    // 一个子句的识别结果有两种状态完整的识别结果(is_complete等于1)和不完整的识别结果(is_complete等于0)；
    // 不完整的识别结果表示本次获取的识别结果并不是该子句的最终结果，下一次GET请求获取的识别结果还是该子句的；
    // 完整的识别结果表示本次获取的结果是该子句的最终结果，下一次GET请求获取的结果是下一个子句的结果；
    // 大部分的使用场景下我们只需要关心每个子句的完整识别结果即可；
    // 在一些实时的ASR识别系统中，为了让用户更早的看到识别结果，可以将子句的非最终结果上屏，并不断更新显示该子句的识别结果，
    // 当获取到该子句的完整识别结果时，在将完整的子句识别结果上屏，这样用户体验会更好。

    String dataArray = jsonObject.getString("data");
    int isComplete = jsonObject.getInteger("is_complete");
    if (dataArray != null && !dataArray.equals("") && isComplete == 1) {
      System.out.println(dataArray);
      if (outFile != null) {
        try {
          outFile.write(dataArray.getBytes());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (mShowOffset) {
        int begin = jsonObject.getInteger("begin");
        int end = jsonObject.getInteger("end");
        System.out.println("begin = " + String.valueOf(begin) + " ms end = " + String.valueOf(end) + " ms");
      }
    }
    return jsonObject.getInteger("is_end") == 1;
  }

  public static void testOnce() {

    String requestId = UUID.randomUUID().toString();

    FileOutputStream fileOut = null;
    if (mSaveToFile) {
      try {
        File f = new File(mSaveResultFile);
        fileOut = new FileOutputStream(f);
      } catch (FileNotFoundException e1) {
        e1.printStackTrace();
      }
    }

    // 读取并发送音频数据
    boolean putSuccess = postData(requestId, "once", readFile(mTxtFile));
    if (!putSuccess) {
      System.out.println(requestId + " POST data failed");
      return;
    }

    // 获取识别结果
    if (mCallbackUrl.isEmpty()) {
      while (!getResult(requestId, fileOut)) {
        try {
          // 停留40ms的作用：
          // 1. 避免线程一直被占用，导致CPU利用率高。40ms为经验值，使用时可根据实际的需求调大或调小。
          Thread.sleep(40);
        } catch (InterruptedException e) {
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
    System.out.println(requestId + " once GET result finished");
  }   

  @SuppressWarnings("static-access")
  public static void testContinue() {

    String requestId = UUID.randomUUID().toString();
    // 读取音频数据
    byte[] testData = readFile(mTxtFile);

    if (testData.length == 0) {
      System.out.println("read audio file " + mTxtFile + " failed");
      return;
    }
    
    FileOutputStream fileOut = null;
    if (mSaveToFile) {
      try {
        File f = new File(mSaveResultFile);
        fileOut = new FileOutputStream(f);
      } catch (FileNotFoundException e1) {
        e1.printStackTrace();
      }
    }

    // 发送音频数据
    int perSize = 32 * 200;
    int sendSize = 0;
    boolean putSuccess = true;
    while (testData.length - sendSize > perSize) {
      putSuccess = postData(requestId, "continue", Arrays.copyOfRange(testData, sendSize, sendSize + perSize));
      sendSize += perSize;
      if (!putSuccess) {
        System.out.println(requestId + " POST data failed");
        return;
      }

      // 获取一次音频数据
      boolean isEnd = getResult(requestId, fileOut);
      if (isEnd) {
        System.out.println(requestId + " GET data finished");
        return;
      }

      try {
    	  // 停留40ms的作用：
    	  // 1. 避免将音频数据瞬时全部发送到服务器而导致服务器任务缓存区满返回51024错误码的情况。
    	  Thread.sleep(40);
      } catch (InterruptedException e) {
        e.printStackTrace();
        return;
      }
    }

    if (testData.length - sendSize  > 0) {
      putSuccess = postData(requestId, "end", Arrays.copyOfRange(testData, sendSize, testData.length));
      if (!putSuccess) {
        System.out.println(requestId + " POST data finished");
        return;
      }
    }

    if (mCallbackUrl.isEmpty()) {
      while (!getResult(requestId, fileOut)) {
        try {
          // 停留40ms的作用：
          // 1. 避免线程一直被占用，导致CPU利用率高。40ms为经验值，使用时可根据实际的需求调大或调小。
          Thread.sleep(40);
        } catch (InterruptedException e) {
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
    System.out.println(requestId + " continue GET result finished ");
  }

  public static void main(String[] args) {
    testOnce();
    testContinue();
  }
}
