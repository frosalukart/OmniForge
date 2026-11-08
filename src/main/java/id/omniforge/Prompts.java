package id.omniforge;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Input teks lewat chat untuk GUI editor. Ketik 'cancel' untuk batal. */
public final class Prompts implements Listener {
    private record Pending(Consumer<String> ok, Runnable cancel) {}

    private final OmniForge pl;
    private final Map<UUID, Pending> pending = new HashMap<>();

    Prompts(OmniForge pl) {
        this.pl = pl;
    }

    public void ask(Player p, String hint, Consumer<String> ok, Runnable cancel) {
        p.closeInventory();
        pending.put(p.getUniqueId(), new Pending(ok, cancel));
        Text.send(p, pl.runner.prefix(), hint + " &8(ketik &ccancel&8 untuk batal)");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending pd = pending.remove(e.getPlayer().getUniqueId());
        if (pd == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(pl, () -> {
            if (text.equalsIgnoreCase("cancel")) pd.cancel().run();
            else pd.ok().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
