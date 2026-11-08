package id.omniforge;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/** Tiap 1 detik: efek pasif, bonus stat AuraSkills, boost XP, actionbar cooldown. */
public final class PassiveTask extends BukkitRunnable {
    private final OmniForge pl;
    private final Map<UUID, Map<String, Double>> xp = new HashMap<>();
    private final Map<UUID, Set<String>> statKeys = new HashMap<>();

    PassiveTask(OmniForge pl) {
        this.pl = pl;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tick(p);
            } catch (Throwable t) {
                pl.getLogger().warning("Passive tick error: " + t);
            }
        }
    }

    private Map<String, ItemStack> equipped(Player p) {
        PlayerInventory inv = p.getInventory();
        Map<String, ItemStack> m = new LinkedHashMap<>();
        m.put("mainhand", inv.getItemInMainHand());
        m.put("offhand", inv.getItemInOffHand());
        m.put("head", inv.getHelmet());
        m.put("chest", inv.getChestplate());
        m.put("legs", inv.getLeggings());
        m.put("feet", inv.getBoots());
        return m;
    }

    private void tick(Player p) {
        Map<String, Double> boost = new HashMap<>();
        Map<String, Object[]> want = new LinkedHashMap<>();

        for (Map.Entry<String, ItemStack> en : equipped(p).entrySet()) {
            ForgeItem f = pl.items.of(en.getValue());
            if (f == null || !f.slotMatches(en.getKey())) continue;
            if (pl.runner.deny(p, f) != null) continue;

            for (Map.Entry<String, Integer> pe : f.passive.entrySet()) {
                PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(pe.getKey()));
                if (t != null) p.addPotionEffect(new PotionEffect(t, 60, pe.getValue(), true, false, false));
            }
            for (Map.Entry<String, Double> sb : f.statBonus.entrySet()) {
                want.put("omniforge:" + en.getKey() + ":" + f.id + ":" + sb.getKey(), new Object[]{sb.getKey(), sb.getValue()});
            }
            for (Map.Entry<String, Double> xb : f.xpBoost.entrySet()) boost.merge(xb.getKey(), xb.getValue(), Double::sum);
        }
        xp.put(p.getUniqueId(), boost);
        syncStats(p, want);

        if (pl.getConfig().getBoolean("display.actionbar", true)) {
            ForgeItem f = pl.items.of(p.getInventory().getItemInMainHand());
            if (f != null && !f.abilities.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Ability a : f.abilities.values()) {
                    if (a.cooldown() <= 0) continue;
                    double r = pl.runner.remaining(p, f, a);
                    if (sb.length() > 0) sb.append(" &8| ");
                    sb.append(r > 0 ? "&c" + a.id() + " " + String.format("%.0f", Math.ceil(r)) + "s" : "&a" + a.id() + " READY");
                }
                if (sb.length() > 0) p.sendActionBar(Text.c(sb.toString()));
            }
        }
    }

    private void syncStats(Player p, Map<String, Object[]> want) {
        if (!pl.hooks.aura.ok()) return;
        Set<String> cur = statKeys.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        for (Iterator<String> it = cur.iterator(); it.hasNext(); ) {
            String k = it.next();
            if (!want.containsKey(k)) {
                pl.hooks.aura.removeStat(p, k);
                it.remove();
            }
        }
        for (Map.Entry<String, Object[]> e : want.entrySet()) {
            if (cur.add(e.getKey())) {
                pl.hooks.aura.addStat(p, e.getKey(), (String) e.getValue()[0], (Double) e.getValue()[1]);
            }
        }
    }

    public void forget(UUID id) {
        xp.remove(id);
        statKeys.remove(id);
    }

    /** Dipanggil saat reload/disable: cabut semua stat modifier. */
    public void clearAll() {
        if (pl.hooks.aura.ok()) {
            for (Map.Entry<UUID, Set<String>> e : statKeys.entrySet()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) continue;
                for (String k : e.getValue()) pl.hooks.aura.removeStat(p, k);
            }
        }
        statKeys.clear();
        xp.clear();
    }

    /** Boost XP AuraSkills: listen XpGainEvent lewat reflection. */
    @SuppressWarnings("unchecked")
    public void registerXp() {
        Class<?> c = Refl.cls("AuraSkills", "dev.aurelium.auraskills.api.event.skill.XpGainEvent");
        if (c == null || !Event.class.isAssignableFrom(c)) return;
        Bukkit.getPluginManager().registerEvent((Class<? extends Event>) c, new Listener() {}, EventPriority.HIGH, (l, ev) -> {
            try {
                if (!c.isInstance(ev)) return;
                Object po = Refl.tryCall(ev, "getPlayer");
                Player p = po instanceof Player pp ? pp : null;
                if (p == null) return;
                Map<String, Double> b = xp.get(p.getUniqueId());
                if (b == null || b.isEmpty()) return;
                Object skill = Refl.call(ev, "getSkill");
                String name = skill instanceof Enum<?> en ? en.name().toLowerCase() : String.valueOf(skill).toLowerCase();
                Double pct = b.get(name);
                if (pct == null) return;
                double amt = ((Number) Refl.call(ev, "getAmount")).doubleValue();
                Refl.call(ev, "setAmount", amt * (1 + pct / 100.0));
            } catch (Throwable ignored) {
            }
        }, pl);
    }
}
