package com.example.socketdemo.activity;

import android.content.DialogInterface;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
//import android.support.v7.app.AlertDialog;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.socketdemo.R;
import com.example.socketdemo.base.AppContext;
import com.example.socketdemo.base.BaseActivity;
import com.example.socketdemo.base.BaseTransfer;
import com.example.socketdemo.bean.FileInfo;
import com.example.socketdemo.common.Consts;
import com.example.socketdemo.common.FileReceiver;
import com.example.socketdemo.common.SpaceItemDecoration;
import com.example.socketdemo.receiver.WifiBroadcaseReceiver;
import com.example.socketdemo.utils.FileUtils;
import com.example.socketdemo.utils.LogUtils;
import com.example.socketdemo.utils.NetUtils;
import com.example.socketdemo.wifitools.WifiMgr;
import com.zhy.adapter.recyclerview.CommonAdapter;
import com.zhy.adapter.recyclerview.MultiItemTypeAdapter;
import com.zhy.adapter.recyclerview.base.ViewHolder;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Created by AA on 2017/3/28.
 */
public class ReceiveFilesActivity extends BaseActivity implements MultiItemTypeAdapter.OnItemClickListener {

    /**
     * ????????????????????????
     */
    public static final int MSG_FILE_RECEIVER_INIT_SUCCESS = 0x661;

    /**
     * ???????????????
     */
    public static final int MSG_UPDATE_ADAPTER = 0x662;

    /**
     * ????????????????????????????????????
     */
    public static final int MSG_SEND_RECEIVE_FILE_LIST = 0x663;

    /**
     * ??????????????????
     */
    public static final int MSG_ADD_FILEINFO = 0x664;

    /**
     * ???????????????
     */
    public static final int MSG_UPDATE_PROGRESS = 0x665;

    /**
     * ??????????????????
     */
    public static final int MSG_SET_STATUS = 0x666;

    @BindView(R.id.tv_receive_files_status)
    TextView tvStatus;

    @BindView(R.id.btn_receive_files)
    Button btnSendFileList;

    @BindView(R.id.rv_receive_files_choose_hotspot)
    RecyclerView mChooseHotspotRecyclerView;
    private CommonAdapter<ScanResult> mChooseHotspotAdapter;

    @BindView(R.id.rv_receive_files)
    RecyclerView mReceiveFilesRecyclerView;
    private CommonAdapter<Map.Entry<String, FileInfo>> mReceiveFilesAdapter;

    /**
     * ??????????????????????????????
     */
    private List<FileInfo> mSendFileInfos = new ArrayList<>();

    /**
     * ??????????????????????????????
     */
    private List<FileReceiver> mFileReceiverList = new ArrayList<>();

    /**
     * WiFi?????????
     */
    private WifiMgr mWifiMgr;

    /**
     * ??????????????????WiFi??????
     */
    private List<ScanResult> mScanResults = new ArrayList<>();

    /**
     * ?????????????????????Socket
     */
    private Socket mClientSocket;

    /**
     * UDP Socket
     */
    private DatagramSocket mDatagramSocket;

    /**
     * ??????????????????
     */
    private ReceiveServerRunnable mReceiveServerRunnable;

    /**
     * ??????????????????????????????
     */
    private boolean mIsSendInitOrder;

    /**
     * ????????????????????????
     */
    private boolean mIsPermissionGranted;

    /**
     * ????????????WiFi???SSID
     */
    private String mSelectedSSID;


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_FILE_RECEIVER_INIT_SUCCESS) {
                //??????????????????????????????????????????
                sendInitSuccessToFileSender();
            } else if (msg.what == MSG_UPDATE_ADAPTER) {
                //???????????????
                setupReceiveFilesAdapter();
            } else if (msg.what == MSG_SEND_RECEIVE_FILE_LIST) {
                //????????????????????????????????????
                sendFileListToFileSender();
            } else if (msg.what == MSG_ADD_FILEINFO) {
                //??????????????????
                mReceiveFilesAdapter.notifyDataSetChanged();
            } else if (msg.what == MSG_UPDATE_PROGRESS) {
                //???????????????
                int position = msg.arg1;
                int progress = msg.arg2;
                if (position >= 0 && position < mReceiveFilesAdapter.getItemCount()) {
                    updateProgress(position, progress);
                }
            } else if (msg.what == MSG_SET_STATUS) {
                //??????????????????
                setStatus(msg.obj.toString());
            }
        }
    };

    @Override
    protected int getLayoutId() {
        return R.layout.activity_receive_files;
    }

    @Override
    protected String getTitleText() {
        return "????????????";
    }

    @Override
    protected void initData() {
        //????????????
        requestPermission(PERMISSION_CONNECT_WIFI, PERMISSION_REQ_CONNECT_WIFI);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mIsPermissionGranted && mWifiBroadcaseReceiver == null) {
            registerWifiReceiver();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mWifiBroadcaseReceiver != null) {
            unregisterWifiReceiver();
        }
    }

    @Override
    public void onBackPressed() {
        if(hasFileReceiving()) {
            showTipsDialog("????????????????????????????????????", "???", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finishActivity();
                }
            }, "???", null);
        } else {
            finishActivity();
        }
    }

    @Override
    protected void permissionSuccess(int requestCode) {
        super.permissionSuccess(requestCode);
        if(requestCode == PERMISSION_REQ_CONNECT_WIFI) {
            //??????????????????
            mIsPermissionGranted = true;

            //??????WiFi?????????WiFi??????
            registerWifiReceiver();
            mWifiMgr = new WifiMgr(getContext());
            if(mWifiMgr.isWifiEnabled()) {
                setStatus("??????????????????WiFi...");
                mWifiMgr.startScan();
            } else {
                mWifiMgr.openWifi();
            }
        }
    }

    @Override
    protected void permissionFail(int requestCode) {
        super.permissionFail(requestCode);
        if(requestCode == PERMISSION_REQ_CONNECT_WIFI) {
            //??????????????????
            mIsPermissionGranted = false;
            showTipsDialog("WiFi??????????????????", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    onBackPressed();
                }
            });
        }
    }

    /**
     * ????????????WiFi?????????????????????
     */
    private void registerWifiReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mWifiBroadcaseReceiver, filter);
    }

    /**
     * ?????????WiFi?????????????????????
     */
    private void unregisterWifiReceiver() {
        if (mWifiBroadcaseReceiver != null) {
            unregisterReceiver(mWifiBroadcaseReceiver);
            mWifiBroadcaseReceiver = null;
        }
    }

    /**
     * ????????????????????????
     */
    private void initReceiverServer() {
        mReceiveServerRunnable = new ReceiveServerRunnable();
        new Thread(mReceiveServerRunnable).start();
    }

    /**
     * ??????????????????????????????
     */
    private void sendInitSuccessToFileSender() {
        new Thread() {
            @Override
            public void run() {
                try {
                    //??????WiFi?????????????????????IP??????
                    int tryCount = 0;
                    String serverIp = mWifiMgr.getIpAddressFromHotspot();
                    while (serverIp.equals(Consts.DEFAULT_UNKNOW_IP) && tryCount < Consts.DEFAULT_TRY_COUNT) {
                        Thread.sleep(1000);
                        serverIp = mWifiMgr.getIpAddressFromHotspot();
                        tryCount ++;
                    }

                    //????????????ping?????????IP??????
                    tryCount = 0;
                    while (!NetUtils.pingIpAddress(serverIp) && tryCount < Consts.DEFAULT_TRY_COUNT) {
                        Thread.sleep(500);
                        LogUtils.i("Try to ping ------" + serverIp + " - " + tryCount);
                        tryCount ++;
                    }

                    //??????UDP??????
                    if(mDatagramSocket == null) {
                        //?????????java.net.BindException: bind failed: EADDRINUSE (Address already in use)
                        mDatagramSocket = new DatagramSocket(null);
                        mDatagramSocket.setReuseAddress(true);
                        mDatagramSocket.bind(new InetSocketAddress(Consts.DEFAULT_SERVER_UDP_PORT));
                    }
                    //???????????????????????????
                    InetAddress ipAddress = InetAddress.getByName(serverIp);
                    byte[] sendData = Consts.MSG_FILE_RECEIVER_INIT_SUCCESS.getBytes(BaseTransfer.UTF_8);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, Consts.DEFAULT_SERVER_UDP_PORT);
                    mDatagramSocket.send(sendPacket);
                    LogUtils.i("???????????? ------->>>" + Consts.MSG_FILE_RECEIVER_INIT_SUCCESS);

                    //??????????????????
                    while (true) {
                        byte[] receiveData = new byte[1024];
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        mDatagramSocket.receive(receivePacket);
                        String response = new String(receivePacket.getData()).trim();
                        if(isNotEmptyString(response)) {
                            //??????????????????????????????
                            LogUtils.e("?????????????????? -------->>>" + response);
                            parseFileInfoList(response);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * ??????WiFi???????????????
     */
    private void setupWifiAdapter() {
        if(mChooseHotspotAdapter == null) {
            mChooseHotspotAdapter = new CommonAdapter<ScanResult>(getContext(), R.layout.item_choose_hotspot, mScanResults) {
                @Override
                protected void convert(ViewHolder holder, ScanResult scanResult, int position) {
                    holder.setText(R.id.tv_item_choose_hotspot_ssid, scanResult.SSID);
                    holder.setText(R.id.tv_item_choose_hotspot_level, String.format(getContext().getString(R.string.item_level), scanResult.level));
                }
            };
            //??????????????????
            mChooseHotspotAdapter.setOnItemClickListener(this);
            //???????????????
            mChooseHotspotRecyclerView.setAdapter(mChooseHotspotAdapter);
            //????????????
            mChooseHotspotRecyclerView.addItemDecoration(new SpaceItemDecoration(10));
            mChooseHotspotRecyclerView.setVisibility(View.VISIBLE);
        } else {
            mChooseHotspotAdapter.notifyDataSetChanged();
        }
    }

    /**
     * ?????????????????????????????????
     */
    private void setupReceiveFilesAdapter() {
        List<Map.Entry<String, FileInfo>> fileInfos = AppContext.getAppContext().getReceiverFileInfoMap();
        Collections.sort(fileInfos, Consts.DEFAULT_COMPARATOR);
        //???????????????
        mReceiveFilesAdapter = new CommonAdapter<Map.Entry<String, FileInfo>>(getContext(), R.layout.item_files_selector, fileInfos) {
            @Override
            protected void convert(ViewHolder holder, Map.Entry<String, FileInfo> fileInfoMap, int position) {
                final FileInfo fileInfo = fileInfoMap.getValue();
                //????????????
                holder.setText(R.id.tv_item_files_selector_file_path, fileInfo.getFilePath());
                //????????????
                holder.setText(R.id.tv_item_files_selector_size, FileUtils.FormetFileSize(fileInfo.getSize()));
                //??????????????????
                if(fileInfo.getProgress() >= 100) {
                    holder.setText(R.id.tv_item_files_selector_status, "????????????");
                } else if(fileInfo.getProgress() == 0) {
                    holder.setText(R.id.tv_item_files_selector_status, "????????????");
                } else if(fileInfo.getProgress() < 100) {
                    holder.setText(R.id.tv_item_files_selector_status, "????????????");
                } else {
                    holder.setText(R.id.tv_item_files_selector_status, "????????????");
                }
                //??????????????????
                ProgressBar progressBar = holder.getView(R.id.pb_item_files_selector);
                progressBar.setProgress(fileInfo.getProgress());

                //????????????
                CheckBox checkBox = holder.getView(R.id.cb_item_files_selector);
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if(isChecked) {
                            mSendFileInfos.add(fileInfo);
                        } else {
                            mSendFileInfos.remove(fileInfo);
                        }
                        //??????????????????????????????????????????????????????
                        btnSendFileList.setEnabled(mSendFileInfos.size() > 0);
                    }
                });
            }
        };
        mReceiveFilesRecyclerView.setAdapter(mReceiveFilesAdapter);
        //??????ListView??????
        mReceiveFilesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        //?????????
        mReceiveFilesRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
    }

    /**
     * ????????????????????????
     * @param position ????????????
     * @param progress ????????????
     */
    private void updateProgress(int position, int progress) {
        FileInfo fileInfo = mReceiveFilesAdapter.getDatas().get(position).getValue();
        fileInfo.setProgress(progress);
        mReceiveFilesAdapter.notifyItemChanged(position);

        if(position == AppContext.getAppContext().getReceiverFileInfoMap().size() - 1 && progress == 100) {
            toast("????????????????????????");
            LogUtils.e("????????????????????????");
        }
    }

    /**
     * ?????????????????????FileInfo??????
     * @param jsonStr
     */
    private void parseFileInfoList(String jsonStr) {
        if(isNotEmptyString(jsonStr)) {
            List<FileInfo> fileInfos = FileInfo.toObjectList(jsonStr);
            if(!isEmptyList(fileInfos)) {
                for(FileInfo fileInfo : fileInfos) {
                    if(fileInfo != null && isNotEmptyString(fileInfo.getFilePath())) {
                        AppContext.getAppContext().addReceiverFileInfo(fileInfo);
                    }
                }
                //???????????????
                mHandler.sendEmptyMessage(MSG_UPDATE_ADAPTER);
            }
        }
    }

    /**
     * ???????????????????????????????????????
     */
    private void sendFileListToFileSender() {
        new Thread() {
            @Override
            public void run() {
                try {
                    //??????WiFi?????????????????????IP??????
                    String serverIp = mWifiMgr.getIpAddressFromHotspot();
                    if(mDatagramSocket == null) {
                        //?????????java.net.BindException: bind failed: EADDRINUSE (Address already in use)
                        mDatagramSocket = new DatagramSocket(null);
                        mDatagramSocket.setReuseAddress(true);
                        mDatagramSocket.bind(new InetSocketAddress(Consts.DEFAULT_SERVER_UDP_PORT));
                    }

                    //???????????????????????????
                    InetAddress ipAddress = InetAddress.getByName(serverIp);
                    String jsonStr = FileInfo.toJsonStr(mSendFileInfos);
                    DatagramPacket sendPacket = new DatagramPacket(jsonStr.getBytes(), jsonStr.getBytes().length, ipAddress, Consts.DEFAULT_SERVER_UDP_PORT);
                    mDatagramSocket.send(sendPacket);
                    LogUtils.i("Send Msg To FileSender ------->>>" + jsonStr);

                    //??????????????????????????????
                    byte[] sendData = Consts.MSG_START_SEND.getBytes(BaseTransfer.UTF_8);
                    DatagramPacket sendPacket2 = new DatagramPacket(sendData, sendData.length, ipAddress, Consts.DEFAULT_SERVER_UDP_PORT);
                    mDatagramSocket.send(sendPacket2);
                    LogUtils.i("Send Msg To FileSender ------->>>" + sendData);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * ??????WiFi???????????????
     * @param title
     * @param listener
     */
    protected void showDialogWithEditText(String title, final OnWifiPasswordConfirmListener listener) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.layout_dialog_with_edittext, null);
        final EditText etPassword = (EditText) dialogView.findViewById(R.id.et_dialog_with_edittext);

        AlertDialog.Builder builder = new AlertDialog.Builder(this.getContext());
        builder.setTitle(title);
        builder.setView(dialogView);
        builder.setPositiveButton(getContext().getString(R.string.confirm), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (listener != null) {
                    listener.onConfirm(etPassword.getText().toString().trim());
                }
            }
        });
        builder.setNegativeButton(getContext().getString(R.string.cancel), null);
        builder.create().show();
    }

    /**
     * ????????????
     * @param status
     */
    private void setStatus(String status) {
        tvStatus.setText(status);
        LogUtils.e(status);
    }

    /**
     * ???????????????????????????
     * @return
     */
    private boolean hasFileReceiving() {
        for(FileReceiver fileReceiver : mFileReceiverList) {
            if(fileReceiver != null && fileReceiver.isRunning()) {
                return true;
            }
        }
        return false;
    }

    /**
     * ??????????????????????????????
     */
    private void stopAllFileReceivingTask() {
        for(FileReceiver fileReceiver : mFileReceiverList) {
            if(fileReceiver != null) {
                fileReceiver.stop();
            }
        }
    }

    /**
     * ?????????Activity
     */
    private void finishActivity() {
        //??????UDP Socket
        closeUdpSocket();

        //??????????????????????????????
        stopAllFileReceivingTask();

        //?????????????????????Socket
        closeClientSocket();

        //??????WiFi??????
        mWifiMgr.clearWifiConfig();

        //????????????????????????
        AppContext.getAppContext().clearReceiverFileInfoMap();

        finish();
    }

    /**
     * ?????????????????????Socket
     */
    private void closeClientSocket() {
        if(mClientSocket != null) {
            try {
                mClientSocket.close();
                mClientSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ??????UDP Socket
     */
    private void closeUdpSocket() {
        if(mDatagramSocket != null) {
            mDatagramSocket.disconnect();
            mDatagramSocket.close();
            mDatagramSocket = null;
        }
    }

    @OnClick(R.id.btn_receive_files)
    public void sendReceiveFileListToFileSender() {
        //????????????????????????????????????????????????????????????
        AppContext.getAppContext().clearReceiverFileInfoMap();
        for(FileInfo fileInfo : mSendFileInfos) {
            fileInfo.setPosition(mSendFileInfos.indexOf(fileInfo));
            AppContext.getAppContext().addReceiverFileInfo(fileInfo);
        }
        setupReceiveFilesAdapter();
        initReceiverServer();
    }

    @Override
    public void onItemClick(View view, RecyclerView.ViewHolder holder, int position) {
        if(position < mChooseHotspotAdapter.getItemCount() && position >= 0) {
            //??????????????????WiFi???SSID
            ScanResult scanResult = mChooseHotspotAdapter.getDatas().get(position);
            mSelectedSSID = scanResult.SSID;

            if((scanResult.capabilities != null && !scanResult.capabilities.equals(WifiMgr.NO_PASSWORD)) || (scanResult.capabilities != null && !scanResult.capabilities.equals(WifiMgr.NO_PASSWORD_WPS))){
                //?????????????????????
                showDialogWithEditText(mSelectedSSID, new OnWifiPasswordConfirmListener() {
                    @Override
                    public void onConfirm(String password) {
                        //??????????????????WiFi
                        if(isNotEmptyString(password)) {
                            try {
                                setStatus("????????????Wifi...");
                                mWifiMgr.connectWifi(mSelectedSSID, password, mScanResults);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            toast("??????????????????");
                        }
                    }
                });
            } else {
                //???????????????WiFi
                try {
                    setStatus("????????????Wifi...");
                    mWifiMgr.connectWifi(mSelectedSSID, "", mScanResults);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean onItemLongClick(View view, RecyclerView.ViewHolder holder, int position) {
        return false;
    }

    /**
     * WiFi???????????????
     */
    private WifiBroadcaseReceiver mWifiBroadcaseReceiver = new WifiBroadcaseReceiver() {
        @Override
        public void onWifiEnabled() {
            //WiFi??????????????????????????????WiFi
            setStatus("??????????????????WiFi...");
            mWifiMgr.startScan();
        }

        @Override
        public void onWifiDisabled() {
            //WiFi????????????????????????WiFi??????
            mSelectedSSID = "";
            mScanResults.clear();
            setupWifiAdapter();
        }

        @Override
        public void onScanResultsAvailable(List<ScanResult> scanResults) {
            //??????????????????WiFi?????????????????????WiFi??????
            mScanResults.clear();
            mScanResults.addAll(scanResults);
            setupWifiAdapter();
        }

        @Override
        public void onWifiConnected(String connectedSSID) {
            //????????????WiFi??????????????????
            if (connectedSSID.equals(mSelectedSSID) && !mIsSendInitOrder) {
                //????????????
                setStatus("Wifi????????????...");
                //???????????????????????????WiFi????????????
                mChooseHotspotRecyclerView.setVisibility(View.GONE);
                mReceiveFilesRecyclerView.setVisibility(View.VISIBLE);

                //??????????????????????????????????????????
                mHandler.sendEmptyMessage(MSG_FILE_RECEIVER_INIT_SUCCESS);
                mIsSendInitOrder = true;
            } else {
//                //???????????????????????????WiFi????????????WiFi?????????????????????WiFi
//                LogUtils.e("???????????????WiFi?????????????????????...");
//                mWifiMgr.disconnectWifi(connectedSSID);
//                mWifiMgr.startScan();
            }
        }

        @Override
        public void onWifiDisconnected() {

        }
    };

    /**
     * ServerSocket????????????
     */
    private class ReceiveServerRunnable implements Runnable {

        @Override
        public void run() {
            try {
                //???????????????????????????
                mHandler.sendEmptyMessage(MSG_SEND_RECEIVE_FILE_LIST);

                Thread.sleep(3000);
                //??????????????????
                String serverIp = mWifiMgr.getIpAddressFromHotspot();
                List<Map.Entry<String, FileInfo>> fileInfoList = AppContext.getAppContext().getReceiverFileInfoMap();
                Collections.sort(fileInfoList, Consts.DEFAULT_COMPARATOR);
                for(final Map.Entry<String, FileInfo> fileInfoMap : fileInfoList) {
                    //??????????????????????????????????????????
                    final int position = fileInfoList.indexOf(fileInfoMap);
                    mClientSocket = new Socket(serverIp, Consts.DEFAULT_FILE_RECEIVE_SERVER_PORT);
                    FileReceiver fileReceiver = new FileReceiver(mClientSocket, fileInfoMap.getValue());
                    fileReceiver.setOnReceiveListener(new FileReceiver.OnReceiveListener() {
                        @Override
                        public void onStart() {
                            mHandler.obtainMessage(MSG_SET_STATUS, "????????????"+ FileUtils.getFileName(fileInfoMap.getValue().getFilePath())).sendToTarget();
                        }

                        @Override
                        public void onProgress(FileInfo fileInfo, long progress, long total) {
                            //????????????????????????
                            int i_progress = (int) (progress * 100 / total);
                            LogUtils.e("???????????????" + fileInfo.getFilePath() + "\n???????????????" + i_progress);

                            Message msg = new Message();
                            msg.what = MSG_UPDATE_PROGRESS;
                            msg.arg1 = position;
                            msg.arg2 = i_progress;
                            mHandler.sendMessage(msg);
                        }

                        @Override
                        public void onSuccess(FileInfo fileInfo) {
                            //????????????
                            mHandler.obtainMessage(MSG_SET_STATUS, "?????????" + FileUtils.getFileName(fileInfo.getFilePath()) + "????????????").sendToTarget();
                            fileInfo.setResult(FileInfo.FLAG_SUCCESS);
                            AppContext.getAppContext().updateReceiverFileInfo(fileInfo);

                            Message msg = new Message();
                            msg.what = MSG_UPDATE_PROGRESS;
                            msg.arg1 = position;
                            msg.arg2 = 100;
                            mHandler.sendMessage(msg);
                        }

                        @Override
                        public void onFailure(Throwable throwable, FileInfo fileInfo) {
                            if(fileInfo != null) {
                                //????????????
                                mHandler.obtainMessage(MSG_SET_STATUS, "?????????" + FileUtils.getFileName(fileInfo.getFilePath()) + "????????????").sendToTarget();
                                fileInfo.setResult(FileInfo.FLAG_FAILURE);
                                AppContext.getAppContext().updateReceiverFileInfo(fileInfo);

                                Message msg = new Message();
                                msg.what = MSG_UPDATE_PROGRESS;
                                msg.arg1 = position;
                                msg.arg2 = -1;
                                mHandler.sendMessage(msg);
                            }
                        }
                    });

                    //?????????????????????
                    mFileReceiverList.add(fileReceiver);
                    AppContext.getAppContext().MAIN_EXECUTOR.execute(fileReceiver);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private interface OnWifiPasswordConfirmListener {
        void onConfirm(String password);
    }
}
