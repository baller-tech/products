package com.baller.test;

import android.app.Activity;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import java.util.ArrayList;
import android.Manifest;
import android.util.Log;

/**
 * 权限请求
 * Created by dway on 2018/1/10.
 */

class BallerPermission {

    private Activity mActivity;


    BallerPermission(@NonNull Activity activity) {
        mActivity = activity;
    }


    // 检查申请的权限是否全部授权
    boolean requestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean isAllGranted = true;//是否全部权限已授权
        if(requestCode == getPermissionsRequestCode()){
            for(int result : grantResults){
                if(result == PackageManager.PERMISSION_DENIED){
                    isAllGranted = false;
                    break;
                }
            }
            if(isAllGranted){
                //已全部授权
                return true;
            }else{
                //权限有缺失
                return false;
            }
        } else {
            Log.i("permission", "requestCode: " + String.valueOf(requestCode));
            return false;
        }
    }

    private int getPermissionsRequestCode() {
        //设置权限请求requestCode，只有不跟onRequestPermissionsResult方法中的其他请求码冲突即可。
        return 10000;
    }

    private String[] getPermissions() {
        //设置该界面所需的全部权限
        return new String[]{
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,

        };
    }

    /**
     * 获取缺失的权限
     * @param context
     * @param permissions
     * @return 返回缺少的权限，null 意味着没有缺少权限
     */
    private static String[] getDeniedPermissions(Context context, String[] permissions){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> deniedPermissionList = new ArrayList<>();
            for(String permission : permissions){
                if(context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED){
                    deniedPermissionList.add(permission);
                }
            }
            int size = deniedPermissionList.size();
            if(size > 0){
                return deniedPermissionList.toArray(new String[deniedPermissionList.size()]);
            }
        }
        return null;
    }

    /**
     * 弹出对话框请求权限
     * @param activity
     * @param permissions
     * @param requestCode
     */
    private static void requestPermissions(Activity activity, String[] permissions, int requestCode){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(permissions, requestCode);
        }
    }

    /**
     * 开始请求缺少的权限。
     */
    int checkPermissions(){
        String[] deniedPermissions = getDeniedPermissions(mActivity, getPermissions());
        if(deniedPermissions != null && deniedPermissions.length > 0){
            requestPermissions(mActivity, deniedPermissions, getPermissionsRequestCode());
            return 3;
        } else {
            return 1;
        }
    }
}