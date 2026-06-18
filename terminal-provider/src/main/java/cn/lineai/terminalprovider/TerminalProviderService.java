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
import java.io.RandomAccessFile;
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
    // 单次分块传输的默认上限（1MB），避免 AIDL Binder 事务超限。
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
    private static final int MAX_CHUNK_SIZE = DEFAULT_CHUNK_SIZE;
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
            long size = file.length();
            if (size <= 0L) {
                return new byte[0];
            }
            if (size > Integer.MAX_VALUE) {
                Log.e(TAG, "readFile: 文件超过 int 范围: " + path);
                return new byte[0];
            }
            int total = (int) size;
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream(total);
                long offset = 0L;
                while (offset < total) {
                    int want = (int) Math.min((long) MAX_CHUNK_SIZE, (long) total - offset);
                    byte[] chunk = readFileChunk(path, offset, want);
                    if (chunk.length == 0) {
                        break;
                    }
                    buffer.write(chunk);
                    offset += chunk.length;
                }
                return buffer.toByteArray();
            } catch (Exception e) {
                Log.e(TAG, "readFile failed: " + path, e);
                return new byte[0];
            }
        }

        @Override
        public boolean writeFile(String path, byte[] data) {
            if (path == null || path.length() == 0) {
                return false;
            }
            byte[] payload = data == null ? new byte[0] : data;
            return writeFileChunk(path, 0L, payload);
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
        public byte[] readFileChunk(String path, long offset, int size) {
            if (path == null || path.length() == 0) {
                return new byte[0];
            }
            if (offset < 0L) {
                Log.w(TAG, "readFileChunk: offset<0 已按 0 处理: " + path);
                offset = 0L;
            }
            int want = size <= 0 ? DEFAULT_CHUNK_SIZE : size;
            if (want > MAX_CHUNK_SIZE) {
                want = MAX_CHUNK_SIZE;
            }
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                return new byte[0];
            }
            long fileLength = file.length();
            if (offset >= fileLength) {
                return new byte[0];
            }
            long remaining = fileLength - offset;
            int toRead = (int) Math.min((long) want, remaining);
            byte[] out = new byte[toRead];
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset);
                int read = 0;
                while (read < toRead) {
                    int n = raf.read(out, read, toRead - read);
                    if (n <= 0) {
                        break;
                    }
                    read += n;
                }
                if (read < toRead) {
                    byte[] trimmed = new byte[read];
                    System.arraycopy(out, 0, trimmed, 0, read);
                    return trimmed;
                }
                return out;
            } catch (IOException e) {
                Log.e(TAG, "readFileChunk failed: " + path + "@" + offset, e);
                return new byte[0];
            }
        }

        @Override
        public boolean writeFileChunk(String path, long offset, byte[] data) {
            if (path == null || path.length() == 0) {
                return false;
            }
            byte[] payload = data == null ? new byte[0] : data;
            File file = new File(path);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    return false;
                }
            }
            boolean append = offset < 0L;
            if (append) {
                try (FileOutputStream fos = new FileOutputStream(file, true)) {
                    fos.write(payload);
                    fos.flush();
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "writeFileChunk(append) failed: " + path, e);
                    return false;
                }
            }
            if (offset < 0L) {
                offset = 0L;
            }
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(offset);
                raf.write(payload);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "writeFileChunk failed: " + path + "@" + offset, e);
                return false;
            }
        }

        @Override
        public long getFileSize(String path) {
            return fileSize(path);
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
