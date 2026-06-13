package cn.lineai.mvp.agent;

public interface AgentProgressMirror {
    void onAgentProgress(String payload, boolean error);
}