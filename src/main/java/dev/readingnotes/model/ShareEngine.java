package dev.readingnotes.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** No IDE dependencies: conservative relocation, portable paths and non-destructive merges. */
public final class ShareEngine {
    private ShareEngine() {}
    public interface SourceAccess { String read(Path path) throws IOException; }
    public enum Scope { ALL, TOPIC, GROUP, NOTE }
    public enum Match { RELIABLE, REVIEW, MISSING }
    public enum Action {
        IMPORT("导入"), SKIP("跳过"), REPLACE("替换当前"), COPY("保留两条");
        private final String label;
        Action(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public record Candidate(int line, String code, int support) {
        @Override public String toString() { return "第 " + line + " 行：" + clip(code.strip(), 90); }
    }
    public static final class Row {
        public final NoteBundle.Entry entry;
        public final String destinationGroup;
        public String path;
        public String source = "";
        public String hash = "";
        public String reason = "";
        public Match match = Match.MISSING;
        public final List<Candidate> candidates = new ArrayList<>();
        public int chosenLine;
        public boolean duplicate;
        public boolean conflict;
        public Action action;
        private Notebook.Note existing;
        private Row(NoteBundle.Entry entry, String group) { this.entry = entry; destinationGroup = group; }
        public boolean attached() { return chosenLine > 0 && !hash.isEmpty(); }
        public void chooseLine(int line) {
            NoteBundle.require(line > 0 && line <= lines(source).size() && !hash.isEmpty(), "请先选择有效的目标文件和行号");
            chosenLine = line;
            classify();
        }
        private void classify() {
            boolean content = existing != null && existing.title.equals(entry.note.title) && existing.text.equals(entry.note.text) && existing.groupId.equals(destinationGroup);
            boolean location = existing != null && existing.filePath.equals(path) && existing.detached == !attached();
            if (location && attached()) {
                List<String> lines = lines(source); int i = chosenLine - 1;
                location = existing.line == chosenLine && existing.context.equals(lines.get(i).strip())
                    && existing.before.equals(i > 0 ? lines.get(i - 1).strip() : "")
                    && existing.after.equals(i + 1 < lines.size() ? lines.get(i + 1).strip() : "");
            } else if (location) {
                location = existing.line == entry.note.line && existing.context.equals(entry.note.context)
                    && existing.before.equals(entry.note.before) && existing.after.equals(entry.note.after);
            }
            duplicate = content && location; conflict = existing != null && !duplicate;
            if (action == null) action = existing == null ? Action.IMPORT : Action.SKIP;
            if (duplicate) action = Action.SKIP;
        }
        public String status() { return attached() ? (match == Match.RELIABLE ? "可靠匹配" : "已手动确认") : match == Match.MISSING ? "文件缺失" : "位置待确认"; }
    }
    public static final class Plan {
        public final Notebook.Data base;
        public final Notebook.Data hierarchy;
        public final Path projectRoot;
        public final List<Row> rows = new ArrayList<>();
        private Plan(Notebook.Data base, Notebook.Data hierarchy, Path root) { this.base = base; this.hierarchy = hierarchy; projectRoot = root; }
        public long selectedCount() { return rows.stream().filter(r -> r.action != Action.SKIP).count(); }
    }

    public static List<String> lines(String text) {
        String clean = text.replace("\r\n", "\n").replace('\r', '\n');
        if (clean.startsWith("\ufeff")) clean = clean.substring(1);
        return Arrays.asList(clean.split("\n", -1));
    }
    public static String hash(String text) {
        try {
            String normalized = String.join("\n", lines(text));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String clip(String value, int max) {
        if (value.length() <= max) return value;
        int end = max;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
    public static String relative(String value) {
        NoteBundle.require(value != null && !value.isBlank() && value.length() <= 1024, "文件相对路径缺失或过长");
        String p = value.replace('\\', '/');
        NoteBundle.require(!p.startsWith("/") && p.indexOf(':') < 0 && p.indexOf('\0') < 0, "分享文件只能使用项目内相对路径");
        for (String part : p.split("/", -1))
            NoteBundle.require(!part.isEmpty() && !part.equals(".") && !part.equals(".."), "相对路径不能包含空目录、. 或 ..");
        return p;
    }
    public static Path safeFile(Path root, String relative) throws IOException {
        Path realRoot = root.toRealPath();
        Path candidate = realRoot.resolve(relative(relative)).normalize();
        NoteBundle.require(candidate.startsWith(realRoot), "目标文件位于所选目录之外");
        Path ancestor = candidate;
        while (!Files.exists(ancestor)) {
            NoteBundle.require(!Files.isSymbolicLink(ancestor), "文件路径包含失效的链接，请重新选择文件");
            ancestor = ancestor.getParent();
        }
        NoteBundle.require(ancestor.toRealPath().startsWith(realRoot), "文件目录链接指向所选目录之外，请重新选择文件");
        if (!Files.isRegularFile(candidate)) return null;
        Path actual = candidate.toRealPath();
        NoteBundle.require(actual.startsWith(realRoot), "目标文件链接指向所选目录之外，请重新选择文件");
        return actual;
    }

    public static NoteBundle export(Notebook.Data data, Scope scope, String selectionId, Path root, SourceAccess source) throws IOException {
        NoteBundle out = new NoteBundle(); out.format = NoteBundle.FORMAT; out.formatVersion = 1; out.exportedAt = System.currentTimeMillis();
        Set<String> topics = new HashSet<>(), groups = new HashSet<>();
        for (Notebook.Topic t : data.topics) if (scope == Scope.ALL || scope == Scope.TOPIC && t.id.equals(selectionId)) topics.add(t.id);
        for (Notebook.Group g : data.groups) if (topics.contains(g.topicId) || scope == Scope.GROUP && g.id.equals(selectionId)) groups.add(g.id);
        Set<String> selectedGroups = Set.copyOf(groups);
        List<Notebook.Note> selectedNotes = data.notes.stream().filter(n -> selectedGroups.contains(n.groupId) || scope == Scope.NOTE && n.id.equals(selectionId)).limit(NoteBundle.MAX_NOTES + 1).toList();
        NoteBundle.require(selectedNotes.size() <= NoteBundle.MAX_NOTES, "笔记过多，请选择较小的主题或分组导出");
        Map<String, String> sources = new HashMap<>();
        long sourceChars = 0;
        for (Notebook.Note n : selectedNotes) {
            NoteBundle.Entry e = new NoteBundle.Entry(); e.note = new Notebook.Note(n);
            e.note.context = clip(n.context, 4096); e.note.before = clip(n.before, 4096); e.note.after = clip(n.after, 4096);
            e.anchor.code = e.note.context;
            if (!e.note.before.isEmpty()) e.anchor.before.add(e.note.before);
            if (!e.note.after.isEmpty()) e.anchor.after.add(e.note.after);
            Path file = null;
            try { e.note.filePath = relative(n.filePath); file = safeFile(root, e.note.filePath); }
            catch (IllegalArgumentException outside) {
                String name = n.filePath.replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1).replace(':', '_');
                e.note.filePath = "external/" + (name.isBlank() || name.equals(".") || name.equals("..") ? "source.txt" : name);
                e.anchor.external = true;
            }
            if (file != null && !n.detached) {
                String text = sources.get(e.note.filePath);
                if (text == null) {
                    try { text = source.read(file); }
                    catch (IOException unavailable) { /* Keep previously captured context. */ }
                    if (text != null) { sourceChars += text.length(); NoteBundle.require(sourceChars <= 32 * 1024 * 1024, "源码范围过大，请分批导出"); sources.put(e.note.filePath, text); }
                }
                List<String> lines = text == null ? List.of() : lines(text); int line = n.line - 1;
                if (line >= 0 && line < lines.size() && lines.get(line).strip().equals(n.context)) {
                    e.anchor.code = clip(lines.get(line), 4096); e.anchor.before.clear(); e.anchor.after.clear();
                    for (int i = Math.max(0, line - 3); i < line; i++) e.anchor.before.add(clip(lines.get(i), 4096));
                    for (int i = line + 1; i <= Math.min(lines.size() - 1, line + 3); i++) e.anchor.after.add(clip(lines.get(i), 4096));
                    e.anchor.sourceHash = hash(text); e.anchor.available = lines.get(line).length() <= 4096;
                }
            }
            out.notes.add(e); groups.add(n.groupId);
        }
        for (Notebook.Group g : data.groups) if (groups.contains(g.id)) { out.groups.add(new Notebook.Group(g.id, g.topicId, g.name)); topics.add(g.topicId); }
        for (Notebook.Topic t : data.topics) if (topics.contains(t.id)) out.topics.add(new Notebook.Topic(t.id, t.name));
        out.validate(); return out;
    }

    public static Plan plan(Notebook.Data current, NoteBundle bundle, Path project, Path codeRoot,
                            Map<String, String> mapping, SourceAccess access) throws IOException {
        bundle.validate(); Path projectRoot = project.toRealPath(), selectedRoot = codeRoot.toRealPath();
        NoteBundle.require(selectedRoot.startsWith(projectRoot) && Files.isDirectory(selectedRoot), "代码根目录必须位于当前项目中");
        Notebook copy = new Notebook(); copy.load(current); Notebook.Data base = copy.snapshot(), result = copy.snapshot();
        Plan plan = new Plan(base, result, projectRoot);
        Map<String, String> topicIds = new HashMap<>(), groupIds = new HashMap<>();
        for (Notebook.Topic t : bundle.topics) {
            Notebook.Topic found = result.topics.stream().filter(x -> x.name.equals(t.name)).findFirst().orElse(null);
            if (found == null) { found = new Notebook.Topic(uniqueId(t.id, result.topics.stream().map(x -> x.id).toList()), t.name); result.topics.add(found); }
            topicIds.put(t.id, found.id);
        }
        for (Notebook.Group g : bundle.groups) {
            String topic = topicIds.get(g.topicId);
            Notebook.Group found = result.groups.stream().filter(x -> x.topicId.equals(topic) && x.name.equals(g.name)).findFirst().orElse(null);
            if (found == null) { found = new Notebook.Group(uniqueId(g.id, result.groups.stream().map(x -> x.id).toList()), topic, g.name); result.groups.add(found); }
            groupIds.put(g.id, found.id);
        }
        Map<Path, String> cache = new HashMap<>();
        long sourceChars = 0;
        for (NoteBundle.Entry e : bundle.notes) {
            Row row = new Row(e, groupIds.get(e.note.groupId)); plan.rows.add(row);
            row.existing = base.notes.stream().filter(x -> x.id.equals(e.note.id)).findFirst().orElse(null);
            String mapped = mapping.getOrDefault(e.note.filePath, e.note.filePath);
            // Even missing files retain a portable target path so their notes survive import.
            String clean = relative(mapped);
            row.path = relative(projectRoot.relativize(selectedRoot.resolve(clean).normalize()).toString());
            if (e.anchor.external && !mapping.containsKey(e.note.filePath)) { row.reason = "原笔记绑定项目外文件，请先映射到当前项目中的文件"; continue; }
            Path file = safeFile(selectedRoot, clean);
            if (file == null) { row.reason = "找不到文件，请在路径映射中选择新文件"; continue; }
            String text = cache.get(file);
            if (text == null) {
                try { text = access.read(file); }
                catch (IOException failure) { row.reason = "无法读取源码：" + failure.getMessage(); continue; }
                sourceChars += text.length(); NoteBundle.require(sourceChars <= 32 * 1024 * 1024, "源码范围过大，请分批导入"); cache.put(file, text);
            }
            row.source = text; row.hash = hash(text);
            resolve(row);
        }
        plan.rows.forEach(Row::classify);
        return plan;
    }

    private static void resolve(Row row) {
        NoteBundle.Anchor a = row.entry.anchor; List<String> lines = lines(row.source);
        int old = row.entry.note.line - 1;
        row.match = Match.REVIEW; row.reason = "代码已变化，请选择候选位置或保留为待绑定";
        if (a.available && !row.entry.note.detached && row.hash.equals(a.sourceHash)
            && old >= 0 && old < lines.size() && lines.get(old).strip().equals(a.code.strip())) {
            row.match = Match.RELIABLE; row.chosenLine = old + 1; row.reason = "文件内容与分享时一致";
        }
        int available = (int) java.util.stream.Stream.concat(a.before.stream(), a.after.stream()).filter(ShareEngine::meaningful).count();
        List<Candidate> exact = new ArrayList<>(), nearby = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            int support = support(lines, i, a);
            if (lines.get(i).strip().equals(a.code.strip())) exact.add(new Candidate(i + 1, lines.get(i), support));
            else if (support >= 2) nearby.add(new Candidate(i + 1, lines.get(i), support));
        }
        Comparator<Candidate> order = Comparator.comparingInt(Candidate::support).reversed().thenComparingInt(c -> Math.abs(c.line - row.entry.note.line));
        exact.sort(order); nearby.sort(order);
        row.candidates.addAll((exact.isEmpty() ? nearby : exact).stream().limit(50).toList());
        if (row.chosenLine > 0) return;
        if (a.available && !a.code.isBlank() && !row.entry.note.detached && !exact.isEmpty() && available > 0) {
            Candidate best = exact.get(0);
            boolean unique = exact.size() == 1 || exact.get(1).support < best.support;
            int required = Math.max(Math.min(2, available), (available + 1) / 2);
            if (unique && best.support >= required) {
                row.match = Match.RELIABLE; row.chosenLine = best.line; row.reason = "代码及周边上下文唯一匹配，已恢复新行号";
            }
        }
    }
    private static int support(List<String> lines, int at, NoteBundle.Anchor a) {
        int score = 0;
        for (int j = 0; j < a.before.size(); j++) {
            int i = at - a.before.size() + j; String value = a.before.get(j);
            if (i >= 0 && meaningful(value) && lines.get(i).strip().equals(value.strip())) score++;
        }
        for (int j = 0; j < a.after.size(); j++) {
            int i = at + 1 + j; String value = a.after.get(j);
            if (i < lines.size() && meaningful(value) && lines.get(i).strip().equals(value.strip())) score++;
        }
        return score;
    }
    private static boolean meaningful(String line) { return line.codePoints().anyMatch(c -> Character.isLetterOrDigit(c) || c == '_'); }
    public static void checkSources(Plan plan, SourceAccess access) throws IOException {
        Set<String> checked = new HashSet<>();
        for (Row row : plan.rows) if (row.action != Action.SKIP && row.attached() && checked.add(row.path)) {
            Path file = safeFile(plan.projectRoot, row.path);
            NoteBundle.require(file != null && hash(access.read(file)).equals(row.hash), "预览后代码文件发生变化，请重新匹配后再导入");
        }
    }
    public static Notebook.Data merge(Plan plan) {
        if (!plan.rows.isEmpty() && plan.selectedCount() == 0) { Notebook untouched = new Notebook(); untouched.load(plan.base); return untouched.snapshot(); }
        Notebook copy = new Notebook(); copy.load(plan.hierarchy); Notebook.Data result = copy.snapshot();
        Set<String> ids = new HashSet<>(); result.notes.forEach(n -> ids.add(n.id));
        for (Row row : plan.rows) {
            if (row.action == Action.SKIP) continue;
            NoteBundle.require(!row.duplicate && (!row.conflict ? row.action == Action.IMPORT : row.action == Action.REPLACE || row.action == Action.COPY), "请为冲突笔记选择跳过、替换当前或保留两条");
            Notebook.Note value = new Notebook.Note(row.entry.note); value.groupId = row.destinationGroup; value.filePath = row.path;
            if (row.action == Action.COPY) value.id = uniqueId("", ids);
            if (row.action == Action.REPLACE) result.notes.removeIf(n -> n.id.equals(value.id));
            NoteBundle.require(ids.add(value.id) || row.action == Action.REPLACE, "笔记 ID 冲突，请重新预览");
            value.detached = !row.attached();
            if (row.attached()) {
                List<String> lines = lines(row.source); int i = row.chosenLine - 1;
                value.line = row.chosenLine; value.context = lines.get(i).strip();
                value.before = i > 0 ? lines.get(i - 1).strip() : ""; value.after = i + 1 < lines.size() ? lines.get(i + 1).strip() : "";
            }
            result.notes.add(value);
        }
        copy.load(result); return copy.snapshot();
    }
    private static String uniqueId(String preferred, Collection<String> used) {
        return !preferred.isBlank() && !used.contains(preferred) ? preferred : UUID.randomUUID().toString();
    }
}
