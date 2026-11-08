package id.omniforge;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/** Satu profil item (hasil parse dari items/*.yml). */
public final class ForgeItem {
    public final String id;
    public String base;          // mmoitems:TYPE.ID | ei:id | null
    public String bind;          // ref item luar yang otomatis memakai profil ini
    public Material material = Material.STONE;
    public String name;
    public List<String> lore = new ArrayList<>();
    public Integer cmd;
    public String itemModel;
    public boolean unbreakable, glow;
    public Map<String, Integer> enchants = new LinkedHashMap<>();
    public Map<String, Integer> aeEnchants = new LinkedHashMap<>();
    public String slot = "mainhand";
    public Map<String, Double> stats = new LinkedHashMap<>();
    public Map<String, Integer> passive = new LinkedHashMap<>();
    public List<String> reqRaces = new ArrayList<>();
    public Map<String, Integer> reqSkills = new LinkedHashMap<>();
    public Map<String, Double> statBonus = new LinkedHashMap<>();
    public Map<String, Double> xpBoost = new LinkedHashMap<>();
    public Map<String, double[]> raceBonus = new LinkedHashMap<>(); // [damageMult, cooldownMult]
    public Map<String, Ability> abilities = new LinkedHashMap<>();

    public ForgeItem(String id) {
        this.id = id;
    }

    public boolean slotMatches(String actual) {
        String s = slot.toLowerCase();
        if (s.equals("any")) return true;
        if (s.equals("armor")) return actual.equals("head") || actual.equals("chest")
                || actual.equals("legs") || actual.equals("feet");
        return s.equals(actual);
    }

    static String statKey(String raw) {
        String k = raw.toLowerCase().replace('_', '-');
        return switch (k) {
            case "attack-damage" -> "damage";
            case "max-health" -> "health";
            case "movement-speed" -> "speed";
            case "armor-toughness" -> "toughness";
            case "knockback-resistance" -> "knockback-resist";
            default -> k;
        };
    }

    private static Map<String, Double> doubles(ConfigurationSection s, boolean statKeys) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (s == null) return m;
        for (String k : s.getKeys(false)) {
            m.put(statKeys ? statKey(k) : k.toLowerCase(), s.getDouble(k));
        }
        return m;
    }

    private static Map<String, Integer> ints(ConfigurationSection s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (s == null) return m;
        for (String k : s.getKeys(false)) m.put(k.toLowerCase(), s.getInt(k));
        return m;
    }

    public static ForgeItem parse(String id, ConfigurationSection s) {
        ForgeItem f = new ForgeItem(id.toLowerCase());
        f.base = blankToNull(s.getString("base"));
        f.bind = blankToNull(s.getString("bind"));
        Material m = Material.matchMaterial(s.getString("material", "STONE"));
        f.material = m != null ? m : Material.STONE;
        f.name = s.getString("name", s.getString("display-name"));
        f.lore = new ArrayList<>(s.getStringList("lore"));
        if (s.contains("custom-model-data")) f.cmd = s.getInt("custom-model-data");
        else if (s.contains("model-data")) f.cmd = s.getInt("model-data");
        f.itemModel = blankToNull(s.getString("item-model"));
        f.unbreakable = s.getBoolean("unbreakable");
        f.glow = s.getBoolean("glow");
        f.enchants = ints(s.getConfigurationSection("enchants"));
        f.aeEnchants = ints(s.getConfigurationSection("ae-enchants"));
        f.slot = s.getString("slot", "mainhand").toLowerCase();

        f.stats = doubles(s.getConfigurationSection("stats"), true);
        f.stats.putAll(doubles(s.getConfigurationSection("attributes"), true)); // alias gaya LegendaryForge

        ConfigurationSection pe = s.getConfigurationSection("passive-effects");
        if (pe == null) pe = s.getConfigurationSection("passive.effects");
        f.passive = ints(pe);

        f.reqRaces = new ArrayList<>();
        for (String r : s.getStringList("requirements.races")) f.reqRaces.add(r.toLowerCase());
        f.reqSkills = ints(s.getConfigurationSection("requirements.auraskills"));
        f.statBonus = doubles(s.getConfigurationSection("stat-bonus"), false);
        f.xpBoost = doubles(s.getConfigurationSection("xp-boost"), false);

        ConfigurationSection rb = s.getConfigurationSection("race-bonus");
        if (rb != null) {
            for (String r : rb.getKeys(false)) {
                f.raceBonus.put(r.toLowerCase(), new double[]{
                        rb.getDouble(r + ".damage-multiplier", 1.0),
                        rb.getDouble(r + ".cooldown-multiplier", 1.0)});
            }
        }

        ConfigurationSection ab = s.getConfigurationSection("abilities");
        if (ab != null) {
            for (String aid : ab.getKeys(false)) {
                ConfigurationSection a = ab.getConfigurationSection(aid);
                if (a == null) continue;
                f.abilities.put(aid.toLowerCase(), new Ability(
                        aid.toLowerCase(),
                        Trigger.parse(a.getString("trigger")),
                        a.getDouble("cooldown", 0),
                        a.getString("permission", ""),
                        a.getBoolean("consume", false),
                        a.getDouble("mana-cost", a.getDouble("mana", 0)),
                        new ArrayList<>(a.getStringList("actions"))));
            }
        }
        return f;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("none") ? null : s.trim();
    }
}
