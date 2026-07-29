package it.moro.minimoro;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ChiseledBookshelfInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Events implements Listener {
    private static ChiseledBookshelfViewer plugin;
    private BukkitTask bookshelfTask;

    // Per evitare lo spam in chat
    private final Map<UUID, String> lastTargetMap = new HashMap<>();

    // Mappa per i TextDisplay (gli ologrammi moderni multiriga)
    private final Map<UUID, org.bukkit.entity.TextDisplay> holograms = new HashMap<>();

    public Events(ChiseledBookshelfViewer plugin) {
        Events.plugin = plugin;
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

                    // 1. Definiamo il nome di base
                    String itemName = switch (item.getType()) {
                        case KNOWLEDGE_BOOK -> "§dLibro della Conoscenza";
                        case WRITABLE_BOOK -> "§fLibro e Penna";
                        case ENCHANTED_BOOK -> "§bLibro Incantato";
                        case WRITTEN_BOOK -> "§6Libro Scritto";
                        default -> "§fLibro";
                    };

                    // 2. Controlliamo se è un libro scritto con un titolo
                    if (item.getItemMeta() instanceof BookMeta bookMeta && bookMeta.hasTitle()) {
                        itemName = "§6" + bookMeta.getTitle();
                    }
                    // 3. Controlliamo se è un libro incantato
                    else if (item.getType() == Material.ENCHANTED_BOOK && item.getItemMeta() instanceof EnchantmentStorageMeta enchMeta) {
                        Map<Enchantment, Integer> enchants = enchMeta.getStoredEnchants();
                        if (!enchants.isEmpty()) {

                            StringBuilder enchString = new StringBuilder("§bLibro Incantato\n");

                            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                                String enchName = formatEnchantmentName(entry.getKey().getKey().getKey());
                                String level = toRoman(entry.getValue());

                                // Aggiunge l'incantesimo e va a capo (\n)
                                enchString.append("§7").append(enchName).append(" ").append(level).append("\n");
                            }

                            // Rimuove l'ultimo \n extra
                            enchString.setLength(enchString.length() - 1);
                            itemName = enchString.toString();

                        } else {
                            itemName = "§bLibro Incantato (Vuoto)";
                        }
                    }
                    // 4. Se è rinominato con l'incudine
                    else if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                        itemName = item.getItemMeta().getDisplayName();
                    }

                    // --- GESTIONE DELL'OUTPUT ---

                    String currentTargetID = target.getWorld().getName() + "_" + target.getX() + "_" + target.getY() + "_" + target.getZ() + "_slot_" + zone;

                    switch (outputType) {
                        case "actionbar":
                            removeHologram(player);
                            // L'actionbar non supporta il \n, quindi lo rimpiazziamo con un divisore visivo " | "
                            sendActionBar(player, itemName.replace("\n", " §8| "));
                            break;

                        case "chat":
                            removeHologram(player);
                            clearActionBar(player);

                            if (!currentTargetID.equals(lastTargetMap.get(player.getUniqueId()))) {
                                player.sendMessage(itemName);
                                lastTargetMap.put(player.getUniqueId(), currentTargetID);
                            }
                            break;

                        case "hologram":
                        default:
                            clearActionBar(player);

                            Location holoLoc = target.getLocation().add(0.5, 0.1, 0.5);
                            Vector frontDirection = hitFace.getDirection();
                            holoLoc.add(frontDirection.multiply(0.9)); //<--------------------------------------------Parametro modificabile per la distanza del hologram dal blocco

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
        int column = 0;

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
            default:
                return -1;
        }

        return (isTopRow ? 0 : 3) + column;
    }

    private void showHologram(Player player, Location loc, String text) {
        org.bukkit.entity.TextDisplay old = holograms.get(player.getUniqueId());

        if (old != null) {
            old.setText(text);
            if (!old.getLocation().equals(loc)) {
                old.teleport(loc);
            }
            return;
        }

        org.bukkit.entity.TextDisplay display = loc.getWorld().spawn(loc, org.bukkit.entity.TextDisplay.class);

        display.setText(text);
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        display.setShadowed(true);

        holograms.put(player.getUniqueId(), display);
    }

    private void removeHologram(Player player) {
        org.bukkit.entity.TextDisplay display = holograms.remove(player.getUniqueId());
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

            for (org.bukkit.entity.TextDisplay display : holograms.values()) {
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
        return switch (number) {
            case 1 -> "I";  case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V";  case 6 -> "VI";
            case 7 -> "VII";case 8 -> "VIII";case 9 -> "IX";
            case 10 -> "X"; default -> String.valueOf(number);
        };
    }
}