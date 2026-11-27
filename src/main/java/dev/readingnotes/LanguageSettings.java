package dev.readingnotes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.util.messages.Topic;
import dev.readingnotes.model.I18n;
import org.jetbrains.annotations.NotNull;

/** Application preference, kept separate from project notebooks and shared note bundles. */
@Service(Service.Level.APP)
@State(name = "ReadingNotesLanguage", storages = @Storage("reading-notes-settings.xml"))
public final class LanguageSettings implements PersistentStateComponent<LanguageSettings.Data> {
    public static final class Data { public String language = "zh-CN"; }
    public interface Listener { void languageChanged(); }
    @Topic.AppLevel
    public static final Topic<Listener> CHANGED = Topic.create("Reading Notes language changed", Listener.class);
    private volatile I18n.Language language = I18n.Language.CHINESE;

    public static LanguageSettings get() { return ApplicationManager.getApplication().getService(LanguageSettings.class); }
    public I18n.Language language() { return language; }
    @Override public Data getState() { Data result = new Data(); result.language = language.code; return result; }
    @Override public void loadState(@NotNull Data state) { apply(I18n.Language.fromCode(state.language)); }
    public void setLanguage(I18n.Language value) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        apply(java.util.Objects.requireNonNull(value));
    }
    private void apply(I18n.Language value) {
        boolean changed = language != value;
        language = value; I18n.setLanguage(value);
        if (changed) ApplicationManager.getApplication().getMessageBus().syncPublisher(CHANGED).languageChanged();
    }
}
