package cn.lineai.mvp;

public interface BackgroundRunner {
    void execute(String name, Runnable runnable);
}
