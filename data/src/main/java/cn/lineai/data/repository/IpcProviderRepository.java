package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderType;
import java.util.List;
import java.util.UUID;

public final class IpcProviderRepository extends BaseRepository implements IpcProviderStore {

    public IpcProviderRepository(LineCodeDatabase database) {
        super(database);
    }

    @Override
    public synchronized List<IpcProviderConfig> getProviders() {
        return queryList(
                "SELECT id, enabled, provider_type, name, package_name, service_class, created_at, updated_at "
                        + "FROM ipc_providers ORDER BY updated_at DESC",
                new String[0],
                this::mapConfig
        );
    }

    @Override
    public synchronized List<IpcProviderConfig> getProvidersByType(String providerType) {
        return queryList(
                "SELECT id, enabled, provider_type, name, package_name, service_class, created_at, updated_at "
                        + "FROM ipc_providers WHERE provider_type = ? ORDER BY updated_at DESC",
                new String[] {providerType == null ? "" : providerType},
                this::mapConfig
        );
    }

    @Override
    public synchronized List<IpcProviderConfig> getProvidersByType(IpcProviderType type) {
        return getProvidersByType(type.getId());
    }

    @Override
    public synchronized IpcProviderConfig saveProvider(IpcProviderConfig input) {
        long now = System.currentTimeMillis();
        String id = input.getId() == null || input.getId().length() == 0
                ? "ipc_" + UUID.randomUUID().toString().replace("-", "")
                : input.getId();
        long createdAt = input.getCreatedAt() <= 0 ? now : input.getCreatedAt();
        IpcProviderConfig saved = IpcProviderConfig.builder()
                .id(id)
                .enabled(input.isEnabled())
                .providerType(input.getProviderType())
                .name(input.getName())
                .packageName(input.getPackageName())
                .serviceClass(input.getServiceClass())
                .createdAt(createdAt)
                .updatedAt(now)
                .build();
        ContentValues values = new ContentValues();
        values.put("id", saved.getId());
        values.put("enabled", saved.isEnabled() ? 1 : 0);
        values.put("provider_type", saved.getProviderType());
        values.put("name", saved.getName());
        values.put("package_name", saved.getPackageName());
        values.put("service_class", saved.getServiceClass());
        values.put("created_at", saved.getCreatedAt());
        values.put("updated_at", saved.getUpdatedAt());
        values.put("raw_json", "");
        insertOrReplace("ipc_providers", values);
        return saved;
    }

    @Override
    public synchronized void setProviderEnabled(String id, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("enabled", enabled ? 1 : 0);
        values.put("updated_at", System.currentTimeMillis());
        database.getWritableDatabase().update(
                "ipc_providers", values, "id = ?", new String[] {id == null ? "" : id});
    }

    @Override
    public synchronized void deleteProvider(String id) {
        deleteById("ipc_providers", id);
    }

    private IpcProviderConfig mapConfig(Cursor cursor) {
        return IpcProviderConfig.builder()
                .id(value(cursor, "id"))
                .enabled(intValue(cursor, "enabled") != 0)
                .providerType(value(cursor, "provider_type"))
                .name(value(cursor, "name"))
                .packageName(value(cursor, "package_name"))
                .serviceClass(value(cursor, "service_class"))
                .createdAt(longValue(cursor, "created_at"))
                .updatedAt(longValue(cursor, "updated_at"))
                .build();
    }

}
