package id.omniforge;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.*;

/** Menjalankan ability: syarat, cooldown, mana, lalu action. */
public final class Runner {
    private final OmniForge pl;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    Runner(OmniForge pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------------ pesan
    String msg(String key, String... kv) {
        String s = pl.getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }

    String prefix() {
        return pl.getConfig().getString("messages.prefix", "");
    }

    // ------------------------------------------------------------------ syarat
    /** null = lolos, selain itu teks alasan. */
    public String deny(Player p, ForgeItem f) {
        if (p.hasPermission("omniforge.bypass")) return null;
        if (!f.reqRaces.isEmpty() && pl.hooks.race.ok()) {
            String race = pl.hooks.race.of(p);
            boolean humanOk = pl.getConfig().getBoolean("racecore.human-bypass", true) && race.equals("human");
            if (!humanOk && !f.reqRaces.contains(race)) {
                return msg("race-denied", "race", race, "allowed", String.join(", ", f.reqRaces));
            }
        }
        if (!f.reqSkills.isEmpty() && pl.hooks.aura.ok()) {
            for (Map.Entry<String, Integer> e : f.reqSkills.entrySet()) {
                if (pl.hooks.aura.level(p, e.getKey()) < e.getValue()) {
                    return msg("level-denied", "skill", e.getKey(), "level", String.valueOf(e.getValue()));
                }
            }
        }
        return null;
    }

    public double[] raceBonus(Player p, ForgeItem f) {
        if (f.raceBonus.isEmpty()) return new double[]{1, 1};
        double[] b = f.raceBonus.get(pl.hooks.race.of(p));
        return b != null ? b : new double[]{1, 1};
    }

    // ------------------------------------------------------------------ cooldown
    public double remaining(Player p, ForgeItem f, Ability a) {
        Map<String, Long> m = cooldowns.get(p.getUniqueId());
        if (m == null) return 0;
        Long until = m.get(f.id + ":" + a.id());
        if (until == null) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000.0);
    }

    public void clear(UUID id) {
        cooldowns.remove(id);
    }

    // ------------------------------------------------------------------ fire
    public boolean fire(Player p, ForgeItem f, Ability a, ItemStack stack, LivingEntity target, double baseDamageMult) {
        if (!a.permission().isEmpty() && !p.hasPermission(a.permission()) && !p.hasPermission("omniforge.bypass")) {
            p.sendActionBar(Text.c(msg("no-permission")));
            return false;
        }
        String d = deny(p, f);
        if (d != null) {
            p.sendActionBar(Text.c(d));
            return false;
        }
        double rem = remaining(p, f, a);
        if (rem > 0) {
            p.sendActionBar(Text.c(msg("cooldown", "time", String.format("%.1f", rem))));
            return false;
        }
        if (a.manaCost() > 0 && pl.hooks.aura.ok()) {
            double mana = pl.hooks.aura.mana(p);
            if (mana < a.manaCost()) {
                p.sendActionBar(Text.c(msg("no-mana", "cost", String.valueOf((int) a.manaCost()))));
                return false;
            }
            pl.hooks.aura.setMana(p, mana - a.manaCost());
        }
        double[] rb = raceBonus(p, f);
        if (a.cooldown() > 0) {
            cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>())
                    .put(f.id + ":" + a.id(), System.currentTimeMillis() + (long) (a.cooldown() * rb[1] * 1000));
        }
        if (a.consume() && stack != null) stack.setAmount(stack.getAmount() - 1);

        for (String act : a.actions()) {
            try {
                action(p, f, target, baseDamageMult * rb[0], act);
            } catch (Throwable t) {
                pl.getLogger().warning("Action '" + act + "' di item " + f.id + " gagal: " + t.getMessage());
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ actions
    private void action(Player p, ForgeItem f, LivingEntity target, double dm, String raw) {
        String[] head = raw.split(":", 2);
        String type = head[0].trim().toUpperCase();
        String rest = head.length > 1 ? head[1] : "";
        String[] a = rest.isEmpty() ? new String[0] : rest.split(":");
        String tname = target != null ? target.getName() : "";

        switch (type) {
            case "MESSAGE" -> p.sendMessage(Text.chat(rest.replace("%player%", p.getName()).replace("%target%", tname)));
            case "ACTIONBAR" -> p.sendActionBar(Text.c(rest.replace("%player%", p.getName())));
            case "COMMAND" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    rest.replace("%player%", p.getName()).replace("%target%", tname));
            case "PLAYER_COMMAND" -> p.performCommand(rest.replace("%player%", p.getName()).replace("%target%", tname));
            case "SOUND" -> {
                if (a.length == 0) return;
                float vol = 1, pitch = 1;
                String key = a[0];
                if (a.length >= 3) {
                    pitch = num(a[a.length - 1], 1);
                    vol = num(a[a.length - 2], 1);
                    key = String.join(":", Arrays.copyOfRange(a, 0, a.length - 2));
                } else if (a.length == 2) {
                    vol = num(a[1], 1);
                }
                p.getWorld().playSound(p.getLocation(), key, vol, pitch);
            }
            case "PARTICLE" -> {
                if (a.length == 0) return;
                Particle pt = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(a[0].toLowerCase()));
                if (pt == null) return;
                int count = a.length > 1 ? (int) num(a[1], 10) : 10;
                double spread = a.length > 2 ? num(a[2], 0.5f) : 0.5;
                p.getWorld().spawnParticle(pt, p.getLocation().add(0, 1, 0), count, spread, spread, spread, 0.02);
            }
            case "POTION" -> potion(p, a);
            case "POTION_TARGET" -> { if (target != null) potion(target, a); }
            case "POTION_NEARBY" -> {
                double r = a.length > 3 ? num(a[3], 5) : 5;
                for (LivingEntity le : enemies(p, r)) potion(le, a);
            }
            case "HEAL" -> {
                AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
                double mx = max != null ? max.getValue() : 20;
                p.setHealth(Math.min(mx, p.getHealth() + num(rest, 4)));
            }
            case "FEED" -> p.setFoodLevel(Math.min(20, p.getFoodLevel() + (int) num(rest, 4)));
            case "DAMAGE_NEARBY" -> {
                double r = a.length > 0 ? num(a[0], 4) : 4;
                double dmg = (a.length > 1 ? num(a[1], 4) : 4) * dm;
                for (LivingEntity le : enemies(p, r)) le.damage(dmg, p);
            }
            case "DAMAGE_TARGET" -> { if (target != null) target.damage(num(rest, 4) * dm, p); }
            case "IGNITE_NEARBY" -> {
                double r = a.length > 0 ? num(a[0], 4) : 4;
                int t = a.length > 1 ? (int) num(a[1], 60) : 60;
                for (LivingEntity le : enemies(p, r)) le.setFireTicks(t);
            }
            case "IGNITE_TARGET" -> { if (target != null) target.setFireTicks((int) num(rest, 60)); }
            case "KNOCKBACK_NEARBY" -> {
                double r = a.length > 0 ? num(a[0], 4) : 4;
                double power = a.length > 1 ? num(a[1], 1) : 1;
                for (LivingEntity le : enemies(p, r)) {
                    Vector v = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() == 0) continue;
                    le.setVelocity(v.normalize().multiply(power).setY(0.35));
                }
            }
            case "LIGHTNING_NEARBY" -> {
                double r = a.length > 0 ? num(a[0], 6) : 6;
                for (LivingEntity le : enemies(p, r)) le.getWorld().strikeLightning(le.getLocation());
            }
            case "LIGHTNING_TARGET" -> { if (target != null) target.getWorld().strikeLightning(target.getLocation()); }
            case "DASH" -> p.setVelocity(p.getLocation().getDirection().normalize().multiply(num(rest, 1.2f)).setY(0.2));
            case "LAUNCH" -> p.setVelocity(new Vector(0, num(rest, 1), 0));
            case "MODEL" -> {
                if (a.length > 0) pl.hooks.model.apply(p, a[0], a.length > 1 ? (long) num(a[1], 40) : 40);
            }
            case "MODEL_TARGET" -> {
                if (a.length > 0 && target != null) pl.hooks.model.apply(target, a[0], a.length > 1 ? (long) num(a[1], 40) : 40);
            }
            case "MANA" -> pl.hooks.aura.addMana(p, num(rest, 10));
            case "AURA_XP" -> {
                if (a.length >= 2) pl.hooks.aura.addXp(p, a[0], num(a[1], 10));
            }
            case "GIVE" -> {
                // GIVE:ref:amount  (ref bisa mmoitems:SWORD.X / ei:id / id_forge); karena ref berisi ':' ambil amount dari akhir
                String ref = rest;
                int amt = 1;
                int idx = rest.lastIndexOf(':');
                if (idx > 0 && rest.substring(idx + 1).matches("\\d+")) {
                    ref = rest.substring(0, idx);
                    amt = Integer.parseInt(rest.substring(idx + 1));
                }
                pl.items.give(p, ref, amt);
            }
            default -> pl.getLogger().warning("Action tidak dikenal: " + raw);
        }
    }

    private float num(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void potion(LivingEntity e, String[] a) {
        if (a.length == 0) return;
        PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(a[0].toLowerCase()));
        if (t == null) return;
        int sec = a.length > 1 ? (int) num(a[1], 5) : 5;
        int amp = a.length > 2 ? (int) num(a[2], 0) : 0;
        e.addPotionEffect(new PotionEffect(t, sec * 20, amp));
    }

    private List<LivingEntity> enemies(Player p, double r) {
        boolean pvp = pl.getConfig().getBoolean("pvp", false);
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) {
            if (!(e instanceof LivingEntity le) || e instanceof ArmorStand || e.equals(p)) continue;
            if (e instanceof Player other) {
                if (!pvp || sameTeam(p, other)) continue;
            }
            out.add(le);
        }
        return out;
    }

    private boolean sameTeam(Player a, Player b) {
        Team t = a.getScoreboard().getEntryTeam(a.getName());
        return t != null && t.hasEntry(b.getName());
    }
}
