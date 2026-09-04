package com.xhy.xp.softaphelper;

import java.util.HashMap;

import de.robv.android.xposed.XSharedPreferences;

/**
 * Runtime configuration shared between the settings Activity (writes via app
 * SharedPreferences) and the module hook (reads via LSPosed XSharedPreferences).
 *
 * Note: the same preference keys are used by MainActivity's ordinary
 * SharedPreferences so the values written there are mirrored into the hooked
 * process by LSPosed.
 */
public class Config {

    public static final String PREFS_NAME = "softaphelper";

    public static final String KEY_WIFI_IP = "wifi_ip";
    public static final String KEY_USB_IP = "usb_ip";
    public static final String KEY_BT_IP = "bt_ip";
    public static final String KEY_P2P_IP = "p2p_ip";
    public static final String KEY_ETH_IP = "eth_ip";
    public static final String KEY_PREFIX_LEN = "prefix_len";

    public static final String DEFAULT_WIFI_IP = "192.168.43.1";
    public static final String DEFAULT_USB_IP = "192.168.42.1";
    public static final String DEFAULT_BT_IP = "192.168.44.1";
    public static final String DEFAULT_P2P_IP = "192.168.49.1";
    public static final String DEFAULT_ETH_IP = "192.168.45.1";
    public static final int DEFAULT_PREFIX_LEN = 24;

    private final XSharedPreferences prefs;

    public Config() {
        XSharedPreferences p = null;
        try {
            p = new XSharedPreferences("com.xhy.xp.softaphelper", PREFS_NAME);
            p.makeWorldReadable();
            p.reload();
        } catch (Throwable ignored) {
            // LSPosed / older frameworks may throw; fall back to defaults.
        }
        prefs = p;
    }

    /** Reload latest values (call before each use to pick up setting changes). */
    public void reload() {
        if (prefs == null) return;
        try {
            prefs.reload();
        } catch (Throwable ignored) {
        }
    }

    private String getString(String key, String def) {
        try {
            String v = prefs != null ? prefs.getString(key, null) : null;
            return v == null ? def : v.trim();
        } catch (Throwable ignored) {
            return def;
        }
    }

    private int getInt(String key, int def) {
        try {
            return prefs != null ? prefs.getInt(key, def) : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    public String getWifiIp() {
        String v = getString(KEY_WIFI_IP, null);
        return (v == null || v.isEmpty()) ? DEFAULT_WIFI_IP : v;
    }

    public String getUsbIp() {
        String v = getString(KEY_USB_IP, null);
        return (v == null || v.isEmpty()) ? DEFAULT_USB_IP : v;
    }

    public String getBtIp() {
        String v = getString(KEY_BT_IP, null);
        return (v == null || v.isEmpty()) ? DEFAULT_BT_IP : v;
    }

    public String getP2pIp() {
        String v = getString(KEY_P2P_IP, null);
        return (v == null || v.isEmpty()) ? DEFAULT_P2P_IP : v;
    }

    public String getEthIp() {
        String v = getString(KEY_ETH_IP, null);
        return (v == null || v.isEmpty()) ? DEFAULT_ETH_IP : v;
    }

    public int getPrefixLen() {
        int v = getInt(KEY_PREFIX_LEN, 0);
        return v < 8 || v > 31 ? DEFAULT_PREFIX_LEN : v;
    }

    /** Builds the full CIDR address (e.g. "192.168.43.1/24"). */
    public String getCidr(String ip) {
        return ip + "/" + getPrefixLen();
    }

    /**
     * Builds the address map used by the hook, keyed by tethering type.
     */
    public HashMap<Integer, String> buildAddressMap() {
        HashMap<Integer, String> map = new HashMap<>();
        String prefix = "/" + getPrefixLen();
        map.put(MainHook.TETHERING_WIFI, getWifiIp() + prefix);
        map.put(MainHook.TETHERING_USB, getUsbIp() + prefix);
        map.put(MainHook.TETHERING_BLUETOOTH, getBtIp() + prefix);
        map.put(MainHook.TETHERING_WIFI_P2P, getP2pIp() + prefix);
        map.put(MainHook.TETHERING_ETHERNET, getEthIp() + prefix);
        return map;
    }

    /** Default addresses for reference / documentation bookkeeping. */
    public static String defaults() {
        return DEFAULT_WIFI_IP + " | " + DEFAULT_USB_IP + " | " + DEFAULT_BT_IP
                + " | " + DEFAULT_P2P_IP + " | " + DEFAULT_ETH_IP + " (prefix /24)";
    }
}