package cn.lineai.ui.component.toolcall;

import android.content.Context;
import cn.lineai.tool.ToolDisplayCategory;
import java.util.HashMap;
import java.util.Map;

public final class ToolCallViewFactoryRegistry {
    private final Map<ToolDisplayCategory, ToolCallViewFactory> factories = new HashMap<>();

    public void register(ToolCallViewFactory factory) {
        factories.put(factory.category(), factory);
    }

    public ToolCallCardView createView(Context context, ToolDisplayCategory category) {
        ToolCallViewFactory factory = factories.get(category);
        if (factory != null) {
            return factory.createView(context);
        }
        ToolCallViewFactory genericFactory = factories.get(ToolDisplayCategory.GENERIC);
        if (genericFactory != null) {
            return genericFactory.createView(context);
        }
        return null;
    }
}
