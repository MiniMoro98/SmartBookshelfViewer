package it.moro.minimoro;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.data.Directional;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ChiseledBookshelfInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Events implements Listener, CommandExecutor, TabCompleter {
    private static ChiseledBookshelfViewer plugin;
    private BukkitTask bookshelfTask;
    private File fileConfig;
    private YamlConfiguration config;
    private final Map<UUID, String> lastTargetMap = new HashMap<>();
    private final Map<UUID, TextDisplay> holograms = new HashMap<>();
    private final Set<UUID> activeActionBarPlayers = new HashSet<>();

    public Events(ChiseledBookshelfViewer plugin) {
        Events.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, Command command, @NonNull String label, String @NonNull [] args) {
        if (!command.getName().equalsIgnoreCase("bookshelf")) return false;
        if (args.length == 0) return true;
        if (args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("smartbookshelf.reload")) {
                reloadConfig();
                if (sender instanceof Player player) {
                    player.sendMessage("§a[SmartBookshelfViewer] Configuration reloaded!");
                } else if (sender instanceof ConsoleCommandSender) {
                    plugin.getLogger().info("\u001B[32mConfiguration reloaded!\u001B[0m");
                }
            }
        } else if (args[0].equalsIgnoreCase("setmode") && args.length == 2) {
            if (sender.hasPermission("smartbookshelf.setmode")) {
                String mode = args[1];
                if (mode.equalsIgnoreCase("chat") || mode.equalsIgnoreCase("actionbar") || mode.equalsIgnoreCase("hologram")) {
                    config.set("output_type", mode);
                    saveConfig("Output mode set to " + mode + ".", "Failed to save config.", sender);
                } else {
                    if (sender instanceof Player) {
                        sender.sendMessage("§e[SmartBookshelfViewer] Unrecognized mode '" + mode + "'.");
                    } else if (sender instanceof ConsoleCommandSender) {
                        plugin.getLogger().info("Unrecognized mode '" + mode + "'.");
                    }
                }
            }
        } else if (args[0].equalsIgnoreCase("settings")) {
            if (sender.hasPermission("smartbookshelf.settings")) {
                if (args.length == 3) {
                    if (isNumber(args[2])) {
                        double val = Double.parseDouble(args[2]);
                        if (args[1].equalsIgnoreCase("set-hologram-distance")) {
                            config.set("hologram.hologram-distance-block", val);
                            saveConfig("Hologram distance set to " + val + ".", "Failed to save config.", sender);
                        } else if (args[1].equalsIgnoreCase("set-hologram-height")) {
                            config.set("hologram.hologram-height-block", val);
                            saveConfig("Hologram height set to " + val + ".", "Failed to save config.", sender);
                        } else if (args[1].equalsIgnoreCase("player-max-distance")) {
                            config.set("player-max-distance", val);
                            saveConfig("Player max distance set to " + val + ".", "Failed to save config.", sender);
                        }
                    } else {
                        String format = args[2].toLowerCase();
                        if(args[1].equalsIgnoreCase("enchant-level-format")){
                            config.set("enchant-level-format", format);
                            saveConfig("Level format to '" + format + "'.", "Failed to save config.", sender);
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("bookshelf")) {
            if (args.length == 1) {
                if (sender.hasPermission("smartbookshelf.reload")) {
                    completions.add("reload");
                }
                if (sender.hasPermission("smartbookshelf.setmode")) {
                    completions.add("setmode");
                }
                if (sender.hasPermission("smartbookshelf.settings")) {
                    completions.add("settings");
                }
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("setmode") && sender.hasPermission("smartbookshelf.setmode")) {
                    completions.add("actionbar");
                    completions.add("chat");
                    completions.add("hologram");
                }
                if (args[0].equalsIgnoreCase("settings") && sender.hasPermission("smartbookshelf.settings")) {
                    completions.add("set-hologram-distance");
                    completions.add("set-hologram-height");
                    completions.add("enchant-level-format");
                    completions.add("player-max-distance");
                }
            } else if (args.length == 3) {
                if (args[0].equalsIgnoreCase("settings") && sender.hasPermission("smartbookshelf.settings")) {
                    if (args[1].equalsIgnoreCase("set-hologram-distance") || args[1].equalsIgnoreCase("set-hologram-height")) {
                        for (double i = 0.5; i <= 1.5; i += 0.1) {
                            completions.add(String.format(Locale.US, "%.1f", i));
                        }
                    } else if (args[1].equalsIgnoreCase("player-max-distance")) {
                        for (double i = 0.5; i <= 8; i += 0.5) {
                            completions.add(String.format(Locale.US, "%.1f", i));
                        }
                    } else if (args[1].equalsIgnoreCase("enchant-level-format")) {
                        completions.add("number");
                        completions.add("roman");
                    }
                }
            }
        }
        return completions;
    }

    public boolean isNumber(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void saveConfig(String mex, String failed, CommandSender sender) {
        try {
            config.save(fileConfig);
            reloadConfig();
            if (mex != null) {
                if (sender instanceof Player) {
                    sender.sendMessage("§a[SmartBookshelfViewer] " + mex);
                } else if (sender instanceof ConsoleCommandSender) {
                    plugin.getLogger().info(mex);
                }
            }
        } catch (IOException e) {
            e.fillInStackTrace();
            if (failed != null) {
                if (sender instanceof Player) {
                    sender.sendMessage("§c[SmartBookshelfViewer] " + failed);
                } else if (sender instanceof ConsoleCommandSender) {
                    plugin.getLogger().warning(failed);
                }
            } else {
                plugin.getLogger().warning("Error saving file!");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Rimuove ologramma, pulisce actionbar e mappa dei puntamenti all'uscita
        clearOutput(event.getPlayer());
    }

    void initializeConfig() {
        fileConfig = new File(plugin.getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(fileConfig);
        checkAndFixConfig();
    }

    private void checkAndFixConfig() {
        InputStream defaultConfigStream = plugin.getResource("config.yml");
        if (defaultConfigStream == null) return;
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)
        );
        boolean updated = false;
        for (String path : defaultConfig.getKeys(true)) {
            if (defaultConfig.isConfigurationSection(path)) {
                continue;
            }
            if (!config.contains(path, true)) {
                config.set(path, defaultConfig.get(path));
                plugin.getLogger().info("Missing key detected and added: " + path);
                updated = true;
            }
        }
        if (updated) {
            try {
                config.save(fileConfig);
                plugin.getLogger().info("Successfully updated config.yml file with missing keys!");
            } catch (IOException e) {
                plugin.getLogger().warning("Unable to save updated config.yml file: " + e.getMessage());
            }
        }
    }

    void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(fileConfig);
        stopBookshelfScanner();
        startBookshelfScanner();
    }

    private void clearActionBarIfActive(Player player) {
        if (activeActionBarPlayers.remove(player.getUniqueId())) {
            clearActionBar(player);
        }
    }

    void startBookshelfScanner() {
        bookshelfTask = new BukkitRunnable() {
            @Override
            public void run() {
                String outputType = config.getString("output_type", "hologram").toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.hasPermission("smartbookshelf.use")) continue;
                    double maxDistance = getDouble("player-max-distance",4);
                    RayTraceResult rayResult = player.rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER);
                    if (rayResult == null || rayResult.getHitBlock() == null) {
                        clearOutput(player);
                        continue;
                    }
                    Block target = rayResult.getHitBlock();
                    if (!(target.getState() instanceof ChiseledBookshelf bookshelf)) {
                        clearOutput(player);
                        continue;
                    }
                    BlockFace hitFace = rayResult.getHitBlockFace();
                    Directional directional = (Directional) target.getBlockData();
                    if (hitFace != directional.getFacing()) {
                        clearOutput(player);
                        continue;
                    }
                    int zone = getTargetedZone(target, rayResult);
                    if (zone == -1) {
                        clearOutput(player);
                        continue;
                    }
                    ChiseledBookshelfInventory inventory = bookshelf.getInventory();
                    ItemStack item = inventory.getItem(zone);
                    if (item == null || item.getType() == Material.AIR) {
                        clearOutput(player);
                        continue;
                    }
                    String itemName = switch (item.getType()) {
                        case KNOWLEDGE_BOOK -> string("books-name.knowledge-book");
                        case WRITABLE_BOOK -> string("books-name.writable-book");
                        case ENCHANTED_BOOK -> string("books-name.enchanted-book");
                        case WRITTEN_BOOK -> string("books-name.written-book");
                        default -> string("books-name.book");
                    };
                    if (item.getItemMeta() instanceof BookMeta bookMeta && bookMeta.hasTitle()) {
                        if (itemName.contains("%customName%")) {
                            itemName = itemName.replace("%customName%", Objects.requireNonNull(bookMeta.getTitle()));
                        }
                    } else if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta enchMeta) {
                        Map<Enchantment, Integer> enchants = enchMeta.getStoredEnchants();
                        if (!enchants.isEmpty()) {
                            String text = string("displayed-text");
                            StringBuilder enchString = new StringBuilder(text + "\n");
                            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                                String enchName = string("enchants-name." + entry.getKey().getKey().getKey());
                                if (enchName.isEmpty()) {
                                    enchName = formatEnchantmentName(entry.getKey().getKey().getKey());
                                }
                                String level = toRoman(entry.getValue());
                                String format = string("enchantments").replace("%enchant%", enchName + " " + level);
                                String enchComp = format + "\n";
                                enchString.append(enchComp);
                            }
                            enchString.setLength(enchString.length() - 1);
                            itemName = enchString.toString();

                        } else {
                            itemName = string("books-name.enchanted-book") + " [Empty]";
                        }
                    } else if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                        itemName = item.getItemMeta().getDisplayName();
                    }
                    String currentTargetID = target.getWorld().getName() + "_" + target.getX() + "_" + target.getY() + "_" + target.getZ() + "_slot_" + zone;
                    switch (outputType) {
                        case "actionbar":
                            removeHologram(player);
                            sendActionBar(player, itemName.replace("\n", " §8| "));
                            activeActionBarPlayers.add(player.getUniqueId());
                            break;
                        case "chat":
                            removeHologram(player);
                            clearActionBarIfActive(player);
                            if (!currentTargetID.equals(lastTargetMap.get(player.getUniqueId()))) {
                                player.sendMessage(itemName.replace("\n", " §8| "));
                                lastTargetMap.put(player.getUniqueId(), currentTargetID);
                            }
                            break;
                        case "hologram":
                        default:
                            clearActionBarIfActive(player);
                            double height = getDouble("hologram.hologram-height-block", 0.5);
                            Location holoLoc = target.getLocation().add(0.5, height, 0.5);
                            Vector frontDirection = hitFace.getDirection();
                            double distance = getDouble("hologram.hologram-distance-block", 0.9);
                            holoLoc.add(frontDirection.multiply(distance));
                            showHologram(player, holoLoc, itemName);
                            break;
                    }
                    if (!outputType.equals("chat")) {
                        lastTargetMap.put(player.getUniqueId(), currentTargetID);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void clearOutput(Player player) {
        removeHologram(player);
        clearActionBarIfActive(player);
        lastTargetMap.remove(player.getUniqueId());
    }

    private int getTargetedZone(Block block, RayTraceResult rayResult) {
        BlockFace hitFace = rayResult.getHitBlockFace();
        if (hitFace == null || hitFace == BlockFace.UP || hitFace == BlockFace.DOWN) return -1;
        Vector hitPos = rayResult.getHitPosition();
        double relativeX = hitPos.getX() - block.getX();
        double relativeY = hitPos.getY() - block.getY();
        double relativeZ = hitPos.getZ() - block.getZ();
        boolean isTopRow = relativeY > 0.5;
        int column = -1;
        switch (hitFace) {
            case NORTH:
                if (relativeX > 0.66) column = 0;
                else if (relativeX > 0.33) column = 1;
                else column = 2;
                break;
            case SOUTH:
                if (relativeX < 0.33) column = 0;
                else if (relativeX < 0.66) column = 1;
                else column = 2;
                break;
            case WEST:
                if (relativeZ < 0.33) column = 0;
                else if (relativeZ < 0.66) column = 1;
                else column = 2;
                break;
            case EAST:
                if (relativeZ > 0.66) column = 0;
                else if (relativeZ > 0.33) column = 1;
                else column = 2;
                break;
            case null:
                break;
            default:
                return -1;
        }
        if (column == -1) {
            return -1;
        }
        return (isTopRow ? 0 : 3) + column;
    }

    private void showHologram(Player player, Location loc, String text) {
        TextDisplay old = holograms.get(player.getUniqueId());
        if (old != null && old.isValid()) {
            old.setText(text);
            if (!old.getLocation().equals(loc)) {
                old.teleport(loc);
            }
            return;
        }
        TextDisplay display = Objects.requireNonNull(loc.getWorld()).spawn(loc, TextDisplay.class, entity -> {
            entity.setText(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setShadowed(true);
            entity.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        holograms.put(player.getUniqueId(), display);
    }

    private void removeHologram(Player player) {
        TextDisplay display = holograms.remove(player.getUniqueId());
        if (display != null) {
            display.remove();
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void clearActionBar(Player player) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    void stopBookshelfScanner() {
        if (bookshelfTask != null && !bookshelfTask.isCancelled()) {
            bookshelfTask.cancel();
            bookshelfTask = null;
            for (TextDisplay display : holograms.values()) {
                display.remove();
            }
            holograms.clear();
            lastTargetMap.clear();
            activeActionBarPlayers.clear();
        }
    }

    private String formatEnchantmentName(String rawName) {
        String[] words = rawName.split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            formatted.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        }
        return formatted.toString().trim();
    }

    private String toRoman(int number) {
        String format = string("enchant-level-format");
        if (format.equalsIgnoreCase("roman")) {
            return switch (number) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                case 6 -> "VI";
                case 7 -> "VII";
                case 8 -> "VIII";
                case 9 -> "IX";
                case 10 -> "X";
                default -> String.valueOf(number);
            };
        }
        return String.valueOf(number);
    }

    public String string(String path) {
        if (config.contains(path)) {
            return Objects.requireNonNull(config.getString(path)).replaceAll("&", "§");
        }
        plugin.getLogger().info("The string " + path + " was not found in the config.yml file.");
        return "";
    }

    public double getDouble(String value, double def) {
        if (config.contains(value)) {
            return config.getDouble(value);
        } else {
            plugin.getLogger().info("Error: The key '" + value + "' was not found in the config.yml file.");
            return def;
        }
    }

}