package id.omniforge;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

/** GUI chest: daftar item -> editor -> editor ability. Input teks lewat chat (Prompts). */
public final class Gui implements Listener {
    private final OmniForge pl;

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final String[] SLOT_CYCLE = {"mainhand", "offhand", "head", "chest", "legs", "feet", "armor", "any"};

    Gui(OmniForge pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------------ holders
    abstract static class H implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    static final class MainH extends H {
        int page;
        final Map<Integer, String> ids = new HashMap<>();
    }

    static final class EditH extends H {
        String id;
        final Map<Integer, String> keys = new HashMap<>();
    }

    static final class AbilH extends H {
        String id;
        final Map<Integer, String> abil = new HashMap<>();
    }

    // ------------------------------------------------------------------ util
    private ItemStack btn(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Text.c(s));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack filler() {
        return btn(Material.GRAY_STAINED_GLASS_PANE, "&8 ");
    }

    private void say(Player p, String s) {
        Text.send(p, pl.runner.prefix(), s);
    }

    private static String fmt(Map<?, ?> m) {
        if (m == null || m.isEmpty()) return "&8(kosong)";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (sb.length() > 0) sb.append("&7, ");
            Object v = e.getValue();
            String val = v instanceof double[] d ? Arrays.toString(d) : String.valueOf(v);
            sb.append("&f").append(e.getKey()).append("&7=&e").append(val);
        }
        return sb.toString();
    }

    private static String val(Object o) {
        return o == null ? "&8(kosong)" : "&f" + o;
    }

    // ------------------------------------------------------------------ MAIN MENU
    public void openMain(Player p, int page) {
        List<ForgeItem> list = new ArrayList<>(pl.items.all());
        int pages = Math.max(1, (list.size() + 44) / 45);
        page = Math.max(0, Math.min(page, pages - 1));

        MainH h = new MainH();
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&8OmniForge &7- Item (" + (page + 1) + "/" + pages + ")"));
        h.inv = inv;

        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= list.size()) break;
            ForgeItem f = list.get(idx);
            h.ids.put(i, f.id);
            inv.setItem(i, icon(f));
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, filler());
        inv.setItem(45, btn(Material.ARROW, "&e< Halaman sebelumnya"));
        inv.setItem(47, btn(Material.REPEATER, "&bReload", "&7Muat ulang config & items"));
        inv.setItem(49, btn(Material.EMERALD, "&a+ Item baru", "&7Buat item native baru",
                "&7(material = item di tangan)"));
        inv.setItem(51, btn(Material.NETHER_STAR, "&d+ Link item di tangan",
                "&7Tambahkan ability/syarat ke item",
                "&7MMOItems / ExecutableItems yang dipegang"));
        inv.setItem(53, btn(Material.ARROW, "&eHalaman berikutnya >"));
        p.openInventory(inv);
    }

    private ItemStack icon(ForgeItem f) {
        ItemStack it;
        try {
            it = pl.items.build(f, 1).clone();
        } catch (Throwable t) {
            it = new ItemStack(f.material);
        }
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        List<Component> lore = new ArrayList<>();
        if (m.lore() != null) lore.addAll(m.lore());
        lore.add(Text.c(""));
        lore.add(Text.c("&8ID: &7" + f.id + (f.base != null ? " &8| base: &7" + f.base : "")
                + (f.bind != null ? " &8| bind: &7" + f.bind : "")));
        lore.add(Text.c("&8Ability: &7" + f.abilities.size()));
        lore.add(Text.c("&aKlik kiri: &7ambil item"));
        lore.add(Text.c("&eKlik kanan: &7edit"));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ EDITOR
    public void openEditor(Player p, String id) {
        ForgeItem f = pl.items.get(id);
        if (f == null) {
            openMain(p, 0);
            return;
        }
        EditH h = new EditH();
        h.id = f.id;
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&8Edit: &6" + f.id));
        h.inv = inv;
        for (int i = 0; i < 54; i++) inv.setItem(i, filler());

        try {
            inv.setItem(4, pl.items.build(f, 1));
        } catch (Throwable t) {
            inv.setItem(4, new ItemStack(f.material));
        }

        List<Object[]> b = new ArrayList<>();
        b.add(new Object[]{"name", Material.NAME_TAG, "&eNama", new String[]{"&7Sekarang: " + val(f.name), "&aKlik: &7ubah"}});
        b.add(new Object[]{"lore", Material.WRITABLE_BOOK, "&eLore (" + f.lore.size() + " baris)", new String[]{"&aKiri: &7tambah baris", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"material", Material.GRASS_BLOCK, "&eMaterial", new String[]{"&7Sekarang: &f" + f.material, "&aKiri: &7ketik nama", "&eKanan: &7pakai item di tangan"}});
        b.add(new Object[]{"base", Material.CHEST, "&eBase (MMOItems / EI)", new String[]{"&7Sekarang: " + val(f.base), "&7Contoh: mmoitems:SWORD.KATANA / ei:id", "&aKiri: &7ketik ref (none = hapus)", "&eKanan: &7ambil dari item di tangan"}});
        b.add(new Object[]{"cmd", Material.PAINTING, "&eCustom Model Data", new String[]{"&7Sekarang: " + val(f.cmd), "&aKlik: &7ketik angka (none = hapus)"}});
        b.add(new Object[]{"model", Material.ITEM_FRAME, "&eItem Model (texture)", new String[]{"&7Sekarang: " + val(f.itemModel), "&7Format: namespace:nama (rpg:fire_sword)", "&aKlik: &7ketik (none = hapus)"}});
        b.add(new Object[]{"slot", Material.SADDLE, "&eSlot aktif", new String[]{"&7Sekarang: &f" + f.slot, "&aKlik: &7ganti slot"}});
        b.add(new Object[]{"unbreakable", Material.ANVIL, "&eUnbreakable", new String[]{f.unbreakable ? "&aAKTIF" : "&cMati", "&aKlik: &7toggle"}});
        b.add(new Object[]{"glow", Material.GLOWSTONE_DUST, "&eGlow", new String[]{f.glow ? "&aAKTIF" : "&cMati", "&aKlik: &7toggle"}});
        b.add(new Object[]{"enchants", Material.ENCHANTED_BOOK, "&eEnchant vanilla", new String[]{fmt(f.enchants), "&aKiri: &7ketik 'sharpness 5'", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"ae", Material.BOOK, "&eEnchant AdvancedEnchantments", new String[]{fmt(f.aeEnchants), "&aKiri: &7ketik 'nama level'", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"stats", Material.IRON_SWORD, "&eStats", new String[]{fmt(f.stats), "&aKiri: &7ketik 'damage 6'", "&7crit-chance crit-damage lifesteal juga bisa", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"passive", Material.POTION, "&ePassive effect", new String[]{fmt(f.passive), "&aKiri: &7ketik 'speed 1' (0 = level I)", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"races", Material.PLAYER_HEAD, "&eSyarat Race (RaceCore)", new String[]{"&7Sekarang: " + (f.reqRaces.isEmpty() ? "&8(semua race)" : "&f" + String.join(", ", f.reqRaces)), "&aKlik: &7ketik 'angel,elf' (none = semua)"}});
        b.add(new Object[]{"skills", Material.EXPERIENCE_BOTTLE, "&eSyarat Level (AuraSkills)", new String[]{fmt(f.reqSkills), "&aKiri: &7ketik 'fighting 40'", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"statbonus", Material.NETHER_STAR, "&eStat Bonus (AuraSkills)", new String[]{fmt(f.statBonus), "&aKiri: &7ketik 'strength 10'", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"xp", Material.EMERALD, "&eXP Boost % (AuraSkills)", new String[]{fmt(f.xpBoost), "&aKiri: &7ketik 'fighting 15'", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"racebonus", Material.DRAGON_HEAD, "&eRace Bonus", new String[]{fmt(f.raceBonus), "&aKiri: &7ketik 'angel 1.3 0.75'", "&7(damage-mult cooldown-mult)", "&cKanan: &7hapus semua"}});
        b.add(new Object[]{"abilities", Material.BLAZE_ROD, "&6Abilities (" + f.abilities.size() + ")", new String[]{"&aKlik: &7kelola ability & action"}});
        b.add(new Object[]{"bind", Material.LEVER, "&eBind ke item luar", new String[]{"&7Sekarang: " + val(f.bind), "&7Item MMOItems/EI dengan ID ini otomatis", "&7dapat ability profil ini.", "&aKiri: &7ketik ref (none = hapus)", "&eKanan: &7ambil dari item di tangan"}});

        for (int i = 0; i < b.size() && i < SLOTS.length; i++) {
            Object[] o = b.get(i);
            h.keys.put(SLOTS[i], (String) o[0]);
            inv.setItem(SLOTS[i], btn((Material) o[1], (String) o[2], (String[]) o[3]));
        }
        inv.setItem(45, btn(Material.ARROW, "&e< Kembali"));
        inv.setItem(48, btn(Material.CHEST_MINECART, "&aAmbil item ini"));
        inv.setItem(50, btn(Material.BARRIER, "&c&lHapus item", "&7SHIFT + klik untuk menghapus"));
        p.openInventory(inv);
    }

    private void ask(Player p, String id, String hint, Consumer<String> ok) {
        pl.prompts.ask(p, hint, ok, () -> openEditor(p, id));
    }

    private void upd(Player p, String id, Consumer<ConfigurationSection> fn) {
        pl.items.edit(id, fn);
        openEditor(p, id);
    }

    private void clickEditor(Player p, EditH h, int slot, ClickType ct) {
        String id = h.id;
        if (slot == 45) { openMain(p, 0); return; }
        if (slot == 48) { pl.items.give(p, "forge:" + id, 1); say(p, "&aItem diberikan."); return; }
        if (slot == 50) {
            if (ct.isShiftClick()) { pl.items.delete(id); say(p, "&cItem &f" + id + " &cdihapus."); openMain(p, 0); }
            else say(p, "&cTahan SHIFT sambil klik untuk menghapus.");
            return;
        }
        String key = h.keys.get(slot);
        if (key == null) return;
        boolean right = ct.isRightClick();
        ForgeItem f = pl.items.get(id);
        if (f == null) return;

        switch (key) {
            case "name" -> ask(p, id, "&eKetik nama item (boleh &warna):", t -> upd(p, id, s -> s.set("name", t)));
            case "lore" -> {
                if (right) upd(p, id, s -> s.set("lore", null));
                else ask(p, id, "&eKetik 1 baris lore:", t -> upd(p, id, s -> {
                    List<String> l = new ArrayList<>(s.getStringList("lore"));
                    l.add(t);
                    s.set("lore", l);
                }));
            }
            case "material" -> {
                if (right) {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand.getType().isAir()) { say(p, "&cTanganmu kosong."); return; }
                    String m = hand.getType().name();
                    upd(p, id, s -> s.set("material", m));
                } else ask(p, id, "&eKetik nama material (contoh DIAMOND_SWORD):", t -> {
                    Material m = Material.matchMaterial(t);
                    if (m == null) { say(p, "&cMaterial tidak dikenal."); openEditor(p, id); return; }
                    upd(p, id, s -> s.set("material", m.name()));
                });
            }
            case "base", "bind" -> {
                String path = key;
                if (right) {
                    String ref = pl.items.identifyExternal(p.getInventory().getItemInMainHand());
                    if (ref == null) { say(p, "&cItem di tangan bukan item MMOItems / ExecutableItems."); return; }
                    upd(p, id, s -> s.set(path, ref));
                } else ask(p, id, "&eKetik ref (mmoitems:TYPE.ID / ei:id / none):",
                        t -> upd(p, id, s -> s.set(path, t.equalsIgnoreCase("none") ? null : t)));
            }
            case "cmd" -> ask(p, id, "&eKetik angka Custom Model Data (none = hapus):", t -> {
                if (t.equalsIgnoreCase("none")) { upd(p, id, s -> s.set("custom-model-data", null)); return; }
                try {
                    int n = Integer.parseInt(t.trim());
                    upd(p, id, s -> s.set("custom-model-data", n));
                } catch (NumberFormatException ex) { say(p, "&cBukan angka."); openEditor(p, id); }
            });
            case "model" -> ask(p, id, "&eKetik item-model (namespace:nama, none = hapus):",
                    t -> upd(p, id, s -> s.set("item-model", t.equalsIgnoreCase("none") ? null : t)));
            case "slot" -> {
                int i = Arrays.asList(SLOT_CYCLE).indexOf(f.slot);
                String next = SLOT_CYCLE[(i + 1) % SLOT_CYCLE.length];
                upd(p, id, s -> s.set("slot", next));
            }
            case "unbreakable" -> upd(p, id, s -> s.set("unbreakable", !f.unbreakable));
            case "glow" -> upd(p, id, s -> s.set("glow", !f.glow));
            case "enchants" -> mapEdit(p, id, right, "enchants", "&eKetik 'nama level' (contoh sharpness 5, level 0 = hapus):", true, false);
            case "ae" -> {
                if (!right) {
                    List<String> all = pl.hooks.ae.all();
                    if (!all.isEmpty()) say(p, "&7Enchant AE tersedia: &f" + String.join(", ", all.subList(0, Math.min(40, all.size()))) + (all.size() > 40 ? "&7 ..." : ""));
                }
                mapEdit(p, id, right, "ae-enchants", "&eKetik 'nama level' (contoh lifesteal 3):", true, false);
            }
            case "stats" -> mapEdit(p, id, right, "stats", "&eKetik 'stat nilai' (contoh damage 6 / crit-chance 15):", false, true);
            case "passive" -> mapEdit(p, id, right, "passive-effects", "&eKetik 'efek level' (contoh speed 1):", true, false);
            case "skills" -> mapEdit(p, id, right, "requirements.auraskills", "&eKetik 'skill level' (contoh fighting 40):", true, false);
            case "statbonus" -> mapEdit(p, id, right, "stat-bonus", "&eKetik 'stat nilai' (contoh strength 10):", false, false);
            case "xp" -> mapEdit(p, id, right, "xp-boost", "&eKetik 'skill persen' (contoh fighting 15):", false, false);
            case "races" -> ask(p, id, "&eKetik race dipisah koma (contoh angel,elf) atau none:", t -> {
                if (t.equalsIgnoreCase("none")) { upd(p, id, s -> s.set("requirements.races", null)); return; }
                List<String> l = new ArrayList<>();
                for (String r : t.split("[,\\s]+")) if (!r.isBlank()) l.add(r.toLowerCase());
                upd(p, id, s -> s.set("requirements.races", l));
            });
            case "racebonus" -> {
                if (right) { upd(p, id, s -> s.set("race-bonus", null)); return; }
                ask(p, id, "&eKetik 'race damage-mult [cooldown-mult]' (contoh angel 1.3 0.75):", t -> {
                    String[] a = t.trim().split("\\s+");
                    try {
                        String race = a[0].toLowerCase();
                        double dm = Double.parseDouble(a[1]);
                        double cm = a.length > 2 ? Double.parseDouble(a[2]) : 1.0;
                        upd(p, id, s -> {
                            s.set("race-bonus." + race + ".damage-multiplier", dm);
                            s.set("race-bonus." + race + ".cooldown-multiplier", cm);
                        });
                    } catch (Exception ex) { say(p, "&cFormat salah."); openEditor(p, id); }
                });
            }
            case "abilities" -> openAbilities(p, id);
            default -> { }
        }
    }

    /** Edit map "kunci -> angka" di path tertentu. */
    private void mapEdit(Player p, String id, boolean clear, String path, String hint, boolean isInt, boolean statKeys) {
        if (clear) { upd(p, id, s -> s.set(path, null)); return; }
        ask(p, id, hint, t -> {
            String[] a = t.trim().split("[\\s:=]+");
            if (a.length < 2) { say(p, "&cFormat: nama nilai"); openEditor(p, id); return; }
            String k = statKeys ? ForgeItem.statKey(a[0]) : a[0].toLowerCase();
            try {
                double v = Double.parseDouble(a[1]);
                upd(p, id, s -> {
                    Object value = v == 0 && !path.equals("passive-effects") ? null : (isInt ? (Object) (int) v : (Object) v);
                    s.set(path + "." + k, value);
                });
            } catch (NumberFormatException ex) { say(p, "&cNilai harus angka."); openEditor(p, id); }
        });
    }

    // ------------------------------------------------------------------ ABILITY MENU
    public void openAbilities(Player p, String id) {
        ForgeItem f = pl.items.get(id);
        if (f == null) { openMain(p, 0); return; }
        AbilH h = new AbilH();
        h.id = f.id;
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&8Ability: &6" + f.id));
        h.inv = inv;
        for (int i = 45; i < 54; i++) inv.setItem(i, filler());

        int i = 0;
        for (Ability a : f.abilities.values()) {
            if (i >= 45) break;
            List<String> lore = new ArrayList<>();
            lore.add("&7Trigger: &f" + a.trigger());
            lore.add("&7Cooldown: &f" + a.cooldown() + "s &8| &7Mana: &b" + a.manaCost() + " &8| &7Consume: &f" + a.consume());
            lore.add("&7Actions:");
            if (a.actions().isEmpty()) lore.add("  &8(belum ada)");
            for (int k = 0; k < a.actions().size() && k < 10; k++) lore.add("  &f" + (k + 1) + ". &7" + a.actions().get(k));
            lore.add("");
            lore.add("&aKiri: &7tambah action   &cKanan: &7hapus action terakhir");
            lore.add("&aShift+Kiri: &7ganti trigger   &cShift+Kanan: &7hapus ability");
            lore.add("&aQ: &7ubah cooldown   &aF: &7ubah mana   &aCtrl+Q: &7toggle consume");
            h.abil.put(i, a.id());
            inv.setItem(i, btn(Material.BLAZE_POWDER, "&6" + a.id(), lore.toArray(new String[0])));
            i++;
        }
        inv.setItem(45, btn(Material.ARROW, "&e< Kembali ke editor"));
        inv.setItem(49, btn(Material.EMERALD, "&a+ Ability baru"));
        p.openInventory(inv);
    }

    private void askAb(Player p, String id, String hint, Consumer<String> ok) {
        pl.prompts.ask(p, hint, ok, () -> openAbilities(p, id));
    }

    private void updAb(Player p, String id, Consumer<ConfigurationSection> fn) {
        pl.items.edit(id, fn);
        openAbilities(p, id);
    }

    private void clickAbility(Player p, AbilH h, int slot, ClickType ct) {
        String id = h.id;
        if (slot == 45) { openEditor(p, id); return; }
        if (slot == 49) {
            askAb(p, id, "&eKetik ID ability baru (huruf/angka/_):", t -> {
                String aid = t.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                updAb(p, id, s -> {
                    s.set("abilities." + aid + ".trigger", "RIGHT_CLICK");
                    s.set("abilities." + aid + ".cooldown", 5);
                    s.set("abilities." + aid + ".actions", new ArrayList<String>());
                });
            });
            return;
        }
        String aid = h.abil.get(slot);
        if (aid == null) return;
        ForgeItem f = pl.items.get(id);
        Ability a = f == null ? null : f.abilities.get(aid);
        if (a == null) return;
        String base = "abilities." + aid;

        switch (ct) {
            case LEFT -> {
                say(p, "&7Contoh action: &fSOUND:entity.blaze.shoot:1:1 &8| &fPARTICLE:flame:40:0.6 &8| &fDAMAGE_NEARBY:5:6 &8| &fPOTION:speed:10:1");
                say(p, "&7Lainnya: &fHEAL:n FEED:n IGNITE_NEARBY:r:t DASH:p LAUNCH:p KNOCKBACK_NEARBY:r:p LIGHTNING_NEARBY:r MESSAGE:teks ACTIONBAR:teks COMMAND:cmd MODEL:id:tick MANA:n AURA_XP:skill:n GIVE:ref:n");
                askAb(p, id, "&eKetik 1 action:", t -> updAb(p, id, s -> {
                    List<String> l = new ArrayList<>(s.getStringList(base + ".actions"));
                    l.add(t);
                    s.set(base + ".actions", l);
                }));
            }
            case RIGHT -> updAb(p, id, s -> {
                List<String> l = new ArrayList<>(s.getStringList(base + ".actions"));
                if (!l.isEmpty()) l.remove(l.size() - 1);
                s.set(base + ".actions", l);
            });
            case SHIFT_LEFT -> {
                Trigger[] all = Trigger.values();
                Trigger next = all[(a.trigger().ordinal() + 1) % all.length];
                updAb(p, id, s -> s.set(base + ".trigger", next.name()));
            }
            case SHIFT_RIGHT -> updAb(p, id, s -> s.set(base, null));
            case DROP -> askAb(p, id, "&eKetik cooldown (detik):", t -> {
                try { double v = Double.parseDouble(t.trim()); updAb(p, id, s -> s.set(base + ".cooldown", v)); }
                catch (NumberFormatException ex) { say(p, "&cBukan angka."); openAbilities(p, id); }
            });
            case SWAP_OFFHAND -> askAb(p, id, "&eKetik mana cost (butuh AuraSkills):", t -> {
                try { double v = Double.parseDouble(t.trim()); updAb(p, id, s -> s.set(base + ".mana-cost", v)); }
                catch (NumberFormatException ex) { say(p, "&cBukan angka."); openAbilities(p, id); }
            });
            case CONTROL_DROP -> updAb(p, id, s -> s.set(base + ".consume", !a.consume()));
            default -> { }
        }
    }

    // ------------------------------------------------------------------ events
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof H holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != top) return;
        int slot = e.getSlot();
        ClickType ct = e.getClick();

        if (holder instanceof MainH m) {
            String id = m.ids.get(slot);
            if (id != null) {
                if (ct.isRightClick()) openEditor(p, id);
                else if (pl.items.give(p, "forge:" + id, 1)) say(p, "&aDiberikan: &f" + id);
                return;
            }
            switch (slot) {
                case 45 -> openMain(p, m.page - 1);
                case 53 -> openMain(p, m.page + 1);
                case 47 -> { pl.reload(); say(p, pl.runner.msg("reloaded", "count", String.valueOf(pl.items.all().size()))); openMain(p, m.page); }
                case 49 -> {
                    Material mat = p.getInventory().getItemInMainHand().getType();
                    Material use = mat.isAir() ? Material.DIAMOND_SWORD : mat;
                    pl.prompts.ask(p, "&eKetik ID item baru (huruf/angka/_):", t -> {
                        String nid = t.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                        if (!pl.items.create(nid, use)) say(p, "&cID sudah dipakai.");
                        openEditor(p, nid);
                    }, () -> openMain(p, m.page));
                }
                case 51 -> {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    String ref = pl.items.identifyExternal(hand);
                    if (ref == null) { say(p, "&cPegang item MMOItems / ExecutableItems dulu."); return; }
                    pl.prompts.ask(p, "&eKetik ID profil untuk &f" + ref + "&e:", t -> {
                        String nid = t.toLowerCase().replaceAll("[^a-z0-9_]", "_");
                        if (pl.items.get(nid) != null) { say(p, "&cID sudah dipakai."); openMain(p, m.page); return; }
                        pl.items.createBound(nid, ref, hand.getType());
                        openEditor(p, nid);
                    }, () -> openMain(p, m.page));
                }
                default -> { }
            }
        } else if (holder instanceof EditH eh) {
            clickEditor(p, eh, slot, ct);
        } else if (holder instanceof AbilH ah) {
            clickAbility(p, ah, slot, ct);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof H) e.setCancelled(true);
    }
}
