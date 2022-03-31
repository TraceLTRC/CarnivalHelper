package xyz.holocons.carnivalhelper;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class CarnivalHelper extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    private final NamespacedKey KEY = new NamespacedKey(this, "carnival");
    private final UUID SKULL_OWNER = new UUID(0, 0);

    @Override
    public void onEnable() {
        this.getCommand("carnival").setExecutor(this);
        this.getCommand("carnival").setTabCompleter(this);
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Command not allowed to be used on console!"));
            return false;
        }

        if (!player.hasPermission("carnivalhelper.use")) {
            player.sendMessage(Component.text("You do not have permission!"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Invalid usage!"));
            return false;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("There is not enough free space in your inventory!", NamedTextColor.RED));
            return true;
        }

        // Fix command - Fix broken/invalid yagoolds
        switch (args[0].toLowerCase()) {
            /*
            Fixes any gold nugget that has 3 lores (no matter the text) to a valid yagoold. Used for villager trading
             */
            case "fix" -> {
                if (!isValidCurrency(heldItem)) {
                    player.sendMessage(Component.text("The item you are holding is not a yagoold! Super yagoolds are not" +
                                    " fixable/upgradable.", NamedTextColor.RED)
                            .append(Component.newline())
                            .append(Component.text("Please contact staff through discord if you think this is wrong.", NamedTextColor.YELLOW))
                    );
                    return true;
                }
                ItemStack validCurrency = makeValidCurrency();
                validCurrency.setAmount(heldItem.getAmount());
                player.getInventory().setItemInMainHand(validCurrency);
                player.sendMessage("Yagoolds has been fixed!");
                this.getLogger().info("Replaced " + player.getName() + " " + validCurrency.getAmount() + " yagoold.");
                return true;
            }
            /*
            Executes scrates commands. One key is equal to one yagoold.
             */
            case "claim" -> {
                if (!isValidCurrency(heldItem)) {
                    player.sendMessage(Component.text("The item you are holding is not a yagoold! Super yagoolds are not" +
                                    " fixable/upgradable.", NamedTextColor.RED)
                            .append(Component.newline())
                            .append(Component.text("Please contact staff through discord if you think this is wrong.", NamedTextColor.YELLOW))
                    );
                    return true;
                }
                int amount = heldItem.getAmount() / 4;
                int remainder = heldItem.getAmount() % 4;

                if (amount < 1) {
                    player.sendMessage(Component.text("You don't have enough yagoold!", NamedTextColor.RED));
                    player.sendMessage(Component.text("4 Yagoold = 1 key", NamedTextColor.YELLOW));
                    return true;
                }

                this.getLogger().info("Removing " + amount + " yagoold from " + player.getName());
                heldItem.setAmount(remainder);
                player.getInventory().setItemInMainHand(heldItem);
                String strCommand = "scrates givekey carnivalCrate " + player.getName() + " " + amount + " -v";
                this.getLogger().info("Executing command [" + strCommand + "]");
                Bukkit.dispatchCommand(this.getServer().getConsoleSender(), strCommand);
                this.getLogger().info("Executed command!");
                return true;
            }
            /*
            Upgrades gold nuggets to iron nuggets.
             */
            case "upgrade" -> {
                if (!isValidCurrency(heldItem)) {
                    player.sendMessage(Component.text("The item you are holding is not a yagoold! Super yagoolds are not" +
                                    " fixable/upgradable.", NamedTextColor.RED)
                            .append(Component.newline())
                            .append(Component.text("Please contact staff through discord if you think this is wrong.", NamedTextColor.YELLOW))
                    );
                    return true;
                }
                ItemStack validSuperCurrency = makeValidSuperCurrency();

                int amount = heldItem.getAmount() / 4;
                int remainder = heldItem.getAmount() % 4;

                if (amount < 1) {
                    player.sendMessage(Component.text("You don't have enough yagoold!", NamedTextColor.RED));
                    return true;
                }

                validSuperCurrency.setAmount(amount);
                heldItem.setAmount(remainder);

                player.getInventory().setItemInMainHand(heldItem);
                var failedItems = player.getInventory().addItem(validSuperCurrency);
                if (!failedItems.isEmpty()) {
                    failedItems.forEach((slot, item) -> {
                        this.getLogger().warning("Item " + item.getType() + " of amount " + item.getAmount() + " was " +
                                "not able to be added to player " + player.getName() + "!");
                    });
                    player.sendMessage("There were some items that we were not able to add to your inventory." +
                            " Please contact staff for help.");
                }
                player.sendMessage("Yagoolds has been upgraded!");
                this.getLogger().info("Replaced " + player.getName() + " " + validSuperCurrency.getAmount() + " yagoold.");
                return true;
            }

            case "shop" -> {
                Merchant merchant = Bukkit.createMerchant(Component.text("Carnival Shop", NamedTextColor.GOLD, TextDecoration.BOLD));
                List<MerchantRecipe> recipes = merchantRecipes();
                merchant.setRecipes(recipes);
                player.openMerchant(merchant, true);

                return true;
            }

            default -> {
                player.sendMessage(
                        Component.text("Carnival Commands", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.newline())
                                .append(Component.text("/carnival fix", NamedTextColor.AQUA))
                                .append(Component.text("/carnival claim", NamedTextColor.AQUA))
                                .append(Component.text("/carnival upgrade", NamedTextColor.AQUA))
                );
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of("fix", "upgrade", "claim");
    }

    private ItemStack makeValidCurrency() {
        ItemStack itemStack = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta itemMeta = itemStack.getItemMeta();

        itemMeta.displayName(Component.text("Yagoold", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        itemMeta.lore(List.of(
                Component.text("Unlock gacha crate and custom"),
                Component.text("items at the carnival using"),
                Component.text("this item!")
        ));

        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    private ItemStack makeValidSuperCurrency() {
        ItemStack itemStack = new ItemStack(Material.IRON_NUGGET);
        ItemMeta itemMeta = itemStack.getItemMeta();

        itemMeta.addEnchant(Enchantment.LURE, 1, false);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        itemMeta.displayName(Component.text("Super Yagoold", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        itemMeta.lore(List.of(
                Component.text("Unlock gacha crate and custom"),
                Component.text("items at the carnival using"),
                Component.text("this item!")
        ));

        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    /*
    Checks if an itemStack is a valid currency. A valid currency is just a gold nugget with 3 lore lines. It
    does not check for the name or the content of the lore.
     */
    private boolean isValidCurrency(ItemStack itemStack) {
        var itemMeta = itemStack.getItemMeta();
        return itemStack.getType() == Material.GOLD_NUGGET && itemMeta.hasLore() && itemMeta.lore().size() == 3;
    }

    private List<MerchantRecipe> merchantRecipes() {
        var validCurrency = makeValidCurrency();
        var validSuperCurrency = makeValidSuperCurrency();
        var trades = new MerchantRecipe[6];

        MerchantRecipe recipe;
        ItemStack itemStack;
        ItemMeta itemMeta;

        // 32 yagoolds = 1 enchanted golden apple
        recipe = new MerchantRecipe(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 0, 999, false);
        recipe.setIgnoreDiscounts(true);
        recipe.addIngredient(validCurrency.asQuantity(32));
        trades[0] = recipe;

        // 20 yagoolds = 1 netherite ingot
        recipe = new MerchantRecipe(new ItemStack(Material.NETHERITE_INGOT), 0, 999, false);
        recipe.setIgnoreDiscounts(true);
        recipe.addIngredient(validCurrency.asQuantity(20));
        trades[1] = recipe;

        // 64 yagoolds = 1 dragon egg
        recipe = new MerchantRecipe(new ItemStack(Material.DRAGON_EGG), 0, 999, false);
        recipe.setIgnoreDiscounts(true);
        recipe.setIngredients(List.of(validCurrency.asQuantity(64)));
        trades[2] = recipe;

        // 20 yagoolds = 1 key for Special HoloItem crate
        itemStack = new ItemStack(Material.PAPER);
        itemMeta = itemStack.getItemMeta();

        itemMeta.displayName(Component.text("1x HoloItems crate key", NamedTextColor.GREEN));
        itemMeta.lore(List.of(
                Component.text("Right click to get 1", NamedTextColor.YELLOW),
                Component.text("Special HoloItems crate key", NamedTextColor.YELLOW))
        );
        itemMeta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "holokey");

        itemStack.setItemMeta(itemMeta);

        recipe = new MerchantRecipe(itemStack, 0, 999, false);
        recipe.setIgnoreDiscounts(true);
        recipe.addIngredient(validCurrency.asQuantity(20));
        trades[3] = recipe;

        // 64 yagoolds = 1 head crate key
        itemStack = new ItemStack(Material.PAPER);
        itemMeta = itemStack.getItemMeta();

        itemMeta.displayName(Component.text("1x HoloHead crate key", NamedTextColor.GREEN));
        itemMeta.lore(List.of(
                Component.text("Right click to get 1", NamedTextColor.YELLOW),
                Component.text("Hololive head crate key", NamedTextColor.YELLOW))
        );
        itemMeta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "headkey");

        itemStack.setItemMeta(itemMeta);

        recipe = new MerchantRecipe(itemStack, 0, 999, false);
        recipe.setIgnoreDiscounts(true);
        recipe.addIngredient(validCurrency.asQuantity(64));
        trades[4] = recipe;

        // 64 SUper Yagoold = 1 Mano Aloe head
        itemStack = playerHeadFromBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjZkYWVkZmFkNjM1MmQ2ZWExOGU1YmVhZDBkZWYzMTVkODQwODFmZDljMDZiMWJhMmE2YmI5MjllYmYyOTExNSJ9fX0=");
        itemMeta = itemStack.getItemMeta();

        itemMeta.displayName(
                Component.text("Mano Aloe").color(TextColor.color(0xF898CA))
                        .append(Component.text("'s head", NamedTextColor.YELLOW))
        );

        itemStack.setItemMeta(itemMeta);

        recipe = new MerchantRecipe(itemStack, 0, 999, false);
        recipe.setIgnoreDiscounts(true);
        recipe.addIngredient(validSuperCurrency.asQuantity(64));
        trades[5] = recipe;

        return Arrays.asList(trades);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event){
        var player = event.getPlayer();
        var item = player.getInventory().getItemInMainHand();

        if (!player.hasPermission("carnivalhelper.use"))
            return;

        if (item.getType() != Material.PAPER || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(KEY))
            return;

        if (item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING).equals("holokey")) {
            this.getLogger().info("Giving " + player.getName() + " 1 virtual Special HoloItems key and removing 1 from inventory");
            player.getInventory().setItemInMainHand(item.asQuantity(item.getAmount() - 1));
            String command = "scrates givekey holoItemsCrate " + player.getName() + " 1 -v";
            this.getLogger().info("Executing command [" + command +"]");
            this.getServer().dispatchCommand(this.getServer().getConsoleSender(), command);
        } else if (item.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING).equals("headkey")) {
            this.getLogger().info("Giving " + player.getName() + " 1 virtual HoloHead key and removing 1 from inventory");
            player.getInventory().setItemInMainHand(item.asQuantity(item.getAmount() - 1));
            String command = "scrates givekey headCrate " + player.getName() + " 1 -v";
            this.getLogger().info("Executing command [" + command +"]");
            this.getServer().dispatchCommand(this.getServer().getConsoleSender(), command);
        }
    }

    /**
     * Returns a player head with the base64 texture. Mostly used for GUI.
     * @param base64 A base 64 string that contains ONLY the texture
     * @return The ItemStack player head
     */
    private ItemStack playerHeadFromBase64(String base64) {
        final var item = new ItemStack(Material.PLAYER_HEAD);
        final var meta = (SkullMeta) item.getItemMeta();
        final var profile = Bukkit.createProfile(SKULL_OWNER);
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }
}
