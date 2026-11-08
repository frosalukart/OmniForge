package id.omniforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

public final class Text {
    private static final LegacyComponentSerializer L =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {}

    /** Teks berwarna (&) tanpa italic — untuk nama/lore item & GUI. */
    public static Component c(String s) {
        return L.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    /** Teks berwarna biasa (boleh italic) — untuk chat. */
    public static Component chat(String s) {
        return L.deserialize(s == null ? "" : s);
    }

    public static void send(CommandSender to, String prefix, String s) {
        to.sendMessage(chat(prefix + s));
    }
}
