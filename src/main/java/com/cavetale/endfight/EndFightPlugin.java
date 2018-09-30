package com.cavetale.endfight;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Data;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.ApiVersion;
import org.bukkit.plugin.java.annotation.plugin.Description;
import org.bukkit.plugin.java.annotation.plugin.Plugin;
import org.bukkit.plugin.java.annotation.plugin.Website;
import org.bukkit.plugin.java.annotation.plugin.author.Author;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

@Plugin(name = "EndFight", version = "0.1")
@Description("EndFight")
@ApiVersion(ApiVersion.Target.v1_13)
@Author("StarTux")
@Website("https://cavetale.com")
@Commands(@Command(name = "endfight",
                   desc = "End Fight Event Plugin",
                   aliases = {},
                   permission = "endfight.endfight",
                   permissionMessage = "You do not have permission!",
                   usage = "/<command>"))
@Permissions(@Permission(name = "endfight.endfight",
                         desc = "Use /endfight",
                         defaultValue = PermissionDefault.OP))
public final class EndFightPlugin extends JavaPlugin implements Listener {
    private World world;
    private State state = new State();
    private Set<EntityType> mobs;
    private Objective objective;
    private Scoreboard scoreboard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        importConfig();
        getServer().getPluginManager().registerEvents(this, this);
        world = getServer().getWorld("home_the_end");
        if (world == null) {
            getLogger().warning("World not found!");
            return;
        }
        getServer().getScheduler().runTaskTimer(this, () -> onTick(), 1L, 1L);
        this.scoreboard = getServer().getScoreboardManager().getNewScoreboard();
        this.objective = scoreboard.registerNewObjective("EndFight", "dummy");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.setDisplayName(ChatColor.DARK_RED + "Kills");
        loadState();
        if (state.enabled) {
            for (Player player: world.getPlayers()) player.setScoreboard(scoreboard);
        }
    }

    @Override
    public void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(state.toString());
            return true;
        }
        switch (args[0]) {
        case "on":
            state.enabled = true;
            saveState();
            for (Player player: world.getPlayers()) player.setScoreboard(scoreboard);
            sender.sendMessage("Enabled");
            break;
        case "off":
            state.enabled = false;
            saveState();
            for (Player player: world.getPlayers()) player.setScoreboard(getServer().getScoreboardManager().getMainScoreboard());
            sender.sendMessage("Disabled");
            break;
        case "skip":
            state.alive = 0;
            saveState();
            sender.sendMessage("Skipping round");
            break;
        case "reset":
            state.alive = 0;
            state.round -= 1;
            saveState();
            sender.sendMessage("Resetting round");
            break;
        case "reload":
            importConfig();
            sender.sendMessage("Config reloaded");
            break;
        case "load":
            loadState();
            sender.sendMessage("State loaded");
            break;
        case "save":
            saveState();
            sender.sendMessage("State saved");
            break;
        case "restart":
            state = new State();
            saveState();
            sender.sendMessage("Restarted");
            break;
        case "mending":
            giveMending();
            sender.sendMessage("Mending initiated");
            break;
        default:
            return false;
        }
        return true;
    }

    void importConfig() {
        mobs = EnumSet.noneOf(EntityType.class);
        for (String key: getConfig().getStringList("mobs")) {
            try {
                mobs.add(EntityType.valueOf(key.toUpperCase()));
            } catch (IllegalArgumentException iae) {
                iae.printStackTrace();
            }
        }
    }

    // Ticking

    void onTick() {
        if (!state.enabled) return;
        if (state.alive <= 0) {
            spawnMobRound();
            for (Player player: world.getPlayers()) {
                player.sendTitle(ChatColor.DARK_RED + "Round " + state.round,
                                 ChatColor.DARK_RED + "End Fight",
                                 4, 10, 60);
            }
            saveState();
        }
    }

    void spawnMobRound() {
        state.mobs = 0;
        state.alive = 0;
        state.round += 1;
        List<EntityType> es = new ArrayList<>(mobs);
        Collections.shuffle(es);
        for (int i = 0; i < 3; i += 1) {
            for (int j = 0; j < state.round + 5; j += 1) {
                EntityType et = es.get(i);
                for (int k = 0; k < 100; k += 1) {
                    if (spawnMob(et)) {
                        state.mobs += 1;
                        state.alive += 1;
                        break;
                    }
                }
            }
        }
    }

    boolean spawnMob(EntityType et) {
        int x = ThreadLocalRandom.current().nextInt(32);
        int z = ThreadLocalRandom.current().nextInt(32);
        if (ThreadLocalRandom.current().nextBoolean()) x = -x;
        if (ThreadLocalRandom.current().nextBoolean()) z = -z;
        Block block = world.getHighestBlockAt(x, z);
        if (block.getY() <= 0) return false;
        Location loc = block.getLocation().add(0.5, 0.0, 0.5);
        switch (et) {
        case GHAST: case PHANTOM: loc = loc.add(0, 8.0, 0);
        default: break;
        }
        LivingEntity e = (LivingEntity)world.spawnEntity(loc, et);
        e.setRemoveWhenFarAway(false);
        return e != null;
    }

    // State

    @Data
    static class State {
        boolean enabled;
        int round, mobs, alive;
        Map<UUID, Integer> scores = new HashMap<>();
    }

    void saveState() {
        Gson gson = new Gson();
        File file = new File(getDataFolder(), "state.json");
        try {
            FileWriter fileWriter = new FileWriter(file);
            gson.toJson(state, fileWriter);
            fileWriter.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    void loadState() {
        Gson gson = new Gson();
        File file = new File(getDataFolder(), "state.json");
        if (!file.exists()) {
            state = new State();
            saveState();
        }
        try {
            FileReader fileReader = new FileReader(file);
            state = gson.fromJson(fileReader, State.class);
            fileReader.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        for (UUID id: state.scores.keySet()) {
            int score = state.scores.get(id);
            Player player = getServer().getPlayer(id);
            if (player != null) {
                objective.getScore(player.getName())
                    .setScore(score);
            }
        }
    }

    // --- Enchanting

    boolean enchant(Player player, EquipmentSlot slot, Enchantment enchantment) {
        ItemStack item;
        List<Enchantment> ench;
        switch (slot) {
        case HAND:
            item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.BOW) {
                ench = Arrays.asList(Enchantment.ARROW_FIRE, Enchantment.ARROW_INFINITE, Enchantment.ARROW_DAMAGE, Enchantment.ARROW_KNOCKBACK);
            } else {
                ench = Arrays.asList(Enchantment.DAMAGE_ARTHROPODS, Enchantment.FIRE_ASPECT, Enchantment.KNOCKBACK, Enchantment.LOOT_BONUS_MOBS, Enchantment.DAMAGE_ALL, Enchantment.DAMAGE_UNDEAD, Enchantment.SWEEPING_EDGE);
            }
            break;
        case HEAD:
            item = player.getInventory().getHelmet();
            ench = Arrays.asList(Enchantment.WATER_WORKER, Enchantment.PROTECTION_EXPLOSIONS, Enchantment.PROTECTION_FIRE, Enchantment.PROTECTION_PROJECTILE, Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.OXYGEN, Enchantment.THORNS);
            break;
        case CHEST:
            item = player.getInventory().getChestplate();
            ench = Arrays.asList(Enchantment.PROTECTION_EXPLOSIONS, Enchantment.PROTECTION_FIRE, Enchantment.PROTECTION_PROJECTILE, Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.THORNS);
            break;
        case LEGS:
            item = player.getInventory().getLeggings();
            ench = Arrays.asList(Enchantment.PROTECTION_EXPLOSIONS, Enchantment.PROTECTION_FIRE, Enchantment.PROTECTION_PROJECTILE, Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.THORNS);
            break;
            case FEET:
            item = player.getInventory().getBoots();
            ench = Arrays.asList(Enchantment.PROTECTION_EXPLOSIONS, Enchantment.PROTECTION_FIRE, Enchantment.PROTECTION_PROJECTILE, Enchantment.PROTECTION_ENVIRONMENTAL, Enchantment.THORNS, Enchantment.PROTECTION_FALL, Enchantment.DEPTH_STRIDER, Enchantment.FROST_WALKER);
            break;
        default: return false;
        }
        if (item == null || item.getType() == Material.AIR) return false;
        Collections.shuffle(ench);
        if (enchantment == null) enchantment = ench.get(0);
        int exist = item.getEnchantmentLevel(enchantment);
        if (exist > 0 || enchantment.canEnchantItem(item)) {
            if (exist < enchantment.getMaxLevel()) {
                item.removeEnchantment(enchantment);
                item.addEnchantment(enchantment, exist + 1);
                getLogger().info("Enchanted " + item.getType().name().toLowerCase() + " (" + slot.name().toLowerCase() + ") of " + player.getName() + " with " + enchantment.getKey().getKey() + " level " + (exist + 1) + ".");
                player.sendMessage("" + ChatColor.AQUA + ChatColor.ITALIC + "Improved your " + item.getType().name().toLowerCase().replace("_", " ") + " with " + enchantment.getKey().getKey() + " level " + (exist + 1));
                return true;
            }
        }
        return false;
    }

    // --- Events

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (!state.enabled) return;
        if (event.getPlayer().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!state.enabled) return;
        if (!event.getEntity().getWorld().equals(world)) return;
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        state.alive -= 1;
        UUID id = player.getUniqueId();
        Integer kills = state.scores.get(id);
        if (kills == null) kills = 0;
        kills += 1;
        state.scores.put(id, kills);
        objective.getScore(player.getName())
            .setScore(kills);
        saveState();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.DARK_RED + "You have " + kills + "kills."));
        if (kills % 10 == 0) {
            switch ((kills / 10) % 5) {
            case 0: enchant(player, EquipmentSlot.HAND, null); break;
            case 1: enchant(player, EquipmentSlot.HEAD, null); break;
            case 2: enchant(player, EquipmentSlot.CHEST, null); break;
            case 3: enchant(player, EquipmentSlot.LEGS, null); break;
            case 4: enchant(player, EquipmentSlot.FEET, null); break;
            default: throw new IllegalStateException("Your math is wrong");
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!state.enabled) return;
        if (event.getFrom().equals(world)) {
            event.getPlayer().setScoreboard(getServer().getScoreboardManager().getMainScoreboard());
        } else if (event.getPlayer().getWorld().equals(world)) {
            event.getPlayer().setScoreboard(scoreboard);
        }
    }

    // --- Finale

    void giveMending() {
        new BukkitRunnable() {
            private int t = 0;

            @Override
            public void run() {
                List<Player> ps = new ArrayList<>();
                for (Player p: getServer().getOnlinePlayers()) {
                    Integer score = state.scores.get(p.getUniqueId());
                    if (score != null && score >= 50) {
                        ps.add(p);
                    }
                }
                int ticks = t++;
                switch (ticks) {
                case 0: case 20: case 40: case 60: case 80:
                    int c = 5 - (ticks / 20);
                    for (Player p: ps) {
                        p.sendTitle("" + ChatColor.DARK_RED + c, ChatColor.DARK_RED + "Get Ready for Mending", 0, 40, 0);
                    }
                    break;
                case 100:
                    for (Player p: ps) {
                        p.sendTitle("" + ChatColor.DARK_RED + "Mending!", "", 0, 20, 100);
                        enchant(p, EquipmentSlot.HAND, Enchantment.MENDING);
                        enchant(p, EquipmentSlot.HEAD, Enchantment.MENDING);
                        enchant(p, EquipmentSlot.CHEST, Enchantment.MENDING);
                        enchant(p, EquipmentSlot.LEGS, Enchantment.MENDING);
                        enchant(p, EquipmentSlot.FEET, Enchantment.MENDING);
                    }
                    cancel();
                    break;
                default:
                    break;
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }
}
