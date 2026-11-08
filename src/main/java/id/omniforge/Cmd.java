package id.omniforge;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Cmd implements TabExecutor {
    private final OmniForge pl;

    Cmd(OmniForge pl) {
        this.pl = pl;
    }

    private void say(CommandSender s, String text) {
        Text.send(s, pl.runner.prefix(), text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        String sub = a.length == 0 ? "gui" : a[0].toLowerCase();
        switch (sub) {
            case "gui", "menu" -> {
                if (!(sender instanceof Player p)) { say(sender, "&cHanya untuk pemain."); return true; }
                pl.gui.openMain(p, 0);
            }
            case "reload" -> {
                pl.reload();
                say(sender, pl.runner.msg("reloaded", "count", String.valueOf(pl.items.all().size())));
            }
            case "list" -> {
                say(sender, "&7Item native (&f" + pl.items.all().size() + "&7):");
                StringBuilder sb = new StringBuilder();
                for (ForgeItem f : pl.items.all()) sb.append("&f").append(f.id).append("&8, ");
                sender.sendMessage(Text.chat(sb.toString()));
            }
            case "info" -> {
                if (!(sender instanceof Player p)) return true;
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { say(sender, "&cTanganmu kosong."); return true; }
                String ext = pl.items.identifyExternal(hand);
                ForgeItem f = pl.items.of(hand);
                say(sender, "&7Material: &f" + hand.getType());
                say(sender, "&7Item luar: &f" + (ext == null ? "-" : ext));
                say(sender, "&7Profil OmniForge: &f" + (f == null ? "-" : f.id));
                say(sender, "&7Race kamu: &f" + pl.hooks.race.of(p));
            }
            case "give" -> {
                if (a.length < 3) { say(sender, "&cPakai: /of give <player> <ref> [jumlah]   ref = id | mmoitems:TYPE.ID | ei:id"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { say(sender, "&cPlayer tidak online."); return true; }
                int amt = 1;
                if (a.length > 3) {
                    try { amt = Math.max(1, Integer.parseInt(a[3])); } catch (NumberFormatException e) { say(sender, "&cJumlah bukan angka."); return true; }
                }
                if (pl.items.give(t, a[2], amt)) {
                    say(sender, pl.runner.msg("given", "item", a[2], "amount", String.valueOf(amt), "player", t.getName()));
                } else {
                    say(sender, "&cItem '" + a[2] + "' tidak ditemukan / plugin terkait belum siap.");
                }
            }
            case "link" -> {
                if (!(sender instanceof Player p)) return true;
                if (a.length < 2) { say(sender, "&cPakai: /of link <id_profil> (pegang item MMOItems/EI)"); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                String ref = pl.items.identifyExternal(hand);
                if (ref == null) { say(sender, "&cItem di tangan bukan item MMOItems / ExecutableItems."); return true; }
                String id = a[1].toLowerCase().replaceAll("[^a-z0-9_]", "_");
                if (pl.items.get(id) != null) { say(sender, "&cID sudah dipakai."); return true; }
                pl.items.createBound(id, ref, hand.getType() == Material.AIR ? Material.STONE : hand.getType());
                say(sender, "&aProfil &f" + id + " &adibuat & di-bind ke &f" + ref);
                pl.gui.openEditor(p, id);
            }
            default -> say(sender, "&7/of &f[gui | give <player> <ref> [n] | list | info | link <id> | reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String s : List.of("gui", "give", "list", "info", "link", "reload")) if (s.startsWith(a[0].toLowerCase())) out.add(s);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase().startsWith(a[1].toLowerCase())) out.add(p.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
            for (ForgeItem f : pl.items.all()) if (f.id.startsWith(a[2].toLowerCase())) out.add(f.id);
            if ("mmoitems:".startsWith(a[2].toLowerCase())) out.add("mmoitems:");
            if ("ei:".startsWith(a[2].toLowerCase())) out.add("ei:");
        }
        return out;
    }
}
