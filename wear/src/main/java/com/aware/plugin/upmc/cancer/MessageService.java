package com.aware.plugin.upmc.cancer;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageApi.MessageListener;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Random;

/**
 * Created by RaghuTeja on 6/23/17.
 */

public class MessageService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks{

    private GoogleApiClient mGoogleApiClient;
    public boolean phoneConnected;
    private NotificationCompat.Builder messageServiceNotifBuilder;

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(Constants.TAG, "MessageService: onDestroy");
        Wearable.CapabilityApi.removeCapabilityListener(mGoogleApiClient, this, null);
        Wearable.MessageApi.removeListener(mGoogleApiClient, this);
        if(mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        stopForeground(true);
        stopSelf();

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Constants.TAG, "MessageService: onCreate");
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectedNodes(List<Node> list) {
        Log.d(Constants.TAG, "MessageService: onConnectedNodes");
        super.onConnectedNodes(list);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(Constants.TAG, "MessageService:onStartCommand");
        Intent dashIntent = new Intent(this, MainActivity.class);
        dashIntent.setAction(new Random().nextInt(50) + "_action");
        PendingIntent dashPendingIntent = PendingIntent.getActivity(this, 0,dashIntent,0);
        messageServiceNotifBuilder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.common_google_signin_btn_icon_dark_normal)
                .setContentTitle("UPMC Dash Wear Client")
                .setContentText("Not synced with phone")
                .setPriority(NotificationCompat.PRIORITY_HIGH);
                //.setContentIntent(dashPendingIntent);

        Intent dashStopIntent = new Intent(Constants.NOTIFICATION_RECEIVER_INTENT_FILTER);
        dashStopIntent.putExtra(Constants.COMM_KEY_NOTIF, "STOP CLIENT");
        PendingIntent dashStopPendingIntent = PendingIntent.getBroadcast(this,0,dashStopIntent,PendingIntent.FLAG_ONE_SHOT);
        NotificationCompat.Action action1 = new NotificationCompat.Action.Builder(R.drawable.ic_cc_clear, "Stop Client", dashStopPendingIntent).build();
        NotificationCompat.Action action2 = new NotificationCompat.Action.Builder(R.drawable.ic_cc_checkmark, "Sync Client", dashStopPendingIntent).build();
        messageServiceNotifBuilder.addAction(action1).extend(new NotificationCompat.WearableExtender().setContentAction(0));
        messageServiceNotifBuilder.addAction(action2).extend(new NotificationCompat.WearableExtender().setContentAction(1));
        startForeground(1, messageServiceNotifBuilder.build());
        return START_NOT_STICKY;
        //return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        Log.d(Constants.TAG, "MessageService: onCapability");
        super.onCapabilityChanged(capabilityInfo);
        if(capabilityInfo.getNodes().size() > 0){
            Log.d(Constants.TAG, "MessageService: onCapability: Mobile Connected");
        }else{
            Log.d(Constants.TAG, "MessageService: onCapability: No Mobile Found");
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(Constants.TAG, "MessageService: onConnected");
        Wearable.CapabilityApi.addCapabilityListener(mGoogleApiClient,
                this,
                Constants.CAPABILITY_PHONE_APP);
        Uri uri = new Uri.Builder().scheme("wear").path("/upmc-dash").build();
        Wearable.MessageApi.addListener(mGoogleApiClient,this, uri, MessageApi.FILTER_PREFIX);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        super.onMessageReceived(messageEvent);
        byte[] input = messageEvent.getData();
        String message = new String(input);
        Log.d(Constants.TAG, "MessageService: onMessageReceived: " + message);
        if(message.equals(Constants.START_SC)) {
            Intent sensorService = new Intent(this, SensorService.class);
            if(!isMyServiceRunning(SensorService.class)) {
                startService(sensorService);
                Log.d(Constants.TAG, "MessageService: Starting SensorService: ");
            }
            else {
                Log.d(Constants.TAG, "MessageService: Already Running SensorService");
            }


        }
        else if(message.equals(Constants.GET_STATUS_WEAR)) {
            sendMessageToPhone(Constants.STATUS_READY);
            messageServiceNotifBuilder.setContentText("Connected");
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(1, messageServiceNotifBuilder.build());

        }
        else if(message.equals(Constants.STOP_SC)) {
            Log.d(Constants.TAG, "Stopping this shit");
            if(isMyServiceRunning(SensorService.class)) {
                this.stopService(new Intent(this, SensorService.class));
            }
            sendMessageToPhone(Constants.STATUS_READY);
        }
        else if(message.equals(Constants.KILL_DASH)) {
            Log.d(Constants.TAG, "Stopping this shit");
            if(isMyServiceRunning(SensorService.class)) {
                this.stopService(new Intent(this, SensorService.class));
            }
            sendBroadcast(new Intent(Constants.NOTIFICATION_RECEIVER_INTENT_FILTER).putExtra(Constants.COMM_KEY_NOTIF, "KILL_REQUEST"));
        }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(Constants.TAG, "MessageService: onConnectionSuspended");
    }




    private void sendMessageToPhone(final String message) {

        PendingResult<MessageApi.SendMessageResult> pendingResult =
                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient,
                        Constants.NODE_ID,
                        Constants.CONNECTION_PATH,
                        message.getBytes());

        pendingResult.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
            @Override
            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                if(!sendMessageResult.getStatus().isSuccess()) {
                    Log.d(Constants.TAG, "MessageService:sendMessageToWear:message failed: " + message);
                } else {
                    Log.d(Constants.TAG, "MessageService:sendMessageToWear:message sent : " + message);
                }
            }
        });

    }


}
