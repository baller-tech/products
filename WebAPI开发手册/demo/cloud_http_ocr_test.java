package com.baller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

public class cloud_http_ocr_test {
  
  //地址配置
  public static String mUrl = "http://api.baller-tech.com/v1/service/v1/ocr";

  // 账号信息 由北京市大牛儿科技发展有限公司统一分配;
  public static long mAppId = 0L;
  public static String mAppkey = "";

  // 测试语种
  // 请查考《图像识别（OCR）HTTP协议WebAPI开发文档.pdf》中“支持的语种”章节
  public static String mLanguage = "chs";
  // 测试使用的文件
  public static String mTestFile = "";
  // 请查考《图像识别（OCR）HTTP协议WebAPI开发文档.pdf》中“PDF识别注意事项”章节
  public static String mFileFormat = "";
  public static String mImageMode = "multi_row";
  // 结果保存文件
  public static String mSaveResultFile = mTestFile + "_out.txt";
  // 推送结果的地址，该地址为调用者自己搭建的接收推送结果的Web服务地址
  public static String mCallbackUrl = "";
  // 结果是否保存到文件中
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
    businessParams.put("image_mode", mImageMode);
    businessParams.put("file_format", mFileFormat);
    businessParams.put("input_mode", "once");
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

    JSONArray dataArray = jsonObject.getJSONArray("data");
    if (!dataArray.isEmpty()) {
      ArrayList<String> dataList = new ArrayList<String>();

      for (int i = 0; i < dataArray.size(); i++) {
        JSONObject dataJson = dataArray.getJSONObject(i);
        if (!dataJson.getString("result").isEmpty()) {
          dataList.add(dataJson.getIntValue("order"), dataJson.getString("result"));
        }
      }

      if (!dataList.isEmpty()) {
        for (int j = 0; j < dataList.size(); j++) {
          System.out.println(dataList.get(j));
          if (outFile != null) {
            try {
              outFile.write(dataList.get(j).getBytes());
              String changeLine = "\n";
              outFile.write(changeLine.getBytes());
            } catch (IOException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
          }
        }
      }
    }
    return jsonObject.getInteger("is_end") == 1;
  }

  /**
   * once 方式发送.
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

    // 读取并发送图像数据
    boolean putSuccess = postData(requestId, readFile(mTestFile));
    if (!putSuccess) {
      System.out.println(requestId + " POST data failed");
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