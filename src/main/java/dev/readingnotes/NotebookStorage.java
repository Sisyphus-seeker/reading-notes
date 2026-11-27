package dev.readingnotes;

import static dev.readingnotes.model.I18n.text;
import com.google.gson.*;
import dev.readingnotes.model.Notebook;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Optional ASCII envelope preserves supplementary Unicode through the IDE XML writer. */
public final class NotebookStorage {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).disableJdkUnsafe().create();
    private NotebookStorage() {}
    public static Notebook.Data encode(Notebook.Data data) {
        Notebook checked = new Notebook(); checked.load(data); Notebook.Data state = checked.snapshot();
        state.utf8Payload = Base64.getEncoder().encodeToString(JSON.toJson(state).getBytes(StandardCharsets.UTF_8));
        return state;
    }
    public static Notebook.Data decode(Notebook.Data state) {
        if (state.utf8Payload == null || state.utf8Payload.isEmpty()) return state;
        try {
            Notebook.Data decoded = JSON.fromJson(new String(Base64.getDecoder().decode(state.utf8Payload), StandardCharsets.UTF_8), Notebook.Data.class);
            if (decoded == null) throw new IllegalArgumentException(text("笔记存储为空"));
            Notebook checked = new Notebook(); checked.load(decoded); return checked.snapshot();
        } catch (RuntimeException failure) { throw new IllegalArgumentException(text("笔记 UTF-8 存储字段损坏，请从备份恢复"), failure); }
    }
}
