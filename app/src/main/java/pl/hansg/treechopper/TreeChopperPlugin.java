package pl.hansg.treechopper;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class TreeChopperPlugin extends JavaPlugin implements Listener {

    private final Random random = new Random();

    private final Set<UUID> toggledPlayers = new HashSet<>();

    private final ThreadLocal<Boolean> syntheticTreeBreak = ThreadLocal.withInitial(() -> false);
    private NamespacedKey syntheticBreakKey;

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDataFile();
        loadToggledPlayers();

        syntheticBreakKey = new NamespacedKey("custommechanics", "synthetic_treechopper_break");

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("treechopper") != null) {
            getCommand("treechopper").setExecutor(this);
            getCommand("treechopper").setTabCompleter(this);
        }

        getLogger().info("SimpleTreeChopper enabled.");
    }

    @Override
    public void onDisable() {
        saveToggledPlayers();
        getLogger().info("SimpleTreeChopper disabled.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if(syntheticTreeBreak.get()) {
            return;
        }


        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!shouldTreeChop(player, block, tool)) {
            return;
        }

        /*
         * Cancel vanilla handling for the original log.
         * This prevents duplicate drops and prevents other plugins with ignoreCancelled=true from processing it after this.
         */
        event.setDropItems(false);
        event.setExpToDrop(0);
        event.setCancelled(true);

        chopTree(player, block, tool);
    }

    private boolean isTreeChopperEnabled(Player player) {
        boolean defaultEnabled = getConfig().getBoolean("tree-chopper.default-enabled-for-players", true);
        boolean isToggled = toggledPlayers.contains(player.getUniqueId());

        if (defaultEnabled) {
            return !isToggled;
        }

        return isToggled;
    }

    private boolean callSyntheticBlockBreakEvent(Player player, Block block) {
        syntheticTreeBreak.set(true);

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(syntheticBreakKey, PersistentDataType.BYTE, (byte) 1);

        try {
            BlockBreakEvent fakeEvent = new BlockBreakEvent(block, player);

            fakeEvent.setDropItems(false);
            fakeEvent.setExpToDrop(0);

            getServer().getPluginManager().callEvent(fakeEvent);

            return !fakeEvent.isCancelled();
        } finally {
            pdc.remove(syntheticBreakKey);
            syntheticTreeBreak.set(false);
        }
    }

    private boolean shouldTreeChop(Player player, Block block, ItemStack tool) {
        if (!getConfig().getBoolean("tree-chopper.enabled", true)) {
            return false;
        }

        if (!player.hasPermission("treechopper.use")) {
            return false;
        }

        if (!isTreeChopperEnabled(player)) {
            return false;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }

        if (!isWorldEnabled(block.getWorld())) {
            return false;
        }

        if (!isLog(block.getType())) {
            return false;
        }

        if (getConfig().getBoolean("tree-chopper.require-sneaking", true) && !player.isSneaking()) {
            return false;
        }

        if (getConfig().getBoolean("tree-chopper.require-axe", true) && !isAxe(tool)) {
            return false;
        }

        return true;
    }

    private boolean isWorldEnabled(World world) {
        List<String> enabledWorlds = getConfig().getStringList("worlds.enabled");
        return enabledWorlds.isEmpty() || enabledWorlds.contains(world.getName());
    }

    private void chopTree(Player player, Block startBlock, ItemStack tool) {
        Set<Block> logs = findConnectedLogs(startBlock);
        Set<Block> choppedLogs = new LinkedHashSet<>();

        boolean damageTool = getConfig().getBoolean("tree-chopper.tool.damage-enabled", true);
        int damagePerLog = getConfig().getInt("tree-chopper.tool.damage-per-log", 1);

        for (Block log : logs) {
            if (!isLog(log.getType())) {
                continue;
            }

            if (tool == null || tool.getType().isAir()) {
                break;
            }

            if (!callSyntheticBlockBreakEvent(player, log)) {
                continue;
            }

            Collection<ItemStack> drops = log.getDrops(tool, player);

            log.setType(Material.AIR, false);
            choppedLogs.add(log);

            for (ItemStack drop : drops) {
                log.getWorld().dropItemNaturally(log.getLocation(), drop);
            }

            if (damageTool) {
                boolean broke = damageTool(player, tool, damagePerLog);

                if (broke) {
                    break;
                }
            }
        }

        if (getConfig().getBoolean("tree-chopper.leaves.enabled", true)) {
            Set<Block> leaves = findLeavesAroundLogs(choppedLogs);
            chopLeaves(player, leaves, tool);
        }
    }

    private void setupDataFile() {
        dataFile = new File(getDataFolder(), "data.yml");

        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException exception) {
                getLogger().severe("Could not create data.yml");
                exception.printStackTrace();
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadToggledPlayers() {
        toggledPlayers.clear();

        List<String> uuids = dataConfig.getStringList("toggled-players");

        for (String uuidString : uuids) {
            try {
                toggledPlayers.add(UUID.fromString(uuidString));
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Invalid UUID in data.yml: " + uuidString);
            }
        }
    }

    private void saveToggledPlayers() {
        List<String> uuids = new ArrayList<>();

        for (UUID uuid : toggledPlayers) {
            uuids.add(uuid.toString());
        }

        dataConfig.set("toggled-players", uuids);

        try {
            dataConfig.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save data.yml");
            exception.printStackTrace();
        }
    }

    private Set<Block> findConnectedLogs(Block startBlock) {
        Set<Block> found = new LinkedHashSet<>();
        Queue<Block> queue = new ArrayDeque<>();

        Material startType = startBlock.getType();
        boolean sameTypeOnly = getConfig().getBoolean("tree-chopper.same-log-type-only", true);
        int maxLogs = getConfig().getInt("tree-chopper.max-logs", 128);

        queue.add(startBlock);
        found.add(startBlock);

        while (!queue.isEmpty() && found.size() < maxLogs) {
            Block current = queue.poll();

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }

                        Block nearby = current.getRelative(x, y, z);
                        Material nearbyType = nearby.getType();

                        if (!isLog(nearbyType)) {
                            continue;
                        }

                        if (sameTypeOnly && nearbyType != startType) {
                            continue;
                        }

                        if (found.contains(nearby)) {
                            continue;
                        }

                        found.add(nearby);
                        queue.add(nearby);

                        if (found.size() >= maxLogs) {
                            return found;
                        }
                    }
                }
            }
        }

        return found;
    }

    private Set<Block> findLeavesAroundLogs(Set<Block> logs) {
        Set<Block> leaves = new HashSet<>();

        int radius = getConfig().getInt("tree-chopper.leaves.radius-around-logs", 3);
        int maxLeaves = getConfig().getInt("tree-chopper.leaves.max-leaves", 128);

        for (Block log : logs) {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (leaves.size() >= maxLeaves) {
                            return leaves;
                        }

                        Block nearby = log.getRelative(x, y, z);

                        if (isLeaves(nearby.getType())) {
                            leaves.add(nearby);
                        }
                    }
                }
            }
        }

        return leaves;
    }

    private void chopLeaves(Player player, Set<Block> leaves, ItemStack tool) {
        boolean dropItems = getConfig().getBoolean("tree-chopper.leaves.drop-items", true);

        for (Block leaf : leaves) {
            if (!isLeaves(leaf.getType())) {
                continue;
            }

            Collection<ItemStack> drops = dropItems
                    ? leaf.getDrops(tool, player)
                    : List.of();

            leaf.setType(Material.AIR, false);

            for (ItemStack drop : drops) {
                leaf.getWorld().dropItemNaturally(leaf.getLocation(), drop);
            }
        }
    }

    private boolean damageTool(Player player, ItemStack tool, int damage) {
        if (damage <= 0) {
            return false;
        }

        if (tool == null || tool.getType().isAir()) {
            return true;
        }

        if (!(tool.getItemMeta() instanceof Damageable meta)) {
            return false;
        }

        int finalDamage = calculateFinalToolDamage(tool, damage);

        if (finalDamage <= 0) {
            return false;
        }

        int newDamage = meta.getDamage() + finalDamage;
        int maxDurability = tool.getType().getMaxDurability();

        if (newDamage >= maxDurability) {
            player.getInventory().setItemInMainHand(null);

            if (getConfig().getBoolean("tree-chopper.tool.play-break-sound", true)) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }

            return true;
        }

        meta.setDamage(newDamage);
        tool.setItemMeta(meta);
        return false;
    }

    private int calculateFinalToolDamage(ItemStack tool, int damage) {
        boolean respectUnbreaking = getConfig().getBoolean("tree-chopper.tool.respect-unbreaking", true);

        if (!respectUnbreaking) {
            return damage;
        }

        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);

        if (unbreakingLevel <= 0) {
            return damage;
        }

        int finalDamage = 0;

        for (int i = 0; i < damage; i++) {
            if (!shouldPreventDamageByUnbreaking(unbreakingLevel)) {
                finalDamage++;
            }
        }

        return finalDamage;
    }

    private boolean shouldPreventDamageByUnbreaking(int unbreakingLevel) {
        /*
         * Simple vanilla-like approximation:
         * Unbreaking I prevents some damage.
         * Higher levels prevent more damage.
         */
        return random.nextInt(unbreakingLevel + 1) > 0;
    }

    private boolean isLog(Material material) {
        String name = material.name();

        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE");
    }

    private boolean isLeaves(Material material) {
        String name = material.name();

        return name.endsWith("_LEAVES")
                || material == Material.NETHER_WART_BLOCK
                || material == Material.WARPED_WART_BLOCK;
    }

    private boolean isAxe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        return item.getType().name().endsWith("_AXE");
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text("SimpleTreeChopper status:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Global enabled: ", NamedTextColor.GRAY).append(Component.text(getConfig().getBoolean("tree-chopper.enabled", true), NamedTextColor.YELLOW)));

        if (sender instanceof Player player) {
            sender.sendMessage(Component.text("Your TreeChopper: ", NamedTextColor.GRAY) 
                    .append((isTreeChopperEnabled(player) ? Component.text("enabled", NamedTextColor.GREEN) : Component.text("disabled", NamedTextColor.RED))));
        }

        sender.sendMessage(Component.text("Require sneaking: ", NamedTextColor.GRAY).append(Component.text(getConfig().getBoolean("tree-chopper.require-sneaking", true), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Require axe: ", NamedTextColor.GRAY).append(Component.text(getConfig().getBoolean("tree-chopper.require-axe", true), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Max logs: ", NamedTextColor.GRAY).append(Component.text(getConfig().getInt("tree-chopper.max-logs", 128), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Leaves enabled: ", NamedTextColor.GRAY).append(Component.text(getConfig().getBoolean("tree-chopper.leaves.enabled", true), NamedTextColor.YELLOW)));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text( "SimpleTreeChopper commands:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/treechopper toggle", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/treechopper status", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/treechopper reload", NamedTextColor.GRAY));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("treechopper")) {
            return false;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return true;
            }

            if (!player.hasPermission("treechopper.toggle")) {
                player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }

            UUID uuid = player.getUniqueId();

            if (toggledPlayers.contains(uuid)) {
                toggledPlayers.remove(uuid);
            } else {
                toggledPlayers.add(uuid);
            }

            saveToggledPlayers();

            boolean enabled = isTreeChopperEnabled(player);

            player.sendMessage(Component.text("TreeChopper is now ", NamedTextColor.YELLOW)
                    .append((enabled ? Component.text("enabled", NamedTextColor.GREEN) : Component.text("disabled", NamedTextColor.RED)))
                    .append(Component.text(".", NamedTextColor.YELLOW)));

            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("treechopper.reload")) {
                sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }

            reloadConfig();
            sender.sendMessage(Component.text("SimpleTreeChopper config reloaded.", NamedTextColor.GREEN));
            return true;
        }

        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!command.getName().equalsIgnoreCase("treechopper")) {
            return suggestions;
        }

        if (args.length == 1) {
            suggestions.add("toggle");
            suggestions.add("status");

            if (sender.hasPermission("treechopper.reload")) {
                suggestions.add("reload");
            }

            return filterSuggestions(suggestions, args[0]);
        }

        return suggestions;
    }

    private List<String> filterSuggestions(List<String> suggestions, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();

        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                filtered.add(suggestion);
            }
        }

        return filtered;
    }
}