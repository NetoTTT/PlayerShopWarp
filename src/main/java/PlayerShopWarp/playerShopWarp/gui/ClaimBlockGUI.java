package PlayerShopWarp.playerShopWarp.gui;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import PlayerShopWarp.playerShopWarp.utils.MessageUtils; // Garanta que este utils está correto
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener; // Removido @EventHandler daqui, ele estará em ShopWarpGUI
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

import java.io.File;
import java.util.*;

public class ClaimBlockGUI implements Listener { // Mantenha Listener se for registrar separadamente,
                                                 // caso contrário, ShopWarpGUI pode delegar cliques

    private final PlayerShopWarp plugin;
    private final ShopWarpGUI mainShopGUI; // Para setCreationProcessState e getCurrentPage

    private double gpPurchaseCostPerBlock = -1;
    private double gpSellValuePerBlock = -1;

    private static class ClaimBlockOption {
        final String id;
        final String displayNameKey; // Chave para messages.yml
        final Material material;
        final int amount;
        double price; // Preço será calculado
        final boolean isBuyOption;

        ClaimBlockOption(String id, String displayNameKey, Material material, int amount, boolean isBuyOption) {
            this.id = id;
            this.displayNameKey = displayNameKey;
            this.material = material;
            this.amount = amount;
            this.isBuyOption = isBuyOption;
            // Preço será setado após carregar gpPurchaseCost/gpSellValue
        }
    }

    private final Map<UUID, Map<Integer, ClaimBlockOption>> playerGUISetup = new HashMap<>();

    public ClaimBlockGUI(PlayerShopWarp plugin, ShopWarpGUI mainShopGUI) {
        this.plugin = plugin;
        this.mainShopGUI = mainShopGUI;
        loadGriefPreventionPrices();
        // Se esta classe não tem seu próprio @EventHandler onInventoryClick,
        // ShopWarpGUI deve delegar os cliques para um método aqui.
        // Se tiver, precisa registrar:
        // plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // Os métodos onInventoryClick, handleCustomConfirmInput, handleBlockPurchase,
    // handleBlockSale
    // permanecem como na sua última versão, pois a lógica de executar os comandos
    // e o feedback visual de preço já estavam corretos para essa abordagem.
    // A principal mudança é como os preços são obtidos (do GP config) e como as
    // opções
    // pré-definidas são construídas se você as mantiver.

    // ... (Cole aqui os métodos: onInventoryClick, handleCustomConfirmInput,
    // handleBlockPurchase, handleBlockSale da sua última versão)
    // Lembre-se que handleBlockPurchase e handleBlockSale NÃO devem mais manipular
    // a economia diretamente.
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;
        int slot = event.getRawSlot();

        Map<Integer, ClaimBlockOption> currentGUISetup = playerGUISetup.get(playerUUID);
        if (currentGUISetup == null)
            return;

        int guiSize = event.getView().getTopInventory().getSize();
        int lastRowFirstSlot = (guiSize / 9 - 1) * 9;

        if (slot == lastRowFirstSlot + 4 && clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            String previousPageContext = mainShopGUI.getCreationProcess(playerUUID);
            if (previousPageContext != null && !previousPageContext.isEmpty() &&
                    (mainShopGUI.getCurrentPage(playerUUID).equals("create")
                            || mainShopGUI.getCurrentPage(playerUUID).equals("select_category"))) {
                mainShopGUI.openCreateWarpGUI(player);
            } else {
                mainShopGUI.openMainGUI(player);
            }
            return;
        }

        if (slot == lastRowFirstSlot + 1 && clickedItem.getType() == Material.ANVIL) {
            player.closeInventory();
            mainShopGUI.setCreationProcessState(playerUUID, "buy_claim_blocks_custom_amount");
            MessageUtils.sendMessage(player, "&eDigite a quantidade de blocos de claim que deseja COMPRAR:");
            return;
        }

        if (slot == lastRowFirstSlot + 7 && clickedItem.getType() == Material.HOPPER) {
            player.closeInventory();
            mainShopGUI.setCreationProcessState(playerUUID, "sell_claim_blocks_custom_amount");
            MessageUtils.sendMessage(player, "&eDigite a quantidade de blocos de claim que deseja VENDER:");
            return;
        }

        if (currentGUISetup.containsKey(slot)) {
            ClaimBlockOption selectedOption = currentGUISetup.get(slot);
            if (selectedOption.isBuyOption) {
                handleBlockPurchase(player, selectedOption.amount, selectedOption.price);
            } else {
                handleBlockSale(player, selectedOption.amount, selectedOption.price);
            }
        }
    }

    // Dentro da classe ClaimBlockGUI.java

    private void loadGriefPreventionPrices() {
        Plugin griefPreventionPlugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (griefPreventionPlugin != null && griefPreventionPlugin.isEnabled()) {
            File gpDataFolder = griefPreventionPlugin.getDataFolder(); // Pasta base do plugin: plugins/GriefPrevention/
            File gpConfigFile = null;

            // 1. Tenta o caminho mais novo: plugins/GriefPreventionData/config.yml
            File gpDataDirConfigFile = new File(plugin.getServer().getWorldContainer(),
                    "plugins/GriefPreventionData/config.yml");
            // Alternativamente, se GriefPreventionData está sempre relativo à pasta de
            // plugins:
            // File gpDataDirConfigFile = new File(gpDataFolder.getParentFile(),
            // "GriefPreventionData/config.yml");

            if (gpDataDirConfigFile.exists()) {
                gpConfigFile = gpDataDirConfigFile;
                plugin.getLogger().info("Encontrado config.yml do GriefPrevention em: " + gpConfigFile.getPath());
            } else {
                // 2. Tenta o caminho mais antigo: plugins/GriefPrevention/config.yml
                File oldGpConfigFile = new File(gpDataFolder, "config.yml");
                if (oldGpConfigFile.exists()) {
                    gpConfigFile = oldGpConfigFile;
                    plugin.getLogger().info(
                            "Encontrado config.yml do GriefPrevention em (caminho antigo): " + gpConfigFile.getPath());
                } else {
                    plugin.getLogger().severe(
                            "Arquivo config.yml do GriefPrevention não encontrado em locais comuns (plugins/GriefPreventionData/ ou plugins/GriefPrevention/).");
                    setFallbackPrices();
                    return;
                }
            }

            if (gpConfigFile != null && gpConfigFile.exists()) { // Redundante mas seguro
                FileConfiguration gpConfig = YamlConfiguration.loadConfiguration(gpConfigFile);

                String purchaseCostPath = "GriefPrevention.Economy.ClaimBlocksPurchaseCost";
                String sellValuePath = "GriefPrevention.Economy.ClaimBlocksSellValue";

                if (gpConfig.contains(purchaseCostPath)) {
                    this.gpPurchaseCostPerBlock = gpConfig.getDouble(purchaseCostPath);
                } else {
                    this.gpPurchaseCostPerBlock = 10.0;
                    plugin.getLogger().warning(
                            "Não foi possível encontrar '" + purchaseCostPath + "' na config do GriefPrevention ("
                                    + gpConfigFile.getName() + "). Usando valor padrão: "
                                    + this.gpPurchaseCostPerBlock);
                }

                if (gpConfig.contains(sellValuePath)) {
                    this.gpSellValuePerBlock = gpConfig.getDouble(sellValuePath);
                } else {
                    this.gpSellValuePerBlock = 5.0;
                    plugin.getLogger().warning(
                            "Não foi possível encontrar '" + sellValuePath + "' na config do GriefPrevention ("
                                    + gpConfigFile.getName() + "). Usando valor padrão: "
                                    + this.gpSellValuePerBlock);
                }

                plugin.getLogger().info("Preços de blocos de claim do GriefPrevention carregados (do arquivo): Compra="
                        + gpPurchaseCostPerBlock + ", Venda=" + gpSellValuePerBlock);

            } else { // Não deveria chegar aqui se a lógica acima estiver correta
                plugin.getLogger().severe("Falha inesperada ao localizar config.yml do GriefPrevention.");
                setFallbackPrices();
            }
        } else {
            plugin.getLogger().severe(
                    "GriefPrevention não encontrado ou não habilitado! A funcionalidade de compra/venda de claims pode não funcionar como esperado ou usará preços padrão.");
            setFallbackPrices();
        }
    }

    private void setFallbackPrices() {
        this.gpPurchaseCostPerBlock = 10.0;
        this.gpSellValuePerBlock = 5.0;
        plugin.getLogger().warning("Usando preços de fallback para blocos de claim: Compra=" + gpPurchaseCostPerBlock
                + ", Venda=" + gpSellValuePerBlock);
    }

    private List<ClaimBlockOption> getPredefinedOptions() {
        List<ClaimBlockOption> predefinedOptions = new ArrayList<>();
        if (gpPurchaseCostPerBlock > 0) {
            ClaimBlockOption buy100 = new ClaimBlockOption("buy_100", "messages.claimblock-predefined-buy-title",
                    Material.STONE, 100, true);
            buy100.price = 100 * gpPurchaseCostPerBlock;
            predefinedOptions.add(buy100);

            ClaimBlockOption buy500 = new ClaimBlockOption("buy_500", "messages.claimblock-predefined-buy-title",
                    Material.IRON_BLOCK, 500, true);
            buy500.price = 500 * gpPurchaseCostPerBlock;
            predefinedOptions.add(buy500);
        }
        if (gpSellValuePerBlock > 0) {
            ClaimBlockOption sell100 = new ClaimBlockOption("sell_100", "messages.claimblock-predefined-sell-title",
                    Material.REDSTONE_BLOCK, 100, false);
            sell100.price = 100 * gpSellValuePerBlock;
            predefinedOptions.add(sell100);
        }
        return predefinedOptions;
    }

    public void openGUI(Player player) {
        List<ClaimBlockOption> currentOptions = getPredefinedOptions();
        int itemsInMenu = currentOptions.size() + 2 + 1; // +2 para custom, +1 para voltar
        int rows = Math.max(3, (int) Math.ceil(itemsInMenu / 7.0)); // Ajustar para ter espaço entre os itens
        rows = Math.min(6, rows);

        String guiTitle = MessageUtils.getMessage("messages.claimblock-gui-title");
        Inventory inv = Bukkit.createInventory(null, rows * 9, guiTitle);
        UUID playerUUID = player.getUniqueId();
        Map<Integer, ClaimBlockOption> activeGuiSlots = new HashMap<>();
        Economy econ = plugin.getEconomy();

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < inv.getSize(); i++)
            inv.setItem(i, filler);

        int optionSlot = 10; // Começa no slot 10 (segunda linha, segundo item)
        for (ClaimBlockOption option : currentOptions) {
            if (optionSlot > 16 && optionSlot < 19)
                optionSlot = 19; // Pula para próxima linha
            if (optionSlot > 25 && optionSlot < 28)
                optionSlot = 28; // Pula para próxima linha
            if (optionSlot >= (rows - 1) * 9 - 1)
                break; // Não preenche a última linha, exceto botões fixos

            ItemStack item = new ItemStack(option.material != null ? option.material : Material.STONE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(MessageUtils.getMessage(option.displayNameKey,
                        new String[][] { { "{amount}", String.valueOf(option.amount) } }));

                List<String> lore = new ArrayList<>();
                lore.add(MessageUtils.getMessage("messages.claimblock-lore-amount",
                        new String[][] { { "{amount}", String.valueOf(option.amount) } }));

                String priceColor = "&a"; // Cor padrão para "receberá"
                if (option.isBuyOption) {
                    priceColor = "&c"; // Cor padrão para "custo"
                    if (econ != null && econ.has(player, option.price)) {
                        priceColor = "&a"; // Verde se tiver dinheiro para comprar
                    }
                }
                String priceLoreKey = option.isBuyOption ? "messages.claimblock-lore-cost"
                        : "messages.claimblock-lore-receive";
                lore.add(MessageUtils.getMessage(priceLoreKey, new String[][] {
                        { "{price_color}", priceColor },
                        { "{price}", String.format("%,.2f", option.price) }
                }));
                lore.add(""); // Linha vazia
                lore.add(MessageUtils.getMessage(option.isBuyOption ? "messages.claimblock-lore-click-buy"
                        : "messages.claimblock-lore-click-sell"));

                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(optionSlot, item);
            activeGuiSlots.put(optionSlot, option);
            optionSlot += 2; // Espaço entre os itens
        }

        int lastRowFirstSlot = (rows - 1) * 9;

        // Botão Comprar Customizado
        ItemStack customBuyItem = new ItemStack(Material.ANVIL);
        ItemMeta customBuyMeta = customBuyItem.getItemMeta();
        if (customBuyMeta != null) {
            customBuyMeta.setDisplayName(MessageUtils.getMessage("messages.claimblock-custom-buy-title"));
            customBuyMeta.setLore(Collections.singletonList(
                    MessageUtils.getMessage("messages.claimblock-price-per-block",
                            new String[][] { { "{price}", String.format("%,.2f", gpPurchaseCostPerBlock) } })));
            customBuyItem.setItemMeta(customBuyMeta);
        }
        inv.setItem(lastRowFirstSlot + 1, customBuyItem); // Ex: Slot 37 (5ª linha, 2º item)

        // Botão Voltar
        ItemStack backButton = new ItemStack(Material.BARRIER); // Ou Material.ARROW
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(MessageUtils.getMessage("messages.claimblock-back-button"));
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(lastRowFirstSlot + 4, backButton); // Ex: Slot 40 (5ª linha, item do meio)

        // Botão Vender Customizado
        ItemStack customSellItem = new ItemStack(Material.HOPPER);
        ItemMeta customSellMeta = customSellItem.getItemMeta();
        if (customSellMeta != null) {
            customSellMeta.setDisplayName(MessageUtils.getMessage("messages.claimblock-custom-sell-title"));
            customSellMeta.setLore(Collections.singletonList(
                    MessageUtils.getMessage("messages.claimblock-value-per-block",
                            new String[][] { { "{price}", String.format("%,.2f", gpSellValuePerBlock) } })));
            customSellItem.setItemMeta(customSellMeta);
        }
        inv.setItem(lastRowFirstSlot + 7, customSellItem); // Ex: Slot 43 (5ª linha, 7º item)

        playerGUISetup.put(playerUUID, activeGuiSlots);
        player.openInventory(inv);
        mainShopGUI.setCurrentPageName(playerUUID, "claim_blocks_gui"); // Informa a ShopWarpGUI qual é a página atual
    }

    public void handleCustomAmountInput(Player player, String message, boolean isBuying) {
        UUID playerUUID = player.getUniqueId();
        try {
            int amount = Integer.parseInt(message);
            if (amount <= 0) {
                MessageUtils.sendMessage(player, "messages.claimblock-invalid-amount");
                mainShopGUI.clearCreationProcess(playerUUID);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            double pricePerBlock = isBuying ? gpPurchaseCostPerBlock : gpSellValuePerBlock;
            if (pricePerBlock < 0) {
                MessageUtils.sendMessage(player, "messages.claimblock-prices-not-loaded");
                mainShopGUI.clearCreationProcess(playerUUID);
                return;
            }

            double totalPrice = Math.round(amount * pricePerBlock * 100.0) / 100.0; // Arredonda para 2 casas decimais

            String confirmMessageKey = isBuying ? "messages.claimblock-confirm-buy"
                    : "messages.claimblock-confirm-sell";
            String priceColor = "&a"; // Cor padrão para "receberá"

            if (isBuying) {
                Economy econ = plugin.getEconomy();
                priceColor = (econ != null && econ.has(player, totalPrice)) ? "&a" : "&c";
            }

            MessageUtils.sendMessage(player, confirmMessageKey, new String[][] {
                    { "{amount}", String.valueOf(amount) },
                    { "{price_color}", priceColor },
                    { "{total_price}", String.format("%,.2f", totalPrice) }
            });

            if (isBuying) {
                Economy econ = plugin.getEconomy();
                if (econ != null && !econ.has(player, totalPrice)) {
                    MessageUtils.sendMessage(player, "messages.claimblock-not-enough-money-feedback");
                }
            }
            // Usa setCreationProcessState da mainShopGUI
            mainShopGUI.setCreationProcessState(playerUUID,
                    (isBuying ? "buy" : "sell") + "_claim_blocks_custom_confirm:" + amount + ":" + totalPrice);

        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(player, "messages.claimblock-not-a-number",
                    new String[][] { { "{input}", message } });
            mainShopGUI.clearCreationProcess(playerUUID);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
    }

    // Este método será chamado pela ShopWarpGUI quando um clique ocorrer na
    // ClaimBlockGUI
    public void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUUID = player.getUniqueId();
        ItemStack clickedItem = event.getCurrentItem();

        // Verificação básica se o item ou setup é nulo
        if (clickedItem == null || clickedItem.getType() == Material.AIR)
            return;
        Map<Integer, ClaimBlockOption> currentGUISetup = playerGUISetup.get(playerUUID);
        if (currentGUISetup == null)
            return;

        int slot = event.getRawSlot();
        InventoryView view = event.getView(); // Usar para pegar o tamanho correto da GUI atual
        int guiSize = view.getTopInventory().getSize();
        int lastRowFirstSlot = (guiSize / 9 - 1) * 9;

        // Botão Voltar
        if (slot == lastRowFirstSlot + 4 && clickedItem.getType() == Material.BARRIER) {
            player.closeInventory(); // Fecha a GUI atual
            // Decide para onde voltar. Se estava no meio de uma criação de warp, volta para
            // lá.
            String previousShopWarpProcess = mainShopGUI.getCreationProcess(playerUUID);
            if (previousShopWarpProcess != null &&
                    (mainShopGUI.getCurrentPage(playerUUID).equals("create")
                            || mainShopGUI.getCurrentPage(playerUUID).equals("select_category"))) {
                mainShopGUI.openCreateWarpGUI(player);
            } else {
                mainShopGUI.openMainGUI(player); // Caso contrário, volta para a GUI principal de lojas
            }
            return; // Importante retornar após tratar o clique
        }

        // Botão Comprar Customizado
        if (slot == lastRowFirstSlot + 1 && clickedItem.getType() == Material.ANVIL) {
            player.closeInventory();
            mainShopGUI.setCreationProcessState(playerUUID, "buy_claim_blocks_custom_amount"); // Usa mainShopGUI para
                                                                                               // gerenciar o estado
            MessageUtils.sendMessage(player, "messages.claimblock-prompt-buy-amount");
            return;
        }

        // Botão Vender Customizado
        if (slot == lastRowFirstSlot + 7 && clickedItem.getType() == Material.HOPPER) {
            player.closeInventory();
            mainShopGUI.setCreationProcessState(playerUUID, "sell_claim_blocks_custom_amount"); // Usa mainShopGUI
            MessageUtils.sendMessage(player, "messages.claimblock-prompt-sell-amount");
            return;
        }

        // Clique em uma opção pré-definida
        if (currentGUISetup.containsKey(slot)) {
            ClaimBlockOption selectedOption = currentGUISetup.get(slot);
            if (selectedOption.isBuyOption) {
                handleBlockPurchase(player, selectedOption.amount, selectedOption.price);
            } else {
                handleBlockSale(player, selectedOption.amount, selectedOption.price);
            }
            // Não precisa fechar a GUI aqui, handleBlockPurchase/Sale reabrem se
            // necessário.
        }
    }

    public void handleCustomConfirmInput(Player player, String message, int amount, double totalPrice,
            boolean isBuying) {
        UUID playerUUID = player.getUniqueId();
        if (message.equalsIgnoreCase("sim") || message.equalsIgnoreCase("yes")) {
            if (isBuying) {
                handleBlockPurchase(player, amount, totalPrice);
            } else {
                handleBlockSale(player, amount, totalPrice);
            }
        } else {
            MessageUtils.sendMessage(player, "messages.claimblock-transaction-cancelled");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
        mainShopGUI.clearCreationProcess(playerUUID); // Limpa o estado após confirmação ou cancelamento
    }

    private void handleBlockPurchase(Player player, int amount, double priceIgnored /*
                                                                                     * Preço é informativo, GP lida com
                                                                                     * isso
                                                                                     */) {
        String commandFormat = plugin.getConfig().getString("claimblocks.buy_command", "buyclaimblocks {amount}");
        String commandToExecute = commandFormat.replace("{player}", player.getName()).replace("{amount}",
                String.valueOf(amount));

        MessageUtils.sendMessage(player, "messages.claimblock-command-sending-buy",
                new String[][] { { "{amount}", String.valueOf(amount) } });
        Bukkit.dispatchCommand(player,
                commandToExecute.startsWith("/") ? commandToExecute.substring(1) : commandToExecute);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        // Reabre a GUI após um pequeno delay para dar tempo do comando do GP processar
        // e o saldo atualizar
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && mainShopGUI.getCurrentPage(player.getUniqueId()).equals("claim_blocks_gui")) {
                openGUI(player); // Reabre para mostrar saldo atualizado e opções
            }
        }, 20L); // 1 segundo de delay
    }

    private void handleBlockSale(Player player, int amount, double priceIgnored) {
        String commandFormat = plugin.getConfig().getString("claimblocks.sell_command", "sellclaimblocks {amount}");
        String commandToExecute = commandFormat.replace("{player}", player.getName()).replace("{amount}",
                String.valueOf(amount));

        MessageUtils.sendMessage(player, "messages.claimblock-command-sending-sell",
                new String[][] { { "{amount}", String.valueOf(amount) } });
        Bukkit.dispatchCommand(player,
                commandToExecute.startsWith("/") ? commandToExecute.substring(1) : commandToExecute);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && mainShopGUI.getCurrentPage(player.getUniqueId()).equals("claim_blocks_gui")) {
                openGUI(player);
            }
        }, 20L);
    }
}