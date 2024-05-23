package com.geeui.face.broadcast;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;


/**
 * 广播管理器
 *
 * @author liujunbin
 */
public class LauncherBroadcastReceiverManager {

    public Context mContext;
    private static LauncherBroadcastReceiverManager instance;

    public static LauncherBroadcastReceiverManager getInstance(Context context) {
        synchronized (LauncherBroadcastReceiverManager.class) {
            if (instance == null) {
                instance = new LauncherBroadcastReceiverManager(context.getApplicationContext());
            }
            return instance;
        }
    }

    public LauncherBroadcastReceiverManager(Context context) {
        this.mContext = context;
        init(context);

    }

    private void init(Context context) {
        setNetWorkChangeListener();
        setWifiChangeListener();
        setTimeListener();
    }

    private void setNetWorkChangeListener() {
        NetWorkChangeReceiver netChangeReceiver = new NetWorkChangeReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
//        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        mContext.registerReceiver(netChangeReceiver, intentFilter);
    }

    private void setWifiChangeListener() {
        WifiReceiver wifiReceiver = new WifiReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mContext.registerReceiver(wifiReceiver, intentFilter);
    }

    private void setTimeListener() {
        TimerReceiver mTimeReceiver = new TimerReceiver();
        IntentFilter timeFilter = new IntentFilter();
        timeFilter.addAction(Intent.ACTION_TIME_TICK);
        mContext.registerReceiver(mTimeReceiver, timeFilter);
    }


}
