package cn.lineai.data.repository;

import cn.lineai.ai.prompt.StringTemplate;
import cn.lineai.model.Strings;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.HashMap;
import java.util.List;

public final class MemoryPromptBuilder {
    private final WorkspacePaths workspacePaths;
    private final PromptTemplateRepository promptTemplateRepository;

    public MemoryPromptBuilder(WorkspacePaths workspacePaths, PromptTemplateRepository promptTemplateRepository) {
        this.workspacePaths = workspacePaths;
        this.promptTemplateRepository = promptTemplateRepository;
    }

    public String build(
            String projectId,
            List<MemoryRanker.Candidate> workingMemory,
            List<MemoryRanker.Candidate> memories,
            List<MemoryRanker.Candidate> history,
            List<MemoryRanker.Candidate> skills
    ) {
        HashMap<String, String> values = new HashMap<>();
        values.put("WORKING_MEMORY_SECTION", section("### 短期/工作记忆（当前项目 RAG Top-K）", workingMemory));
        values.put("MEMORY_SECTION", section("### 长期记忆（本地检索 Top-K）", memories));
        values.put("HISTORY_SECTION", section("### 相关聊天记录（当前项目本地检索 Top-K）", history));
        values.put("SKILL_PATHS_SECTION", skillPathsSection(projectId));
        values.put("SKILLS_SECTION", section("### 可用 Skills（RAG Top-K）", skills));
        values.put("PRIVATE_BOUNDARY_SECTION", privateBoundarySection());
        return template().render(values);
    }

    private String section(String title, List<MemoryRanker.Candidate> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(title);
        for (MemoryRanker.Candidate row : rows) {
            builder.append('\n').append(row.formatted);
        }
        return builder.toString();
    }

    private String skillPathsSection(String projectId) {
        StringBuilder builder = new StringBuilder("### Skills 路径");
        builder.append('\n').append("- app: ").append(workspacePaths.getSkillsRoot().getAbsolutePath());
        if (Strings.nullToEmpty(projectId).length() > 0) {
            builder.append('\n').append("- project: ").append(new File(projectId, ".linecode/skills").getAbsolutePath());
        }
        builder.append('\n').append("- ssh: ~/.linecode/skills");
        builder.append('\n').append("需要完整 Skill 指南时，可使用文件读取/搜索/列目录工具访问对应 SKILL.md；Skills 的创建和安装由扩展系统处理，文件工具只维护已授权目录里的 SKILL.md。");
        return builder.toString();
    }

    private String privateBoundarySection() {
        String skillsRoot = workspacePaths.getSkillsRoot().getAbsolutePath();
        return "### 私有目录边界\n"
                + "文件工具可使用系统明确注入的 Skills 路径（例如 " + skillsRoot + " 和当前项目 .linecode/skills）；不要猜测或访问其他应用私有目录。";
    }

    private StringTemplate template() {
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_LEARNING_CONTEXT));
    }
}
