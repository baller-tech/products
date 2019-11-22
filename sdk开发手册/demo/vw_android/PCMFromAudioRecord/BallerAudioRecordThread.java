package baller.com.ballersdktest;

import android.content.res.AssetManager;
import android.util.Log;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.content.Context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileOutputStream;

public class BallerAudioRecordThread extends Thread {

    private boolean mWritePCM;
    private String mLogTag;
    private BallerVWThread mWakeThread;

    public BallerAudioRecordThread(Context context, AssetManager assetManager) {

        this.mLogTag = "BallerAudioRecordThread";

        this.mWakeThread = new BallerVWThread(context, assetManager);

        if (this.mWakeThread.startSession())
        {
            this.mWakeThread.start();
        }
        else
        {
            Log.e(mLogTag, "mWakeThread.startSession failed");
        }

        this.mWritePCM = false;
    }

    @Override
    public void run() {

        int recordBufSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);  //audioRecord能接受的最小的buffer大小
        AudioRecord mWakeRecorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, recordBufSize);
        byte[] data = new byte[recordBufSize];

        FileOutputStream outputStream = null;
        if (mWritePCM)
        {
            String tmpName = "Baller+" + System.currentTimeMillis() + "_1";
            final File tmpFile = createFile(tmpName + ".pcm");
            try {
                outputStream = new FileOutputStream(tmpFile.getAbsoluteFile());
            } catch ( FileNotFoundException e ) {
                e.printStackTrace();
            }
        }

        mWakeRecorder.startRecording();
        Log.i(this.mLogTag, "Audio Record Is Working...");

        while (true) {
            int iReadSize = mWakeRecorder.read(data, 0, data.length);
            if (iReadSize < 0) {
                break;
            }

            if (iReadSize > 0)
            {
                if (outputStream != null && mWritePCM)
                {
                    // write pcm data to sdcard
                    try {
                        outputStream.write(data);
                    } catch ( IOException e ) {
                        e.printStackTrace();
                    }
                }

                // pass pcm data to wake thread
                mWakeThread.putPCMData(data, iReadSize);
            }
        }

        mWakeThread.endSession();
    }

    private File createFile(String name) {
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