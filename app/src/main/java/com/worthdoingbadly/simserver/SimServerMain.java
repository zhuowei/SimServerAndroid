package com.worthdoingbadly.simserver;

import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;

public class SimServerMain {
    public static void main(String[] args) throws Exception {
        System.out.println("Hello world!");

        TelephonyManager telephonyManager = (TelephonyManager)
                TelephonyManager.class.getMethod("getDefault")
                        .invoke(null);
        IccOpenLogicalChannelResponse iccOpenLogicalChannelResponse =
                telephonyManager.iccOpenLogicalChannel("");
        // TODO(zhuowei): error handling: app shutting down doesn't clean up channel
        final int logicalChannel = iccOpenLogicalChannelResponse.getChannel();
        System.out.println("Opened logical channel: " + logicalChannel);
        //String authVal = telephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
        //        TelephonyManager.AUTHTYPE_EAP_AKA, "lololol");
        //System.out.println(authVal);
        //String response = telephonyManager.iccTransmitApduLogicalChannel(0, 1, 2, 3, 4, 5, "6");
        // https://www.mutek.com/apdus-to-get-sim-card-iccid/
        // https://cs.android.com/android/platform/superproject/+/master:cts/tests/tests/carrierapi/src/android/carrierapi/cts/CarrierApiTest.java;l=824;drc=3d8adba77f5c6c4796c5a085de19b274823c01b5
        String response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel,0xa0, 0xa4, 0x00, 0x00, 0x02, "3F00");
        System.out.println(response);
        response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xa4, 0x00, 0x00, 0x02, "2FE2");
        System.out.println(response);
        response =  telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xb0, 0x00, 0x00, 0x0a, "");
        System.out.println(response);
        telephonyManager.iccCloseLogicalChannel(logicalChannel);
        StringBuilder iccId = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            iccId.append(response.charAt(i*2+1)).append(response.charAt(i*2));
        }
        System.out.println(iccId);
    }
}
