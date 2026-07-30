package it.moro.minimoro;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ChiseledBookshelfInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Events implements Listener, CommandExecutor {
    private static ChiseledBookshelfViewer plugin;
    private BukkitTask bookshelfTask;
    private File fileConfig;
    private YamlConfiguration config;
    private final Map<UUID, String> lastTargetMap = new HashMap<>();
    private final Map<UUID, TextDisplay> holograms = new HashMap<>();

    public Events(ChiseledBookshelfViewer plugin) {
        Events.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, Command command, @NonNull String label, String @NonNull [] args) {
        if (!command.getName().equalsIgnoreCase("bookshelf")) return false;
        if (args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("bookshelf.reload")) {
                reloadConfig();
                if (sender instanceof Player player) {
                    player.sendMessage("§aConfiguration reloaded!");
                } else if (sender instanceof ConsoleCommandSender) {
                    plugin.getLogger().info("§aConfiguration reloaded!");
                }
            }
        }
        return true;
    }

    void initializConfig() {
        fileConfig = new File(plugin.getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(fileConfig);
    }

    void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(fileConfig);
        stopBookshelfScanner();
        startBookshelfScanner();
    }

    void startBookshelfScanner() {
        bookshelfTask = new BukkitRunnable() {
            @Override
            public void run() {
                String outputType = plugin.getConfig().getString("output_type", "hologram").toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    RayTraceResult rayResult = player.rayTraceBlocks(6.0, FluidCollisionMode.NEVER);
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
                        case WRITTEN_BOOK -> string("books-name.written_book");
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
                                    enchName = entry.getKey().getKey().getKey();
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
                            break;
                        case "chat":
                            removeHologram(player);
                            clearActionBar(player);
                            if (!currentTargetID.equals(lastTargetMap.get(player.getUniqueId()))) {
                                player.sendMessage(itemName.replace("\n", " §8| "));
                                lastTargetMap.put(player.getUniqueId(), currentTargetID);
                            }
                            break;
                        case "hologram":
                        default:
                            clearActionBar(player);
                            double height = getDouble("hologram.hologram-height-block", 0.5);
                            Location holoLoc = target.getLocation().add(0.5, height, 0.5);
                            Vector frontDirection = hitFace.getDirection();
                            double distance = getDouble("hologram.holohram-distance-block", 0.9);
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
        clearActionBar(player);
        lastTargetMap.remove(player.getUniqueId());
    }

    private int getTargetedZone(Block block, RayTraceResult rayResult) {
        BlockFace hitFace = rayResult.getHitBlockFace();
        if (hitFace == BlockFace.UP || hitFace == BlockFace.DOWN) return -1;
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
        if (old != null) {
            old.setText(text);
            if (!old.getLocation().equals(loc)) {
                old.teleport(loc);
            }
            return;
        }
        TextDisplay display = Objects.requireNonNull(loc.getWorld()).spawn(loc, TextDisplay.class);
        display.setText(text);
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setShadowed(true);
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
            plugin.getLogger().info("Error: The key '" + value + "' was not found in the quests.yml file.");
            return def;
        }
    }

}