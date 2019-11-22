package baller.com.ballersdktest;

import android.content.res.AssetManager;
import android.content.Context;
import android.util.Log;

import com.baller.common.*;
import com.baller.vw.*;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BallerVWTest {

    static
    {
        System.loadLibrary("baller_common_jni");
        System.loadLibrary("baller_vw_jni");
    }

    public static void testWake_ReadFromAssets(Context context, AssetManager assetManager)
    {
        final String strLogTag = "ReadFromAssets";

        // call login
        int iRet = BallerErrorCode.BALLER_SUCCESS;
        String loginParam = "org_id=xxxxx, app_id=xxxx, app_key=xxxxx, log_level=debug, log_path=/mnt/sdcard/baller/baller_log";
        iRet = BallerCommon.login(loginParam, "baller/baller_sdk.license", context, assetManager);

        Log.i(strLogTag, "call BallerCommon.login." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call BallerCommon.login." + iRet);
            return ;
        }

        // call sessioin begin
        BallerVW wakeInstance = new BallerVW();
        String strSessionBeginParam = "wakeup_word=DA KAI DIAN SHI,engine_type=local,hardware=cpu_slow,vad=on,simple_rate=16,sample_size=16";
        iRet = wakeInstance.sessionBegin(strSessionBeginParam, "baller/data", assetManager, new BallerVWProcess(){
            public void onWake(int wordIndex)
            {
                Log.i(strLogTag, "wake up word index." + wordIndex);
            }
        });

        Log.i(strLogTag, "call session begin." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call session begin." + iRet);
            BallerCommon.logout();
            return ;
        }

        // read pcm
        byte bytePCM[] = null;
        int iFileSize = 0;
        File f= new File("/mnt/sdcard/baller/DaKaiDianShi.pcm");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            DataInputStream dis = new DataInputStream(fis);

            iFileSize = dis.available();

            bytePCM = new byte[iFileSize];

            dis.read(bytePCM);
            dis.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        String strPutSession="input_mode=once";
        iRet = wakeInstance.put(strPutSession, bytePCM, iFileSize);

        Log.i(strLogTag, "call put." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call put." + iRet);

            wakeInstance.sessionEnd();
            BallerCommon.logout();
            return ;
        }

        // call session end
        iRet = wakeInstance.sessionEnd();
        Log.i(strLogTag, "call session end." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call call session end." + iRet);
        }

        // call logou
        iRet = BallerCommon.logout();
        Log.i(strLogTag, "call BallerCommon.logout." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call BallerCommon.logout." + iRet);
        }
    }

    public static void testWake_ReadFromExternalStorage(Context context)
    {
        final String strLogTag = "ReadFromExternalStorage";

        // call login
        int iRet = BallerErrorCode.BALLER_SUCCESS;
        String loginParam = "org_id=xxxxx, app_id=xxxx, app_key=xxxxx, log_level=debug, log_path=/mnt/sdcard/baller/baller_log";
        iRet = BallerCommon.login(loginParam, "/mnt/sdcard/baller/baller_sdk.license", context, null);

        Log.i(strLogTag, "call BallerCommon.login." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call BallerCommon.login." + iRet);
            return ;
        }

        // call session begin
        BallerVW wakeInstance = new BallerVW();
        String strSessionBeginParam = "wakeup_word=DA KAI DIAN SHI,engine_type=local,hardware=cpu_slow,vad=on,simple_rate=16,sample_size=16";
        iRet = wakeInstance.sessionBegin(strSessionBeginParam, "/mnt/sdcard/baller/data", null, new BallerVWProcess(){
            public void onWake(int wordIndex)
            {
                Log.i(strLogTag, "wake up word index." + wordIndex);
            }
        });

        Log.i(strLogTag, "call sessionBegin." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            BallerCommon.logout();

            Log.e(strLogTag, "call sessionBegin." + iRet);
            return ;
        }

        // read pcm
        byte bytePCM[] = null;
        int iFileSize = 0;
        File f= new File("/mnt/sdcard/baller/DaKaiDianShi.pcm");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            DataInputStream dis = new DataInputStream(fis);

            iFileSize = dis.available();

            bytePCM = new byte[iFileSize];

            int iReadSize = dis.read(bytePCM);
            dis.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        // call put
        String strPutSession="input_mode=once";
        iRet = wakeInstance.put(strPutSession, bytePCM, iFileSize);

        Log.i(strLogTag, "call put." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call put." + iRet);

            wakeInstance.sessionEnd();
            BallerCommon.logout();
            return ;
        }

        // call session end
        iRet = wakeInstance.sessionEnd();
        Log.i(strLogTag, "call sessionEnd." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call sessionEnd." + iRet);

            BallerCommon.logout();
            return ;
        }

        // call logot
        iRet = BallerCommon.logout();
        Log.i(strLogTag, "call BallerCommon.logout." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call BallerCommon.logout." + iRet);
        }
    }

    public static void testWake_PutContinue(Context context, AssetManager assetManager)
    {
        final String strLogTag = "PutContinue";

        // call login
        int iRet = BallerErrorCode.BALLER_SUCCESS;
        String loginParam = "org_id=xxxxx, app_id=xxxx, app_key=xxxxx, log_level=debug, log_path=/mnt/sdcard/baller/baller_log";
        iRet = BallerCommon.login(loginParam, "baller/baller_sdk.license", context, assetManager);

        Log.i(strLogTag, "call BallerCommon.login." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call BallerCommon.login." + iRet);
            return ;
        }

        // call sessioin begin
        BallerVW wakeInstance = new BallerVW();
        String strSessionBeginParam = "wakeup_word=DA KAI DIAN SHI,engine_type=local,hardware=cpu_slow,vad=on,simple_rate=16,sample_size=16";
        iRet = wakeInstance.sessionBegin(strSessionBeginParam, "baller/data", assetManager, new BallerVWProcess(){
            public void onWake(int wordIndex)
            {
                Log.i(strLogTag, "wake up word index." + wordIndex);
            }
        });

        Log.i(strLogTag, "call session begin." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call session begin." + iRet);
            BallerCommon.logout();
            return ;
        }

        // read pcm
        byte bytePCM[] = null;
        int iFileSize = 0;
        File f= new File("/mnt/sdcard/baller/DaKaiDianShi.pcm");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            DataInputStream dis = new DataInputStream(fis);

            iFileSize = dis.available();

            bytePCM = new byte[iFileSize];

            dis.read(bytePCM);
            dis.close();
        } catch ( IOException e ) {
            e.printStackTrace();
        }

        // Every time put 200ms data
        int iPutPerSize = 16 * 2 * 200;
        int iPutIndex = 0;
        for (iPutIndex = 0; iFileSize - iPutIndex > iPutPerSize; iPutIndex += iPutPerSize)
        {
            String strPutSession="input_mode=continue";
            byte [] tmp = new byte[iPutPerSize];
            System.arraycopy(bytePCM, iPutIndex, tmp, 0, iPutPerSize);
            iRet = wakeInstance.put(strPutSession, tmp, iPutPerSize);

            Log.i(strLogTag, "call put." + iRet);
            if (BallerErrorCode.BALLER_SUCCESS != iRet)
            {
                Log.e(strLogTag, "call put." + iRet);

                wakeInstance.sessionEnd();
                BallerCommon.logout();
                return ;
            }
        }

        int iLeftSize = iFileSize - iPutIndex;
        String strPutSessionEnd="input_mode=end";
        byte[] tmpEnd = new byte[iLeftSize];
        System.arraycopy(bytePCM, iPutIndex, tmpEnd, 0, iLeftSize);
        iRet = wakeInstance.put(strPutSessionEnd, tmpEnd, iLeftSize);

        Log.i(strLogTag, "call put." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call put." + iRet);

            wakeInstance.sessionEnd();
            BallerCommon.logout();
            return ;
        }

        // call session end
        iRet = wakeInstance.sessionEnd();
        Log.i(strLogTag, "call session end." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call call session end." + iRet);
        }

        // call logou
        iRet = BallerCommon.logout();
        Log.i(strLogTag, "call BallerCommon.logout." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet)
        {
            Log.e(strLogTag, "call BallerCommon.logout." + iRet);
        }
    }

}
