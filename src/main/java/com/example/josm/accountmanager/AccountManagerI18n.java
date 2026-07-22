package com.example.josm.accountmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.openstreetmap.josm.tools.Logging;

/** Loads translations without adding entries to JOSM's process-wide I18n maps. */
final class AccountManagerI18n {
    private static final Map<String, String> SIMPLIFIED_CHINESE = loadSimplifiedChinese();

    private AccountManagerI18n() {
    }

    static String trc(String context, String text, Object... arguments) {
        String translated = isSimplifiedChinese()
                ? SIMPLIFIED_CHINESE.getOrDefault(text, text)
                : text;
        return arguments.length == 0 ? translated : MessageFormat.format(translated, arguments);
    }

    private static boolean isSimplifiedChinese() {
        Locale locale = Locale.getDefault();
        return Locale.CHINESE.getLanguage().equals(locale.getLanguage())
                && !"TW".equalsIgnoreCase(locale.getCountry())
                && !"HK".equalsIgnoreCase(locale.getCountry())
                && !"MO".equalsIgnoreCase(locale.getCountry());
    }

    private static Map<String, String> loadSimplifiedChinese() {
        InputStream stream = AccountManagerI18n.class.getResourceAsStream(
                "/com/example/josm/accountmanager/i18n/messages_zh_CN.po");
        if (stream == null) return Collections.emptyMap();

        Map<String, String> translations = new HashMap<>();
        String messageId = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("msgid \"") && !line.equals("msgid \"\"")) {
                    messageId = unquote(line.substring(6));
                } else if (messageId != null && line.startsWith("msgstr \"")) {
                    translations.put(messageId, unquote(line.substring(7)));
                    messageId = null;
                }
            }
        } catch (IOException exception) {
            Logging.error(exception);
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(translations);
    }

    private static String unquote(String value) {
        String content = value.substring(1, value.length() - 1);
        return content.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\");
    }
}
