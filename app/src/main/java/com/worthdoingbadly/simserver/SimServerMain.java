package com.worthdoingbadly.simserver;

import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class SimServerMain {
    public static TelephonyManager telephonyManager;
    // no error checking is done. Good luck!

    // Yes I know there's a nice telephonyManager method to get this for us
    // This is useful to make sure TelephonyManager methods work...
    public static String getIccId(int logicalChannel) {
        String response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel,0xa0, 0xa4, 0x00, 0x00, 0x02, "3F00");
        System.out.println(response);
        response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xa4, 0x00, 0x00, 0x02, "2FE2");
        System.out.println(response);
        response =  telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xb0, 0x00, 0x00, 0x0a, "");
        System.out.println(response);
        StringBuilder iccId = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            iccId.append(response.charAt(i*2+1)).append(response.charAt(i*2));
        }
        System.out.println(iccId);
        return iccId.toString();
    }
    public static String getAuthentication(int logicalChannel, String simAppletAid, String rand, String autn) {
        // https://github.com/fasferraz/USIM-https-server/blob/main/usim_https_server.p
        System.out.println("HEY this doesn't work Android blocks it!!! TODO(zhuowei) fix");
        System.out.println("Using SIM applet " + simAppletAid);
        /*
        String response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel,0xa0, 0xa4, 0x00, 0x00, 0x02, "3F00");
        System.out.println(response);
        response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xa4, 0x00, 0x00, 0x02, "2F00");
        System.out.println(response);
        */
        // stupid
        //telephonyManager.iccCloseLogicalChannel(logicalChannel);
        //IccOpenLogicalChannelResponse openLogicalChannelResponse = telephonyManager.iccOpenLogicalChannel(simAppletAid);
        //logicalChannel = openLogicalChannelResponse.getChannel();
        //System.out.println("reopened channel! " + logicalChannel);
        // select sim applet
        // SELECT_FILE
        String response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xa4, 0x04, 0x00, simAppletAid.length() / 2, simAppletAid.toUpperCase());
        System.out.println(response);
        if (response.equals("0000")) {
            System.out.println("failed to select the sim applet");
            return "nope";
        }
        response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0x88, 0x00, 0x81, 0x22, 0x10, rand.toUpperCase() + "10" + autn.toUpperCase());
        System.out.println(response);
        // response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xc0, 0x00, 0x00, 0x00, resultLen, "");
        // TODO(zhuowei): dump response
        return "nope";
    }
    public static List<String> getAllEfDirApplets(int logicalChannel) {
        String response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel,0xa0, 0xa4, 0x00, 0x00, 0x02, "3F00");
        System.out.println(response);
        // 2F00 = EF DIR
        // SELECT_FILE
        response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xa4, 0x00, 0x00, 0x02, "2F00");
        System.out.println(response);
        // https://arfan.wordpress.com/2015/08/13/list-of-sw1-sw2-in-smart-card/
        // https://github.com/osmocom/pysim/blob/c8387dc03126302414079c3906a5b58845a19f13/pySim/cards.py#L264
        // https://cardwerk.com/smart-card-standard-iso7816-4-section-6-basic-interindustry-commands/
        // https://neapay.com/post/read-smart-card-chip-data-with-apdu-commands-iso-7816_76.html
        // https://github.com/mitshell/card/blob/790a8fb0b8fbc0b06f568feba51aeef28352145e/card/ICC.py#L1659
        int expectedResponseLength = Integer.parseInt(response.substring(2), 16);
        // GET_RESPONSE
        response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xc0, 0x00, 0x00, expectedResponseLength, "");
        System.out.println(response);
        // https://github.com/mitshell/card/blob/790a8fb0b8fbc0b06f568feba51aeef28352145e/card/SIM.py#L282
        int recordSize = Integer.parseInt(response.substring(14 * 2, 15*2), 16);
        int fileSize = Integer.parseInt(response.substring(2*2, 4*2), 16);
        System.out.println("Record size = " + recordSize + " file size = " + fileSize);
        List<String> retval = new ArrayList<>();
        for (int appletIndex = 0; appletIndex < fileSize / recordSize; appletIndex++) {
            // READ_RECORD
            response = telephonyManager.iccTransmitApduLogicalChannel(logicalChannel, 0xa0, 0xb2, appletIndex, 0x4, recordSize, "");
            System.out.println("Applet #" + appletIndex + ":" + response);
            retval.add(response);
        }
        return retval;
    }
    public static String getSimAppletAid(int logicalChannel) {
        List<String> applets = getAllEfDirApplets(logicalChannel);
        // we try to grab the ISIM ("a0000000871004") since we're going for VoWifi/VoLTE
        // if we want USIM ("a0000000871002") for non-VoWifi, just switch it here
        String iSimPrefix = "a0000000871004";
        for (String applet: applets) {
            if (applet.length() != 80) {
                continue;
            }
            String aid = applet.substring(8, 8 + 32);
            if (aid.startsWith(iSimPrefix)) {
                return aid;
            }
        }
        // give up
        System.out.println("can't find a sim applet?");
        return "";
    }
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
    private static void unusedStuff(String[] args) {
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
        try {
            if (args.length == 0 || args[0].equals("iccid")) {
                getIccId(logicalChannel);
            } else if (args[0].equals("rand-autn")) {
                String simAppletAid = args.length == 4? args[3]: getSimAppletAid(logicalChannel);
                getAuthentication(logicalChannel, simAppletAid, args[1], args[2]);
            } else if (args[0].equals("applets")) {
                getAllEfDirApplets(logicalChannel);
            } else if (args[0].equals("imsi")) {
                System.out.println(telephonyManager.getSubscriberId());
            } else if (args[0].equals("auth")) {
                telephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_ISIM, TelephonyManager.AUTHTYPE_EAP_AKA, "lol");
            }
        } finally {
            telephonyManager.iccCloseLogicalChannel(logicalChannel);
        }
    }
}
