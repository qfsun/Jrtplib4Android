package com.wtoe.jrtplib;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.util.SparseArray;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * author：Sun on 2018/1/10/0010.
 * email：1564063766@qq.com
 * remark:网络管理工具
 */
public class NetworkUtil {
    private static final String tag = NetworkUtil.class.getSimpleName();

    private static final String SUBTYPE_TD_SCDMA = "SCDMA";
    private static final String SUBTYPE_WCDMA = "WCDMA";
    private static final String SUBTYPE_CDMA2000 = "CDMA2000";

    private static NetworkUtil mInstance;

    public static NetworkUtil getInstance() {
        if (mInstance == null) {
            synchronized (NetworkUtil.class) {
                if (mInstance == null) {
                    mInstance = new NetworkUtil();
                }
            }
        }
        return mInstance;
    }

    /**
     * 判断是否已连接到网络.
     *
     * @param context Context
     * @return 是否已连接到网络
     */
    public boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                if (info.getState() == NetworkInfo.State.CONNECTED
                        || isWifiConnected(context) //是否连接了wifi
                        || isWifiApEnabled(getWifiManager(context)) //是否开启了热点
                        ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * wifi是否连接
     *
     * @param context
     * @return
     */
    public boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiNetworkInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public WifiInfo getWifiInfo(Context context) {
        WifiManager wifiManager = getWifiManager(context);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo;
        } else {
            return null;
        }
    }

    public WifiManager getWifiManager(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager;
    }

    /**
     * 开启手机个人热点
     *
     * @param context
     * @param ssid
     * @param passwd
     */
    public void startWifiAp(Context context, String ssid, String passwd) {
        WifiManager mWifiManager = getWifiManager(context);
        //wifi和热点不能同时打开，所以打开热点的时候需要关闭wifi
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }
        if (!isWifiApEnabled(mWifiManager)) {
            stratWifiAp(mWifiManager, ssid, passwd);
        }
    }

    /**
     * 热点开关是否打开
     *
     * @return
     */
    private boolean isWifiApEnabled(WifiManager mWifiManager) {
        try {
            Method method = mWifiManager.getClass().getMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(mWifiManager);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 设置热点名称及密码，并创建热点
     *
     * @param mSSID
     * @param mPasswd
     */
    private void stratWifiAp(WifiManager mWifiManager, String mSSID, String mPasswd) {
        Method method1 = null;
        try {
            //通过反射机制打开热点
            method1 = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            WifiConfiguration netConfig = new WifiConfiguration();

            netConfig.SSID = mSSID;
            if (mPasswd != null && !mPasswd.isEmpty()) {
                netConfig.preSharedKey = mPasswd;
                netConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
                netConfig.allowedKeyManagement.set(4);  //WifiConfiguration.KeyMgmt.WPA2_PSK = 4
                netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            }
//            netConfig.status = WifiConfiguration.Status.ENABLED;
            method1.invoke(mWifiManager, netConfig, true);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭WiFi热点
     */
    public void closeWifiAp(Context mContext) {
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (isWifiApEnabled(wifiManager)) {
            try {
                Method method = wifiManager.getClass().getMethod("getWifiApConfiguration");
                method.setAccessible(true);
                WifiConfiguration config = (WifiConfiguration) method.invoke(wifiManager);
                Method method2 = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
                method2.invoke(wifiManager, config, false);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }


    public DhcpInfo getDhcpInfo(Context context) {
        WifiManager wifiManager = getWifiManager(context);
        if (wifiManager != null) {
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            return dhcpInfo;
        } else {
            return null;
        }
    }


    public String getCurrentSsid(Context context) {
        WifiInfo wifiInfo = getWifiInfo(context);
        if (wifiInfo != null) {
            return wifiInfo.getSSID();
        } else {
            return null;
        }
    }

    /**
     * 获取设备连接的网关的ip地址
     */
    public String getGateWayIp(Context context) {
        String gatewayIp = null;
        DhcpInfo dhcpInfo = getDhcpInfo(context);
        if (dhcpInfo != null) {
            gatewayIp = Int2String(dhcpInfo.gateway);
        }

        Log.d(tag, "gate way ip = " + gatewayIp);
        return gatewayIp;
    }


    /**
     * 获取设备的mac地址
     */
    private final String marshmallowMacAddress = "02:00:00:00:00:00";
    private final String fileAddressMac = "/sys/class/net/wlan0/address";

    public String getLocalMac(Context context) {
        WifiManager wifiMan = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = (null == wifiMan ? null : wifiMan.getConnectionInfo());
//        if(!wifiMan.isWifiEnabled()) {
//            //必须先打开，才能获取到MAC地址
//            wifiMan.setWifiEnabled(true);
//            wifiMan.setWifiEnabled(false);
//        }
        if (wifiInf != null && marshmallowMacAddress.equals(wifiInf.getMacAddress())) {
            String result = null;
            try {
                //这里是先获取移动网络mac，再获取WLAN_mac
                result = getAdressMacByInterface();
                if (result != null) {
                    return result;
                } else {
                    result = getAddressMacByFile(wifiMan);
                    return result;
                }
                //改为直接获取WLAN_mac
//                result = getAddressMacByFile(wifiMan);
//                LogUtil.e(tag, "本地WLAN_mac: " + result);
//                return result;
            } catch (IOException e) {
                Log.e("MobileAccess", "Erreur lecture propriete Adresse MAC");
            } catch (Exception e) {
                Log.e("MobileAcces", "Erreur lecture propriete Adresse MAC ");
            }
        } else {
            if (wifiInf != null && wifiInf.getMacAddress() != null) {
                return wifiInf.getMacAddress();
            } else {
                return "";
            }
        }
        return marshmallowMacAddress;
    }

    private String getAdressMacByInterface() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (nif.getName().equalsIgnoreCase("wlan0")) {
                    byte[] macBytes = nif.getHardwareAddress();
                    if (macBytes == null) {
                        return "";
                    }

                    StringBuilder res1 = new StringBuilder();
                    for (byte b : macBytes) {
                        res1.append(String.format("%02X:", b));
                    }

                    if (res1.length() > 0) {
                        res1.deleteCharAt(res1.length() - 1);
                    }
                    return res1.toString();
                }
            }

        } catch (Exception e) {
            Log.e("MobileAcces", "Erreur lecture propriete Adresse MAC ");
        }
        return null;
    }

    private String getAddressMacByFile(WifiManager wifiMan) throws Exception {
        String ret;
        try {
            int wifiState = wifiMan.getWifiState();
            wifiMan.setWifiEnabled(true);
            File fl = new File(fileAddressMac);
            FileInputStream fin = new FileInputStream(fl);
            ret = crunchifyGetStringFromStream(fin);
            fin.close();

            boolean enabled = WifiManager.WIFI_STATE_ENABLED == wifiState;
            wifiMan.setWifiEnabled(enabled);
        } catch (NullPointerException e) {
            e.printStackTrace();
            ret = "";
        }
        return ret;
    }

    private String crunchifyGetStringFromStream(InputStream crunchifyStream) throws IOException {
        if (crunchifyStream != null) {
            Writer crunchifyWriter = new StringWriter();

            char[] crunchifyBuffer = new char[2048];
            try {
                Reader crunchifyReader = new BufferedReader(new InputStreamReader(crunchifyStream, "UTF-8"));
                int counter;
                while ((counter = crunchifyReader.read(crunchifyBuffer)) != -1) {
                    crunchifyWriter.write(crunchifyBuffer, 0, counter);
                }
            } finally {
                crunchifyStream.close();
            }
            return crunchifyWriter.toString();
        } else {
            return "No Contents";
        }
    }

    /**
     * 互殴去你所在网络的广播地址
     *
     * @throws UnknownHostException
     */
    public InetAddress getBroadcastAddress(Context context)
            throws UnknownHostException {
        DhcpInfo dhcpInfo = getDhcpInfo(context);
        if (dhcpInfo == null) {
            return InetAddress.getByName("255.255.255.255");
        }
        int broadcast = (dhcpInfo.ipAddress & dhcpInfo.netmask) |
                ~dhcpInfo.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++) {
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        }

        return InetAddress.getByAddress(quads);
    }


    /**
     * 获取设备的ip地址
     */
    public String getLocalIp(Context context) {
        String localIp = null;
        try {
            NetworkInfo info = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                if (info.getType() == ConnectivityManager.TYPE_MOBILE) {//当前使用2G/3G/4G网络
                    try {
                        //Enumeration<NetworkInterface> en=NetworkInterface.getNetworkInterfaces();
                        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                            NetworkInterface intf = en.nextElement();
                            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                                InetAddress inetAddress = enumIpAddr.nextElement();
                                if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                    localIp = inetAddress.getHostAddress();
                                }
                            }
                        }
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }

                } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {//当前使用无线网络
                    WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    String ipAddress = Int2String(wifiInfo.getIpAddress());//得到IPV4地址
                    localIp = ipAddress;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return localIp;
    }

    /**
     * 获取外网的IP(必须放到子线程里处理)
     */
    /**
     * 获取外网IP地址
     * @return
     */
    public String GetNetIp(){
        String IP = "";
        try {
            String address = "http://ip.taobao.com/service/getIpInfo2.php?ip=myip";
            URL url = new URL(address);

            HttpURLConnection connection = (HttpURLConnection) url
                    .openConnection();
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("user-agent",
                    "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.7 Safari/537.36"); //设置浏览器ua 保证不出现503

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream in = connection.getInputStream();

                // 将流转化为字符串
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in));

                String tmpString = "";
                StringBuilder retJSON = new StringBuilder();
                while ((tmpString = reader.readLine()) != null) {
                    retJSON.append(tmpString + "\n");
                }

                JSONObject jsonObject = new JSONObject(retJSON.toString());
                String code = jsonObject.getString("code");

                if (code.equals("0")) {
                    JSONObject data = jsonObject.getJSONObject("data");
                    IP = data.getString("ip") + "(" + data.getString("country")
                            + data.getString("area") + "区"
                            + data.getString("region") + data.getString("city")
                            + data.getString("isp") + ")";

                    Log.e("提示", "您的IP地址是：" + IP);
                } else {
                    IP = "";
                    Log.e("提示", "IP接口异常，无法获取IP地址！");
                }
            } else {
                IP = "";
                Log.e("提示", "网络连接异常，无法获取IP地址！");
            }
        } catch (Exception e) {
            IP = "";
            Log.e("提示", "获取IP地址时出现异常，异常信息是：" + e.toString());
        }
        return IP;
    }


    public String Int2String(int IP) {
        String ipStr = "";
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            ipStr += String.valueOf(0xFF & IP);
            ipStr += ".";
            ipStr += String.valueOf(0xFF & IP >> 8);
            ipStr += ".";
            ipStr += String.valueOf(0xFF & IP >> 16);
            ipStr += ".";
            ipStr += String.valueOf(0xFF & IP >> 24);
        } else {
            ipStr += String.valueOf(0xFF & IP >> 24);
            ipStr += ".";
            ipStr += String.valueOf(0xFF & IP >> 16);
            ipStr += ".";
            ipStr += String.valueOf(0xFF & IP >> 8);
            ipStr += ".";
            ipStr += String.valueOf(0xFF & IP);
        }
        return ipStr;
    }


    private final ArrayList<Integer> channelsFrequency = new ArrayList<Integer>(
            Arrays.asList(0, 2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447,
                    2452, 2457, 2462, 2467, 2472, 2484));

    private static SparseArray<Integer> mChannelFrequency = new SparseArray<>();

    static {
        mChannelFrequency.put(1, 2412);
        mChannelFrequency.put(2, 2417);
        mChannelFrequency.put(3, 2422);
        mChannelFrequency.put(4, 2427);
        mChannelFrequency.put(5, 2432);
        mChannelFrequency.put(6, 2437);
        mChannelFrequency.put(7, 2442);
        mChannelFrequency.put(8, 2447);
        mChannelFrequency.put(9, 2452);
        mChannelFrequency.put(10, 2457);
        mChannelFrequency.put(11, 2462);
        mChannelFrequency.put(12, 2467);
        mChannelFrequency.put(13, 2472);
        mChannelFrequency.put(14, 2484);

        mChannelFrequency.put(36, 5180);
        mChannelFrequency.put(40, 5200);
        mChannelFrequency.put(44, 5220);
        mChannelFrequency.put(48, 5240);
        mChannelFrequency.put(52, 5260);
        mChannelFrequency.put(56, 5280);
        mChannelFrequency.put(60, 5300);
        mChannelFrequency.put(64, 5320);
        mChannelFrequency.put(100, 5500);
        mChannelFrequency.put(104, 5520);
        mChannelFrequency.put(108, 5540);
        mChannelFrequency.put(112, 5560);
        mChannelFrequency.put(116, 5580);
        mChannelFrequency.put(120, 5600);
        mChannelFrequency.put(124, 5620);
        mChannelFrequency.put(128, 5640);
        mChannelFrequency.put(132, 5660);
        mChannelFrequency.put(136, 5680);
        mChannelFrequency.put(140, 5700);
        mChannelFrequency.put(149, 5745);
        mChannelFrequency.put(153, 5765);
        mChannelFrequency.put(157, 5785);
        mChannelFrequency.put(161, 5805);
    }

    /**
     * 2.4G
     *
     * @param frequency 频率
     */
    public int getChannelFromFrequency(int frequency) {
        return channelsFrequency.indexOf(Integer.valueOf(frequency));
    }

    /**
     * 2.4G and 5G
     */
    public int getChannel(int frequency) {
        for (int i = 0; i < mChannelFrequency.size(); i++) {
            if (mChannelFrequency.valueAt(i) == frequency) {
                return mChannelFrequency.keyAt(i);
            }
        }

        return -1;
    }


    /**
     * 是否ping通
     */
    public boolean isPingOk(String ip) {
        LogUtil.e(tag, "ping ip = " + ip);
        try {
            Process p = Runtime.getRuntime()
                    .exec("/system/bin/ping -c 5 -w 4 " + ip);
            if (p == null) {
                return false;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("bytes from")) {
                    LogUtil.e(tag, "ping read line = " + line);
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    public String ping(String ip, String count) {
        String result = "";
        try {
            Process p = Runtime.getRuntime()
                    .exec("/system/bin/ping -c " + count + " -w 4 " + ip);
            if (p == null) {
                return result;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains("bytes from")) {
                    Log.d(tag, "ping result = " + line);
                    result += line + "\n";
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }


    /**
     * 该ip的端口某些端口是否打开
     * 22 linux ssh端口
     * 80和8081 http 端口
     * 135 远程打开对方的telnet服务器
     * 137 在局域网中提供计算机的名字或OP地址查询服务，一般安装了NetBIOS协议后，就会自动开放
     * 139 Windows获得NetBIOS/SMB服务
     * 445 局域网中文件的共享端口
     * 3389 远程桌面服务端口
     * 1900 ssdp协议
     * 5351 AppleBonjour、回到我的 Mac
     * 5353 Apple Bonjour、AirPlay、家庭共享、打印机查找、回到我的 Mac
     * 62078 Apple的一个端口
     * see link{https://support.apple.com/zh-cn/HT202944}
     */
    public boolean isAnyPortOk(String ip) {
        int portArray[] = {22, 80, 135, 137, 139, 445, 3389, 4253, 1034, 1900,
                993, 5353, 5351, 62078};
        Selector selector;
        for (int i = 0; i < portArray.length; i++) {
            try {
                Log.d(tag, "is any port ok ? ip = " + ip + " port =" +
                        portArray[i]);
                //tcp port detection
                selector = Selector.open();
                SocketChannel channel = SocketChannel.open();
                SocketAddress address = new InetSocketAddress(ip, portArray[i]);
                channel.configureBlocking(false);
                channel.connect(address);
                channel.register(selector, SelectionKey.OP_CONNECT, address);
                if (selector.select(500) != 0) {
                    Log.d(tag, ip + "is any port ok port ? " + portArray[i] +
                            " tcp is ok");
                    selector.close();
                    return true;
                } else {
                    selector.close();
                    continue;
                }
            } catch (Exception e) {
                if (e != null)
                    e.printStackTrace();
            }
        }
        return false;
    }
}
