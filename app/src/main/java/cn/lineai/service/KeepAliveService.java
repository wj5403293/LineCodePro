package cn.lineai.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.view.WindowManager;
import cn.lineai.R;

public final class KeepAliveService extends Service {
    public static final String ACTION_START = "cn.lineai.action.START_KEEP_ALIVE";
    public static final String ACTION_STOP = "cn.lineai.action.STOP_KEEP_ALIVE";
    public static final String ACTION_UPDATE_STATUS = "cn.lineai.action.UPDATE_STATUS";

    public static final String EXTRA_WAKE_LOCK = "wake_lock";
    public static final String EXTRA_FOREGROUND = "foreground";
    public static final String EXTRA_FAKE_AUDIO = "fake_audio";
    public static final String EXTRA_STATUS_TEXT = "status_text";

    private static final String CHANNEL_ID = "linecode_keep_alive";
    private static final int NOTIFICATION_ID = 1001;

    private PowerManager.WakeLock wakeLock;
    private AudioTrack fakeAudioTrack;
    private NotificationManager notificationManager;
    private boolean isForeground = false;
    private String currentStatus = "正在编码";

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopKeepAlive();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE_STATUS.equals(action)) {
            String status = intent.getStringExtra(EXTRA_STATUS_TEXT);
            if (status != null && status.length() > 0) {
                currentStatus = status;
                updateNotification();
            }
            return START_STICKY;
        }

        if (ACTION_START.equals(action)) {
            boolean wakeLockEnabled = intent.getBooleanExtra(EXTRA_WAKE_LOCK, false);
            boolean foregroundEnabled = intent.getBooleanExtra(EXTRA_FOREGROUND, false);
            boolean fakeAudioEnabled = intent.getBooleanExtra(EXTRA_FAKE_AUDIO, false);

            startKeepAlive(wakeLockEnabled, foregroundEnabled, fakeAudioEnabled);
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopKeepAlive();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_keep_alive_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("LineCode 编码任务后台保活通知");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startKeepAlive(boolean wakeLockEnabled, boolean foregroundEnabled, boolean fakeAudioEnabled) {
        if (wakeLockEnabled) {
            acquireWakeLock();
        }

        if (foregroundEnabled) {
            startForeground(NOTIFICATION_ID, buildNotification());
            isForeground = true;
        }

        if (fakeAudioEnabled) {
            startFakeAudio();
        }
    }

    private void stopKeepAlive() {
        releaseWakeLock();
        stopFakeAudio();

        if (isForeground) {
            stopForeground(true);
            isForeground = false;
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null) {
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "LineCode:EncodingWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void startFakeAudio() {
        if (fakeAudioTrack != null) {
            return;
        }
        int sampleRate = 44100;
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build();
            fakeAudioTrack = new AudioTrack(attributes, format, bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        } else {
            fakeAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, channelConfig, audioFormat, bufferSize, AudioTrack.MODE_STREAM);
        }

        if (fakeAudioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            fakeAudioTrack.play();
            writeSilenceLoop();
        }
    }

    private void writeSilenceLoop() {
        new Thread(() -> {
            byte[] silence = new byte[1024];
            while (fakeAudioTrack != null && fakeAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                fakeAudioTrack.write(silence, 0, silence.length);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void stopFakeAudio() {
        if (fakeAudioTrack != null) {
            if (fakeAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                fakeAudioTrack.stop();
            }
            fakeAudioTrack.release();
            fakeAudioTrack = null;
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.notification_keep_alive_title))
                    .setContentText(currentStatus)
                    .setOngoing(true)
                    .setPriority(Notification.PRIORITY_LOW)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.notification_keep_alive_title))
                    .setContentText(currentStatus)
                    .setOngoing(true)
                    .build();
        }
    }

    private void updateNotification() {
        if (isForeground && notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    public static void start(Context context, boolean wakeLock, boolean foreground, boolean fakeAudio) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_WAKE_LOCK, wakeLock);
        intent.putExtra(EXTRA_FOREGROUND, foreground);
        intent.putExtra(EXTRA_FAKE_AUDIO, fakeAudio);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static void updateStatus(Context context, String status) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_UPDATE_STATUS);
        intent.putExtra(EXTRA_STATUS_TEXT, status);
        context.startService(intent);
    }
}