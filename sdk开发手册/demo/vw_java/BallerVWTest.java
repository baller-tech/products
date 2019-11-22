package com.baller.vw.test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import com.baller.common.*;
import com.baller.vw.*;

public class BallerVWTest {
	
	static {
		System.loadLibrary("libballer_common_jni_x64"); 
		System.loadLibrary("libballer_vw_jni_x64"); 
	}
	
	public static void test()
	{
		int iRet = BallerErrorCode.BALLER_SUCCESS;
		
		// login
		String longinParam = "org_id=1164931236239507464,app_id=1164931258798571529, app_key=9b5ace06b590541485645866789b623e,"
				+ "license=baller_sdk.license, log_level=debug, log_path=baller_log";
		iRet = BallerCommon.login(longinParam);
		System.out.printf("call BallerEarCommon.Login.%d\n", iRet);
		
		if (iRet == BallerErrorCode.BALLER_SUCCESS)
		{
			// session start
			BallerVW wakeInstance = new BallerVW();
			String session_param = "res_dir=data,wakeup_word=DA KAI DIAN SHI,engine_type=local,hardware=cpu_slow,vad=on,sample_rate=16,sample_size=16";
			iRet = wakeInstance.sessionBegin(session_param, new BallerVWProcess() {
				public void onWake(int wordIndex) {
					System.out.printf("wake up by %d\n", wordIndex);
				}
			});
			System.out.printf("call sessionBegin.%d\n", iRet);
			
			if (iRet == BallerErrorCode.BALLER_SUCCESS)
			{
				// read pcm
				byte bytePCM[] = null;
				int iPCMSize = 0;
				try
				{
					File f= new File("DaKaiDianShi.pcm");
					FileInputStream fis = new FileInputStream(f);
					DataInputStream dis = new DataInputStream(fis);
					
					iPCMSize = dis.available();
					bytePCM = new byte[iPCMSize];
					dis.read(bytePCM);
					dis.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				
				// test once
				String putParam = "input_mode=once";
				
				iRet = wakeInstance.put(putParam, bytePCM, iPCMSize);
				System.out.printf("call put.%d\n", iRet);
				System.out.printf("--------------------------end test once--------------------------\n");
				
				{
					// test continue
					int iInputIndex = 0;
					// Pass 200ms data at a time
					int iInputPerSize = 16 * 2 * 200;
					for (iInputIndex = 0; iPCMSize - iInputIndex > iInputPerSize; iInputIndex += iInputPerSize)
					{
						String putParamContinue = "input_mode=continue";
						byte[] tmp = new byte[iInputPerSize];
						System.arraycopy(bytePCM, iInputIndex, tmp, 0, iInputPerSize);
						iRet = wakeInstance.put(putParamContinue, tmp, iInputPerSize);
						System.out.printf("call put.%d\n", iRet);
					}
					
					int iLeftSize = iPCMSize - iInputIndex;
					String putParamEnd = "input_mode=end";
					byte[] tmp = new byte[iLeftSize];
					System.arraycopy(bytePCM, iInputIndex, tmp, 0, iLeftSize);
					iRet = wakeInstance.put(putParamEnd, tmp, iLeftSize);
					System.out.printf("call put.%d\n", iRet);
					System.out.printf("--------------------------end test continue--------------------------\n");
				}
				
				iRet = wakeInstance.sessionEnd();
				System.out.printf("call sessionEnd.%d\n", iRet);
			}
			
			iRet = BallerCommon.logout();
			System.out.printf("call BallerEarCommon.Logout.%d\n", iRet);
		}
	}
	
	public static void main(String[] args) {
		test();
	}
}
