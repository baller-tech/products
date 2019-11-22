package baller.com.ballersdktest;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.baller.common.*;
import com.baller.vw.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class BallerVWThread extends Thread {

    static {
        System.loadLibrary("baller_common_jni");
        System.loadLibrary("baller_vw_jni");
    }

    private String mStrLogTag;
    AssetManager mAssetManager;
    Context mContext;

    private BallerVW wakeInstance;
    private String mStrLicenseFilePath;
    private String mStrResDir;
    private String mStrLogDir;
    private String mStrWakupWord;

    private boolean mWritePCM;
    private List<byte[]> mlsPCMData;
    ReentrantLock mPCMDataLock;


    public
    BallerVWThread(Context context, AssetManager mAssetManager) {
        this.mStrLogTag = "BallerVWThread";

        this.mAssetManager = mAssetManager;
        this.mContext = context;
        this.mPCMDataLock = new ReentrantLock();
        this.mlsPCMData = new LinkedList <byte[]>();
        this.mWritePCM = false;

        this.mStrWakupWord = "DA KAI SHE BEI";
        this.mStrLicenseFilePath = "baller/baller_sdk.license";
        this.mStrResDir = "baller/data";
        this.mStrLogDir = "/mnt/sdcard/baller/baller_log";
    }

    public boolean startSession()
    {
        String loginParam = "org_id=xxxxx, app_id=xxxx, app_key=xxxxx, log_level=debug,log_path=" + mStrLogDir;
        int iRet = BallerCommon.login(loginParam, mStrLicenseFilePath, mContext, mAssetManager);
        Log.i(mStrLogTag, "call BallerCommon.login." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet) {
            Log.e(mStrLogTag, "call BallerCommon.login." + iRet);
            return false;
        }

        wakeInstance = new BallerVW();
        String strSessionBeginParam = "wakeup_word=" + mStrWakupWord + ",engine_type=local,hardware=cpu_slow,vad=on,simple_rate=16,sample_size=16";
        iRet = wakeInstance.sessionBegin(strSessionBeginParam, mStrResDir, mAssetManager, new BallerVWProcess() {
            public void onWake(int wordIndex) {
                Log.e(mStrLogTag, "wake up word index." + wordIndex);
            }
        });
        if (BallerErrorCode.BALLER_SUCCESS != iRet) {
            Log.e(mStrLogTag, "call sessionBegin." + iRet);
            return false;
        }

        return true;
    }

    public boolean endSession()
    {
        int iRet = wakeInstance.sessionEnd();
        Log.i(mStrLogTag, "call session end." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet) {
            Log.e(mStrLogTag, "call call session end." + iRet);
            return false;
        }

        iRet = BallerCommon.logout();
        Log.i(mStrLogTag, "call BallerCommon.logout." + iRet);
        if (BallerErrorCode.BALLER_SUCCESS != iRet) {
            Log.e(mStrLogTag, "call BallerCommon.logout." + iRet);
            return false;
        }

        return true;
    }

    public void putPCMData(byte[]data, int dataLen) {
        if (data != null && dataLen > 0)
        {
            byte[] copyData = new byte[dataLen];
            System.arraycopy(data, 0, copyData, 0, dataLen);

            mPCMDataLock.lock();
            mlsPCMData.add(copyData);
            mPCMDataLock.unlock();
        }
    }

    public void run() {

        FileOutputStream outputStream = null;
        if (this.mWritePCM)
        {
            String tmpName = "Baller+" + System.currentTimeMillis() + "_2";
            final File tmpFile = createFile(tmpName + ".pcm");
            try {
                outputStream = new FileOutputStream(tmpFile.getAbsoluteFile());
            } catch ( FileNotFoundException e ) {
                e.printStackTrace();
            }
        }

        while (true) {
            byte[] data = null;

            mPCMDataLock.lock();
            if (mlsPCMData.size() == 0) {
                mPCMDataLock.unlock();
                continue;
            }
            data = mlsPCMData.get(0);
            mlsPCMData.remove(0);
            mPCMDataLock.unlock();

            if (data != null) {

                if (outputStream != null && this.mWritePCM)
                {
                    try {
                        outputStream.write(data);
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }

                String strPutSession = "input_mode=continue";
                int iRet = wakeInstance.put(strPutSession, data, data.length);
                Log.i(mStrLogTag, "call put." + iRet);

                if (iRet != BallerErrorCode.BALLER_SUCCESS)
                {
                    Log.e(mStrLogTag, "call put." + iRet);
                    break;
                }
            }
        }
    }

    private
    File createFile(String name) {
        String dirPath = "/mnt/sdcard/baller/";
        File file = new File(dirPath);

        if (!file.exists()) {
            file.mkdirs();
        }

        String filePath = dirPath + name;
        File objFile = new File(filePath);
        if (!objFile.exists()) {
            try {
                objFile.createNewFile();
                return objFile;
            } catch ( IOException e ) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
