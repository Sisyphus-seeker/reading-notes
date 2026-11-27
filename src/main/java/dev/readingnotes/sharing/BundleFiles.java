package dev.readingnotes.sharing;

import static dev.readingnotes.model.I18n.text;
import com.google.gson.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;
import dev.readingnotes.model.*;
import dev.readingnotes.NotebookStorage;
import org.jdom.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** JSON transfer is local and bounded. Backups use the existing IDE XML storage format. */
public final class BundleFiles {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).disableJdkUnsafe().setPrettyPrinting().create();
    private BundleFiles() {}

    public static NoteBundle read(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        NoteBundle.require(bytes.length <= MAX_BYTES, text("分享文件超过 16 MB，请分批导入"));
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        if (text.startsWith("\ufeff")) text = text.substring(1);
        checkDepth(text);
        try {
            NoteBundle bundle = JSON.fromJson(text, NoteBundle.class);
            NoteBundle.require(bundle != null, text("分享文件为空")); bundle.validate(); return bundle;
        } catch (JsonParseException e) { throw new IllegalArgumentException(text("分享文件不是有效的 JSON：{0}", e.getMessage()), e); }
    }
    public static void write(Path file, NoteBundle bundle) throws IOException {
        bundle.validate(); byte[] bytes = JSON.toJson(bundle).getBytes(StandardCharsets.UTF_8);
        NoteBundle.require(bytes.length <= MAX_BYTES, text("导出内容超过 16 MB，请选择较小的主题或分组"));
        atomicWrite(file, bytes);
    }
    public static Path backup(Path projectRoot, Notebook.Data data) throws IOException {
        Element project = new Element("project").setAttribute("version", "4");
        Element component = XmlSerializer.serialize(NotebookStorage.encode(data)); component.setName("component"); component.setAttribute("name", "ReadingNotes");
        project.addContent(component);
        Path directory = projectRoot.resolve(".idea/reading-notes-backups"); Files.createDirectories(directory);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
        Path file = directory.resolve("reading-notes-" + stamp + "-" + UUID.randomUUID().toString().substring(0, 8) + ".xml");
        atomicWrite(file, JDOMUtil.writeElement(project).getBytes(StandardCharsets.UTF_8)); return file;
    }
    private static void atomicWrite(Path file, byte[] bytes) throws IOException {
        Path target = file.toAbsolutePath().normalize(); Path parent = target.getParent();
        NoteBundle.require(parent != null && Files.isDirectory(parent), text("目标目录不存在"));
        Path temporary = Files.createTempFile(parent, ".reading-notes-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void checkDepth(String text) {
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; }
            else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 32) throw new IllegalArgumentException(text("JSON 嵌套过深，无法读取")); }
            else if (c == '}' || c == ']') depth--;
        }
    }
}
