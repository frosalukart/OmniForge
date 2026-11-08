package id.omniforge;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public final class ItemRegistry {
    private final OmniForge pl;
    public final NamespacedKey idKey;
    private final Map<String, ForgeItem> items = new LinkedHashMap<>();
    private final Map<String, File> files = new HashMap<>();
    private final Map<String, ForgeItem> binds = new HashMap<>();
    private final File folder;

    ItemRegistry(OmniForge pl) {
        this.pl = pl;
        this.idKey = new NamespacedKey(pl, "id");
        this.folder = new File(pl.getDataFolder(), "items");
    }

    // ------------------------------------------------------------------ load
    public int load() {
        items.clear();
        files.clear();
        binds.clear();
        if (!folder.exists()) {
            folder.mkdirs();
            pl.saveResource("items/example.yml", false);
        }
        File[] list = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (list != null) {
            Arrays.sort(list);
            for (File f : list) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                for (String key : y.getKeys(false)) {
                    ConfigurationSection s = y.getConfigurationSection(key);
                    if (s == null) continue;
                    try {
                        ForgeItem fi = ForgeItem.parse(key, s);
                        items.put(fi.id, fi);
                        files.put(fi.id, f);
                        if (fi.bind != null) binds.put(fi.bind.toLowerCase(), fi);
                    } catch (Exception e) {
                        pl.getLogger().warning("Item '" + key + "' di " + f.getName() + " gagal dimuat: " + e.getMessage());
                    }
                }
            }
        }
        return items.size();
    }

    public Collection<ForgeItem> all() { return items.values(); }
    public ForgeItem get(String id) { return id == null ? null : items.get(id.toLowerCase()); }

    // ------------------------------------------------------------------ edit (dipakai GUI)
    public void edit(String id, Consumer<ConfigurationSection> fn) {
        File f = files.get(id);
        if (f == null) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String realKey = id;
        for (String k : y.getKeys(false)) if (k.equalsIgnoreCase(id)) realKey = k;
        ConfigurationSection s = y.getConfigurationSection(realKey);
        if (s == null) s = y.createSection(realKey);
        fn.accept(s);
        save(y, f);
        load();
    }

    public boolean create(String id, Material mat) {
        id = id.toLowerCase().replaceAll("[^a-z0-9_]", "_");
        if (items.containsKey(id)) return false;
        File f = new File(folder, "gui_items.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection s = y.createSection(id);
        s.set("material", mat.name());
        s.set("name", "&f" + id);
        save(y, f);
        load();
        return true;
    }

    public void createBound(String id, String ref, Material mat) {
        create(id, mat);
        edit(id.toLowerCase().replaceAll("[^a-z0-9_]", "_"), s -> {
            s.set("bind", ref);
            s.set("name", null);
        });
    }

    public void delete(String id) {
        File f = files.get(id);
        if (f == null) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String k : new ArrayList<>(y.getKeys(false))) if (k.equalsIgnoreCase(id)) y.set(k, null);
        save(y, f);
        load();
    }

    private void save(YamlConfiguration y, File f) {
        try {
            y.save(f);
        } catch (IOException e) {
            pl.getLogger().warning("Gagal menyimpan " + f.getName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ identify
    /** Mengembalikan ref item luar (mmoitems:TYPE.ID / ei:id) atau null. */
    public String identifyExternal(ItemStack s) {
        if (s == null || s.getType().isAir()) return null;
        String r = pl.hooks.mmo.identify(s);
        if (r == null) r = pl.hooks.ei.identify(s);
        return r;
    }

    /** Cari profil untuk item ini (tag PDC milik kita, atau 'bind'). */
    public ForgeItem of(ItemStack s) {
        if (s == null || s.getType().isAir() || !s.hasItemMeta()) return null;
        String id = s.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id != null) return items.get(id);
        if (!binds.isEmpty()) {
            String ref = identifyExternal(s);
            if (ref != null) return binds.get(ref.toLowerCase());
        }
        return null;
    }

    // ------------------------------------------------------------------ build / give
    /** ref: forge:id | id | mmoitems:TYPE.ID | ei:id */
    public ItemStack createRef(String ref, int amount) {
        if (ref == null) return null;
        String lower = ref.toLowerCase();
        ItemStack it = null;
        if (lower.startsWith("mmoitems:")) it = pl.hooks.mmo.create(ref.substring(9));
        else if (lower.startsWith("ei:")) it = pl.hooks.ei.create(ref.substring(3), amount);
        else {
            ForgeItem f = get(lower.startsWith("forge:") ? ref.substring(6) : ref);
            if (f != null) return build(f, amount);
        }
        if (it != null) it.setAmount(Math.max(1, amount));
        return it;
    }

    public boolean give(Player p, String ref, int amount) {
        String lower = ref.toLowerCase();
        ItemStack it = createRef(ref, amount);
        if (it == null && lower.startsWith("ei:") && Hooks.enabled("ExecutableItems")) {
            pl.hooks.ei.giveByCommand(p, ref.substring(3), amount);
            return true;
        }
        if (it == null) return false;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        left.values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
        return true;
    }

    public ItemStack build(ForgeItem f, int amount) {
        String baseRef = f.base != null ? f.base : f.bind;
        ItemStack item = null;
        boolean external = false;
        if (baseRef != null) {
            String lower = baseRef.toLowerCase();
            if (lower.startsWith("mmoitems:")) item = pl.hooks.mmo.create(baseRef.substring(9));
            else if (lower.startsWith("ei:")) item = pl.hooks.ei.create(baseRef.substring(3), 1);
            if (item == null) {
                pl.getLogger().warning("Item '" + f.id + "': base '" + baseRef + "' tidak bisa dibuat (plugin/API tidak siap) -> pakai material " + f.material);
            } else {
                external = true;
            }
        }
        if (item == null) item = new ItemStack(f.material);
        item.setAmount(Math.max(1, amount));

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (f.name != null) meta.displayName(Text.c(f.name));

        List<Component> lore = new ArrayList<>();
        if (external && meta.lore() != null) lore.addAll(meta.lore());
        for (String l : f.lore) lore.add(Text.c(l));
        if (pl.getConfig().getBoolean("show-stats-in-lore", true)) {
            String fmt = pl.getConfig().getString("stat-format", "&7{name}: &a+{value}");
            for (Map.Entry<String, Double> e : f.stats.entrySet()) {
                String nm = pl.getConfig().getString("stat-names." + e.getKey(), e.getKey());
                double v = e.getValue();
                String val = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
                lore.add(Text.c(fmt.replace("{name}", nm).replace("{value}", val)));
            }
        }
        if (!lore.isEmpty()) meta.lore(lore);

        applyTexture(meta, f);
        if (f.unbreakable) meta.setUnbreakable(true);
        if (f.glow) meta.setEnchantmentGlintOverride(true);

        for (Map.Entry<String, Integer> e : f.enchants.entrySet()) {
            Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(e.getKey()));
            if (en != null) meta.addEnchant(en, e.getValue(), true);
            else pl.getLogger().warning("Item '" + f.id + "': enchant vanilla '" + e.getKey() + "' tidak dikenal.");
        }

        EquipmentSlotGroup group = group(f.slot);
        for (Map.Entry<String, Double> e : f.stats.entrySet()) {
            Attribute at = attribute(e.getKey());
            if (at == null) continue; // stat custom (crit/lifesteal) dibaca saat serangan
            meta.addAttributeModifier(at, new AttributeModifier(
                    new NamespacedKey(pl, ("s_" + f.id + "_" + e.getKey()).replaceAll("[^a-z0-9_]", "_")),
                    e.getValue(), AttributeModifier.Operation.ADD_NUMBER, group));
        }

        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, f.id);
        item.setItemMeta(meta);

        // AdvancedEnchantments diterapkan TERAKHIR supaya lore-nya tidak ditimpa
        for (Map.Entry<String, Integer> e : f.aeEnchants.entrySet()) {
            item = pl.hooks.ae.apply(item, e.getKey(), e.getValue());
        }
        return item;
    }

    /** Texture: item-model (1.21.2+) + custom model data (komponen 1.21.4+, fallback legacy). */
    private void applyTexture(ItemMeta meta, ForgeItem f) {
        if (f.itemModel != null) {
            NamespacedKey k = NamespacedKey.fromString(f.itemModel.toLowerCase());
            if (k != null) meta.setItemModel(k);
            else pl.getLogger().warning("Item '" + f.id + "': item-model '" + f.itemModel + "' tidak valid (format namespace:nama).");
        }
        if (f.cmd != null) {
            try {
                var comp = meta.getCustomModelDataComponent();
                comp.setFloats(List.of((float) f.cmd));
                meta.setCustomModelDataComponent(comp);
            } catch (Throwable t) {
                try {
                    ItemMeta.class.getMethod("setCustomModelData", Integer.class).invoke(meta, f.cmd);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    static EquipmentSlotGroup group(String s) {
        return switch (s.toLowerCase()) {
            case "mainhand" -> EquipmentSlotGroup.MAINHAND;
            case "offhand" -> EquipmentSlotGroup.OFFHAND;
            case "head" -> EquipmentSlotGroup.HEAD;
            case "chest" -> EquipmentSlotGroup.CHEST;
            case "legs" -> EquipmentSlotGroup.LEGS;
            case "feet" -> EquipmentSlotGroup.FEET;
            case "armor" -> EquipmentSlotGroup.ARMOR;
            default -> EquipmentSlotGroup.ANY;
        };
    }

    static Attribute attribute(String stat) {
        return switch (stat) {
            case "damage" -> Attribute.ATTACK_DAMAGE;
            case "attack-speed" -> Attribute.ATTACK_SPEED;
            case "armor" -> Attribute.ARMOR;
            case "toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "health" -> Attribute.MAX_HEALTH;
            case "speed" -> Attribute.MOVEMENT_SPEED;
            case "knockback-resist" -> Attribute.KNOCKBACK_RESISTANCE;
            default -> null;
        };
    }
}
