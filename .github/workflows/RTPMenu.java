package com.fiatx2.rtp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RTPMenu extends JavaPlugin implements Listener, CommandExecutor {
    private static final int RADIUS = 5000;
    private final Map<UUID, BukkitTask> countdowns = new HashMap<>();

    private static final class Menu implements InventoryHolder {
        private final boolean confirm;
        private final World.Environment env;
        private Inventory inventory;

        private Menu(boolean confirm, World.Environment env) {
            this.confirm = confirm;
            this.env = env;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        if (getCommand("rtp") == null) {
            getLogger().severe("Command 'rtp' is missing from plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("rtp").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        countdowns.values().forEach(BukkitTask::cancel);
        countdowns.clear();
    }

    private ItemStack item(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }

        Menu holder = new Menu(false, null);
        Inventory inventory = Bukkit.createInventory(holder, 27, "RTP");
        holder.inventory = inventory;
        inventory.setItem(11, item(Material.GRASS_BLOCK, "§aOverworld"));
        inventory.setItem(13, item(Material.NETHERRACK, "§cNether"));
        inventory.setItem(15, item(Material.END_STONE, "§eThe End"));
        player.openInventory(inventory);
        return true;
    }

    private void openConfirm(Player player, World.Environment environment) {
        Menu holder = new Menu(true, environment);
        Inventory inventory = Bukkit.createInventory(holder, 27, "CONFIRM SELECTION");
        holder.inventory = inventory;
        inventory.setItem(11, item(Material.LIME_CONCRETE, "§aConfirm"));
        inventory.setItem(15, item(Material.RED_CONCRETE, "§cCancel"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        if (!menu.confirm) {
            switch (slot) {
                case 11 -> openConfirm(player, World.Environment.NORMAL);
                case 13 -> openConfirm(player, World.Environment.NETHER);
                case 15 -> openConfirm(player, World.Environment.THE_END);
                default -> { }
            }
        } else if (slot == 15) {
            player.closeInventory();
        } else if (slot == 11) {
            player.closeInventory();
            startCountdown(player, menu.env);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!countdowns.containsKey(id)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ())) {
            BukkitTask task = countdowns.remove(id);
            if (task != null) task.cancel();
            event.getPlayer().sendMessage("§cยกเลิกการวาป เพราะคุณขยับ");
        }
    }

    private void startCountdown(Player player, World.Environment environment) {
        UUID id = player.getUniqueId();
        if (countdowns.containsKey(id)) {
            player.sendMessage("§eคุณกำลังนับถอยหลังวาร์ปอยู่แล้ว");
            return;
        }

        World world = Bukkit.getWorlds().stream()
                .filter(candidate -> candidate.getEnvironment() == environment)
                .findFirst().orElse(null);
        if (world == null) {
            player.sendMessage("§cไม่พบโลกนี้");
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            private int number = 3;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    countdowns.remove(id);
                    cancel();
                    return;
                }
                if (number > 0) {
                    player.sendTitle("§e" + number, "", 0, 20, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    number--;
                    return;
                }

                countdowns.remove(id);
                cancel();
                Location destination = findSafe(world);
                if (destination == null) {
                    player.sendMessage("§cหาจุดวาร์ปไม่เจอ ลองใหม่");
                    return;
                }
                player.teleport(destination);
                player.sendMessage("§aวาร์ปสำเร็จ!");
            }
        }.runTaskTimer(this, 0L, 20L);
        countdowns.put(id, task);
    }

    private boolean unsafe(Material material) {
        return material == Material.LAVA || material == Material.WATER
                || material == Material.MAGMA_BLOCK || material == Material.CACTUS
                || material == Material.FIRE || material == Material.SOUL_FIRE
                || material == Material.CAMPFIRE || material == Material.SWEET_BERRY_BUSH
                || material == Material.POWDER_SNOW || material == Material.SOUL_CAMPFIRE
                || material == Material.WITHER_ROSE || material == Material.POINTED_DRIPSTONE;
    }

    private Location findSafe(World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = random.nextInt(-RADIUS, RADIUS + 1);
            int z = random.nextInt(-RADIUS, RADIUS + 1);

            if (world.getEnvironment() == World.Environment.NETHER) {
                x /= 5;
                z /= 5;
                for (int y = Math.min(120, world.getMaxHeight() - 2); y > Math.max(32, world.getMinHeight()); y--) {
                    Material floor = world.getBlockAt(x, y - 1, z).getType();
                    Material feet = world.getBlockAt(x, y, z).getType();
                    Material head = world.getBlockAt(x, y + 1, z).getType();
                    if (floor.isSolid() && !unsafe(floor) && feet.isAir() && head.isAir()) {
                        return new Location(world, x + 0.5, y, z + 0.5);
                    }
                }
            } else {
                int y = world.getHighestBlockYAt(x, z);
                if (y <= world.getMinHeight() || y + 2 >= world.getMaxHeight()) continue;
                Material floor = world.getBlockAt(x, y, z).getType();
                Material feet = world.getBlockAt(x, y + 1, z).getType();
                Material head = world.getBlockAt(x, y + 2, z).getType();
                if (floor.isSolid() && !unsafe(floor) && feet.isAir() && head.isAir()) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        return null;
    }
}
