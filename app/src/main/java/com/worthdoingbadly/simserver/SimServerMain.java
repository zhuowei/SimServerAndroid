package com.worthdoingbadly.simserver;

import android.telephony.TelephonyManager;

public class SimServerMain {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello world!");

        TelephonyManager telephonyManager = (TelephonyManager)
                TelephonyManager.class.getMethod("getDefault")
                        .invoke(null);
        //String authVal = telephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
        //        TelephonyManager.AUTHTYPE_EAP_AKA, "lololol");
        //System.out.println(authVal);
        String response = telephonyManager.iccTransmitApduLogicalChannel(0, 1, 2, 3, 4, 5, "6");
        System.out.println(response);
    }
}
