package com.baller.sdk.test;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.baller.sdk.common.BallerCommon;
import com.baller.sdk.common.BallerErrorCode;
import com.baller.sdk.vw.BallerVW;
import com.baller.sdk.vw.BallerVWProcess;

public class main_vw {

	static {
		System.loadLibrary("baller_vw");
		System.loadLibrary("baller_vw_jni");
		System.loadLibrary("baller_common");
		System.loadLibrary("baller_common_jni");
	}

	private static long mOrgId = 0L;
	private static long mAppId = 0L;
	private static String mAppKey = "";
	private static String mPCMFile = "./pcms/0001.pcm";

	// 读取图片
	@SuppressWarnings("resource")
	private static byte[] readFile(String strFile) {
		byte[] image = null;

		BufferedInputStream inputStream;
		try {
			inputStream = new BufferedInputStream(new FileInputStream(strFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return image;
		}

		try {
			image = new byte[inputStream.available()];
		} catch (IOException e) {
			e.printStackTrace();
			return image;
		}

		try {
			inputStream.read(image);
		} catch (IOException e) {
			e.printStackTrace();
			return image;
		}

		return image;
	}

	public static void main(String[] args) {
		// 1. 调用login接口
		String loginParam = "org_id=" + mOrgId + ",app_id=" + mAppId + ",app_key=" + mAppKey;
		loginParam += ",license=./baller_license/baller_sdk.license";
		loginParam += ",log_level=warning, log_path=./baller/baller_log";
		int iRet = BallerCommon.login(loginParam);
		if (BallerErrorCode.BALLER_SUCCESS != iRet) {
			System.out.println("login failed " + iRet);
			return;
		}

		do {
			// 为了简化put/接口出错时的处理，这里采用do...while的结构
			BallerVW vwSession = new BallerVW();
			String strOcrVersion = vwSession.version();
			System.out.println("ocr version: " + strOcrVersion);

			// 2. 调用sessionBegin接口，启动一个session
			String sessionParam = "res_dir=./data,wakeup_word=DA KAI DIAN SHI";
			iRet = vwSession.sessionBegin(sessionParam, new BallerVWProcess() {
				public void onWake(int index) {
					System.out.println("wake up by " + index);
				}
			});
			if (BallerErrorCode.BALLER_SUCCESS != iRet) {
				System.out.println("call sessionBegin faild(" + iRet + ") with param: " + sessionParam);
				break;
			}

			byte[] pcmData = readFile(mPCMFile);
			System.out.println(mPCMFile + " size: " + pcmData.length);

			do {
				// 为了简化put接口出错时的处理，这里采用do...while的结构
				// 3.1. 使用once的方式调用put接口，传递音频数据
				String putParam = "input_mode=once";
				iRet = vwSession.put(putParam, pcmData);
				if (BallerErrorCode.BALLER_SUCCESS != iRet) {
					System.out.println("call put faild(" + iRet + ") with param: " + putParam);
					break;
				}

				// 3.2. 使用continue的方式调用put接口，传递音频数据
				int iInputPerSize = 16 * 2 * 200;
				int iInputIndex = 0;
				for (iInputIndex = 0; pcmData.length - iInputIndex > iInputPerSize && BallerErrorCode.BALLER_SUCCESS == iRet; iInputIndex += iInputPerSize)
				{
					putParam = "input_mode=continue";
					byte[] tmp = new byte[iInputPerSize];
					System.arraycopy(pcmData, iInputIndex, tmp, 0, iInputPerSize);
					
					iRet = vwSession.put(putParam, tmp);
					if (BallerErrorCode.BALLER_SUCCESS != iRet) {
						System.out.println("call put faild(" + iRet + ") with param: " + putParam);
						break;
					}					
				}
				
				if (BallerErrorCode.BALLER_SUCCESS == iRet && pcmData.length - iInputIndex > 0)
				{
					putParam = "input_mode=end";
					byte[] tmp = new byte[iInputPerSize];
					System.arraycopy(pcmData, iInputIndex, tmp, 0, pcmData.length - iInputIndex);
					
					iRet = vwSession.put(putParam, tmp);
					if (BallerErrorCode.BALLER_SUCCESS != iRet) {
						System.out.println("call put faild(" + iRet + ") with param: " + putParam);
						break;
					}					
				}

			} while (false);

			// 4. 调用sessionEnd接口，结束一个session
			iRet = vwSession.sessionEnd();
			if (BallerErrorCode.BALLER_SUCCESS != iRet) {
				System.out.println("call sessionEnd faild(" + iRet + ")");
				break;
			}

		} while (false);

		// 5. 调用logout接口
		iRet = BallerCommon.logout();
		if (BallerErrorCode.BALLER_SUCCESS != iRet) {
			System.out.println("logout failed " + iRet);
			return;
		}

	}
}
