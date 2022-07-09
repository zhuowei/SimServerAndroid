package com.worthdoingbadly.simserver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Base64;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class SimServerMain {
    public static TelephonyManager telephonyManager;
    public static void main(String[] args) throws Exception {
        if (Build.VERSION.SDK_INT >= 30 /* Android 11 */) {
            // https://cs.android.com/android/platform/superproject/+/master:frameworks/base/cmds/telecom/src/com/android/commands/telecom/Telecom.java;l=48;drc=4b033d73165f96900b13d8a91536a7f7268a2b39
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            activityThreadClass.getMethod("initializeMainlineModules").invoke(null);
            Looper.prepareMainLooper();
            Object mainActivityThread = activityThreadClass.getMethod("systemMain").invoke(null);
            Context context = (Context) activityThreadClass.getMethod("getSystemContext").invoke(mainActivityThread);
            if (Build.VERSION.SDK_INT >= 31 /* Android 12 */) {
                // Android 12 needs context.getOpPackageName() to match our user's package
                // there's probably a proper way to do this (ActivityThread.getPackageInfo/LoadedApk.makeApplication) but oh well
                @SuppressLint("SoonBlockedPrivateApi")
                Field opPackageNameField = Class.forName("android.app.ContextImpl").getDeclaredField("mOpPackageName");
                opPackageNameField.setAccessible(true);
                opPackageNameField.set(context, "com.android.shell");
                context = context.createPackageContext("com.android.shell", 0);
            }
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
            new SimHTTPD(Integer.parseInt(args[1])).start();
            Looper.loop();
        } else {
            System.out.println("Unknown command");
        }
    }
    public static String first(List<String> l) {
        if (l == null || l.size() == 0) return null;
        return l.get(0);
    }

    // ripped from AOSP
    // returns a byte array where the first element is the length and the rest is bytes decoded from
    // hex data from the string
    private static byte[] parseHex(String hex) {
        /* This only works for good input; don't throw bad data at it */
        if (hex == null) {
            return new byte[0];
        }

        if (hex.length() % 2 != 0) {
            throw new NumberFormatException(hex + " is not a valid hex string");
        }

        byte[] result = new byte[(hex.length()) / 2 + 1];
        result[0] = (byte) ((hex.length()) / 2);
        for (int i = 0, j = 1; i < hex.length(); i += 2, j++) {
            byte b = (byte)(Integer.parseInt(hex.substring(i, i + 2), 16) & 0xff);
            result[j] = b;
        }

        return result;
    }

    private static byte[] concatHex(byte[] array1, byte[] array2) {

        int len = array1.length + array2.length;

        byte[] result = new byte[len];

        int index = 0;
        if (array1.length != 0) {
            for (byte b : array1) {
                result[index] = b;
                index++;
            }
        }

        if (array2.length != 0) {
            for (byte b : array2) {
                result[index] = b;
                index++;
            }
        }

        return result;
    }

    private static String makeHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String makeHex(byte[] bytes, int from, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02x", bytes[from + i]));
        }
        return sb.toString();
    }

    public static String runAuth(String randStr, String authnStr) {
        // https://android.googlesource.com/platform/frameworks/opt/net/wifi/+/refs/heads/nougat-release/service/java/com/android/server/wifi/WifiMonitor.java#314
        // https://cs.android.com/android/platform/superproject/+/master:packages/modules/Wifi/service/java/com/android/server/wifi/ClientModeImpl.java;l=5871;drc=master
        // https://cs.android.com/android/platform/superproject/+/master:packages/modules/Wifi/service/java/com/android/server/wifi/WifiCarrierInfoManager.java;l=1296;drc=master
        // shamelessly copied from AOSP
        byte[] rand = parseHex(randStr);
        byte[] authn = parseHex(authnStr);
        String base64Challenge = Base64.encodeToString(concatHex(rand, authn), Base64.NO_WRAP);
        System.out.println("Challenge: " + base64Challenge);
        // TODO(zhuowei): isim or usim?
        String tmResponse = telephonyManager.getIccAuthentication(TelephonyManager.APPTYPE_USIM,
                TelephonyManager.AUTHTYPE_EAP_AKA, base64Challenge);
        System.out.println("Challenge response: " + tmResponse);

        boolean goodReponse = false;
        String response = null;
        if (tmResponse != null && tmResponse.length() > 4) {
            byte[] result = Base64.decode(tmResponse, Base64.DEFAULT);
            System.out.println("Hex Response - " + makeHex(result));
            byte tag = result[0];
            if (tag == (byte) 0xdb) {
                System.out.println("successful 3G authentication ");
                int resLen = result[1];
                String res = makeHex(result, 2, resLen);
                int ckLen = result[resLen + 2];
                String ck = makeHex(result, resLen + 3, ckLen);
                int ikLen = result[resLen + ckLen + 3];
                String ik = makeHex(result, resLen + ckLen + 4, ikLen);
                response = "{\"ik\": \"" + ik + "\", \"ck\": \"" + ck + "\", \"res\": \"" + res + "\"}";
                System.out.println("ik: " + ik + " ck: " + ck + " res: " + res);
            } else if (tag == (byte) 0xdc) {
                System.out.println("synchronisation failure");
                int autsLen = result[1];
                String auts = makeHex(result, 2, autsLen);
                response = "{\"auts\": \"" + auts + "\"}";
                System.out.println("auts:" + auts);
            } else {
                System.out.println("bad response - unknown tag = " + tag);
            }
        } else {
            System.out.println("bad response - " + tmResponse);
        }
        return response;
    }
    private static class SimHTTPD extends NanoHTTPD {
        SimHTTPD(int port) {
            super(port);
        }
        @Override
        public Response serve(IHTTPSession session) {
            // https://github.com/fasferraz/USIM-https-server
            Map<String, List<String>> params = session.getParameters();
            String type = first(params.get("type"));
            if (type == null) {
                return newFixedLengthResponse("no command");
            }
            if (type.equals("imsi")) {
                String imsi = telephonyManager.getSubscriberId();
                return newFixedLengthResponse("{\"imsi\": \"" + imsi + "\"}");
            } else if (type.equals("rand-autn")) {
                String rand = first(params.get("rand"));
                String autn = first(params.get("autn"));
                String response = runAuth(rand, autn);
                return newFixedLengthResponse(response);
            } else {
                return newFixedLengthResponse("invalid command");
            }
        }
    }
}
