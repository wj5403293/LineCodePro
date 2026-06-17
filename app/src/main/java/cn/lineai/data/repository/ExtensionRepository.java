package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.model.SkillRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * 扩展仓库门面，委托到 AgentExtensionRepository、McpExtensionRepository、SkillRepository 三个子仓库。
 * 保留原有公有 API，调用方无需修改。
 */
public final class ExtensionRepository extends BaseRepository {
    private final AgentExtensionRepository agentRepository;
    private final McpExtensionRepository mcpRepository;
    private final SkillRepository skillRepository;

    public ExtensionRepository(Context context) {
        super(LineCodeDatabase.getInstance(context.getApplicationContext()));
        LineCodeDatabase database = LineCodeDatabase.getInstance(context.getApplicationContext());
        this.agentRepository = new AgentExtensionRepository(database);
        this.mcpRepository = new McpExtensionRepository(database);
        this.skillRepository = new SkillRepository(context, this.agentRepository, this.mcpRepository);
    }

    public synchronized ExtensionOverviewState getOverview(String homePath) {
        List<SkillRecord> skills = getSkills(homePath);
        return new ExtensionOverviewState(getAgentExtensions(), getMcpExtensions(), skills);
    }

    public synchronized List<ExtensionAgentConfig> getAgentExtensions() {
        return agentRepository.getAgentExtensions();
    }

    public synchronized ExtensionAgentConfig saveAgentExtension(ExtensionAgentConfig input) {
        return agentRepository.saveAgentExtension(input);
    }

    public synchronized void setAgentEnabled(String id, boolean enabled) {
        agentRepository.setAgentEnabled(id, enabled);
    }

    public synchronized void deleteAgent(String id) {
        agentRepository.deleteAgent(id);
    }

    public synchronized List<ExtensionMcpConfig> getMcpExtensions() {
        return mcpRepository.getMcpExtensions();
    }

    public synchronized ExtensionMcpConfig saveMcpExtension(ExtensionMcpConfig input) {
        return mcpRepository.saveMcpExtension(input);
    }

    public synchronized void setMcpEnabled(String id, boolean enabled) {
        mcpRepository.setMcpEnabled(id, enabled);
    }

    public synchronized void deleteMcp(String id) {
        mcpRepository.deleteMcp(id);
    }

    public List<McpToolSummary> queryMcpTools(String url, List<McpRequestHeader> headers) throws Exception {
        return mcpRepository.queryMcpTools(url, headers);
    }

    public synchronized List<SkillRecord> getSkills(String homePath) {
        return skillRepository.getSkills(homePath);
    }

    public synchronized SkillRecord createSkill(String homePath, String location, String name, String description, String content) {
        return skillRepository.createSkill(homePath, location, name, description, content);
    }

    public synchronized SkillRecord installSkill(String homePath, String location, String sourcePath, String name) throws Exception {
        return skillRepository.installSkill(homePath, location, sourcePath, name);
    }

    public synchronized SkillRecord installSkillFromUri(String homePath, String location, String uri, String displayName) throws Exception {
        return skillRepository.installSkillFromUri(homePath, location, uri, displayName);
    }

    public synchronized void setSkillEnabled(String id, boolean enabled) {
        skillRepository.setSkillEnabled(id, enabled);
    }

    public synchronized void deleteSkill(String id) {
        skillRepository.deleteSkill(id);
    }

    public synchronized String buildExtensionPrompt(String homePath) {
        return skillRepository.buildExtensionPrompt(homePath);
    }

    public ArrayList<String> skillWriteRoots(String homePath) {
        return skillRepository.skillWriteRoots(homePath);
    }
}
