package cn.lineai.model;

public final class ModelProviderPreset {
    private final String id;
    private final ModelProtocolType protocolType;
    private final String baseUrl;
    private final String placeholder;

    public ModelProviderPreset(String id, ModelProtocolType protocolType, String baseUrl, String placeholder) {
        this.id = id;
        this.protocolType = protocolType;
        this.baseUrl = baseUrl;
        this.placeholder = placeholder;
    }

    public String getId() {
        return id;
    }

    public ModelProtocolType getProtocolType() {
        return protocolType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getPlaceholder() {
        return placeholder;
    }
}
