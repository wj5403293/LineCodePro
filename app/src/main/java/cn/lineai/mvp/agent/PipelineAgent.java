package cn.lineai.mvp.agent;

import java.util.ArrayList;

public final class PipelineAgent {
    private final String id;
    private final String type;
    private final String description;
    private final String prompt;
    private final ArrayList<String> readScope;
    private final ArrayList<String> writeScope;
    private final ArrayList<String> dependencies;

    public PipelineAgent(
            String id,
            String type,
            String description,
            String prompt,
            ArrayList<String> readScope,
            ArrayList<String> writeScope,
            ArrayList<String> dependencies
    ) {
        this.id = id == null ? "" : id;
        this.type = type == null ? "" : type;
        this.description = description == null ? "" : description;
        this.prompt = prompt == null ? "" : prompt;
        this.readScope = readScope == null ? new ArrayList<>() : readScope;
        this.writeScope = writeScope == null ? new ArrayList<>() : writeScope;
        this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getPrompt() {
        return prompt;
    }

    public ArrayList<String> getReadScope() {
        return readScope;
    }

    public ArrayList<String> getWriteScope() {
        return writeScope;
    }

    public ArrayList<String> getDependencies() {
        return dependencies;
    }
}