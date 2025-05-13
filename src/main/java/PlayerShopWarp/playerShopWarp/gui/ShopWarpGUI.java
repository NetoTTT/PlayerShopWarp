package PlayerShopWarp.playerShopWarp.gui;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import PlayerShopWarp.playerShopWarp.models.ShopWarp;
import PlayerShopWarp.playerShopWarp.utils.MessageUtils;
import net.milkbowl.vault.economy.Economy; // Import Vault Economy API
import net.milkbowl.vault.economy.EconomyResponse; // Import Vault EconomyResponse
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Location; // <<<--- ADICIONADO IMPORT

import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType; // Novo import
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public class ShopWarpGUI implements Listener {

    private final PlayerShopWarp plugin;
    private final Map<UUID, String> currentPage;
    private final Map<UUID, Map<Integer, String>> slotWarpMap;
    private final Map<UUID, Integer> playerPages;
    private final Map<UUID, String> categoryFilter;
    private final Map<UUID, String> creationProcess;
    private final Map<UUID, String> pendingWarpName;
    private ClaimBlockGUI claimBlockGUIInstance; // Para acessar os métodos da ClaimBlockGUI
    private final Map<UUID, String> pendingWarpDescription; // Mantido para fluxo normal
    private final int WARPS_PER_PAGE = 45;
    // Removido: private final Map<UUID, org.bukkit.Location> pendingSignLocations =
    // new HashMap<>();

    public ShopWarpGUI(PlayerShopWarp plugin) {
        this.plugin = plugin;
        this.currentPage = new HashMap<>();
        this.slotWarpMap = new HashMap<>();
        this.playerPages = new HashMap<>();
        this.categoryFilter = new HashMap<>();
        this.creationProcess = new HashMap<>();
        this.pendingWarpName = new HashMap<>();
        this.pendingWarpDescription = new HashMap<>();
        this.claimBlockGUIInstance = new ClaimBlockGUI(plugin, this); // Instancia aqui

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // --- openMainGUI, openCategoryGUI, openMyWarpsGUI, addNavigationButtons ---
    // --- createWarpItem, openWarpCategoryGUI, openWarpManagementGUI permanecem
    // iguais ---
    // --- Métodos auxiliares createScanButton, createHereButton,
    // createCommandButton permanecem iguais ---
    // --- Mas openCreateWarpGUI e handleCreateWarpGUIClick precisam de ajustes para
    // economia ---

    public void openMainGUI(Player player, int page) {
        String title = MessageUtils
                .colorize(plugin.getConfigManager().getConfig().getString("gui.title", "&8Lojas dos Jogadores"));
        int rows = 6; // Sempre 6 linhas para acomodar navegação e filtros

        Inventory inv = Bukkit.createInventory(null, rows * 9, title);
        UUID playerUUID = player.getUniqueId();

        // Preenche o inventário com as warps disponíveis
        List<ShopWarp> allWarps = plugin.getShopWarpManager().getAllWarps();

        // Aplica filtro de categoria se houver
        String currentCategory = categoryFilter.getOrDefault(playerUUID, "");
        if (!currentCategory.isEmpty() && plugin.getConfigManager().areCategoriesEnabled()) {
            allWarps = allWarps.stream()
                    .filter(warp -> warp.getCategory() != null && warp.getCategory().equalsIgnoreCase(currentCategory))
                    .collect(Collectors.toList());
        }

        // Paginação
        int totalPages = (int) Math.ceil((double) allWarps.size() / WARPS_PER_PAGE);
        if (totalPages == 0)
            totalPages = 1; // Garante pelo menos 1 página
        if (page >= totalPages)
            page = Math.max(0, totalPages - 1);
        if (page < 0)
            page = 0;

        playerPages.put(playerUUID, page);

        int startIndex = page * WARPS_PER_PAGE;
        int endIndex = Math.min(startIndex + WARPS_PER_PAGE, allWarps.size());

        Map<Integer, String> warpSlots = new HashMap<>();

        // Adiciona warps na página atual
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot >= WARPS_PER_PAGE)
                break; // Segurança extra
            ShopWarp warp = allWarps.get(i);
            ItemStack item = createWarpItem(warp);
            inv.setItem(slot, item);
            warpSlots.put(slot, warp.getId());
            slot++;
        }

        // Adiciona botões de navegação na última linha
        addNavigationButtons(inv, player, page, totalPages);

        // Adiciona botão para minhas lojas
        ItemStack myShopsItem = new ItemStack(Material.CHEST);
        ItemMeta myShopsMeta = myShopsItem.getItemMeta();
        myShopsMeta.setDisplayName(MessageUtils.colorize("&aMinhas Lojas"));
        myShopsMeta.setLore(Collections.singletonList(MessageUtils.colorize("&7Clique para gerenciar suas lojas")));
        myShopsItem.setItemMeta(myShopsMeta);
        inv.setItem(49, myShopsItem); // Slot do meio da última linha

        // Adiciona botão de filtro por categoria se habilitado
        if (plugin.getConfigManager().areCategoriesEnabled()) {
            ItemStack categoryItem = new ItemStack(Material.HOPPER);
            ItemMeta categoryMeta = categoryItem.getItemMeta();
            categoryMeta.setDisplayName(MessageUtils.colorize("&eFiltrar por Categoria"));

            List<String> categoryLore = new ArrayList<>();
            categoryLore.add(MessageUtils.colorize("&7Categoria atual: " +
                    (currentCategory.isEmpty() ? "&fTodas" : "&f" + currentCategory)));
            categoryLore.add(MessageUtils.colorize("&7Clique para alterar"));

            categoryMeta.setLore(categoryLore);
            categoryItem.setItemMeta(categoryMeta);
            inv.setItem(47, categoryItem); // Slot à esquerda do meio
        }

        slotWarpMap.put(playerUUID, warpSlots);
        currentPage.put(playerUUID, "main");

        player.openInventory(inv);
    }

    // Removido: setSignBlock, isSignLocationMatching

    public String getCreationProcess(UUID playerId) {
        return creationProcess.get(playerId);
    }

    public void clearCreationProcess(UUID playerId) {
        creationProcess.remove(playerId);
        pendingWarpName.remove(playerId);
        pendingWarpDescription.remove(playerId);
    }

    public void openCategoryGUI(Player player) {
        List<String> categories = plugin.getConfigManager().getCategories();
        int numCategories = categories.size();
        int rows = Math.max(2, (int) Math.ceil((numCategories + 1.0) / 9.0) + 1); // Calcula linhas: 1 para
                                                                                  // header/footer, resto para itens
        rows = Math.min(6, rows); // Limita a 6 linhas

        String title = MessageUtils.colorize("&8Categorias");
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        UUID playerUUID = player.getUniqueId();

        // Adiciona botão para mostrar todas as categorias
        ItemStack allCategoriesButton = new ItemStack(Material.BARRIER); // Ou CHEST_MINECART
        ItemMeta allMeta = allCategoriesButton.getItemMeta();
        allMeta.setDisplayName(MessageUtils.colorize("&eMostrar Todas"));
        allMeta.setLore(Collections.singletonList(
                MessageUtils.colorize("&7Clique para remover o filtro de categoria")));
        allCategoriesButton.setItemMeta(allMeta);
        inventory.setItem(4, allCategoriesButton); // Slot 4 (meio da primeira linha)

        // Adiciona as categorias
        int slot = 9; // Começa na segunda linha
        for (String category : categories) {
            if (slot >= (rows - 1) * 9)
                break; // Não preenche a última linha

            ItemStack item = new ItemStack(Material.BOOK); // Ou outro item representativo
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(MessageUtils.colorize("&b" + category));

            // Conta quantas lojas existem nesta categoria
            long count = plugin.getShopWarpManager().getAllWarps().stream()
                    .filter(warp -> category.equals(warp.getCategory()))
                    .count();

            meta.setLore(Arrays.asList(
                    MessageUtils.colorize("&7Clique para filtrar lojas"),
                    MessageUtils.colorize("&7por esta categoria."),
                    "",
                    MessageUtils.colorize("&7Lojas: &f" + count)));

            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }

        // Adiciona botão voltar na última linha
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(MessageUtils.colorize("&cVoltar"));
        backButton.setItemMeta(backMeta);
        inventory.setItem(rows * 9 - 5, backButton); // Slot do meio da última linha

        player.openInventory(inventory);
        currentPage.put(playerUUID, "categories"); // Define a página atual
    }

    public void openMyWarpsGUI(Player player, int page) {
        String title = MessageUtils.colorize("&8Minhas Lojas");
        int rows = 6; // Sempre 6 linhas
        UUID playerUUID = player.getUniqueId();

        Inventory inv = Bukkit.createInventory(null, rows * 9, title);

        // Preenche o inventário com as warps do jogador
        List<ShopWarp> playerWarps = plugin.getShopWarpManager().getPlayerWarps(playerUUID);

        // Paginação
        int totalPages = (int) Math.ceil((double) playerWarps.size() / WARPS_PER_PAGE);
        if (totalPages == 0)
            totalPages = 1; // Garante pelo menos 1 página
        if (page >= totalPages)
            page = Math.max(0, totalPages - 1);
        if (page < 0)
            page = 0;

        playerPages.put(playerUUID, page);

        int startIndex = page * WARPS_PER_PAGE;
        int endIndex = Math.min(startIndex + WARPS_PER_PAGE, playerWarps.size());

        Map<Integer, String> warpSlots = new HashMap<>();

        // Adiciona warps na página atual
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot >= WARPS_PER_PAGE)
                break; // Segurança extra
            ShopWarp warp = playerWarps.get(i);
            ItemStack item = createWarpItem(warp); // Usa a função existente
            // Adiciona lore extra para gerenciamento
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MessageUtils.colorize("&aClique para gerenciar"));
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            warpSlots.put(slot, warp.getId());
            slot++;
        }

        // Adiciona botões de navegação
        addNavigationButtons(inv, player, page, totalPages);

        // Adiciona botão para criar nova warp
        if (player.hasPermission("playershopwarp.player.create")) {
            int maxWarps = plugin.getShopWarpManager().getPlayerMaxWarps(player);
            boolean canCreateMore = playerWarps.size() < maxWarps;

            ItemStack createItem = new ItemStack(canCreateMore ? Material.EMERALD_BLOCK : Material.BARRIER); // Bloco
                                                                                                             // esmeralda
            ItemMeta meta = createItem.getItemMeta();
            meta.setDisplayName(
                    MessageUtils.colorize(canCreateMore ? "&aCriar Nova Loja" : "&cLimite de Lojas Atingido"));

            List<String> lore = new ArrayList<>();
            if (canCreateMore) {
                lore.add(MessageUtils.colorize("&7Clique para iniciar a criação"));
                lore.add(MessageUtils.colorize("&7Custo: &f$" + plugin.getConfigManager().getWarpCreationCost()));
                lore.add(MessageUtils.colorize("&7Lojas: &f" + playerWarps.size() + "/" + maxWarps));
            } else {
                lore.add(MessageUtils.colorize("&7Você atingiu o limite de lojas"));
                lore.add(MessageUtils.colorize("&7Lojas: &c" + playerWarps.size() + "/" + maxWarps));
            }

            meta.setLore(lore);
            createItem.setItemMeta(meta);
            inv.setItem(49, createItem); // Slot do meio
        }

        // Adiciona botão para voltar
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(MessageUtils.colorize("&cVoltar"));
        backMeta.setLore(Collections.singletonList(MessageUtils.colorize("&7Voltar para a lista principal")));
        backItem.setItemMeta(backMeta);
        inv.setItem(45, backItem); // Slot 45 (canto inferior esquerdo)

        slotWarpMap.put(playerUUID, warpSlots);
        currentPage.put(playerUUID, "mywarps");

        player.openInventory(inv);
    }

    // --- createScanButton, createHereButton, createCommandButton permanecem iguais
    // ---

    public void openCreateWarpGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MessageUtils.colorize("&8Criar Loja"));
        UUID playerUUID = player.getUniqueId();

        // Botão de definir nome
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = nameItem.getItemMeta();
        nameMeta.setDisplayName(MessageUtils.colorize("&e1. Definir Nome"));
        List<String> nameLore = new ArrayList<>();
        nameLore.add(MessageUtils.colorize("&7Nome atual: &f" +
                (pendingWarpName.getOrDefault(playerUUID, "&cNão definido")))); // Usa cor se não definido
        nameLore.add("");
        nameLore.add(MessageUtils.colorize("&aClique para definir via chat"));
        nameMeta.setLore(nameLore);
        nameItem.setItemMeta(nameMeta);

        // Botão de definir descrição
        ItemStack descItem = new ItemStack(Material.BOOK);
        ItemMeta descMeta = descItem.getItemMeta();
        descMeta.setDisplayName(MessageUtils.colorize("&e2. Definir Descrição"));
        List<String> descLore = new ArrayList<>();
        descLore.add(MessageUtils.colorize("&7Descrição atual: &f" +
                (pendingWarpDescription.getOrDefault(playerUUID, "&cNão definida")))); // Usa cor se não definido
        descLore.add("");
        descLore.add(MessageUtils.colorize("&aClique para definir via chat"));
        descMeta.setLore(descLore);
        descItem.setItemMeta(descMeta);

        // Botão de confirmar (agora realmente confirma)
        ItemStack confirmItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(MessageUtils.colorize("&aConfirmar e Criar Loja"));
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(MessageUtils.colorize("&7Verifique nome e descrição acima."));
        String pendingCategory = creationProcess.getOrDefault(playerUUID, "").startsWith("category:")
                ? creationProcess.get(playerUUID).substring(9)
                : "Nenhuma";
        if (plugin.getConfigManager().areCategoriesEnabled()) {
            confirmLore.add(MessageUtils.colorize("&7Categoria selecionada: &f" + pendingCategory));
        }
        confirmLore.add(MessageUtils.colorize("&7Custo: &f$" + plugin.getConfigManager().getWarpCreationCost()));
        confirmLore.add("");
        confirmLore.add(MessageUtils.colorize("&aClique para finalizar a criação!"));
        confirmMeta.setLore(confirmLore);
        confirmItem.setItemMeta(confirmMeta);

        // Botão de voltar
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(MessageUtils.colorize("&cCancelar / Voltar"));
        backItem.setItemMeta(backMeta);

        // --- Posições ---
        inv.setItem(11, nameItem); // Centro Esquerda
        inv.setItem(13, descItem); // Centro
        // Slot 15 é para categoria
        inv.setItem(22, confirmItem); // Centro Inferior
        inv.setItem(18, backItem); // Canto Inferior Esquerdo

        // Botão de categoria (se habilitado)
        if (plugin.getConfigManager().areCategoriesEnabled()) {
            ItemStack categoryItem = new ItemStack(Material.CHEST); // Ou BOOK_AND_QUILL
            ItemMeta categoryMeta = categoryItem.getItemMeta();
            categoryMeta.setDisplayName(MessageUtils.colorize("&e3. Definir Categoria"));
            List<String> catLore = new ArrayList<>();
            catLore.add(MessageUtils.colorize("&7Categoria atual: &f" + pendingCategory));
            catLore.add("");
            catLore.add(MessageUtils.colorize("&aClique para escolher"));
            categoryMeta.setLore(catLore);
            categoryItem.setItemMeta(categoryMeta);
            inv.setItem(15, categoryItem); // Centro Direita
        }

        if (player.hasPermission("playershopwarp.player.claimblocks.gui")) { // Permissão para acessar
            ItemStack claimBlocksButton = new ItemStack(Material.GOLD_INGOT); // Ou DIAMOND_BLOCK, EMERALD, etc.
            ItemMeta claimBlocksMeta = claimBlocksButton.getItemMeta();
            if (claimBlocksMeta != null) {
                claimBlocksMeta.setDisplayName(MessageUtils.colorize("&6Gerenciar Blocos de Claim"));
                List<String> lore = new ArrayList<>();
                lore.add(MessageUtils.colorize("&7Compre ou venda blocos"));
                lore.add(MessageUtils.colorize("&7de proteção do GriefPrevention."));
                lore.add("");
                lore.add(MessageUtils.colorize("&eClique para abrir o menu de claims."));
                claimBlocksMeta.setLore(lore);
                claimBlocksButton.setItemMeta(claimBlocksMeta);
            }
            inv.setItem(8, claimBlocksButton); // Slot no canto superior direito
        }

        // --- Botões Auxiliares ---
        // Botão de scan - Slot 0
        // inv.setItem(0, createScanButton());

        // Botão de criar aqui - Slot 8
        // inv.setItem(8, createHereButton());

        // Botão de criar via comando - Slot 4
        // inv.setItem(4, createCommandButton());

        player.openInventory(inv);
        currentPage.put(playerUUID, "create");
    }

    // Ajustado: handleCreateWarpGUIClick
    private void handleCreateWarpGUIClick(Player player, int slot) {
        UUID playerUUID = player.getUniqueId();
        String currentFullProcess = creationProcess.get(playerUUID); // Ex: "name;category:Farming" ou
                                                                     // "category:Farming" ou null
        String baseProcess = currentFullProcess != null && currentFullProcess.contains(";")
                ? currentFullProcess.split(";", 2)[0]
                : currentFullProcess;
        String savedCategoryData = currentFullProcess != null && currentFullProcess.contains(";")
                ? currentFullProcess.split(";", 2)[1]
                : (baseProcess != null && baseProcess.startsWith("category:") ? baseProcess : null);

        if (slot == 18) { // Voltar / Cancelar
            clearCreationProcess(playerUUID);
            openMyWarpsGUI(player);
        } else if (slot == 11) { // Definir Nome
            player.closeInventory();
            // Salva o estado da categoria se houver
            creationProcess.put(playerUUID, "name" + (savedCategoryData != null ? ";" + savedCategoryData : ""));
            MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
        } else if (slot == 13) { // Definir Descrição
            if (!pendingWarpName.containsKey(playerUUID)) {
                MessageUtils.sendMessage(player, "messages.warp-name-required");
                openCreateWarpGUI(player);
                return;
            }
            player.closeInventory();
            creationProcess.put(playerUUID, "description" + (savedCategoryData != null ? ";" + savedCategoryData : ""));
            MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");
        } else if (slot == 15 && plugin.getConfigManager().areCategoriesEnabled()) { // Definir Categoria
            if (!pendingWarpName.containsKey(playerUUID)) {
                MessageUtils.sendMessage(player, "messages.warp-name-required");
                openCreateWarpGUI(player);
                return;
            }
            if (!pendingWarpDescription.containsKey(playerUUID)) {
                MessageUtils.sendMessage(player, "&cPor favor, defina uma descrição primeiro.");
                openCreateWarpGUI(player);
                return;
            }
            // O creationProcess já deve ter o nome e descrição pendentes,
            // a GUI de categoria vai ler isso implicitamente.
            // Se uma categoria já foi selecionada, o 'savedCategoryData' a terá.
            // O importante é que openWarpCategoryGUI lide com o estado atual.
            openWarpCategoryGUI(player);
        } else if (slot == 8) {
            if (player.hasPermission("playershopwarp.player.claimblocks.gui")) {
                // Importante: Não feche a CreateWarpGUI aqui,
                // pois o jogador pode querer voltar para ela após usar a ClaimBlockGUI.
                // A ClaimBlockGUI terá seu próprio botão de voltar que poderá
                // reabrir a CreateWarpGUI (se souber que veio dela) ou a MainGUI.
                claimBlockGUIInstance.openGUI(player);
                // Opcional: você pode querer salvar o estado atual da criação
                // para que a ClaimBlockGUI possa voltar para CreateWarpGUI no mesmo estado.
                // Por exemplo, setar currentPage para "create_pending_claimblock_management"
                // mainShopGUI.setCurrentPageName(playerUUID, "create_pending_claimblock_management");
            } else {
                MessageUtils.sendMessage(player, "messages.no-permission");
                // Não fecha a GUI se clicou sem permissão, apenas informa.
            }
        } else if (slot == 22) { // Confirmar e Criar Loja
            String name = pendingWarpName.get(playerUUID);
            String description = pendingWarpDescription.get(playerUUID);

            if (name == null || name.isEmpty()) {
                MessageUtils.sendMessage(player, "messages.warp-name-required");
                openCreateWarpGUI(player);
                return;
            }
            if (description == null || description.isEmpty()) {
                MessageUtils.sendMessage(player, "&cPor favor, defina uma descrição para a loja.");
                openCreateWarpGUI(player);
                return;
            }

            String category = null;
            if (plugin.getConfigManager().areCategoriesEnabled()) {
                if (savedCategoryData != null && savedCategoryData.startsWith("category:")) {
                    category = savedCategoryData.substring(9);
                } else {
                    MessageUtils.sendMessage(player, "&cPor favor, selecione uma categoria para a loja.");
                    openCreateWarpGUI(player);
                    return;
                }
            }

            Economy econ = plugin.getEconomy();
            double cost = plugin.getConfigManager().getWarpCreationCost();
            if (econ != null && cost > 0) {
                if (!econ.has(player, cost)) {
                    MessageUtils.sendMessage(player, "messages.not-enough-money",
                            new String[][] { { "{cost}", String.format("%.2f", cost) } });
                    player.closeInventory();
                    clearCreationProcess(playerUUID);
                    return;
                }
                EconomyResponse response = econ.withdrawPlayer(player, cost);
                if (!response.transactionSuccess()) {
                    MessageUtils.sendMessage(player, "&cOcorreu um erro ao retirar R$" + String.format("%.2f", cost)
                            + " da sua conta: " + response.errorMessage);
                    player.closeInventory();
                    clearCreationProcess(playerUUID);
                    return;
                }
                MessageUtils.sendMessage(player, "messages.money-taken",
                        new String[][] { { "{amount}", String.format("%.2f", cost) } });
            }

            boolean created = plugin.getShopWarpManager().createWarp(player, name, description, category);

            if (created) {
                player.closeInventory();
                clearCreationProcess(playerUUID);
            } else {
                MessageUtils.sendMessage(player, "&cNão foi possível criar a warp. Verifique as mensagens anteriores.");
                if (econ != null && cost > 0) { // Devolve o dinheiro APENAS se a criação falhou e o dinheiro foi
                                                // retirado.
                    // Como a verificação de has() é feita antes, assumimos que withdraw() foi
                    // tentado.
                    // Para ser mais seguro, a flag 'costDebitedByManager' no Manager é a melhor
                    // abordagem.
                    // Aqui, estamos simplificando e assumindo que se chegou aqui e 'created' é
                    // false, o débito ocorreu.
                    EconomyResponse depositResponse = econ.depositPlayer(player, cost);
                    if (depositResponse.transactionSuccess()) {
                        MessageUtils.sendMessage(player,
                                "&aO custo de R$" + String.format("%.2f", cost) + " foi devolvido.");
                    } else {
                        MessageUtils.sendMessage(player, "&cErro ao devolver o custo da warp. Contate um admin.");
                    }
                }
                openCreateWarpGUI(player); // Reabre para o jogador tentar novamente
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        String currentPageName = currentPage.get(playerUUID);
        String process = creationProcess.get(playerUUID);

        // Limpa estado se o jogador fechar a GUI manualmente (ESC) e não estiver
        // esperando entrada no chat
        if (currentPageName != null && !currentPageName.isEmpty()) {
            if (process == null || (!process.startsWith("name") && !process.startsWith("description") &&
                    !process.startsWith("announce") && !process.startsWith("edit_desc") &&
                    !process.equals("create_here_name"))) {
                currentPage.remove(playerUUID);
                // slotWarpMap.remove(playerUUID); // Geralmente não precisa limpar aqui,
                // pois é populado ao abrir a GUI.
                // playerPages.remove(playerUUID); // Mantém para a próxima abertura.
                // categoryFilter.remove(playerUUID); // Mantém filtro ativo.

                // Limpa o processo de criação apenas se não estiver aguardando entrada no chat
                // e não for um processo de edição de categoria (que volta para GUI de criação)
                if (process != null && !process.startsWith("category:")) {
                    clearCreationProcess(playerUUID);
                } else if (process == null && !currentPageName.equals("create")
                        && !currentPageName.equals("select_category")) {
                    // Se não há processo e não estava na GUI de criação ou seleção de categoria,
                    // pode limpar
                    // No entanto, a GUI de criação agora mantém nome/desc pendentes,
                    // então clearCreationProcess deve ser chamado mais seletivamente (ex: ao clicar
                    // em Voltar).
                }
            }
        }
    }

    private void addNavigationButtons(Inventory inv, Player player, int currentPageNum, int totalPages) {
        // Adiciona informação da página
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        pageInfoMeta.setDisplayName(
                MessageUtils.colorize("&ePágina " + (currentPageNum + 1) + " de " + Math.max(1, totalPages)));
        pageInfo.setItemMeta(pageInfoMeta);
        inv.setItem(53, pageInfo); // Slot 53 (canto inferior direito)

        // Botão página anterior
        if (currentPageNum > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(MessageUtils.colorize("&e<< Página Anterior"));
            prevPage.setItemMeta(prevMeta);
            inv.setItem(48, prevPage); // Slot 48 (esquerda do meio)
        } else {
            // Placeholder ou vidro cinza se não houver página anterior
            ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = placeholder.getItemMeta();
            meta.setDisplayName(" ");
            placeholder.setItemMeta(meta);
            inv.setItem(48, placeholder);
        }

        // Botão próxima página
        if (currentPageNum < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(MessageUtils.colorize("&ePróxima Página >>"));
            nextPage.setItemMeta(nextMeta);
            inv.setItem(50, nextPage); // Slot 50 (direita do meio)
        } else {
            // Placeholder ou vidro cinza se não houver próxima página
            ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = placeholder.getItemMeta();
            meta.setDisplayName(" ");
            placeholder.setItemMeta(meta);
            inv.setItem(50, placeholder);
        }
    }

    public void openWarpCategoryGUI(Player player) {
        List<String> categories = plugin.getConfigManager().getCategories();
        int numCategories = categories.size();
        int rows = Math.max(2, (int) Math.ceil((numCategories) / 9.0) + 1); // Calcula linhas: 1 para header/footer,
                                                                            // resto para itens
        rows = Math.min(6, rows); // Limita a 6 linhas

        String title = MessageUtils.colorize("&8Selecionar Categoria");
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);
        UUID playerUUID = player.getUniqueId();

        // Adiciona as categorias
        int slot = 0;
        for (String category : categories) {
            if (slot >= (rows - 1) * 9)
                break; // Não preenche a última linha

            ItemStack item = new ItemStack(Material.BOOK); // Ou outro item
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(MessageUtils.colorize("&b" + category));
            meta.setLore(Collections.singletonList(
                    MessageUtils.colorize("&7Clique para selecionar esta categoria")));

            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }

        // Adiciona botão voltar na última linha
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(MessageUtils.colorize("&cVoltar"));
        backMeta.setLore(Collections.singletonList(MessageUtils.colorize("&7Voltar para a criação da loja")));
        backButton.setItemMeta(backMeta);
        inventory.setItem(rows * 9 - 5, backButton); // Slot do meio da última linha

        player.openInventory(inventory);
        currentPage.put(playerUUID, "select_category"); // Define a página atual
    }

    public void openWarpManagementGUI(Player player, ShopWarp warp) {
        Inventory inventory = Bukkit.createInventory(null, 54, // 6 linhas
                MessageUtils.colorize("&8Gerenciar: &f" + warp.getName()));
        UUID playerUUID = player.getUniqueId();

        // Limpa o mapa de slots para esta GUI
        Map<Integer, String> warpSlots = new HashMap<>(); // Cria novo mapa local

        // Adiciona informações da warp
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(MessageUtils.colorize("&e" + warp.getName()));

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtils.colorize("&7Descrição: &f" + warp.getDescription()));
        String categoryDisplay = (warp.getCategory() != null && !warp.getCategory().isEmpty()) ? warp.getCategory()
                : "Nenhuma";
        if (plugin.getConfigManager().areCategoriesEnabled()) {
            lore.add(MessageUtils.colorize("&7Categoria: &f" + categoryDisplay));
        }
        lore.add(MessageUtils.colorize("&7Dono: &f" + warp.getOwnerName()));
        lore.add("");
        Location loc = warp.getLocation(); // Cache location
        lore.add(MessageUtils.colorize("&7Localização:"));
        lore.add(MessageUtils
                .colorize("&7  Mundo: &f" + (loc.getWorld() != null ? loc.getWorld().getName() : "Desconhecido")));
        lore.add(MessageUtils.colorize("&7  X: &f" + loc.getBlockX()));
        lore.add(MessageUtils.colorize("&7  Y: &f" + loc.getBlockY()));
        lore.add(MessageUtils.colorize("&7  Z: &f" + loc.getBlockZ()));

        infoMeta.setLore(lore);
        infoItem.setItemMeta(infoMeta);
        inventory.setItem(4, infoItem); // Slot 4 (centro superior)
        warpSlots.put(4, warp.getId()); // Mapeia slot

        // --- Linha de Ações Principais (linha 3, slots 19-25) ---
        // Botão de teleporte
        ItemStack teleportButton = new ItemStack(Material.ENDER_PEARL);
        ItemMeta teleportMeta = teleportButton.getItemMeta();
        teleportMeta.setDisplayName(MessageUtils.colorize("&aTeleportar"));
        teleportMeta.setLore(Collections.singletonList(
                MessageUtils.colorize("&7Clique para ir até esta loja")));
        teleportButton.setItemMeta(teleportMeta);
        inventory.setItem(20, teleportButton); // Esquerda
        warpSlots.put(20, warp.getId());

        // Botão de anúncio
        if (player.hasPermission("playershopwarp.player.announce")) {
            ItemStack announceButton = new ItemStack(Material.BELL);
            ItemMeta announceMeta = announceButton.getItemMeta();
            announceMeta.setDisplayName(MessageUtils.colorize("&6Anunciar"));
            announceMeta.setLore(Collections.singletonList(
                    MessageUtils.colorize("&7Clique para anunciar sua loja")));
            announceButton.setItemMeta(announceMeta);
            inventory.setItem(22, announceButton); // Centro
            warpSlots.put(22, warp.getId());
        }

        // Botão de deletar
        if (player.hasPermission("playershopwarp.player.delete") || player.getUniqueId().equals(warp.getOwnerId())) {
            ItemStack deleteButton = new ItemStack(Material.BARRIER);
            ItemMeta deleteMeta = deleteButton.getItemMeta();
            deleteMeta.setDisplayName(MessageUtils.colorize("&cDeletar Loja"));
            deleteMeta.setLore(Collections.singletonList(
                    MessageUtils.colorize("&7&lAtenção:&r&c Clique para DELETAR permanentemente!")));
            deleteButton.setItemMeta(deleteMeta);
            inventory.setItem(24, deleteButton); // Direita
            warpSlots.put(24, warp.getId());
        }

        // --- Linha de Edição (linha 5, slots 37-43) ---
        // Botão de editar descrição
        ItemStack editDescButton = new ItemStack(Material.WRITABLE_BOOK); // Livro editável
        ItemMeta editDescMeta = editDescButton.getItemMeta();
        editDescMeta.setDisplayName(MessageUtils.colorize("&eEditar Descrição"));
        editDescMeta.setLore(Collections.singletonList(
                MessageUtils.colorize("&7Clique para definir nova descrição no chat")));
        editDescButton.setItemMeta(editDescMeta);
        inventory.setItem(38, editDescButton); // Esquerda
        warpSlots.put(38, warp.getId());

        // Botão de editar categoria (se categorias estiverem habilitadas)
        if (plugin.getConfigManager().areCategoriesEnabled()) {
            ItemStack editCatButton = new ItemStack(Material.CHEST); // Baú
            ItemMeta editCatMeta = editCatButton.getItemMeta();
            editCatMeta.setDisplayName(MessageUtils.colorize("&eEditar Categoria"));
            editCatMeta.setLore(Collections.singletonList(
                    MessageUtils.colorize("&7Clique para escolher outra categoria")));
            editCatButton.setItemMeta(editCatMeta);
            inventory.setItem(40, editCatButton); // Centro
            warpSlots.put(40, warp.getId());
        }

        // Botão de mudar local (placeholder)
        // ItemStack moveButton = new ItemStack(Material.COMPASS);
        // ItemMeta moveMeta = moveButton.getItemMeta();
        // moveMeta.setDisplayName(MessageUtils.colorize("&eMudar Local (Em breve)"));
        // moveButton.setItemMeta(moveMeta);
        // inventory.setItem(42, moveButton); // Direita
        // warpSlots.put(42, warp.getId());

        // Botão voltar (canto inferior esquerdo)
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(MessageUtils.colorize("&cVoltar"));
        backMeta.setLore(Collections.singletonList(MessageUtils.colorize("&7Voltar para 'Minhas Lojas'")));
        backButton.setItemMeta(backMeta);
        inventory.setItem(45, backButton);
        // Não precisa mapear o botão voltar

        // Preenche espaços vazios com painel de vidro cinza para estética
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }

        slotWarpMap.put(playerUUID, warpSlots); // Armazena o mapa de slots atualizado
        currentPage.put(playerUUID, "manage"); // Define a página atual

        player.openInventory(inventory);
    }

    private ItemStack createWarpItem(ShopWarp warp) {
        ItemStack item;
        UUID ownerId = warp.getOwnerId();
        String ownerName = warp.getOwnerName();
        String warpName = warp.getName();
        String description = warp.getDescription();
        String category = warp.getCategory();

        // Tenta usar cabeça do jogador se possível
        try {
            item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
            // Tenta obter OfflinePlayer, pode retornar null
            org.bukkit.OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
            if (owner != null && owner.hasPlayedBefore()) {
                skullMeta.setOwningPlayer(owner);
            } else {
                // Fallback se jogador não encontrado (raro, mas possível)
                skullMeta.setOwner(ownerName); // Usa nome como fallback se API permitir
                // Se setOwner falhar ou não for o ideal, volta pro item padrão
                // throw new Exception("OfflinePlayer not found or never played."); // Força
                // fallback
            }
            item.setItemMeta(skullMeta);
        } catch (Exception e) {
            // Fallback para item configurável ou padrão (ex: Ender Pearl)
            Material fallbackMat = Material.ENDER_PEARL;
            try {
                fallbackMat = Material.matchMaterial(
                        plugin.getConfigManager().getConfig().getString("gui.fallback-item", "ENDER_PEARL"));
                if (fallbackMat == null)
                    fallbackMat = Material.ENDER_PEARL;
            } catch (Exception ignored) {
            }
            item = new ItemStack(fallbackMat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item; // Segurança

        String nameFormat = plugin.getConfigManager().getConfig().getString("gui.item-name-format", "&e{shop_name}");
        meta.setDisplayName(MessageUtils.colorize(nameFormat.replace("{shop_name}", warpName)));

        List<String> loreFormat = plugin.getConfigManager().getConfig().getStringList("gui.item-lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreFormat) {
            line = line.replace("{owner}", ownerName)
                    .replace("{description}", description);

            if (plugin.getConfigManager().areCategoriesEnabled()) {
                line = line.replace("{category}", (category != null && !category.isEmpty()) ? category : "Nenhuma");
            } else {
                // Remove a linha de categoria se não estiverem habilitadas ou deixa em branco
                if (line.contains("{category}"))
                    continue; // Pula a linha
            }

            lore.add(MessageUtils.colorize(line));
        }

        // Adiciona lore padrão se a formatação estiver vazia
        if (lore.isEmpty()) {
            lore.add(MessageUtils.colorize("&7Dono: &f" + ownerName));
            lore.add(MessageUtils.colorize("&7Descrição: &f" + description));
            if (plugin.getConfigManager().areCategoriesEnabled()) {
                lore.add(MessageUtils.colorize(
                        "&7Categoria: &f" + ((category != null && !category.isEmpty()) ? category : "Nenhuma")));
            }
            lore.add("");
            lore.add(MessageUtils.colorize("&aClique para teleportar!"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    // Removida: handleMainGUIClick(Player player, int slot)
    // Removida: handleMyWarpsGUIClick(Player player, int slot)

    // Corrigido: handleMainGUIClick agora usa warpSlots
    private void handleMainGUIClick(Player player, int slot, Map<Integer, String> warpSlots) {
        UUID playerUUID = player.getUniqueId();
        int currentPageNum = playerPages.getOrDefault(playerUUID, 0);

        if (warpSlots != null && warpSlots.containsKey(slot)) {
            // Clicou em uma warp
            String warpId = warpSlots.get(slot);
            // Verifica se warpId é válido antes de teleportar
            if (plugin.getShopWarpManager().getWarp(warpId) != null) {
                plugin.getShopWarpManager().teleportToWarp(player, warpId);
                player.closeInventory(); // Fecha apenas no teleporte
            } else {
                MessageUtils.sendMessage(player, "&cErro: Esta loja não existe mais.");
                openMainGUI(player, currentPageNum); // Reabre a GUI na mesma página
            }
        } else if (slot == 49) { // Botão minhas lojas
            openMyWarpsGUI(player);
        } else if (slot == 48 && currentPageNum > 0) { // Página anterior
            openMainGUI(player, currentPageNum - 1);
        } else if (slot == 50) { // Próxima página
            // Precisa verificar se existe próxima página antes de abrir
            List<ShopWarp> allWarps = plugin.getShopWarpManager().getAllWarps(); // Recalcula baseado no filtro atual
            String currentCategory = categoryFilter.getOrDefault(playerUUID, "");
            if (!currentCategory.isEmpty() && plugin.getConfigManager().areCategoriesEnabled()) {
                allWarps = allWarps.stream()
                        .filter(warp -> warp.getCategory() != null
                                && warp.getCategory().equalsIgnoreCase(currentCategory))
                        .collect(Collectors.toList());
            }
            int totalPages = (int) Math.ceil((double) allWarps.size() / WARPS_PER_PAGE);
            if (currentPageNum < totalPages - 1) {
                openMainGUI(player, currentPageNum + 1);
            }
        } else if (slot == 47 && plugin.getConfigManager().areCategoriesEnabled()) { // Botão de filtro por categoria
            openCategoryGUI(player);
        }
        // Ignora cliques em outros slots (como vidro)
    }

    // Corrigido: handleMyWarpsGUIClick agora usa warpSlots
    private void handleMyWarpsGUIClick(Player player, int slot, Map<Integer, String> warpSlots) {
        UUID playerUUID = player.getUniqueId();
        int currentPageNum = playerPages.getOrDefault(playerUUID, 0);

        if (slot == 45) { // Botão voltar
            openMainGUI(player);
        } else if (slot == 49 && player.hasPermission("playershopwarp.player.create")) { // Botão criar nova warp
            int maxWarps = plugin.getShopWarpManager().getPlayerMaxWarps(player);
            int currentWarps = plugin.getShopWarpManager().getPlayerWarps(playerUUID).size();
            if (currentWarps < maxWarps) {
                clearCreationProcess(playerUUID); // Limpa estado antigo antes de abrir
                openCreateWarpGUI(player);
            } else {
                MessageUtils.sendMessage(player, "messages.max-warps-reached",
                        new String[][] { { "{max}", String.valueOf(maxWarps) } });
                // Mantém a GUI aberta
            }
        } else if (slot == 48 && currentPageNum > 0) { // Página anterior
            openMyWarpsGUI(player, currentPageNum - 1);
        } else if (slot == 50) { // Próxima página
            List<ShopWarp> playerWarps = plugin.getShopWarpManager().getPlayerWarps(playerUUID);
            int totalPages = (int) Math.ceil((double) playerWarps.size() / WARPS_PER_PAGE);
            if (currentPageNum < totalPages - 1) {
                openMyWarpsGUI(player, currentPageNum + 1);
            }
        } else if (warpSlots != null && warpSlots.containsKey(slot)) { // Clicou em uma warp
            String warpId = warpSlots.get(slot);
            ShopWarp warp = plugin.getShopWarpManager().getWarp(warpId);
            if (warp != null) {
                // Verifica se o jogador é o dono para abrir gerenciamento
                if (warp.getOwnerId().equals(playerUUID)) {
                    openWarpManagementGUI(player, warp);
                } else {
                    // Se não for o dono (caso raro, talvez admin vendo), apenas teleporta
                    plugin.getShopWarpManager().teleportToWarp(player, warpId);
                    player.closeInventory();
                }
            } else {
                MessageUtils.sendMessage(player, "&cErro: Esta loja não existe mais.");
                openMyWarpsGUI(player, currentPageNum); // Reabre a GUI
            }
        }
        // Ignora cliques em outros slots
    }

    // Método para abrir a GUI principal sem especificar página
    public void openMainGUI(Player player) {
        openMainGUI(player, 0); // Sempre abre na página 0 por padrão
    }

    // Ajustado: openMyWarpsGUI sem página
    public void openMyWarpsGUI(Player player) {
        int currentPageNum = playerPages.getOrDefault(player.getUniqueId(), 0);
        openMyWarpsGUI(player, currentPageNum);
    }

    // Ajustado: handleCategoryGUIClick para *APENAS* filtrar ou voltar
    private void handleCategoryGUIClick(Player player, int slot, String title) {
        UUID playerUUID = player.getUniqueId();

        // Botão "Mostrar Todas"
        if (slot == 4) {
            categoryFilter.remove(playerUUID);
            openMainGUI(player);
            return;
        }

        // Clicou em uma categoria para filtrar
        List<String> categories = plugin.getConfigManager().getCategories();
        int categoryStartIndex = 9; // Onde começam os itens de categoria
        if (slot >= categoryStartIndex && slot < categoryStartIndex + categories.size()) {
            int categoryIndex = slot - categoryStartIndex;
            // Segurança extra
            if (categoryIndex >= 0 && categoryIndex < categories.size()) {
                String selectedCategory = categories.get(categoryIndex);
                categoryFilter.put(playerUUID, selectedCategory);
                openMainGUI(player); // Abre a GUI principal filtrada
                return;
            }
        }

        // Botão voltar (calcular baseado no tamanho)
        int rows = Math.min(6, (categories.size() / 9) + 2);
        int backButtonSlot = rows * 9 - 5;
        if (slot == backButtonSlot) {
            // Sempre volta para a GUI principal quando está na GUI de categorias de filtro
            openMainGUI(player);
        }
        // Ignora outros cliques
    }

    // Ajustado: handleManageWarpGUIClick com novos slots
    private void handleManageWarpGUIClick(Player player, int slot, String warpId) {
        // Se warpId for null, só pode ser o botão voltar
        if (warpId == null) {
            if (slot == 45) { // Botão voltar
                openMyWarpsGUI(player);
            }
            return;
        }

        ShopWarp warp = plugin.getShopWarpManager().getWarp(warpId);
        if (warp == null) {
            MessageUtils.sendMessage(player, "&cErro: A loja não foi encontrada.");
            player.closeInventory();
            openMyWarpsGUI(player); // Volta para a lista
            return;
        }

        // Verifica se o jogador ainda é o dono (segurança)
        if (!warp.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("playershopwarp.admin.manage")) {
            MessageUtils.sendMessage(player, "&cVocê não tem permissão para gerenciar esta loja.");
            player.closeInventory();
            openMyWarpsGUI(player);
            return;
        }

        UUID playerUUID = player.getUniqueId();

        switch (slot) {
            case 45: // Botão voltar (já tratado se warpId é null)
                openMyWarpsGUI(player);
                break;
            case 20: // Teleportar
                plugin.getShopWarpManager().teleportToWarp(player, warpId);
                player.closeInventory();
                break;
            case 22: // Anunciar
                if (player.hasPermission("playershopwarp.player.announce")) {
                    player.closeInventory();
                    creationProcess.put(playerUUID, "announce:" + warpId);
                    MessageUtils.sendMessage(player, "messages.announce-prompt",
                            new String[][] { { "{name}", warp.getName() } });
                }
                break;
            case 24: // Deletar
                if (player.hasPermission("playershopwarp.player.delete")
                        || player.getUniqueId().equals(warp.getOwnerId())) {
                    // Adicionar talvez uma confirmação extra aqui?
                    plugin.getShopWarpManager().deleteWarp(player, warpId);
                    openMyWarpsGUI(player); // Atualiza a lista
                }
                break;
            case 38: // Editar descrição
                player.closeInventory();
                creationProcess.put(playerUUID, "edit_desc:" + warpId);
                MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");
                break;
            case 40: // Editar categoria
                if (plugin.getConfigManager().areCategoriesEnabled()) {
                    // Guarda o processo de edição de categoria
                    creationProcess.put(playerUUID, "edit_cat:" + warpId);
                    openWarpCategoryGUI(player); // Abre a GUI de seleção de categoria
                }
                break;
            // case 42: // Mudar Local (Futuro)
            // MessageUtils.sendMessage(player, "&cFuncionalidade ainda não implementada.");
            // break;
            default:
                // Clicou em outro item (info, vidro), não faz nada
                break;
        }
    }

    // Removido: handleInventoryClick(Player player, int slot, String title)

    // Dentro da classe ShopWarpGUI.java

    public void handlePlayerChat(Player player, String message) {
        UUID playerUUID = player.getUniqueId();
        String fullProcessState = creationProcess.get(playerUUID);

        if (fullProcessState == null) {
            return;
        }
        // Importante: Se o processo já foi delegado para ClaimBlockGUI no listener,
        // este método não deve tentar processá-lo novamente.
        // A verificação acima no listener já deve prevenir isso, mas uma segurança
        // extra aqui:
        if (fullProcessState.startsWith("buy_claim_blocks_") || fullProcessState.startsWith("sell_claim_blocks_")) {
            return;
        }

        message = message.trim();
        if (message.isEmpty()) {
            MessageUtils.sendMessage(player, "&cEntrada inválida. Tente novamente.");
            if (fullProcessState.startsWith("name"))
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
            else if (fullProcessState.startsWith("description"))
                MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");
            else if (fullProcessState.startsWith("announce:"))
                MessageUtils.sendMessage(player, "messages.announce-prompt");
            else if (fullProcessState.startsWith("edit_desc:"))
                MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");
            else if (fullProcessState.equals("create_here_name"))
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
            return;
        }

        String mainProcess = fullProcessState;
        String savedData = null;

        if (fullProcessState.contains(";")) {
            String[] parts = fullProcessState.split(";", 2);
            mainProcess = parts[0];
            if (parts.length > 1)
                savedData = parts[1];
        }

        if (mainProcess.equals("name")) {
            if (plugin.getShopWarpManager().isWarpNameTaken(message)) {
                MessageUtils.sendMessage(player, "messages.warp-name-taken");
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
                return;
            }
            pendingWarpName.put(playerUUID, message);
            MessageUtils.sendMessage(player, "messages.name-set-feedback", new String[][] { { "{name}", message } });
            creationProcess.put(playerUUID, "description" + (savedData != null ? ";" + savedData : ""));
            MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");

        } else if (mainProcess.equals("create_here_name")) {
            if (plugin.getShopWarpManager().isWarpNameTaken(message)) {
                MessageUtils.sendMessage(player, "messages.warp-name-taken");
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
                return;
            }
            Economy econ = plugin.getEconomy();
            double cost = plugin.getConfigManager().getWarpCreationCost();
            if (econ != null && cost > 0) {
                if (!econ.has(player, cost)) {
                    MessageUtils.sendMessage(player, "messages.not-enough-money",
                            new String[][] { { "{cost}", String.format("%.2f", cost) } });
                    clearCreationProcess(playerUUID);
                    return;
                }
                EconomyResponse response = econ.withdrawPlayer(player, cost);
                if (!response.transactionSuccess()) {
                    MessageUtils.sendMessage(player, "&cErro ao debitar: " + response.errorMessage);
                    clearCreationProcess(playerUUID);
                    return;
                }
                MessageUtils.sendMessage(player, "messages.money-taken",
                        new String[][] { { "{amount}", String.format("%.2f", cost) } });
            }
            boolean created = plugin.getShopWarpManager().createWarp(player, message, "Loja de " + player.getName(),
                    null);
            if (!created && econ != null && cost > 0) {
                EconomyResponse depositResponse = econ.depositPlayer(player, cost);
                if (depositResponse.transactionSuccess()) {
                    MessageUtils.sendMessage(player,
                            "&aO custo de R$" + String.format("%.2f", cost) + " foi devolvido.");
                } else {
                    MessageUtils.sendMessage(player, "&cErro ao devolver o custo da warp. Contate um admin.");
                }
            }
            clearCreationProcess(playerUUID);

        } else if (mainProcess.equals("description")) {
            String name = pendingWarpName.get(playerUUID);
            if (name == null) {
                MessageUtils.sendMessage(player, "&cErro interno: Nome da loja não encontrado. Tente novamente.");
                clearCreationProcess(playerUUID);
                Bukkit.getScheduler().runTask(plugin, () -> openCreateWarpGUI(player));
                return;
            }
            pendingWarpDescription.put(playerUUID, message);
            MessageUtils.sendMessage(player, "messages.description-set-feedback");
            creationProcess.put(playerUUID, savedData != null ? savedData : "");
            Bukkit.getScheduler().runTask(plugin, () -> openCreateWarpGUI(player));

        } else if (fullProcessState.startsWith("announce:")) {
            String warpId = fullProcessState.substring(9);
            plugin.getShopWarpManager().announceShop(player, warpId, message);
            creationProcess.remove(playerUUID);

        } else if (fullProcessState.startsWith("edit_desc:")) {
            String warpId = fullProcessState.substring(10);
            ShopWarp warp = plugin.getShopWarpManager().getWarp(warpId);
            if (warp != null
                    && (warp.getOwnerId().equals(playerUUID) || player.hasPermission("playershopwarp.admin.manage"))) {
                plugin.getShopWarpManager().updateWarpDescription(player, warpId, message);
                ShopWarp updatedWarp = plugin.getShopWarpManager().getWarp(warpId);
                if (updatedWarp != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> openWarpManagementGUI(player, updatedWarp));
                }
            } else if (warp == null) {
                MessageUtils.sendMessage(player, "messages.warp-not-found");
            } else {
                MessageUtils.sendMessage(player, "messages.not-owner");
            }
            creationProcess.remove(playerUUID);
        }
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) { // Usando o evento síncrono PlayerChatEvent
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        String message = event.getMessage(); // Mensagem original do chat

        String currentFullProcessState = creationProcess.get(playerUUID);

        if (currentFullProcessState == null) {
            return; // Não está em nenhum processo que espera chat, deixa a mensagem passar
        }

        // Variável para a mensagem processada (trim)
        String trimmedMessage = message.trim();

        // --- Início da Lógica de Delegação para ClaimBlockGUI ---
        if (currentFullProcessState.startsWith("buy_claim_blocks_custom_amount")) {
            event.setCancelled(true);
            // Usa trimmedMessage para os handlers da ClaimBlockGUI
            Bukkit.getScheduler().runTask(plugin, () -> {
                claimBlockGUIInstance.handleCustomAmountInput(player, trimmedMessage, true); // true for buying
            });
            return; // Processado pela ClaimBlockGUI, termina aqui
        } else if (currentFullProcessState.startsWith("sell_claim_blocks_custom_amount")) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                claimBlockGUIInstance.handleCustomAmountInput(player, trimmedMessage, false); // false for selling
            });
            return;
        } else if (currentFullProcessState.startsWith("buy_claim_blocks_custom_confirm:")) {
            event.setCancelled(true);
            String[] parts = currentFullProcessState.split(":");
            try {
                int amount = Integer.parseInt(parts[1]);
                double totalPrice = Double.parseDouble(parts[2]);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    claimBlockGUIInstance.handleCustomConfirmInput(player, trimmedMessage, amount, totalPrice, true);
                });
            } catch (Exception e) {
                MessageUtils.sendMessage(player, "&cErro ao processar confirmação (compra). Tente novamente.");
                clearCreationProcess(playerUUID);
            }
            return;
        } else if (currentFullProcessState.startsWith("sell_claim_blocks_custom_confirm:")) {
            event.setCancelled(true);
            String[] parts = currentFullProcessState.split(":");
            try {
                int amount = Integer.parseInt(parts[1]);
                double totalPrice = Double.parseDouble(parts[2]);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    claimBlockGUIInstance.handleCustomConfirmInput(player, trimmedMessage, amount, totalPrice, false);
                });
            } catch (Exception e) {
                MessageUtils.sendMessage(player, "&cErro ao processar confirmação (venda). Tente novamente.");
                clearCreationProcess(playerUUID);
            }
            return;
        }
        // --- Fim da Lógica de Delegação para ClaimBlockGUI ---

        // Se chegou aqui, o processo NÃO é da ClaimBlockGUI, então deve ser da
        // ShopWarpGUI.
        // Verifica os processos da ShopWarpGUI que esperam chat.
        if (currentFullProcessState.startsWith("name") ||
                currentFullProcessState.startsWith("description") ||
                currentFullProcessState.startsWith("announce:") ||
                currentFullProcessState.startsWith("edit_desc:") ||
                currentFullProcessState.equals("create_here_name")) {

            event.setCancelled(true); // Cancela a mensagem de ir para o chat normal

            // Chama o método handlePlayerChat INTERNO da ShopWarpGUI
            // que você já tinha e corrigiu anteriormente.
            // Passa a trimmedMessage.
            final String finalTrimmedMessage = trimmedMessage; // Efetivamente final para a lambda
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.handleShopWarpGUIChatInput(player, finalTrimmedMessage); // Chama o método renomeado
            });
        }
        // Se não corresponder a nenhum processo conhecido que espera chat, a mensagem
        // vai para o chat normalmente.
    }

    public void handleShopWarpGUIChatInput(Player player, String message) { // message aqui já é trimmed
        UUID playerUUID = player.getUniqueId();
        String fullProcessState = creationProcess.get(playerUUID);

        // Segurança: se por algum motivo o estado for nulo ou de claimblock, não faz
        // nada
        if (fullProcessState == null || fullProcessState.startsWith("buy_claim_blocks_")
                || fullProcessState.startsWith("sell_claim_blocks_")) {
            return;
        }

        if (message.isEmpty()) { // Verifica mensagem vazia (já foi trimmed)
            MessageUtils.sendMessage(player, "&cEntrada inválida. Tente novamente.");
            if (fullProcessState.startsWith("name"))
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
            else if (fullProcessState.startsWith("description"))
                MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");
            else if (fullProcessState.startsWith("announce:"))
                MessageUtils.sendMessage(player, "messages.announce-prompt");
            else if (fullProcessState.startsWith("edit_desc:"))
                MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");
            else if (fullProcessState.equals("create_here_name"))
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
            return;
        }

        String mainProcess = fullProcessState;
        String savedData = null;

        if (fullProcessState.contains(";")) {
            String[] parts = fullProcessState.split(";", 2);
            mainProcess = parts[0];
            if (parts.length > 1)
                savedData = parts[1];
        }

        if (mainProcess.equals("name")) {
            if (plugin.getShopWarpManager().isWarpNameTaken(message)) {
                MessageUtils.sendMessage(player, "messages.warp-name-taken");
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
                return;
            }
            pendingWarpName.put(playerUUID, message);
            MessageUtils.sendMessage(player, "messages.name-set-feedback", new String[][] { { "{name}", message } });
            creationProcess.put(playerUUID, "description" + (savedData != null ? ";" + savedData : ""));
            MessageUtils.sendMessage(player, "messages.create-warp-description-prompt");

        } else if (mainProcess.equals("create_here_name")) {
            if (plugin.getShopWarpManager().isWarpNameTaken(message)) {
                MessageUtils.sendMessage(player, "messages.warp-name-taken");
                MessageUtils.sendMessage(player, "messages.create-warp-name-prompt");
                return;
            }
            Economy econ = plugin.getEconomy();
            double cost = plugin.getConfigManager().getWarpCreationCost();
            if (econ != null && cost > 0) {
                if (!econ.has(player, cost)) {
                    MessageUtils.sendMessage(player, "messages.not-enough-money",
                            new String[][] { { "{cost}", String.format("%.2f", cost) } });
                    clearCreationProcess(playerUUID);
                    return;
                }
                EconomyResponse response = econ.withdrawPlayer(player, cost);
                if (!response.transactionSuccess()) {
                    MessageUtils.sendMessage(player, "&cErro ao debitar: " + response.errorMessage);
                    clearCreationProcess(playerUUID);
                    return;
                }
                MessageUtils.sendMessage(player, "messages.money-taken",
                        new String[][] { { "{amount}", String.format("%.2f", cost) } });
            }
            boolean created = plugin.getShopWarpManager().createWarp(player, message, "Loja de " + player.getName(),
                    null);
            if (!created && econ != null && cost > 0) {
                EconomyResponse depositResponse = econ.depositPlayer(player, cost);
                if (depositResponse.transactionSuccess()) {
                    MessageUtils.sendMessage(player,
                            "&aO custo de R$" + String.format("%.2f", cost) + " foi devolvido.");
                } else {
                    MessageUtils.sendMessage(player, "&cErro ao devolver o custo da warp. Contate um admin.");
                }
            }
            clearCreationProcess(playerUUID);

        } else if (mainProcess.equals("description")) {
            String name = pendingWarpName.get(playerUUID);
            if (name == null) {
                MessageUtils.sendMessage(player, "&cErro interno: Nome da loja não encontrado. Tente novamente.");
                clearCreationProcess(playerUUID);
                Bukkit.getScheduler().runTask(plugin, () -> openCreateWarpGUI(player));
                return;
            }
            pendingWarpDescription.put(playerUUID, message);
            MessageUtils.sendMessage(player, "messages.description-set-feedback");
            creationProcess.put(playerUUID, savedData != null ? savedData : "");
            Bukkit.getScheduler().runTask(plugin, () -> openCreateWarpGUI(player));

        } else if (fullProcessState.startsWith("announce:")) {
            String warpId = fullProcessState.substring(9);
            plugin.getShopWarpManager().announceShop(player, warpId, message);
            creationProcess.remove(playerUUID);

        } else if (fullProcessState.startsWith("edit_desc:")) {
            String warpId = fullProcessState.substring(10);
            ShopWarp warp = plugin.getShopWarpManager().getWarp(warpId);
            if (warp != null
                    && (warp.getOwnerId().equals(playerUUID) || player.hasPermission("playershopwarp.admin.manage"))) {
                plugin.getShopWarpManager().updateWarpDescription(player, warpId, message);
                ShopWarp updatedWarp = plugin.getShopWarpManager().getWarp(warpId);
                if (updatedWarp != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> openWarpManagementGUI(player, updatedWarp));
                }
            } else if (warp == null) {
                MessageUtils.sendMessage(player, "messages.warp-not-found");
            } else {
                MessageUtils.sendMessage(player, "messages.not-owner");
            }
            creationProcess.remove(playerUUID);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();
        InventoryView view = event.getView();
        Inventory topInventory = view.getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();
        UUID playerUUID = player.getUniqueId();

        String title = view.getTitle();
        boolean isGUIShopWarpRelated = false;

        List<String> shopWarpMainTitles = Arrays.asList(
                MessageUtils.colorize(
                        plugin.getConfigManager().getConfig().getString("gui.title", "&8Lojas dos Jogadores")),
                MessageUtils.colorize("&8Minhas Lojas"),
                MessageUtils.colorize("&8Criar Loja"),
                MessageUtils.colorize("&8Categorias"),
                MessageUtils.colorize("&8Selecionar Categoria"));
        String shopWarpManagePrefix = MessageUtils.colorize("&8Gerenciar: ");
        String claimBlockGUITitle = MessageUtils.colorize("&8Comprar/Vender Blocos de Claim");

        if (shopWarpMainTitles.contains(title) || title.startsWith(shopWarpManagePrefix)
                || title.equals(claimBlockGUITitle)) {
            isGUIShopWarpRelated = true;
        }

        if (!isGUIShopWarpRelated) {
            return;
        }

        if (clickedInventory != null && clickedInventory.equals(topInventory)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            Map<Integer, String> currentWarpSlots = slotWarpMap.getOrDefault(playerUUID, Collections.emptyMap());
            ItemStack currentItem = event.getCurrentItem(); // Pega o item clicado

            if (title.equals(claimBlockGUITitle)) {
                claimBlockGUIInstance.onInventoryClick(event); // Delega para o handler da ClaimBlockGUI
                return;
            }

            // Handlers da ShopWarpGUI
            if (title.equals(shopWarpMainTitles.get(0))) { // Lojas dos Jogadores
                // Botão para ClaimBlockGUI (Exemplo: slot 51, ícone GOLD_INGOT)
                if (slot == 51 && currentItem != null && currentItem.getType() == Material.GOLD_INGOT) {
                    if (player.hasPermission("playershopwarp.player.claimblocks.gui")) { // Permissão específica
                        claimBlockGUIInstance.openGUI(player);
                    } else {
                        MessageUtils.sendMessage(player, "messages.no-permission");
                    }
                } else {
                    handleMainGUIClick(player, slot, currentWarpSlots);
                }
            } else if (title.equals(shopWarpMainTitles.get(1))) { // Minhas Lojas
                handleMyWarpsGUIClick(player, slot, currentWarpSlots);
            } else if (title.equals(shopWarpMainTitles.get(2))) { // Criar Loja
                handleCreateWarpGUIClick(player, slot);
            } else if (title.equals(shopWarpMainTitles.get(3))) { // Categorias (Filtragem)
                handleCategoryGUIClick(player, slot, title);
            } else if (title.equals(shopWarpMainTitles.get(4))) { // Selecionar Categoria (Criação/Edição)
                handleWarpCategoryGUIClick(player, slot, title);
            } else if (title.startsWith(shopWarpManagePrefix)) { // Gerenciar
                String warpId = currentWarpSlots.get(slot);
                if (warpId == null && slot == 45) { // Slot do botão voltar na GUI de Gerenciar
                    handleManageWarpGUIClick(player, slot, null);
                } else if (warpId != null) {
                    handleManageWarpGUIClick(player, slot, warpId);
                }
            }
        } else if (clickedInventory != null && clickedInventory.getType() == InventoryType.PLAYER) {
            if (event.getClick().isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    // Ajustado: handleWarpCategoryGUIClick (APENAS para criação/edição)
    private void handleWarpCategoryGUIClick(Player player, int slot, String title) {
        List<String> categories = plugin.getConfigManager().getCategories();
        UUID playerUUID = player.getUniqueId();
        String fullProcessState = creationProcess.get(playerUUID);

        // Verifica se clicou em um item de categoria
        if (slot >= 0 && slot < categories.size()) {
            String selectedCategory = categories.get(slot);

            if (fullProcessState == null) {
                player.closeInventory();
                plugin.getLogger()
                        .warning("Jogador " + player.getName() + " na GUI de seleção de categoria sem processo ativo.");
                return;
            }

            if (fullProcessState.startsWith("edit_cat:")) {
                String warpId = fullProcessState.substring(9);
                ShopWarp warp = plugin.getShopWarpManager().getWarp(warpId);
                if (warp != null && (warp.getOwnerId().equals(playerUUID)
                        || player.hasPermission("playershopwarp.admin.manage"))) {
                    plugin.getShopWarpManager().updateWarpCategory(player, warpId, selectedCategory);
                    openWarpManagementGUI(player, warp);
                } else if (warp == null) {
                    MessageUtils.sendMessage(player, "messages.warp-not-found");
                    openMyWarpsGUI(player);
                } else {
                    MessageUtils.sendMessage(player, "messages.not-owner");
                    openMyWarpsGUI(player);
                }
                creationProcess.remove(playerUUID); // Limpa o processo de edição de categoria

            } else { // Veio da criação da loja
                // Atualiza o estado de criação para incluir a categoria selecionada
                // Preserva 'name' ou 'description' se já estavam no processo
                String baseProcessPart = "";
                if (fullProcessState.startsWith("name") || fullProcessState.startsWith("description")) {
                    baseProcessPart = fullProcessState.split(";", 2)[0] + ";";
                }
                creationProcess.put(playerUUID, baseProcessPart + "category:" + selectedCategory);
                openCreateWarpGUI(player); // Volta para a GUI de criação para confirmar
            }

        } else { // Botão voltar ou clique fora dos itens de categoria
            int rows = Math.min(6, (categories.size() / 9) + 2);
            int backButtonSlot = rows * 9 - 5;

            if (slot == backButtonSlot) {
                if (fullProcessState != null && fullProcessState.startsWith("edit_cat:")) {
                    String warpId = fullProcessState.substring(9);
                    ShopWarp warp = plugin.getShopWarpManager().getWarp(warpId);
                    if (warp != null) {
                        openWarpManagementGUI(player, warp);
                    } else {
                        openMyWarpsGUI(player);
                    }
                    // Não limpa o processo aqui, pois o jogador pode querer voltar e escolher outra
                } else { // Veio da criação
                    openCreateWarpGUI(player);
                    // Não limpa o processo aqui, pois o jogador pode querer voltar e escolher outra
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        currentPage.remove(playerUUID);
        slotWarpMap.remove(playerUUID);
        playerPages.remove(playerUUID);
        categoryFilter.remove(playerUUID);
        clearCreationProcess(playerUUID);
    }

    /**
     * Define o nome pendente para o processo de criação de warp de um jogador.
     * 
     * @param playerId O UUID do jogador.
     * @param name     O nome da warp pendente.
     */
    public void setPendingWarpName(UUID playerId, String name) {
        this.pendingWarpName.put(playerId, name);
    }

    /**
     * Define a descrição pendente para o processo de criação de warp de um jogador.
     * 
     * @param playerId    O UUID do jogador.
     * @param description A descrição da warp pendente.
     */
    public void setPendingWarpDescription(UUID playerId, String description) {
        this.pendingWarpDescription.put(playerId, description);
    }

    /**
     * Define o estado atual do processo de criação/edição para um jogador.
     * 
     * @param playerId O UUID do jogador.
     * @param state    A string que representa o estado (ex: "name", "description",
     *                 "category:Farming").
     */
    public void setCreationProcessState(UUID playerId, String state) {
        this.creationProcess.put(playerId, state);
    }

    /**
     * Define a página/GUI atual que o jogador está visualizando.
     * 
     * @param playerId O UUID do jogador.
     * @param pageName O nome da página/GUI (ex: "main", "claim_blocks_gui").
     */
    public void setCurrentPageName(UUID playerId, String pageName) {
        this.currentPage.put(playerId, pageName);
    }

    public boolean isInCreationProcess(UUID playerId) {
        return creationProcess.containsKey(playerId);
    }

    public String getCurrentPage(UUID playerId) {
        return currentPage.getOrDefault(playerId, "");
    }

    public Map<Integer, String> getSlotWarpMap(UUID playerId) {
        // Retorna mapa vazio se não existir para evitar NPE, mas não imutável para
        // poder ser usado
        return slotWarpMap.getOrDefault(playerId, Collections.emptyMap());
    }
}