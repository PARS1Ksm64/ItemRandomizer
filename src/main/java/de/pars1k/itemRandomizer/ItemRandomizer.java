package de.pars1k.itemRandomizer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class ItemRandomizer extends JavaPlugin implements Listener {

    private static final String PREFIX = "§8[§bChallenges§8] §r";

    private boolean running = false;
    private boolean started = false;
    private boolean finished = false;

    private int secondsElapsed = 0;
    private BukkitTask timerTask;

    private final Map<Material, Material> dropMap = new HashMap<>();

    @Override
    public void onEnable() {

        getLogger().info("ItemRandomizer Plugin by PARS1K has been enabled!");

        getServer().getPluginManager().registerEvents(this, this);

        generateRandomDrops();

        getCommand("start").setExecutor((sender, command, label, args) -> {
            if (started) {
                sender.sendMessage(PREFIX + "§cDie Challenge wurde bereits gestartet!");
                return true;
            }
            started = true;
            running = true;
            finished = false;

            startTimer();

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }

            Bukkit.broadcast(Component.text(PREFIX + "§aDie Challenge hat begonnen! Viel Erfolg!"));
            return true;
        });

        getCommand("pause").setExecutor((sender, command, label, args) -> {
            if (!started) {
                sender.sendMessage(PREFIX + "§cDie Challenge wurde noch nicht gestartet!");
                return true;
            }
            if (finished) {
                sender.sendMessage(PREFIX + "§cDie Challenge ist bereits beendet!");
                return true;
            }
            if (!running) {
                sender.sendMessage(PREFIX + "§cDie Challenge ist bereits pausiert!");
                return true;
            }
            running = false;
            Bukkit.broadcast(Component.text(PREFIX + "§eDie Challenge wurde pausiert!"));
            return true;
        });

        getCommand("resume").setExecutor((sender, command, label, args) -> {
            if (!started) {
                sender.sendMessage(PREFIX + "§cDie Challenge wurde noch nicht gestartet!");
                return true;
            }
            if (finished) {
                sender.sendMessage(PREFIX + "§cDie Challenge ist bereits beendet!");
                return true;
            }
            if (running) {
                sender.sendMessage(PREFIX + "§cDie Challenge läuft bereits!");
                return true;
            }
            running = true;
            Bukkit.broadcast(Component.text(PREFIX + "§aDie Challenge wurde fortgesetzt!"));
            return true;
        });
    }

    private void generateRandomDrops() {
        List<Material> allItems = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (!mat.isAir() && mat.isItem()) {
                allItems.add(mat);
            }
        }

        Random random = new Random();
        for (Material mat : Material.values()) {
            if (mat.isBlock() && !mat.isAir()) {
                Material randomItem = allItems.get(random.nextInt(allItems.size()));
                dropMap.put(mat, randomItem);
            }
        }
    }

    private void startTimer() {
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (finished) {
                    cancel();
                    return;
                }

                if (!running) {
                    Component pausedText = MiniMessage.miniMessage().deserialize(
                            "<gradient:#0213FD:#015BFE:#01A2FE:#00EAFF><b>Timer pausiert</b></gradient>"
                    );
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendActionBar(pausedText);
                    }
                    return;
                }

                secondsElapsed++;

                int minutes = secondsElapsed / 60;
                int seconds = secondsElapsed % 60;
                String timeFormatted = String.format("%02d:%02d", minutes, seconds);

                Component actionBarText = MiniMessage.miniMessage().deserialize(
                        "<gradient:#0213FD:#015BFE:#01A2FE:#00EAFF><b>" + timeFormatted + "</b></gradient>"
                );

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(actionBarText);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (finished) {
            return;
        }

        if (!running) {
            if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
                return;
            }

            if (!Objects.equals(event.getFrom().getWorld(), event.getTo().getWorld())) {
                return;
            }

            double distance = event.getFrom().distanceSquared(event.getTo());
            if (distance > 0.001) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!running && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!running) {
            event.setCancelled(true);
            return;
        }

        Material blockType = event.getBlock().getType();
        if (dropMap.containsKey(blockType)) {
            event.setDropItems(false);

            Material customDrop = dropMap.get(blockType);
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    new ItemStack(customDrop, 1)
            );
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!running) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!running) return;

        running = false;
        finished = true;
        if (timerTask != null) timerTask.cancel();

        String deadPlayerName = event.getEntity().getName();
        Bukkit.broadcast(Component.text(PREFIX + "§c§lCHALLENGE GESCHEITERT! §e" + deadPlayerName + " §cist gestorben."));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!running) return;

        if (event.getEntity() instanceof EnderDragon) {
            running = false;
            finished = true;

            if (timerTask != null) timerTask.cancel();

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }

            Bukkit.broadcast(Component.text(PREFIX + "§a§lCHALLENGE GESCHAFFT! §rDer Enderdrache wurde besiegt!"));
        }
    }
    @Override
    public void onDisable() {
        getLogger().info("ItemRandomizer Plugin by PARS1K has been disabled!");
    }
}