package cn.lineai.ipc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public abstract class BaseIpcProvider {
    protected final IpcProviderConfig config;
    protected volatile IBinder serviceBinder;
    protected volatile boolean bound;
    private ServiceConnection connection;

    protected BaseIpcProvider(IpcProviderConfig config) {
        this.config = config;
    }

    public abstract IpcProviderType getProviderType();

    public final synchronized boolean bind(Context context) {
        if (bound) {
            return true;
        }
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(getProviderType().getIntentAction());
        intent.setPackage(config.getPackageName());
        if (config.getServiceClass() != null && config.getServiceClass().length() > 0) {
            intent.setClassName(config.getPackageName(), config.getServiceClass());
        }
        connection = createConnection();
        try {
            bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException ignored) {
            bound = false;
        }
        return bound;
    }

    public final synchronized void unbind(Context context) {
        if (!bound || connection == null) {
            bound = false;
            connection = null;
            serviceBinder = null;
            return;
        }
        try {
            context.getApplicationContext().unbindService(connection);
        } catch (IllegalArgumentException ignored) {
        }
        connection = null;
        serviceBinder = null;
        bound = false;
    }

    public final boolean isBound() {
        return bound;
    }

    public final IpcProviderConfig getConfig() {
        return config;
    }

    public final IBinder getServiceBinder() {
        return serviceBinder;
    }

    public boolean requiresConfirmation() {
        return false;
    }

    protected final void onServiceConnected(IBinder binder) {
        this.serviceBinder = binder;
    }

    protected final void onServiceDisconnected() {
        this.serviceBinder = null;
    }

    private ServiceConnection createConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                BaseIpcProvider.this.onServiceConnected(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                BaseIpcProvider.this.onServiceDisconnected();
            }
        };
    }
}
