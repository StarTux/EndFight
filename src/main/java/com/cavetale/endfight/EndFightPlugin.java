package com.cavetale.endfight;

import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.item.ItemKinds;
import com.cavetale.core.util.Json;
import com.cavetale.fam.trophy.Highscore;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.item.trophy.TrophyCategory;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;

public final class EndFightPlugin extends JavaPlugin implements Listener {
    private State state = new State();
    private List<EntityType> mobs = List.of(EntityType.ZOMBIE,
                                            EntityType.ZOMBIE_VILLAGER,
                                            EntityType.SKELETON,
                                            EntityType.CREEPER,
                                            EntityType.SPIDER,
                                            EntityType.STRAY,
                                            EntityType.MAGMA_CUBE,
                                            EntityType.SLIME,
                                            EntityType.DROWNED,
                                            EntityType.HUSK,
                                            EntityType.CAVE_SPIDER,
                                            EntityType.WITHER_SKELETON,
                                            EntityType.PHANTOM,
                                            EntityType.WITCH,
                                            EntityType.ENDERMAN,
                                            EntityType.BLAZE,
                                            EntityType.PILLAGER,
                                            EntityType.VINDICATOR,
                                            EntityType.GHAST,
                                            EntityType.EVOKER,
                                            EntityType.WARDEN,
                                            EntityType.WITHER);
    private List<Highscore> highscore = List.of();
    private List<Component> highscoreLines = List.of();
    private int mendingTicks;
    private boolean mending;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> onTick(), 1L, 1L);
        loadState();
    }

    @Override
    public void onDisable() {
        saveState();
    }

    private World getWorld() {
        return Bukkit.getWorld("end_fight");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(state.toString());
            return true;
        }
        switch (args[0]) {
        case "on":
            state.enabled = true;
            saveState();
            sender.sendMessage("Enabled");
            break;
        case "off":
            state.enabled = false;
            saveState();
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
            mending = true;
            mendingTicks = 0;
            sender.sendMessage("Mending initiated");
            break;
        case "enchant":
            enchant(Bukkit.getPlayerExact(args[1]), EquipmentSlot.valueOf(args[2].toUpperCase()), null);
            break;
        case "next":
            spawnMobRound();
            break;
        case "dragonoff":
            getWorld().getEnderDragonBattle().getEnderDragon().setHealth(0.0);
            break;
        case "dragonon":
            getWorld().spawn(getWorld().getSpawnLocation().add(0.0, 10.0, 0.0), EnderDragon.class, e -> {
                    e.setPersistent(true);
                    e.setRemoveWhenFarAway(false);
                    e.setPhase(EnderDragon.Phase.CIRCLING);
                });
            sender.sendMessage("Dragon on?");
            break;
        case "reward":
            Highscore.reward(state.scores,
                             "end_fight",
                             TrophyCategory.MEDAL,
                             text("End Fight 2022", DARK_RED),
                             hi -> "You killed " + hi.score + " mob" + (hi.score == 1 ? "" : "s"));
        default:
            return false;
        }
        return true;
    }

    // Ticking

    private void onTick() {
        if (!state.enabled) return;
        if (state.alive <= 0 || state.roundTicks > 20 * 60 * 2) {
            spawnMobRound();
            saveState();
        } else {
            state.roundTicks += 1;
        }
        if (mending) tickMending();
    }

    private void spawnMobRound() {
        state.mobs = 0;
        state.alive = 0;
        state.round += 1;
        state.roundTicks = 0;
        List<EntityType> es = new ArrayList<>(mobs.subList(0, Math.max(3, Math.min(state.round, mobs.size()))));
        Collections.shuffle(es);
        for (int i = 0; i < 3; i += 1) {
            EntityType et = es.get(i);
            int amount = et != EntityType.WARDEN && et != EntityType.WITHER
                ? state.round + 5
                : 1;
            for (int j = 0; j < amount; j += 1) {
                for (int k = 0; k < 100; k += 1) {
                    if (spawnMob(et)) {
                        state.mobs += 1;
                        state.alive += 1;
                        break;
                    }
                }
            }
        }
        for (Player player : getWorld().getPlayers()) {
            player.showTitle(title(text("Round " + state.round, DARK_RED),
                                   text("End Fight", DARK_RED),
                                   times(Duration.ofMillis(4L * 50L),
                                         Duration.ofMillis(10L * 50L),
                                         Duration.ofMillis(60L * 60L))));
        }
    }

    private static final NamespacedKey MOB_KEY = NamespacedKey.fromString("endfight:mob");

    private boolean spawnMob(EntityType et) {
        int x = ThreadLocalRandom.current().nextInt(32);
        int z = ThreadLocalRandom.current().nextInt(32);
        if (ThreadLocalRandom.current().nextBoolean()) x = -x;
        if (ThreadLocalRandom.current().nextBoolean()) z = -z;
        Block block = getWorld().getHighestBlockAt(x, z);
        if (block.getY() <= 0) return false;
        Location loc = block.getLocation().add(0.5, 1.0, 0.5);
        switch (et) {
        case GHAST: case PHANTOM: loc = loc.add(0, 8.0, 0);
        default: break;
        }
        LivingEntity e = (LivingEntity) getWorld().spawnEntity(loc, et);
        e.setRemoveWhenFarAway(false);
        e.getPersistentDataContainer().set(MOB_KEY, PersistentDataType.BYTE, (byte) 1);
        return e != null;
    }

    // State

    private void saveState() {
        getDataFolder().mkdirs();
        Json.save(new File(getDataFolder(), "state.json"), state);
    }

    private void loadState() {
        state = Json.load(new File(getDataFolder(), "state.json"), State.class, State::new);
        computeHighscore();
    }

    // --- Enchanting

    private static String roman(int level) {
        switch (level) {
        case 1: return "I";
        case 2: return "II";
        case 3: return "III";
        case 4: return "IV";
        case 5: return "V";
        case 6: return "VI";
        case 7: return "VII";
        case 8: return "VIII";
        case 9: return "IX";
        default: return "" + level;
        }
    }

    private boolean enchant(Player player, EquipmentSlot slot, Enchantment enchantment) {
        final ItemStack item = player.getInventory().getItem(slot);
        if (item == null || item.getType().isAir()) {
            player.sendMessage(text("Unfortunately your " + slot.name().toLowerCase() + " slot is empty", RED));
            return false;
        }
        Mytems mytems = Mytems.forItem(item);
        if (mytems != null) {
            player.sendMessage(join(noSeparators(),
                                    text("Cannot enchant "),
                                    mytems.getMytem().getDisplayName())
                               .color(RED));
            return false;
        }
        if (item.getType().getEquipmentSlot() != slot) {
            player.sendMessage(join(noSeparators(),
                                    text("Unfortunately your "), ItemKinds.chatDescription(item),
                                    text(" does not belong in the " + slot.name().toLowerCase() + " slot"))
                               .color(RED));
            return false;
        }
        if (enchantment == null) {
            List<Enchantment> enchantments = new ArrayList<>();
            for (Enchantment ench : Enchantment.values()) {
                if (ench.equals(Enchantment.MENDING)) continue;
                if (ench.isCursed()) continue;
                if (!ench.canEnchantItem(item)) continue;
                enchantments.add(ench);
            }
            if (enchantments.isEmpty()) {
                player.sendMessage(join(noSeparators(),
                                        text("Unfortunately your "), ItemKinds.chatDescription(item),
                                        text(" cannot be enchanted"))
                                   .color(RED));
                return false;
            }
            enchantment = enchantments.get(ThreadLocalRandom.current().nextInt(enchantments.size()));
        }
        if (!enchantment.canEnchantItem(item)) {
            player.sendMessage(join(noSeparators(),
                                    text("Unfortunately your "), ItemKinds.chatDescription(item),
                                    text(" cannot be enchanted with "),
                                    translatable(enchantment))
                               .color(RED));
        }
        ItemMeta meta = item.getItemMeta();
        final int levelHas = meta.getEnchantLevel(enchantment);
        final int level = levelHas + 1;
        if (levelHas >= enchantment.getMaxLevel()) {
            player.sendMessage(join(noSeparators(),
                                    text("Unfortunately your "), ItemKinds.chatDescription(item),
                                    text(" has reached maximum level: "),
                                    translatable(enchantment),
                                    text(" " + roman(levelHas)))
                               .color(RED));
            return false;
        }
        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        getLogger().info("Enchanted " + item.getType() + " of " + player.getName() + " with " + enchantment.getKey().getKey() + " " + level);
        player.sendMessage(join(noSeparators(),
                                text("Enchanted your "), ItemKinds.chatDescription(item),
                                text(" with "),
                                translatable(enchantment),
                                text(" " + roman(level)))
                           .color(GREEN));
        return true;
    }

    // --- Events

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        if (!state.enabled) return;
        if (event.getPlayer().getWorld().equals(getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!state.enabled) return;
        if (!event.getEntity().getWorld().equals(getWorld())) return;
        if (!event.getEntity().getPersistentDataContainer().has(MOB_KEY)) return;
        state.alive -= 1;
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        UUID id = player.getUniqueId();
        Integer kills = state.scores.get(id);
        if (kills == null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ml add " + player.getName());
            kills = 0;
        }
        kills += 1;
        state.scores.put(id, kills);
        saveState();
        computeHighscore();
        player.sendActionBar(text("You have " + kills + "kills", DARK_RED));
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
    private void onPlayerHud(PlayerHudEvent event) {
        if (!state.enabled) return;
        if (!event.getPlayer().getWorld().equals(getWorld())) return;
        List<Component> lines = new ArrayList<>();
        lines.add(join(noSeparators(), text(tiny("your kills "), GRAY),
                       text(state.scores.getOrDefault(event.getPlayer().getUniqueId(), 0), DARK_RED)));
        lines.addAll(highscoreLines);
        event.sidebar(PlayerHudPriority.HIGH, lines);
        if (mending) {
            float progress = ((float) mendingTicks) / 200.0f;
            event.bossbar(PlayerHudPriority.HIGHEST,
                          text("Get Ready for Mending", DARK_RED),
                          BossBar.Color.RED, BossBar.Overlay.PROGRESS, progress);
        } else {
            float progress = (float) state.alive / (float) state.mobs;
            event.bossbar(PlayerHudPriority.DEFAULT,
                          join(separator(space()),
                               text(tiny("round"), GRAY),
                               text(state.round, DARK_RED),
                               text(tiny("mobs"), GRAY),
                               text(state.alive, DARK_RED),
                               text("/", GRAY),
                               text(state.mobs, DARK_RED)),
                          BossBar.Color.RED, BossBar.Overlay.PROGRESS, progress);
        }
    }

    // --- Finale

    private void tickMending() {
        if (mendingTicks == 200) {
            mending = false;
            List<Player> ps = new ArrayList<>();
            for (Player p : getWorld().getPlayers()) {
                Integer score = state.scores.get(p.getUniqueId());
                if (score != null && score > 0) {
                    ps.add(p);
                }
            }
            for (Player p : ps) {
                p.showTitle(title(text("Mending!", DARK_RED),
                                  empty(),
                                  times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5))));
                enchant(p, EquipmentSlot.HAND, Enchantment.MENDING);
                enchant(p, EquipmentSlot.HEAD, Enchantment.MENDING);
                enchant(p, EquipmentSlot.CHEST, Enchantment.MENDING);
                enchant(p, EquipmentSlot.LEGS, Enchantment.MENDING);
                enchant(p, EquipmentSlot.FEET, Enchantment.MENDING);
            }
        }
        mendingTicks += 1;
    }

    private void computeHighscore() {
        highscore = Highscore.of(state.scores);
        highscoreLines = Highscore.sidebar(highscore, TrophyCategory.MEDAL);
    }
}
