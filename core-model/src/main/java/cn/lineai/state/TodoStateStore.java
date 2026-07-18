package cn.lineai.state;

import cn.lineai.model.TodoItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TodoStateStore {
    private final List<TodoItem> items = new ArrayList<>();

    public synchronized void replace(List<TodoItem> next) {
        items.clear();
        if (next != null) {
            for (TodoItem item : next) {
                if (item != null) {
                    items.add(item);
                }
            }
        }
    }

    public synchronized List<TodoItem> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public synchronized int completedCount() {
        int count = 0;
        for (TodoItem item : items) {
            if (item != null && item.isCompleted()) {
                count++;
            }
        }
        return count;
    }

    public synchronized int totalCount() {
        return items.size();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    public synchronized void clear() {
        items.clear();
    }
}
