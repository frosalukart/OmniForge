package id.omniforge;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.*;

/** Semua jembatan ke plugin lain (via reflection, aman kalau plugin-nya tidak ada). */
public final class Hooks {
    private final OmniForge pl;
    public final Mmo mmo = new Mmo();
    public final Ei ei = new Ei();
    public final Ae ae = new Ae();
    public final Aura aura = new Aura();
    public final Race race = new Race();
    public final Model model = new Model();

    Hooks(OmniForge pl) {
        this.pl = pl;
    }

    static boolean enabled(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled();
    }

    void init() {
        String[] names = {"MMOItems", "ExecutableItems", "AdvancedEnchantments", "AuraSkills", "RaceCore", "ModelEngine"};
        boolean[] ok = new boolean[6];
        try { if (enabled(names[0])) { mmo.init(); ok[0] = mmo.ok; } } catch (Throwable t) { warn(names[0], t); }
        try { if (enabled(names[1])) { ei.init(); ok[1] = ei.ok; } } catch (Throwable t) { warn(names[1], t); }
        try { if (enabled(names[2])) { ae.init(); ok[2] = ae.ok; } } catch (Throwable t) { warn(names[2], t); }
        try { if (enabled(names[3])) { aura.init(); ok[3] = aura.ok; } } catch (Throwable t) { warn(names[3], t); }
        try { if (enabled(names[4])) { race.init(); ok[4] = race.ok; } } catch (Throwable t) { warn(names[4], t); }
        try { if (enabled(names[5])) { model.ok = true; ok[5] = true; } } catch (Throwable t) { warn(names[5], t); }
        for (int i = 0; i < names.length; i++) {
            pl.getLogger().info("Hook " + names[i] + ": " + (ok[i] ? "OK" : (enabled(names[i]) ? "TERPASANG tapi API tidak cocok" : "tidak ada")));
        }
    }

    private void warn(String n, Throwable t) {
        pl.getLogger().warning("Hook " + n + " gagal: " + t);
    }

    // ------------------------------------------------------------------ MMOItems
    public final class Mmo {
        boolean ok;
        Class<?> main;
        Object plugin;

        void init() {
            main = Refl.cls("MMOItems", "net.Indyuce.mmoitems.MMOItems");
            if (main != null) plugin = Refl.get(main, "plugin");
            ok = plugin != null;
        }

        public boolean ok() { return ok; }

        /** ref = TYPE.ID  (contoh SWORD.KATANA) */
        public ItemStack create(String ref) {
            if (!ok) return null;
            try {
                String[] p = ref.split("\\.", 2);
                if (p.length < 2) return null;
                Object types = Refl.call(plugin, "getTypes");
                Object type = Refl.call(types, "get", p[0].toUpperCase());
                if (type == null) return null;
                Object r = Refl.call(plugin, "getItem", type, p[1].toUpperCase());
                return r instanceof ItemStack s ? s : null;
            } catch (Throwable t) {
                return null;
            }
        }

        public String identify(ItemStack s) {
            if (!ok) return null;
            try {
                Object id = Refl.call(main, "getID", s);
                Object type = Refl.call(main, "getTypeName", s);
                if (id == null || type == null) return null;
                return "mmoitems:" + type + "." + id;
            } catch (Throwable t) {
                return null;
            }
        }

        public List<String> typeIds() {
            List<String> out = new ArrayList<>();
            if (!ok) return out;
            try {
                Object types = Refl.call(plugin, "getTypes");
                Object all = Refl.call(types, "getAll");
                if (all instanceof Collection<?> c) {
                    for (Object t : c) out.add(String.valueOf(Refl.call(t, "getId")));
                }
            } catch (Throwable ignored) {
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ ExecutableItems
    public final class Ei {
        boolean ok;
        Object manager;
        final NamespacedKey key = NamespacedKey.fromString("executableitems:ei-id");

        void init() {
            Class<?> api = Refl.cls("ExecutableItems", "com.ssomar.score.api.executableitems.ExecutableItemsAPI");
            if (api == null) api = Refl.cls("SCore", "com.ssomar.score.api.executableitems.ExecutableItemsAPI");
            if (api != null) {
                try { manager = Refl.call(api, "getExecutableItemsManager"); } catch (Throwable ignored) { }
            }
            ok = manager != null;
        }

        /** EI selalu dianggap tersedia kalau plugin-nya aktif (give lewat command), tapi wrap butuh API. */
        public boolean ok() { return ok; }

        public ItemStack create(String id, int amount) {
            if (!ok) return null;
            try {
                Object opt = Refl.call(manager, "getExecutableItem", id);
                Object item = opt instanceof Optional<?> o ? o.orElse(null) : opt;
                if (item == null) return null;
                for (Method m : item.getClass().getMethods()) {
                    if (!m.getName().equals("buildItem")) continue;
                    Object[] args = synth(m.getParameterTypes(), amount);
                    if (args == null) continue;
                    try {
                        m.setAccessible(true);
                        Object r = m.invoke(item, args);
                        if (r instanceof ItemStack s) return s;
                    } catch (Exception ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private Object[] synth(Class<?>[] types, int amount) {
            Object[] a = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                if (t == int.class || t == Integer.class) a[i] = amount;
                else if (t == Optional.class) a[i] = Optional.empty();
                else if (t == boolean.class) a[i] = false;
                else if (org.bukkit.OfflinePlayer.class.isAssignableFrom(t)) a[i] = null;
                else return null;
            }
            return a;
        }

        public String identify(ItemStack s) {
            if (!enabled("ExecutableItems") || key == null) return null;
            try {
                ItemMeta m = s.getItemMeta();
                if (m == null) return null;
                String id = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                return id == null ? null : "ei:" + id;
            } catch (Throwable t) {
                return null;
            }
        }

        public void giveByCommand(Player p, String id, int amount) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ei give " + p.getName() + " " + id + " " + amount);
        }
    }

    // ------------------------------------------------------------------ AdvancedEnchantments
    public final class Ae {
        boolean ok;
        Class<?> api;

        void init() {
            api = Refl.cls("AdvancedEnchantments", "net.advancedplugins.ae.api.AEAPI");
            ok = api != null;
        }

        public boolean ok() { return ok; }

        public ItemStack apply(ItemStack item, String enchant, int level) {
            if (!ok) return item;
            try {
                Object r = Refl.call(api, "applyEnchant", enchant, level, item);
                return r instanceof ItemStack s ? s : item;
            } catch (Throwable t) {
                pl.getLogger().warning("AE enchant '" + enchant + "' gagal diterapkan: " + t.getMessage());
                return item;
            }
        }

        public List<String> all() {
            List<String> out = new ArrayList<>();
            if (!ok) return out;
            try {
                Object r = Refl.call(api, "getAllEnchantments");
                if (r instanceof Collection<?> c) for (Object o : c) out.add(String.valueOf(o));
            } catch (Throwable ignored) {
            }
            Collections.sort(out);
            return out;
        }
    }

    // ------------------------------------------------------------------ AuraSkills
    public final class Aura {
        boolean ok;
        Object api;
        Class<?> skillsCls, statsCls, modCls, nidCls;

        void init() {
            Class<?> a = Refl.cls("AuraSkills", "dev.aurelium.auraskills.api.AuraSkillsApi");
            if (a == null) return;
            api = Refl.call(a, "get");
            skillsCls = Refl.cls("AuraSkills", "dev.aurelium.auraskills.api.skill.Skills");
            statsCls = Refl.cls("AuraSkills", "dev.aurelium.auraskills.api.stat.Stats");
            modCls = Refl.cls("AuraSkills", "dev.aurelium.auraskills.api.stat.StatModifier");
            nidCls = Refl.cls("AuraSkills", "dev.aurelium.auraskills.api.registry.NamespacedId");
            ok = api != null;
        }

        public boolean ok() { return ok; }

        private Object user(Player p) {
            return Refl.call(api, "getUser", p.getUniqueId());
        }

        private Object skill(String name) {
            Object s = Refl.enumVal(skillsCls, name);
            if (s != null) return s;
            try {
                Object id = Refl.call(nidCls, "fromDefault", name.toLowerCase());
                return Refl.call(Refl.call(api, "getGlobalRegistry"), "getSkill", id);
            } catch (Throwable t) {
                return null;
            }
        }

        public int level(Player p, String skillName) {
            if (!ok) return Integer.MAX_VALUE;
            try {
                Object sk = skill(skillName);
                if (sk == null) return 0;
                return ((Number) Refl.call(user(p), "getSkillLevel", sk)).intValue();
            } catch (Throwable t) {
                return 0;
            }
        }

        public double mana(Player p) {
            try { return ((Number) Refl.call(user(p), "getMana")).doubleValue(); } catch (Throwable t) { return 0; }
        }

        public void setMana(Player p, double v) {
            try { Refl.call(user(p), "setMana", Math.max(0, v)); } catch (Throwable ignored) { }
        }

        public void addMana(Player p, double v) {
            try {
                Object u = user(p);
                double max = ((Number) Refl.call(u, "getMaxMana")).doubleValue();
                double cur = ((Number) Refl.call(u, "getMana")).doubleValue();
                Refl.call(u, "setMana", Math.min(max, Math.max(0, cur + v)));
            } catch (Throwable ignored) {
            }
        }

        public void addXp(Player p, String skillName, double amount) {
            try {
                Object sk = skill(skillName);
                if (sk != null) Refl.call(user(p), "addSkillXp", sk, amount);
            } catch (Throwable ignored) {
            }
        }

        public void addStat(Player p, String key, String stat, double value) {
            try {
                Object st = Refl.enumVal(statsCls, stat);
                if (st == null) return;
                Object mod = Refl.newInstance(modCls, key, st, value);
                Refl.call(user(p), "addStatModifier", mod);
            } catch (Throwable ignored) {
            }
        }

        public void removeStat(Player p, String key) {
            try { Refl.call(user(p), "removeStatModifier", key); } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------ RaceCore
    public final class Race {
        boolean ok;
        Object data;

        void init() {
            Plugin rc = Bukkit.getPluginManager().getPlugin("RaceCore");
            if (rc != null) data = Refl.get(rc, "data");
            ok = data != null;
        }

        public boolean ok() { return ok; }

        public String of(Player p) {
            if (!ok) return "human";
            try {
                Object r = Refl.call(data, "raceOf", p.getUniqueId());
                if (r == null) return "human";
                Object k = Refl.firstField(r, "key", "id", "name");
                return k == null ? "human" : k.toString().toLowerCase();
            } catch (Throwable t) {
                return "human";
            }
        }
    }

    // ------------------------------------------------------------------ ModelEngine
    public final class Model {
        boolean ok;

        public boolean ok() { return ok; }

        public void apply(Entity e, String modelId, long ticks) {
            if (!ok) return;
            try {
                Class<?> api = Refl.cls("ModelEngine", "com.ticxo.modelengine.api.ModelEngineAPI");
                Object active = Refl.call(api, "createActiveModel", modelId);
                if (active == null) {
                    pl.getLogger().warning("Model '" + modelId + "' tidak ditemukan di ModelEngine.");
                    return;
                }
                Object modeled = Refl.call(api, "getOrCreateModeledEntity", e);
                Refl.call(modeled, "addModel", active, true);
                Bukkit.getScheduler().runTaskLater(pl, () -> {
                    try { Refl.call(modeled, "removeModel", modelId); } catch (Throwable ignored) { }
                }, Math.max(1, ticks));
            } catch (Throwable t) {
                pl.getLogger().warning("Gagal apply model '" + modelId + "': " + t.getMessage());
            }
        }
    }
}
