package dev.readingnotes.model;

import static dev.readingnotes.model.I18n.text;
import java.util.*;

/** Portable sharing format, separate from the IDE's persisted notebook schema. */
public final class NoteBundle {
    public static final String FORMAT = "reading-notes.share";
    public static final int MAX_NOTES = 5000;
    public String format;
    public int formatVersion;
    public long exportedAt;
    public List<Notebook.Topic> topics = new ArrayList<>();
    public List<Notebook.Group> groups = new ArrayList<>();
    public List<Entry> notes = new ArrayList<>();

    public static final class Entry {
        public Notebook.Note note = new Notebook.Note();
        public Anchor anchor = new Anchor();
    }
    public static final class Anchor {
        public String code = "";
        public List<String> before = new ArrayList<>();
        public List<String> after = new ArrayList<>();
        public String sourceHash = "";
        public boolean available;
        public boolean external;
    }

    public void validate() {
        require(FORMAT.equals(format) && formatVersion == 1, text("不是受支持的 Reading Notes 分享文件（格式版本应为 1）"));
        require(topics != null && groups != null && notes != null, text("分享文件缺少主题、分组或笔记列表"));
        require(topics.size() <= MAX_NOTES && groups.size() <= MAX_NOTES && notes.size() <= MAX_NOTES, text("分享文件条目过多，请分批导入"));
        Notebook.Data data = new Notebook.Data();
        data.topics = topics; data.groups = groups;
        for (Notebook.Topic t : topics) { require(t != null, text("主题不能为空")); field(t.id, 160); field(t.name, 160); }
        for (Notebook.Group g : groups) { require(g != null, text("分组不能为空")); field(g.id, 160); field(g.topicId, 160); field(g.name, 160); }
        for (Entry e : notes) {
            require(e != null && e.note != null && e.anchor != null, text("笔记或定位信息缺失"));
            Notebook.Note n = e.note; Anchor a = e.anchor;
            field(n.id, 160); field(n.groupId, 160); field(n.title, 160); field(n.text, 1_048_576);
            ShareEngine.relative(n.filePath);
            require(n.line > 0 && n.createdAt >= 0 && n.updatedAt >= 0, text("笔记行号或时间无效"));
            field(n.context, 4096); field(n.before, 4096); field(n.after, 4096); field(a.code, 4096);
            require(a.before != null && a.after != null && a.before.size() <= 3 && a.after.size() <= 3, text("源码上下文最多保留前后各 3 行"));
            a.before.forEach(s -> field(s, 4096)); a.after.forEach(s -> field(s, 4096));
            require(a.sourceHash != null && (a.sourceHash.isEmpty() || a.sourceHash.matches("[0-9a-f]{64}")), text("文件指纹无效"));
            data.notes.add(n);
        }
        Notebook checked = new Notebook(); checked.load(data);
        Notebook.Data normalized = checked.snapshot();
        topics = normalized.topics; groups = normalized.groups;
        for (int i = 0; i < notes.size(); i++) notes.get(i).note = normalized.notes.get(i);
    }

    private static void field(String value, int max) { require(value != null && value.length() <= max, text("分享文件含缺失或过长的文本字段")); }
    public static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
