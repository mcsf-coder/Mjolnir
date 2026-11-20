package org.mjolnir.mjolnir;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Mjolnir extends JavaPlugin implements Listener {

    private NamespacedKey mjolnirKey;
    private final Map chargeStart = new HashMap();
    private final Map lastPing = new HashMap();

    @Override
    public void onEnable() {
        mjolnirKey = new NamespacedKey(this, "mjolnir_hammer");
        getServer().getPluginManager().registerEvents(this, this);
        registerRecipe();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            Iterator it = chargeStart.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                UUID uuid = (UUID) entry.getKey();
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline() || !isMjolnir(player.getInventory().getItemInMainHand())) {
                    it.remove();
                    lastPing.remove(uuid);
                    continue;
                }

                long startMs = (Long) entry.getValue();
                long lastPingMs = (Long) lastPing.getOrDefault(uuid, startMs);

                if ((now - lastPingMs) > 320L) {
                    long heldMs = Math.min(5000L, Math.max(200L, lastPingMs - startMs));
                    it.remove();
                    lastPing.remove(uuid);
                    throwMjolnir(player, heldMs);
                    continue;
                }

                long elapsed = Math.min(5000L, now - startMs);
                double seconds = elapsed / 1000.0;
                int filledBars = (int) Math.round((elapsed / 5000.0) * 10);
                StringBuilder bar = new StringBuilder(ChatColor.AQUA + "Mjolnir " + ChatColor.DARK_GRAY + "[");
                for (int i = 0; i < 10; i++) {
                    bar.append(i < filledBars ? ChatColor.GOLD + "■" : ChatColor.GRAY + "□");
                }
                bar.append(ChatColor.DARK_GRAY).append("] ").append(ChatColor.YELLOW).append(String.format("%.1fs", seconds));
                if (elapsed >= 5000L) bar.append(ChatColor.AQUA).append(ChatColor.BOLD).append(" MAX!");
                player.sendActionBar(bar.toString());

                float pitch = 0.7f + (float) ((elapsed / 5000.0) * 1.3f);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, pitch);
            }
        }, 2L, 2L);
    }

    private void registerRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "mjolnir_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createMjolnir());
        recipe.shape("III", "IBI", " R ");
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('B', Material.HEAVY_CORE);
        recipe.setIngredient('R', Material.BREEZE_ROD);
        Bukkit.addRecipe(recipe);
    }

    public ItemStack createMjolnir() {
        ItemStack hammer = new ItemStack(Material.MACE);
        ItemMeta meta = hammer.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Mjolnir");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Hold " + ChatColor.YELLOW + "Right-Click" + ChatColor.GRAY + " (up to 5s) to charge",
                    ChatColor.GRAY + "and hurl up to 100 blocks with " + ChatColor.AQUA + "Thunder",
                    ChatColor.GRAY + "before returning to your hand.",
                    ChatColor.DARK_GRAY + "Mythic Weapon"
            ));
            meta.addEnchant(Enchantment.LOYALTY, 3, true);
            meta.addEnchant(Enchantment.CHANNELING, 1, true);
            meta.addEnchant(Enchantment.IMPALING, 5, true);
            meta.getPersistentDataContainer().set(mjolnirKey, PersistentDataType.BYTE, (byte) 1);
            hammer.setItemMeta(meta);
        }
        return hammer;
    }

    private boolean isMjolnir(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(mjolnirKey, PersistentDataType.BYTE)
                || (meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).equals("Mjolnir"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("mjolnir")) {
            player.getInventory().addItem(createMjolnir());
            player.sendMessage(ChatColor.GOLD + "You summoned " + ChatColor.BOLD + "Mjolnir" + ChatColor.GOLD + "!");
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chargeStart.remove(uuid);
        lastPing.remove(uuid);
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chargeStart.remove(uuid);
        lastPing.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        if ((action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) && isMjolnir(item)) {
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
                return;
            }
            event.setCancelled(true);
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            chargeStart.putIfAbsent(uuid, now);
            lastPing.put(uuid, now);
        }
    }

    private void throwMjolnir(Player player, long chargeMs) {
        double ratio = Math.min(1.0, Math.max(0.15, chargeMs / 5000.0));
        int maxSteps = (int) (15 + (ratio * 85));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.4f);

        ItemStack thrownHammer = player.getInventory().getItemInMainHand().clone();
        thrownHammer.setAmount(1);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getAmount() == 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
            }
            if (thrownHammer.getItemMeta() instanceof Damageable dmg) {
                int nextDamage = dmg.getDamage() + 1;
                if (nextDamage == thrownHammer.getType().getMaxDurability()) {
                    thrownHammer = null;
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                } else {
                    dmg.setDamage(nextDamage);
                    thrownHammer.setItemMeta(dmg);
                }
            }
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        final ItemStack returningHammer = thrownHammer;
        final Location currentLoc = player.getEyeLocation().clone();
        final Vector stepDir = player.getEyeLocation().getDirection().normalize().multiply(2.0);
        final Set hitEntities = new HashSet();

        final ItemDisplay visualHammer = player.getWorld().spawn(currentLoc, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.MACE));
            display.setGlowing(true);
        });

        final AtomicInteger stepCount = new AtomicInteger(0);
        final AtomicBoolean returning = new AtomicBoolean(false);

        Bukkit.getScheduler().runTaskTimer(this, new java.util.function.Consumer() {
            @Override
            public void accept(Object objTask) {
                org.bukkit.scheduler.BukkitTask task = (org.bukkit.scheduler.BukkitTask) objTask;
                if (!player.isOnline()) {
                    visualHammer.remove();
                    if (returningHammer != null) player.getWorld().dropItemNaturally(currentLoc, returningHammer);
                    task.cancel();
                    return;
                }

                Particle.DustOptions thunderDust = new Particle.DustOptions(Color.AQUA, 1.5f);

                if (!returning.get()) {
                    currentLoc.add(stepDir);
                    int steps = stepCount.addAndGet(2);
                    visualHammer.teleport(currentLoc);
                    currentLoc.getWorld().spawnParticle(Particle.DUST, currentLoc, 5, 0.15, 0.15, 0.15, 0, thunderDust);

                    for (Object obj : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.5, 1.5, 1.5)) {
                        Entity entity = (Entity) obj;
                        if (entity instanceof LivingEntity target && !target.equals(player) && !hitEntities.contains(target.getUniqueId())) {
                            hitEntities.add(target.getUniqueId());
                            target.damage(14.0, player);
                            currentLoc.getWorld().strikeLightningEffect(target.getLocation());
                        }
                    }

                    if (currentLoc.getBlock().getType().isSolid() || steps == maxSteps || steps == maxSteps + 1) {
                        returning.set(true);
                        currentLoc.getWorld().playSound(currentLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 1.1f);
                        currentLoc.getWorld().strikeLightningEffect(currentLoc);
                    }
                } else {
                    Location targetHand = player.getEyeLocation();
                    Vector move = targetHand.toVector().subtract(currentLoc.toVector());

                    if (move.lengthSquared() == 0.0 || (int) targetHand.distance(currentLoc) == 0 || (int) targetHand.distance(currentLoc) == 1 || (int) targetHand.distance(currentLoc) == 2) {
                        visualHammer.remove();
                        if (returningHammer != null) {
                            HashMap leftover = player.getInventory().addItem(returningHammer);
                            for (Object drop : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), (ItemStack) drop);
                            }
                        }
                        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.0f);
                        task.cancel();
                        return;
                    }

                    currentLoc.add(move.normalize().multiply(2.4));
                    visualHammer.teleport(currentLoc);
                    currentLoc.getWorld().spawnParticle(Particle.DUST, currentLoc, 4, 0.12, 0.12, 0.12, 0, thunderDust);
                }
            }
        }, 1L, 1L);

        player.sendActionBar(ChatColor.GOLD + "" + ChatColor.BOLD + "MJOLNIR THROWN! " + ChatColor.AQUA + "(" + maxSteps + " blocks)");
    }
}
