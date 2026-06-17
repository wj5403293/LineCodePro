package cn.lineai.terminalprovider;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.ITerminalProviderService;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.json.JSONObject;

public final class TerminalProviderService extends Service {
    private static final String TAG = "TerminalProvider";
    private static final String SHELL = "/system/bin/sh";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final ITerminalProviderService.Stub binder = new ITerminalProviderService.Stub() {
        @Override
        public String getProviderType() {
            return "terminal";
        }

        @Override
        public String getProviderInfo() {
            JSONObject info = new JSONObject();
            try {
                info.put("name", "Android Shell Terminal Provider");
                info.put("version", "1.0");
                info.put("shell", SHELL);
                info.put("capabilities", new org.json.JSONArray()
                        .put("executeShell")
                        .put("readFile")
                        .put("writeFile")
                        .put("deleteFile")
                        .put("listDir")
                        .put("fileExists")
                        .put("fileSize"));
            } catch (Exception ignored) {
            }
            return info.toString();
        }

        @Override
        public boolean isAvailable() {
            return new File(SHELL).exists();
        }

        @Override
        public int executeShell(String command, String cwd, long timeoutMs,
                                 ITerminalProviderCallback callback) {
            if (command == null || command.length() == 0) {
                if (callback != null) {
                    try {
                        callback.onError("命令为空");
                    } catch (RemoteException ignored) {
                    }
                }
                return -1;
            }
            File workingDir = (cwd != null && cwd.length() > 0)
                    ? new File(cwd)
                    : new File(getFilesDir().getAbsolutePath());
            if (!workingDir.exists()) {
                workingDir = getFilesDir();
            }
            final File finalWorkingDir = workingDir;
            Future<Integer> future = executor.submit(() -> {
                Process process = null;
                BufferedReader stdoutReader = null;
                BufferedReader stderrReader = null;
                try {
                    ProcessBuilder pb = new ProcessBuilder(SHELL, "-c", command);
                    pb.directory(finalWorkingDir);
                    pb.redirectErrorStream(false);
                    process = pb.start();
                    stdoutReader = new BufferedReader(
                            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    stderrReader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
                    final BufferedReader finalStdout = stdoutReader;
                    final BufferedReader finalStderr = stderrReader;
                    final Process finalProcess = process;
                    Thread stdoutThread = new Thread(() -> {
                        String line;
                        try {
                            while ((line = finalStdout.readLine()) != null) {
                                if (callback != null) {
                                    callback.onOutput(line + "\n");
                                }
                            }
                        } catch (IOException | RemoteException ignored) {
                        }
                    });
                    Thread stderrThread = new Thread(() -> {
                        String line;
                        try {
                            while ((line = finalStderr.readLine()) != null) {
                                if (callback != null) {
                                    callback.onOutput(line + "\n");
                                }
                            }
                        } catch (IOException | RemoteException ignored) {
                        }
                    });
                    stdoutThread.start();
                    stderrThread.start();
                    int exitCode = finalProcess.waitFor();
                    stdoutThread.join(1000);
                    stderrThread.join(1000);
                    if (callback != null) {
                        callback.onComplete(exitCode);
                    }
                    return exitCode;
                } catch (Exception e) {
                    Log.e(TAG, "executeShell failed", e);
                    if (callback != null) {
                        try {
                            callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
                        } catch (RemoteException ignored) {
                        }
                    }
                    return -1;
                } finally {
                    if (stdoutReader != null) {
                        try { stdoutReader.close(); } catch (IOException ignored) {}
                    }
                    if (stderrReader != null) {
                        try { stderrReader.close(); } catch (IOException ignored) {}
                    }
                    if (process != null) {
                        process.destroy();
                    }
                }
            });
            try {
                long effectiveTimeout = timeoutMs > 0 ? timeoutMs : 30000L;
                return future.get(effectiveTimeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                if (callback != null) {
                    try {
                        callback.onError("命令执行超时");
                    } catch (RemoteException ignored) {
                    }
                }
                return -2;
            } catch (Exception e) {
                Log.e(TAG, "executeShell wait failed", e);
                if (callback != null) {
                    try {
                        callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
                    } catch (RemoteException ignored) {
                    }
                }
                return -1;
            }
        }

        @Override
        public byte[] readFile(String path) {
            if (path == null || path.length() == 0) {
                return new byte[0];
            }
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                return new byte[0];
            }
            try (InputStream is = new java.io.FileInputStream(file)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                while ((read = is.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                return buffer.toByteArray();
            } catch (IOException e) {
                Log.e(TAG, "readFile failed: " + path, e);
                return new byte[0];
            }
        }

        @Override
        public boolean writeFile(String path, byte[] data) {
            if (path == null || path.length() == 0) {
                return false;
            }
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    return false;
                }
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data == null ? new byte[0] : data);
                fos.flush();
                return true;
            } catch (IOException e) {
                Log.e(TAG, "writeFile failed: " + path, e);
                return false;
            }
        }

        @Override
        public boolean deleteFile(String path) {
            if (path == null || path.length() == 0) {
                return false;
            }
            File file = new File(path);
            if (!file.exists()) {
                return false;
            }
            return file.delete();
        }

        @Override
        public String[] listDir(String path) {
            if (path == null || path.length() == 0) {
                path = getFilesDir().getAbsolutePath();
            }
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                return new String[0];
            }
            File[] files = dir.listFiles();
            if (files == null) {
                return new String[0];
            }
            List<String> result = new ArrayList<>();
            for (File f : files) {
                result.add(f.getName());
            }
            return result.toArray(new String[0]);
        }

        @Override
        public boolean fileExists(String path) {
            if (path == null || path.length() == 0) {
                return false;
            }
            return new File(path).exists();
        }

        @Override
        public long fileSize(String path) {
            if (path == null || path.length() == 0) {
                return -1;
            }
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                return -1;
            }
            return file.length();
        }

        @Override
        public String listDirDetailed(String path) {
            if (path == null || path.length() == 0) {
                path = getFilesDir().getAbsolutePath();
            }
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                return "[]";
            }
            File[] files = dir.listFiles();
            if (files == null) {
                return "[]";
            }
            org.json.JSONArray array = new org.json.JSONArray();
            for (File f : files) {
                try {
                    JSONObject entry = new JSONObject();
                    entry.put("name", f.getName());
                    entry.put("dir", f.isDirectory());
                    entry.put("size", f.length());
                    array.put(entry);
                } catch (Exception ignored) {
                }
            }
            return array.toString();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "TerminalProviderService onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "TerminalProviderService onUnbind");
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
