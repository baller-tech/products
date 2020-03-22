package com.baller.sdk.test;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.baller.sdk.common.BallerCommon;
import com.baller.sdk.common.BallerErrorCode;
import com.baller.sdk.ocr.BallerOCR;
import com.baller.sdk.ocr.BallerOCRResult;

public class main_ocr {

	static {
		System.loadLibrary("baller_ocr");
		System.loadLibrary("baller_ocr_jni");
		System.loadLibrary("baller_common");
		System.loadLibrary("baller_common_jni");
	}

	// TODO: 请根据实际情况填写以下参数	
	private static long mOrgId = 0L;
	private static long mAppId = 0L;
	private static String mAppKey = "";
	private static String mImagePath = "./images/image01.bmp";

	// 读取图片
	@SuppressWarnings("resource")
	private static byte[] readImageFile(String strFile) {
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
			// 为了简化put/get接口出错时的处理，这里采用do...while的结构
			BallerOCR ocrSession = new BallerOCR();
			String strOcrVersion = ocrSession.version();
			System.out.println("ocr version: " + strOcrVersion);

			// 2. 调用sessionBegin接口，启动一个session
			String sessionParam = "res_dir=./data, mode=multi_row";
			iRet = ocrSession.sessionBegin(sessionParam);
			if (BallerErrorCode.BALLER_SUCCESS != iRet) {
				System.out.println("call sessionBegin faild(" + iRet + ") with param: " + sessionParam);
				break;
			}

			byte[] imageData = readImageFile(mImagePath);
			System.out.println(mImagePath + " size: " + imageData.length);

			do {
				// 为了简化put/get接口出错时的处理，这里采用do...while的结构
				// 3. 调用put接口，传递图像数据
				String putParam = "input_mode=once";
				iRet = ocrSession.put(putParam, imageData);
				if (BallerErrorCode.BALLER_SUCCESS != iRet) {
					System.out.println("call put faild(" + iRet + ") with param: " + putParam);
					break;
				}

				// 4. 调用get接口，获取识别结果
				while (true) {
					BallerOCRResult result = new BallerOCRResult();
					iRet = ocrSession.get(result);
					if (BallerErrorCode.BALLER_SUCCESS == iRet) {
						if (null != result.mResult) {
							System.out.println(new String(result.mResult));
						}

						if (result.mFinish) {
							break;
						} else {
							try {
								Thread.sleep(150);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
							continue;
						}
					} else {
						System.out.println("call get faild(" + iRet + ")");
						break;
					}
				}
			} while (false);

			// 5. 调用sessionEnd接口，结束一个session
			iRet = ocrSession.sessionEnd();
			if (BallerErrorCode.BALLER_SUCCESS != iRet) {
				System.out.println("call sessionEnd faild(" + iRet + ")");
				break;
			}

		} while (false);

		// 6. 调用logout接口
		iRet = BallerCommon.logout();
		if (BallerErrorCode.BALLER_SUCCESS != iRet) {
			System.out.println("logout failed " + iRet);
			return;
		}

	}
}
