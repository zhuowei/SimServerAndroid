package com.worthdoingbadly.simserver;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.telephony.TelephonyManager;

import java.util.ArrayList;
import java.util.List;

public class SimServerMain {
    public static TelephonyManager telephonyManager;
    public static void main(String[] args) throws Exception {
        if (Build.VERSION.SDK_INT >= 30 /* Android 11 */) {
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            activityThreadClass.getMethod("initializeMainlineModules").invoke(null);
            Looper.prepareMainLooper();
            Object mainActivityThread = activityThreadClass.getMethod("systemMain").invoke(null);
            Context context = (Context) activityThreadClass.getMethod("getSystemContext").invoke(mainActivityThread);
            telephonyManager = context.getSystemService(TelephonyManager.class);
        } else {
            // Android 8.1
            telephonyManager = (TelephonyManager)
                    TelephonyManager.class.getMethod("getDefault")
                            .invoke(null);
        }
        if (args.length == 0) {
            System.out.println("Usage:\n" +
                    "imsi: prints imsi\n" +
                    "auth <base64-encoded data>: authenticates with EAP_AKA on ISIM\n" +
                    "serve <port>: serves web server");
            return;
        }
        if (args[0].equals("imsi")) {
            System.out.println(telephonyManager.getSubscriberId());
        } else if (args[0].equals("auth")) {
            String response = telephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_ISIM, TelephonyManager.AUTHTYPE_EAP_AKA, args[1]);
            System.out.println(response);
        } else if (args[0].equals("serve")) {
            // TODO(zhuowei)
            System.out.println("todo");
        } else {
            System.out.println("Unknown command");
        }
    }
}
