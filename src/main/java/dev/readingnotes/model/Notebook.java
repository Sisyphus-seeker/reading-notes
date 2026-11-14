package dev.readingnotes.model;

import java.util.*;

/** Plain Java model. Every read returns a copy, so IDE persistence never races with edits. */
public final class Notebook {
    public static final class Data {
        public int version = 1;
        public List<Topic> topics = new ArrayList<>();
        public List<Group> groups = new ArrayList<>();
        public List<Note> notes = new ArrayList<>();
        public boolean horizontal = false;
        public float split = 0.45f;
        public String lastGroupId = "";
    }

    public static final class Topic {
        public String id = "";
        public String name = "";
        public Topic() {}
        public Topic(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static final class Group {
        public String id = "";
        public String topicId = "";
        public String name = "";
        public Group() {}
        public Group(String id, String topicId, String name) { this.id = id; this.topicId = topicId; this.name = name; }
        @Override public String toString() { return name; }
    }

    public static final class Note {
        public String id = "";
        public String groupId = "";
        public String title = "";
        public String text = "";
        /** Relative to the project root, or an absolute file:// URL for external files. */
        public String filePath = "";
        public int line = 1;
        public String context = "";
        public String before = "";
        public String after = "";
        public boolean detached = false;
        public long createdAt = 0;
        public long updatedAt = 0;
        public Note() {}
        public Note(Note n) {
            id = n.id; groupId = n.groupId; title = n.title; text = n.text;
            filePath = n.filePath; line = n.line; context = n.context;
            before = n.before; after = n.after; detached = n.detached;
            createdAt = n.createdAt; updatedAt = n.updatedAt;
        }
        @Override public String toString() { return title; }
    }

    private Data data;

    public Notebook() {
        data = new Data();
        Topic topic = addTopic("我的学习");
        Group group = addGroup(topic.id, "默认分组");
        data.lastGroupId = group.id;
    }

    public synchronized Data snapshot() { return copy(data); }

    public synchronized void load(Data incoming) {
        Objects.requireNonNull(incoming, "笔记数据不能为空");
        Data clean = copy(incoming);
        Set<String> ids = new HashSet<>();
        for (Topic t : clean.topics) {
            if (t.id.isBlank() || !ids.add(t.id)) throw new IllegalArgumentException("主题 ID 无效或重复");
            t.name = required(t.name);
        }
        Set<String> groupIds = new HashSet<>();
        for (Group g : clean.groups) {
            if (!ids.contains(g.topicId) || g.id.isBlank() || !groupIds.add(g.id)) throw new IllegalArgumentException("分组引用无效");
            g.name = required(g.name);
        }
        Set<String> noteIds = new HashSet<>();
        for (Note n : clean.notes) {
            if (!groupIds.contains(n.groupId) || n.id.isBlank() || !noteIds.add(n.id)) throw new IllegalArgumentException("笔记引用无效");
            n.title = required(n.title);
            n.line = Math.max(1, n.line);
        }
        clean.split = Math.max(.15f, Math.min(.85f, clean.split));
        data = clean;
    }

    public synchronized Topic addTopic(String name) {
        String value = required(name);
        if (data.topics.stream().anyMatch(t -> t.name.equals(value))) throw new IllegalArgumentException("已有同名主题");
        Topic t = new Topic(id(), value); data.topics.add(t);
        return new Topic(t.id, t.name);
    }

    public synchronized Group addGroup(String topicId, String name) {
        if (data.topics.stream().noneMatch(t -> t.id.equals(topicId))) throw new IllegalArgumentException("请先选择主题");
        String value = required(name);
        if (data.groups.stream().anyMatch(g -> g.topicId.equals(topicId) && g.name.equals(value))) throw new IllegalArgumentException("该主题下已有同名分组");
        Group g = new Group(id(), topicId, value); data.groups.add(g);
        return new Group(g.id, g.topicId, g.name);
    }

    public synchronized void renameTopic(String id, String name) {
        String value = required(name);
        if (data.topics.stream().anyMatch(t -> !t.id.equals(id) && t.name.equals(value))) throw new IllegalArgumentException("已有同名主题");
        data.topics.stream().filter(t -> t.id.equals(id)).findFirst().orElseThrow().name = value;
    }

    public synchronized void renameGroup(String id, String name) {
        Group g = data.groups.stream().filter(it -> it.id.equals(id)).findFirst().orElseThrow();
        String value = required(name);
        if (data.groups.stream().anyMatch(it -> !it.id.equals(id) && it.topicId.equals(g.topicId) && it.name.equals(value))) throw new IllegalArgumentException("该主题下已有同名分组");
        g.name = value;
    }

    public synchronized Note saveNote(Note draft) {
        if (data.groups.stream().noneMatch(g -> g.id.equals(draft.groupId))) throw new IllegalArgumentException("请选择笔记所属分组");
        Note value = new Note(draft);
        value.title = required(value.title);
        if (value.filePath.isBlank()) throw new IllegalArgumentException("请绑定代码文件");
        Note old = data.notes.stream().filter(n -> n.id.equals(value.id)).findFirst().orElse(null);
        long now = System.currentTimeMillis();
        if (old == null) {
            value.id = id(); value.createdAt = now;
        } else {
            value.createdAt = old.createdAt;
        }
        value.updatedAt = now;
        if (old != null) data.notes.remove(old);
        data.notes.add(value);
        data.lastGroupId = value.groupId;
        return new Note(value);
    }

    public synchronized Note note(String id) {
        return data.notes.stream().filter(n -> n.id.equals(id)).findFirst().map(Note::new).orElse(null);
    }

    public synchronized void deleteNote(String id) { data.notes.removeIf(n -> n.id.equals(id)); }

    public synchronized void deleteGroup(String id) {
        data.notes.removeIf(n -> n.groupId.equals(id));
        data.groups.removeIf(g -> g.id.equals(id));
        if (data.lastGroupId.equals(id)) data.lastGroupId = "";
    }

    public synchronized void deleteTopic(String id) {
        List<String> groups = data.groups.stream().filter(g -> g.topicId.equals(id)).map(g -> g.id).toList();
        groups.forEach(this::deleteGroup);
        data.topics.removeIf(t -> t.id.equals(id));
    }

    public synchronized void position(String id, String path, int line, String context, String before, String after, boolean detached) {
        data.notes.stream().filter(n -> n.id.equals(id)).findFirst().ifPresent(n -> {
            n.filePath = path; n.line = Math.max(1, line); n.context = context;
            n.before = before; n.after = after; n.detached = detached;
        });
    }

    public synchronized void layout(boolean horizontal, float split) {
        data.horizontal = horizontal; data.split = Math.max(.15f, Math.min(.85f, split));
    }

    public static boolean matches(Note n, String query) {
        String q = query.strip().toLowerCase(Locale.ROOT);
        return q.isEmpty() || (n.title + "\n" + n.text + "\n" + n.filePath).toLowerCase(Locale.ROOT).contains(q);
    }

    private static String required(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("名称或标题不能为空");
        if (text.strip().length() > 160) throw new IllegalArgumentException("名称或标题不能超过 160 个字符");
        return text.strip();
    }

    private static String id() { return UUID.randomUUID().toString(); }

    private static Data copy(Data from) {
        Data to = new Data();
        to.version = from.version; to.horizontal = from.horizontal; to.split = from.split;
        to.lastGroupId = from.lastGroupId == null ? "" : from.lastGroupId;
        for (Topic t : from.topics) to.topics.add(new Topic(t.id, t.name));
        for (Group g : from.groups) to.groups.add(new Group(g.id, g.topicId, g.name));
        for (Note n : from.notes) to.notes.add(new Note(n));
        return to;
    }
}
