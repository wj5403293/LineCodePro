package cn.lineai.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ProjectRepository {
    private final Context context;
    private final LineCodeDatabase database;
    private final WorkspacePaths workspacePaths;

    public ProjectRepository(Context context) {
        this.context = context.getApplicationContext();
        database = LineCodeDatabase.getInstance(context);
        workspacePaths = new WorkspacePaths(this.context);
        workspacePaths.ensurePrivateRoots();
        ensureDefaultProject();
    }

    public synchronized List<ProjectRecord> getProjects() {
        ArrayList<ProjectRecord> projects = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().query(
                "projects",
                null,
                null,
                null,
                null,
                null,
                "selected DESC, updated_at DESC"
        );
        try {
            while (cursor.moveToNext()) {
                projects.add(readProject(cursor));
            }
        } finally {
            cursor.close();
        }
        if (projects.isEmpty()) {
            ensureDefaultProject();
            return getProjects();
        }
        return projects;
    }

    public synchronized ProjectRecord getSelectedProject() {
        Cursor cursor = database.getReadableDatabase().query(
                "projects",
                null,
                "selected = 1",
                null,
                null,
                null,
                "updated_at DESC",
                "1"
        );
        try {
            if (cursor.moveToFirst()) {
                return readProject(cursor);
            }
        } finally {
            cursor.close();
        }
        List<ProjectRecord> projects = getProjects();
        return projects.isEmpty() ? defaultProject() : projects.get(0);
    }

    public synchronized void save(ProjectRecord project) {
        ensureProjectPath(project);
        ContentValues values = valuesFor(project);
        SQLiteDatabase db = database.getWritableDatabase();
        db.insertWithOnConflict("projects", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (project.isSelected()) {
            setSelected(project.getId());
        }
    }

    public synchronized void setSelected(String id) {
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            selectProjectInTransaction(db, id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized boolean deleteProject(String id) {
        String safeId = id == null ? "" : id.trim();
        if (safeId.length() == 0 || WorkspacePaths.DEFAULT_PROJECT_ID.equals(safeId)) {
            return false;
        }
        ensureDefaultProject();
        ProjectRecord project = findProject(safeId);
        if (project == null) {
            return false;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            int deleted = db.delete("projects", "id = ?", new String[] {safeId});
            if (deleted > 0 && project.isSelected()) {
                selectProjectInTransaction(db, WorkspacePaths.DEFAULT_PROJECT_ID);
            }
            db.setTransactionSuccessful();
            return deleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    public synchronized ProjectRecord createManagedProject(String name) {
        String cleanName = sanitizeProjectName(name);
        if (cleanName.length() == 0) {
            cleanName = "Project-" + System.currentTimeMillis();
        }
        File path = new File(workspacePaths.getProjectRoot(), cleanName);
        path.mkdirs();
        long now = System.currentTimeMillis();
        ProjectRecord project = new ProjectRecord(
                "managed:" + cleanName.toLowerCase(),
                cleanName,
                path.getAbsolutePath(),
                WorkspacePaths.SOURCE_MANAGED,
                ".linecode/project",
                true,
                now,
                now
        );
        save(project);
        return getSelectedProject();
    }

    public synchronized ProjectRecord saveExternalProject(String path, String label) {
        String resolved = path == null ? "" : path.trim();
        if (resolved.length() == 0) {
            return getSelectedProject();
        }
        String name = label == null || label.trim().length() == 0
                ? WorkspacePaths.basename(resolved)
                : label.trim();
        if (name.length() == 0) {
            name = "外部工作区";
        }
        long now = System.currentTimeMillis();
        ProjectRecord project = new ProjectRecord(
                "external:" + resolved,
                name,
                resolved,
                WorkspacePaths.SOURCE_EXTERNAL,
                resolved,
                true,
                now,
                now
        );
        save(project);
        return getSelectedProject();
    }

    public synchronized ProjectRecord ensureSelectedProjectPath() {
        ProjectRecord selected = getSelectedProject();
        ensureProjectPath(selected);
        return selected;
    }

    public String getLinecodeRootPath() {
        return workspacePaths.getLinecodeRoot().getAbsolutePath();
    }

    public String getDefaultHomePath() {
        return workspacePaths.getHomeRoot().getAbsolutePath();
    }

    private ProjectRecord findProject(String id) {
        Cursor cursor = database.getReadableDatabase().query(
                "projects",
                null,
                "id = ?",
                new String[] {id},
                null,
                null,
                null,
                "1"
        );
        try {
            if (cursor.moveToFirst()) {
                return readProject(cursor);
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    private void ensureDefaultProject() {
        SQLiteDatabase db = database.getWritableDatabase();
        Cursor cursor = db.query("projects", null, "id = ?", new String[] {WorkspacePaths.DEFAULT_PROJECT_ID}, null, null, null, "1");
        try {
            if (cursor.moveToFirst()) {
                ProjectRecord existing = readProject(cursor);
                ProjectRecord defaultProject = defaultProject();
                if (!existing.getPath().equals(defaultProject.getPath())
                        || !WorkspacePaths.SOURCE_DEFAULT.equals(existing.getSource())) {
                    save(new ProjectRecord(
                            WorkspacePaths.DEFAULT_PROJECT_ID,
                            defaultProject.getLabel(),
                            defaultProject.getPath(),
                            WorkspacePaths.SOURCE_DEFAULT,
                            defaultProject.getDescription(),
                            existing.isSelected(),
                            existing.getCreatedAt(),
                            System.currentTimeMillis()
                    ));
                } else {
                    ensureProjectPath(existing);
                }
                return;
            }
        } finally {
            cursor.close();
        }
        save(defaultProject());
    }

    private ProjectRecord defaultProject() {
        long now = System.currentTimeMillis();
        File home = workspacePaths.getHomeRoot();
        return new ProjectRecord(
                WorkspacePaths.DEFAULT_PROJECT_ID,
                "LineCode",
                home.getAbsolutePath(),
                WorkspacePaths.SOURCE_DEFAULT,
                "默认 home 工作区",
                true,
                now,
                now
        );
    }

    private void ensureProjectPath(ProjectRecord project) {
        if (project == null) {
            return;
        }
        if (WorkspacePaths.SOURCE_EXTERNAL.equals(project.getSource())) {
            File root = new File(project.getPath());
            if (root.isDirectory()) {
                new File(root, ".linecode/skills").mkdirs();
            }
            return;
        }
        File path = workspacePaths.resolvePrivatePath(project.getPath());
        if (!path.exists()) {
            path.mkdirs();
        }
        new File(path, ".linecode/skills").mkdirs();
    }

    private String sanitizeProjectName(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", "-");
        if (value.length() > 60) {
            value = value.substring(0, 60);
        }
        return value;
    }

    private void selectProjectInTransaction(SQLiteDatabase db, String id) {
        ContentValues clear = new ContentValues();
        clear.put("selected", 0);
        db.update("projects", clear, null, null);

        ContentValues select = new ContentValues();
        select.put("selected", 1);
        select.put("updated_at", System.currentTimeMillis());
        int updated = db.update("projects", select, "id = ?", new String[] {id});
        if (updated == 0) {
            db.update("projects", select, "id = ?", new String[] {WorkspacePaths.DEFAULT_PROJECT_ID});
        }
    }

    private ContentValues valuesFor(ProjectRecord project) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", project.getId());
        values.put("label", project.getLabel());
        values.put("path", project.getPath());
        values.put("source", project.getSource());
        values.put("description", project.getDescription());
        values.put("selected", project.isSelected() ? 1 : 0);
        values.put("created_at", project.getCreatedAt() > 0 ? project.getCreatedAt() : now);
        values.put("updated_at", project.getUpdatedAt() > 0 ? project.getUpdatedAt() : now);
        return values;
    }

    private ProjectRecord readProject(Cursor cursor) {
        return new ProjectRecord(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("label")),
                cursor.getString(cursor.getColumnIndexOrThrow("path")),
                cursor.getString(cursor.getColumnIndexOrThrow("source")),
                cursor.getString(cursor.getColumnIndexOrThrow("description")),
                cursor.getInt(cursor.getColumnIndexOrThrow("selected")) == 1,
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        );
    }
}
