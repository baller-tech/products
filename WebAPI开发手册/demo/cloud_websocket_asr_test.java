package com.baller;

import java.io.BufferedInputStream;
import java.io.File;
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


public class cloud_websocket_asr_test {
	// 地址配置
	public static String mUrl = "ws://api.baller-tech.com/v1/service/ws/v1/asr";
	public static String mHost = "api.baller-tech.com";

	// 账号信息 由北京市大牛儿科技发展有限公司统一分配
    public static long mOrgId = 0L;
    public static long mAppId = 0L;
    public static String mAppkey = "";

	// 测试使用的语种
	// 请查考《语音识别（ASR）WebSocket协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
	public static String mLanguage = "zho";
	// 测试使用的音频文件
	public static String mAudioFile = "zho_16000_16.wav";
	// 测试使用的音频格式
	// 请查考《语音识别（ASR）WebSocket协议WebAPI开发文档.pdf》中“支持的音频格式”章节
	public static String mAudioFormat = "wav";

	// 音频采样率
	public static int mSampleRate = 16000;
	// 请查考《语音识别（ASR）HTTP协议WebAPI开发文档.pdf》中“支持的语种以及采样格式”章节
	public static String mSampleFormat = "audio/L16;rate=" + String.valueOf(mSampleRate);
	// 结果保存文件
	public static String mSaveResultFile = mAudioFile + "_out.txt";
	// 结果是否保存到文件中
	public static boolean mSaveToFile = false;
	// 测试使用的服务模式
	// sentence: 句子模式（默认值，任务有时长限制）
	// realtime: 实时模式（任务无时长限制）
	public static String mServiceType = "sentence";
	// 显示子句的位移信息
	public static boolean mShowOffset = false;

	// 读取测试数据
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
	
	
	/**
	 * 
	 * 发送数据的线程类
	 */
	public static class ASRSendFrameThread extends Thread {
		private WebSocketClient mClient;
		private boolean mIsFirstFrame = true;

		ASRSendFrameThread(WebSocketClient wsClient) {
			this.mClient = wsClient;
		}

		public String makeFrame(byte[] data, String inputMode) {
			JSONObject jsonParams = new JSONObject();
			if (mIsFirstFrame) {
				JSONObject jsonBusiness = new JSONObject();
				try {
					jsonBusiness.put("language", mLanguage);
					jsonBusiness.put("sample_format", mSampleFormat);
					jsonBusiness.put("audio_format", mAudioFormat);
					jsonBusiness.put("service_type", mServiceType);
					jsonBusiness.put("vad", "on");
					jsonBusiness.put("dynamic_correction", "off");
					jsonParams.put("business", jsonBusiness);
				} catch (JSONException e) {
					e.printStackTrace();
					return null;
				}

				mIsFirstFrame = false;
			}

			JSONObject jsonData = new JSONObject();
			try {
				jsonData.put("input_mode", inputMode);
				jsonData.put("audio", Base64.getEncoder().encodeToString(data));
				jsonParams.put("data", jsonData);
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}

			return jsonParams.toString();
		}

		public void run() {
			// 读取测试文件
			byte[] pcmData = readFile(mAudioFile);
			if (pcmData == null || pcmData.length == 0) {
				System.out.println("读取音频文件出错");
				return;
			}

			// 每一帧发送400ms的音频数据
			int iInputPerSize = 400 * 16 * 2;
			int iInputIndex = 0;
			for (iInputIndex = 0; pcmData.length - iInputIndex > iInputPerSize; iInputIndex += iInputPerSize) {
				byte[] audio = new byte[iInputPerSize];
				System.arraycopy(pcmData, iInputIndex, audio, 0, iInputPerSize);
				String strSendFrame = this.makeFrame(audio, "continue");
				if (strSendFrame != null) {
					if (mClient.isClosed() || mClient.isClosing()) {
						return;
					}
					mClient.send(strSendFrame);
				} else {
					return;
				}

				// 停留40ms的作用：
				// 1. 模拟人说话时间间隙。
				// 2. 避免将音频数据瞬时全部发送到服务器而导致服务器任务缓存区满返回51024错误码的情况。
				try {
					Thread.sleep(40);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// 发送最后一帧
			if (pcmData.length - iInputIndex > 0) {
				byte[] audio = new byte[iInputPerSize];
				System.arraycopy(pcmData, iInputIndex, audio, 0, pcmData.length - iInputIndex);
				String strSendFrame = this.makeFrame(audio, "end");
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
	}


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

		String strAuthorizationBase64 = Base64.getEncoder().encodeToString(jsonAuthorization.toString().getBytes());
		String strAuthorizationFinal = "";
		try {
			strAuthorizationFinal = "authorization=" + URLEncoder.encode(strAuthorizationBase64, "UTF-8") + "&host="
					+ URLEncoder.encode(mHost, "UTF-8") + "&date="
					+ URLEncoder.encode(strRequestDateTime, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return strAuthorizationFinal;
	}

	public static void TestContinue() {
	    FileOutputStream [] fileOut = new FileOutputStream [1];
	    if (mSaveToFile) {
	      try {
	        File f = new File(mSaveResultFile);
	        fileOut[0] = new FileOutputStream(f);
	      } catch (FileNotFoundException e1) {
	        e1.printStackTrace();
	      }
	    }
	    
		// 开始连接
		WebSocketClient wsClient = null;
		try {
			String strUrl = mUrl + "?" + makeAuthorization();
			wsClient = new WebSocketClient(new URI(strUrl), new Draft_6455()) {
				@Override
				public void onOpen(ServerHandshake handshakedata) {
					// 启动数据发送线程
					ASRSendFrameThread sendThread = new ASRSendFrameThread(this);
					sendThread.start();
				}

				@Override
				public void onMessage(String message) {
					JSONObject jsonObject = JSONObject.parseObject(message);
					int iCode = jsonObject.getInteger("code");
					
					if (jsonObject.containsKey("task_id")) {
						System.out.println("task id  " + jsonObject.getString("task_id"));
					}

					if (0 == iCode) {
						// 正常流程
						String strText = jsonObject.getString("data");
						boolean isComplete = jsonObject.getBoolean("is_complete");
						boolean isEnd = jsonObject.getBoolean("is_end");

						// 语音识别时，会将传入的语音根据一定的规则分为不同的子句，每次GET请求返回的一个子句的识别结果
						// 一个子句的识别结果有两种状态完整的识别结果(is_complete等于1)和不完整的识别结果(is_complete等于0)；
						// 不完整的识别结果表示本次获取的识别结果并不是该子句的最终结果，下一次GET请求获取的识别结果还是该子句的；
						// 完整的识别结果表示本次获取的结果是该子句的最终结果，下一次GET请求获取的结果是下一个子句的结果；
						// 大部分的使用场景下我们只需要关心每个子句的完整识别结果即可；
						// 在一些实时的ASR识别系统中，为了让用户更早的看到识别结果，可以将子句的非最终结果上屏，并不断更新显示该子句的识别结果，
						// 当获取到该子句的完整识别结果时，在将完整的子句识别结果上屏，这样用户体验会更好。

						if (isComplete) {
							System.out.println(strText);
							if (mShowOffset)
							{
								System.out.println("begin = " + jsonObject.getInteger("begin") + " ms end = " + jsonObject.getInteger("end") + " ms");
							}
							
						    if (fileOut[0] != null) {
						      try {
						    	  fileOut[0].write(strText.getBytes());
						      } catch (IOException e) {
						        e.printStackTrace();
						      }
						   }
						}

						if (isEnd) {
							System.out.println("task finish");
							// 处理完毕主动关闭连接
							this.close();
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
		
		try {
			wsClient.closeBlocking();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	    if (fileOut[0] != null) {
	        try {
	        	fileOut[0].close();
	        } catch (IOException e) {
	          e.printStackTrace();
	        }
	     }
	}
	
	public static void main(String[] args) {
		// 检查运行条件
		if (mAppId == 0L || mAppkey == "") {
			System.out.println("请填写账号信息");
			return;
		}
		
		TestContinue();
	}
}
