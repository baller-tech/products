package com.baller.test;

import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class BallerBase extends Thread {
    AtomicBoolean finish = new AtomicBoolean(false);
    static long mAppId = 1187565976207491106L;
    static String mAppkey = "165a8fdeb9ba57c3bc81547a275c6552";
    Handler mHandler = null;

    static String getGmtTime() {
        SimpleDateFormat sdf3 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf3.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf3.format(new Date());
    }

    void setEnd() {
        finish.set(true);
    }

    void setmHandler(Handler handler) {
        mHandler = handler;
    }

    void sendResult(String reuslt) {
        Message msg = Message.obtain();
        msg.what = 1;
        msg.obj = reuslt;
        mHandler.sendMessage(msg);
    }

    private void saveFile(byte[] audio) {
        getFile(audio, "/sdcard/baller/", "tmp.pcm");

    }

    private void getFile(byte[] bfile, String filePath, String fileName) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.mkdirs();
            }
            File tempWav = new File(file, "temp.pcm");
            if (!tempWav.exists())
                tempWav.createNewFile();
            FileOutputStream fos = new FileOutputStream(tempWav, true);
            if (0 != bfile[0])
                fos.write(bfile);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static String MD5(String sourceStr) {
        try {
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            mdInst.update(sourceStr.getBytes());
            byte[] md = mdInst.digest();
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < md.length; i++) {
                int tmp = md[i];
                if (tmp < 0)
                    tmp += 256;
                if (tmp < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(tmp));
            }
            return buf.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
