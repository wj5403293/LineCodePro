package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import java.util.List;

/**
 * 按协议类型获取模型目录列表的策略接口。
 * 每种协议实现提供自己的 CatalogFetcher 实例，
 * 注册到 ModelCatalogClient 以消除 if-else 分发。
 */
public interface CatalogFetcher {
    List<String> fetch(String baseUrl, String apiKey) throws ModelCompletionException;
}
