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

public class cloud_http_nmt_test {

  // 请求地址
  public static String mUrl = "http://api.baller-tech.com/v1/service/v1/mt";

  // 账号信息 由北京市大牛儿科技发展有限公司统一分配;
  public static long mAppId = 0L;
  public static String mAppkey = "";

  // 测试使用的文件
  public static String mTxtFile = "";
  // 结果保存文件
  public static String mSaveResultFile = mTxtFile + "_out.txt";

  // 测试语种
  // 请查考《机器翻译（NMT）HTTP协议WebAPI开发文档.pdf》中“支持的语种”章节
  public static String mLanguage = "uig-chs";
  // 是否将结果保存到文件
  public static boolean mSaveToFile = true;

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
    String businessParamsBase64 = 
        Base64.getEncoder().encodeToString(JSON.toJSONString(businessParams).getBytes());

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
  public static boolean getResult(String requestId, FileOutputStream outFile) {

    Map<String, String> paramMap = new HashMap<>();
    paramMap.put("request_id", requestId);
    String businessParamBase64 = Base64.getEncoder().encodeToString(
        JSON.toJSONString(paramMap).getBytes());

    String requestTime = getGmtTime();
    String checkSum = mAppkey + requestTime + businessParamBase64;
    String md5 = DigestUtils.md5DigestAsHex(checkSum.getBytes());

    MultiValueMap headerMap = new LinkedMultiValueMap<String, String>();
    headerMap.set("B-AppId", String.valueOf(mAppId));
    headerMap.set("B-CurTime", requestTime);
    headerMap.set("B-Param", businessParamBase64);     
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

    String dataArray = jsonObject.getString("data");
    if (dataArray != null && !dataArray.equals("")) {
      System.out.println(dataArray);
      if (outFile != null) {
        try {
          outFile.write(dataArray.getBytes());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return jsonObject.getInteger("is_end") == 1;
  }

  /**
   * main once 方式发送.
   */
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

    boolean putSuccess = postData(requestId, readFile(mTxtFile));
    if (!putSuccess) {
      System.out.println(requestId + " POST data failed");
      return;
    }

    // 获取结果
    while (!getResult(requestId, fileOut)) {
      try {
    	  // 停留40ms的作用：
    	  // 1. 避免线程一直被占用，导致CPU利用率高。40ms为经验值，使用时可根据实际的需求调大或调小。
    	  Thread.sleep(40);
      } catch (InterruptedException e) {
        e.printStackTrace();
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