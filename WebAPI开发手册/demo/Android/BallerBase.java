package com.baller.test;

import android.os.Handler;
import android.os.Message;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

class BallerBase extends Thread {
    AtomicBoolean finish = new AtomicBoolean(false);
    static String mHost = "api.baller-tech.com";
    static long mAppId = 0L;
    static String mAppkey = "";
    private Handler mHandler = null;

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

    static byte[] readStream(InputStream inStream) throws Exception{
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while((len = inStream.read(buffer)) != -1)
        {
            outStream.write(buffer,0,len);
        }
        inStream.close();
        return outStream.toByteArray();
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
