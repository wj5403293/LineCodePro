package cn.lineai.mvp;

public interface CoordinatorHost {
    String basename(String path);

    void render();

    String parentPath(String path);
}
