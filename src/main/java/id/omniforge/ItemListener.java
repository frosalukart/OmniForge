package id.omniforge;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ItemListener implements Listener {
    private final OmniForge pl;
    private final Random rnd = new Random();

    ItemListener(OmniForge pl) {
        this.pl = pl;
    }

    private void fireAll(Player p, ItemStack stack, Trigger trig, LivingEntity target, double dmgMult) {
        ForgeItem f = pl.items.of(stack);
        if (f == null) return;
        for (Ability a : new ArrayList<>(f.abilities.values())) {
            if (a.trigger() == trig) pl.runner.fire(p, f, a, stack, target, dmgMult);
        }
    }

    private boolean has(ItemStack stack, Trigger t) {
        ForgeItem f = pl.items.of(stack);
        if (f == null) return false;
        for (Ability a : f.abilities.values()) if (a.trigger() == t) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() == null) return;
        ItemStack item = e.getItem();
        if (item == null) return;
        Action act = e.getAction();
        boolean right = act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK;
        boolean left = act == Action.LEFT_CLICK_AIR || act == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        // hanya item di tangan yang memang berprofil, dan slot-nya cocok
        ForgeItem f = pl.items.of(item);
        if (f == null) return;
        String slot = e.getHand() == EquipmentSlot.HAND ? "mainhand" : "offhand";
        if (!f.slotMatches(slot) && !f.slot.equals("armor")) return;

        Player p = e.getPlayer();
        Trigger normal = right ? Trigger.RIGHT_CLICK : Trigger.LEFT_CLICK;
        Trigger shift = right ? Trigger.SHIFT_RIGHT_CLICK : Trigger.SHIFT_LEFT_CLICK;
        Trigger use = p.isSneaking() && has(item, shift) ? shift : normal;
        fireAll(p, item, use, null, 1.0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;

        if (attacker != null && e.getEntity() instanceof LivingEntity victim) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            ForgeItem f = pl.items.of(hand);
            if (f != null && f.slotMatches("mainhand") && pl.runner.deny(attacker, f) == null) {
                double[] rb = pl.runner.raceBonus(attacker, f);
                double dmg = e.getDamage() * rb[0];
                double cc = f.stats.getOrDefault("crit-chance", 0.0);
                if (cc > 0 && rnd.nextDouble() * 100 < cc) {
                    dmg *= 1 + f.stats.getOrDefault("crit-damage", 50.0) / 100.0;
                    attacker.sendActionBar(Text.c("&c&lCRITICAL!"));
                }
                e.setDamage(dmg);
                double ls = f.stats.getOrDefault("lifesteal", 0.0);
                if (ls > 0) {
                    double max = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                    attacker.setHealth(Math.min(max, attacker.getHealth() + dmg * ls / 100.0));
                }
                fireAll(attacker, hand, Trigger.HIT_ENTITY, victim, rb[0]);
            }
        }

        if (e.getEntity() instanceof Player victim) {
            LivingEntity source = e.getDamager() instanceof LivingEntity le ? le
                    : (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le2 ? le2 : null);
            List<ItemStack> worn = new ArrayList<>();
            worn.add(victim.getInventory().getItemInMainHand());
            worn.add(victim.getInventory().getItemInOffHand());
            for (ItemStack a : victim.getInventory().getArmorContents()) worn.add(a);
            for (ItemStack s : worn) fireAll(victim, s, Trigger.WHEN_HIT, source, 1.0);
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        fireAll(killer, killer.getInventory().getItemInMainHand(), Trigger.KILL_ENTITY, e.getEntity(), 1.0);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        fireAll(e.getPlayer(), e.getItem(), Trigger.CONSUME, null, 1.0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pl.runner.clear(e.getPlayer().getUniqueId());
        pl.passive.forget(e.getPlayer().getUniqueId());
    }
}
