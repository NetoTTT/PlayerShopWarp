package PlayerShopWarp.playerShopWarp.managers;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import PlayerShopWarp.playerShopWarp.models.ShopWarp;
import PlayerShopWarp.playerShopWarp.utils.MessageUtils;
import PlayerShopWarp.playerShopWarp.utils.SafetyChecker;
import net.milkbowl.vault.economy.Economy; // Import Vault Economy
import net.milkbowl.vault.economy.EconomyResponse; // Import Vault EconomyResponse
import org.bukkit.Bukkit;
import org.bukkit.Location; // <<<--- ADICIONADO IMPORT
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ShopWarpManager {

    private final PlayerShopWarp plugin;
    private final Map<UUID, List<ShopWarp>> playerWarps;
    private final Map<String, ShopWarp> allWarps;
    private final Map<UUID, Long> lastAnnouncement;
    private final Map<UUID, Long> lastTeleport; // Adicionado para consistência
    private final SafetyChecker safetyChecker;

    public ShopWarpManager(PlayerShopWarp plugin) {
        this.plugin = plugin;
        this.playerWarps = new ConcurrentHashMap<>();
        this.allWarps = new ConcurrentHashMap<>();
        this.lastAnnouncement = new ConcurrentHashMap<>();
        this.lastTeleport = new ConcurrentHashMap<>(); // Inicializado
        this.safetyChecker = new SafetyChecker(plugin);

        loadWarps();
    }

    private void loadWarps() {
        playerWarps.clear(); // Limpa antes de carregar para evitar duplicatas no reload
        allWarps.clear();
        ConfigurationSection warpsSection = plugin.getConfigManager().getWarpsConfig().getConfigurationSection("warps");
        if (warpsSection == null) {
            plugin.getLogger().info("Nenhuma seção 'warps' encontrada em warps.yml.");
            return;
        }


        int loadedCount = 0;
        for (String warpId : warpsSection.getKeys(false)) {
            ConfigurationSection warpSection = warpsSection.getConfigurationSection(warpId);
            if (warpSection == null) continue;

            try {
                String name = warpSection.getString("name");
                String description = warpSection.getString("description", "");
                UUID ownerId = UUID.fromString(warpSection.getString("owner"));
                String ownerName = warpSection.getString("ownerName");
                // Usa null como padrão se categoria não existir ou estiver vazia, ou usa "Outros" se preferir
                String category = warpSection.getString("category");

                // Carrega a localização
                String worldName = warpSection.getString("location.world");
                if (worldName == null || Bukkit.getWorld(worldName) == null) {
                     plugin.getLogger().warning("Mundo inválido ou não carregado para a warp '" + name + "' (ID: " + warpId + "). Pulando warp.");
                     continue; // Pula esta warp se o mundo não existe
                }
                double x = warpSection.getDouble("location.x");
                double y = warpSection.getDouble("location.y");
                double z = warpSection.getDouble("location.z");
                float yaw = (float) warpSection.getDouble("location.yaw");
                float pitch = (float) warpSection.getDouble("location.pitch");

                Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);

                ShopWarp warp = new ShopWarp(warpId, name, description, ownerId, ownerName, location, category);

                // Adiciona aos mapas
                allWarps.put(warpId, warp);
                playerWarps.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(warp);
                loadedCount++;

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Erro ao carregar warp com ID '" + warpId + "' (UUID inválido?): " + e.getMessage());
            } catch (Exception e) {
                 plugin.getLogger().severe("Erro inesperado ao carregar warp com ID '" + warpId + "': " + e.getMessage());
                 e.printStackTrace();
            }
        }

        plugin.getLogger().info("Carregadas " + loadedCount + " warps de lojas válidas.");
    }

    public void saveAllWarps() {
        plugin.getConfigManager().getWarpsConfig().set("warps", null); // Limpa antes de salvar
        for (ShopWarp warp : allWarps.values()) {
            saveWarp(warp);
        }
        plugin.getConfigManager().saveWarpsConfig();
         plugin.getLogger().info("Salvas " + allWarps.size() + " warps de lojas.");
    }

    private void saveWarp(ShopWarp warp) {
        String path = "warps." + warp.getId();
        plugin.getConfigManager().getWarpsConfig().set(path + ".name", warp.getName());
        plugin.getConfigManager().getWarpsConfig().set(path + ".description", warp.getDescription());
        plugin.getConfigManager().getWarpsConfig().set(path + ".owner", warp.getOwnerId().toString());
        plugin.getConfigManager().getWarpsConfig().set(path + ".ownerName", warp.getOwnerName());
        // Salva categoria apenas se não for nula ou vazia
        if (warp.getCategory() != null && !warp.getCategory().isEmpty()) {
            plugin.getConfigManager().getWarpsConfig().set(path + ".category", warp.getCategory());
        } else {
             plugin.getConfigManager().getWarpsConfig().set(path + ".category", null); // Remove se for nula/vazia
        }


        Location loc = warp.getLocation();
        // Garante que o mundo não é nulo antes de salvar
        if (loc != null && loc.getWorld() != null) {
            plugin.getConfigManager().getWarpsConfig().set(path + ".location.world", loc.getWorld().getName());
            plugin.getConfigManager().getWarpsConfig().set(path + ".location.x", loc.getX());
            plugin.getConfigManager().getWarpsConfig().set(path + ".location.y", loc.getY());
            plugin.getConfigManager().getWarpsConfig().set(path + ".location.z", loc.getZ());
            plugin.getConfigManager().getWarpsConfig().set(path + ".location.yaw", loc.getYaw());
            plugin.getConfigManager().getWarpsConfig().set(path + ".location.pitch", loc.getPitch());
        } else {
             plugin.getLogger().warning("Tentativa de salvar warp '" + warp.getName() + "' com localização inválida (mundo nulo?). A localização não será salva.");
        }
    }

    // --- MÉTODO CREATEWARP MODIFICADO ---
    public boolean createWarp(Player player, String name, String description, String category) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation(); // Pega localização atual

        // 1. Verifica Limite de Warps
        int maxWarps = getPlayerMaxWarps(player);
        List<ShopWarp> existingWarps = playerWarps.getOrDefault(playerId, Collections.emptyList());
        if (existingWarps.size() >= maxWarps) {
            MessageUtils.sendMessage(player, "messages.max-warps-reached", new String[][]{{"{max}", String.valueOf(maxWarps)}});
            return false;
        }

        // 2. Verifica Nome Duplicado
        if (isWarpNameTaken(name)) {
            MessageUtils.sendMessage(player, "messages.warp-name-taken");
            return false;
        }

        // 3. Verifica Local Seguro
        if (!safetyChecker.isSafeLocation(player, location, playerId)) {
            // SafetyChecker já envia a mensagem de local inseguro
            return false;
        }

         // 4. Valida Categoria (se categorias estiverem habilitadas)
         if (plugin.getConfigManager().areCategoriesEnabled()) {
             if (category == null || category.isEmpty()) {
                  MessageUtils.sendMessage(player, "&cVocê precisa selecionar uma categoria para criar a loja.");
                  return false; // Categoria é obrigatória se sistema estiver ativo
             }
             if (!plugin.getConfigManager().getCategories().contains(category)) {
                 MessageUtils.sendMessage(player, "messages.invalid-category");
                 return false;
             }
         } else {
              category = null; // Garante que categoria seja null se o sistema estiver desabilitado
         }


        // 5. Verifica e Debita Custo (Já feito na GUI, mas podemos re-verificar por segurança)
        Economy econ = plugin.getEconomy();
        double cost = plugin.getConfigManager().getWarpCreationCost();
        boolean costDebitedByManager = false; // Flag para saber se precisamos devolver

        if (econ != null && cost > 0) {
            if (!econ.has(player, cost)) {
                MessageUtils.sendMessage(player, "messages.not-enough-money", new String[][]{{"{cost}", String.format("%.2f", cost)}});
                return false;
            }
            // Tenta debitar aqui por segurança, caso a GUI falhe ou seja bypassada
            EconomyResponse response = econ.withdrawPlayer(player, cost);
             if (!response.transactionSuccess()) {
                 MessageUtils.sendMessage(player, "&cErro ao debitar custo (Manager): " + response.errorMessage);
                 return false;
             }
             costDebitedByManager = true; // Marcamos que debitamos aqui
             // Não envia mensagem de dinheiro retirado aqui, pois a GUI já deve ter enviado
        }

        // 6. Cria o Objeto Warp
        String warpId = UUID.randomUUID().toString();
        // Usa a categoria validada (pode ser null se categorias desabilitadas)
        ShopWarp warp = new ShopWarp(warpId, name, description, playerId, player.getName(), location, category);

        // 7. Adiciona aos Mapas
        allWarps.put(warpId, warp);
        playerWarps.computeIfAbsent(playerId, k -> new ArrayList<>()).add(warp);

        // 8. Salva no Arquivo
        try {
            saveWarp(warp);
            plugin.getConfigManager().saveWarpsConfig(); // Salva o arquivo inteiro após adicionar
        } catch (Exception e) {
             plugin.getLogger().severe("Falha ao salvar a nova warp '" + name + "': " + e.getMessage());
             e.printStackTrace();
             // Desfaz as operações se salvar falhar
             allWarps.remove(warpId);
             List<ShopWarp> pWarps = playerWarps.get(playerId);
             if (pWarps != null) pWarps.remove(warp);
             // Devolve o dinheiro se debitamos
             if (costDebitedByManager && econ != null) {
                 econ.depositPlayer(player, cost);
                 MessageUtils.sendMessage(player, "&cErro ao salvar a warp. O custo foi devolvido.");
             } else {
                 MessageUtils.sendMessage(player, "&cOcorreu um erro crítico ao salvar a warp. Contate um admin.");
             }
             return false;
        }

        // 9. Envia Mensagem de Sucesso
        MessageUtils.sendMessage(player, "messages.warp-created", new String[][]{{"{name}", name}});
        return true;
    }
    // --- FIM DO MÉTODO CREATEWARP MODIFICADO ---


    public boolean updateWarpDescription(Player player, String warpId, String newDescription) {
        ShopWarp warp = allWarps.get(warpId);

        if (warp == null) {
            MessageUtils.sendMessage(player, "messages.warp-not-found");
            return false;
        }

        if (!warp.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("playershopwarp.admin.manage")) { // Permissão mais específica
            MessageUtils.sendMessage(player, "messages.not-owner");
            return false;
        }

        warp.setDescription(newDescription);
        saveWarp(warp);
        plugin.getConfigManager().saveWarpsConfig();
        MessageUtils.sendMessage(player, "messages.description-updated", new String[][]{{"{name}", warp.getName()}});
        return true;
    }


    public boolean updateWarpCategory(Player player, String warpId, String category) {
        // Só executa se categorias estiverem habilitadas
         if (!plugin.getConfigManager().areCategoriesEnabled()) {
             MessageUtils.sendMessage(player, "&cO sistema de categorias está desabilitado.");
             return false;
         }

        ShopWarp warp = allWarps.get(warpId);
        if (warp == null) {
            MessageUtils.sendMessage(player, "messages.warp-not-found");
            return false;
        }

        if (!warp.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("playershopwarp.admin.manage")) {
            MessageUtils.sendMessage(player, "messages.not-owner");
            return false;
        }

        // Permite setar categoria para null ou vazio para "Nenhuma"? Ou força uma categoria válida?
        // Vamos forçar uma categoria válida da lista por enquanto.
        if (category == null || category.isEmpty() || !plugin.getConfigManager().getCategories().contains(category)) {
            MessageUtils.sendMessage(player, "messages.invalid-category");
            return false;
        }

        warp.setCategory(category);
        saveWarp(warp);
        plugin.getConfigManager().saveWarpsConfig();
        MessageUtils.sendMessage(player, "messages.category-updated", new String[][]{{"{name}", warp.getName()}, {"{category}", category}});
        return true;
    }

    // --- Métodos de Cooldown (isPlayerInTeleportCooldown, getRemainingTeleportCooldown, registerTeleport) ---
     public boolean isPlayerInTeleportCooldown(Player player) {
        if (player.hasPermission("playershopwarp.nocooldown.teleport")) { // Permissão específica
            return false;
        }
        long now = System.currentTimeMillis();
        long lastTime = lastTeleport.getOrDefault(player.getUniqueId(), 0L);
        int cooldownSeconds = plugin.getConfigManager().getTeleportCooldown();
        if (cooldownSeconds <= 0) return false; // Sem cooldown se for 0 ou negativo
        return (now - lastTime) < (cooldownSeconds * 1000L);
    }

    public int getRemainingTeleportCooldown(Player player) {
        if (player.hasPermission("playershopwarp.nocooldown.teleport")) return 0;

        long now = System.currentTimeMillis();
        long lastTime = lastTeleport.getOrDefault(player.getUniqueId(), 0L);
        int cooldownSeconds = plugin.getConfigManager().getTeleportCooldown();
         if (cooldownSeconds <= 0) return 0;

        long millisRemaining = (cooldownSeconds * 1000L) - (now - lastTime);
        return Math.max(0, (int) Math.ceil(millisRemaining / 1000.0)); // Arredonda pra cima
    }

    public void registerTeleport(Player player) {
         if (plugin.getConfigManager().getTeleportCooldown() > 0 && !player.hasPermission("playershopwarp.nocooldown.teleport")) {
            lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
         }
    }
    // --- Fim Métodos de Cooldown ---


    public List<ShopWarp> getWarpsByCategory(String category) {
        // Retorna todas se categoria for null ou vazia
        if (category == null || category.isEmpty() || category.equalsIgnoreCase("Todas")) {
            return getAllWarps();
        }
        // Filtra por categoria exata (ignorando case)
        return allWarps.values().stream()
                .filter(warp -> category.equalsIgnoreCase(warp.getCategory()))
                .sorted(Comparator.comparing(ShopWarp::getName, String.CASE_INSENSITIVE_ORDER)) // Ordena alfabeticamente
                .collect(Collectors.toList());
    }


    public boolean deleteWarp(Player player, String warpId) {
        ShopWarp warp = allWarps.get(warpId);
        if (warp == null) {
            MessageUtils.sendMessage(player, "messages.warp-not-found");
            return false;
        }

        // Admin pode deletar qualquer uma, dono só a própria
        if (!warp.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("playershopwarp.admin.delete")) { // Permissão mais específica
            MessageUtils.sendMessage(player, "messages.not-owner");
            return false;
        }

        // Remove dos mapas
        allWarps.remove(warpId);
        List<ShopWarp> playerWarpList = playerWarps.get(warp.getOwnerId());
        if (playerWarpList != null) {
            playerWarpList.removeIf(w -> w.getId().equals(warpId));
            // Se a lista ficar vazia, remove a entrada do mapa de playerWarps
            if (playerWarpList.isEmpty()) {
                playerWarps.remove(warp.getOwnerId());
            }
        }

        // Remove do arquivo
        plugin.getConfigManager().getWarpsConfig().set("warps." + warpId, null);
        plugin.getConfigManager().saveWarpsConfig(); // Salva imediatamente

        MessageUtils.sendMessage(player, "messages.warp-deleted", new String[][]{{"{name}", warp.getName()}});
        return true;
    }


    public boolean teleportToWarp(Player player, String warpId) {
        ShopWarp warp = allWarps.get(warpId);
        if (warp == null) {
            MessageUtils.sendMessage(player, "messages.warp-not-found");
            return false;
        }

        // 1. Verifica Cooldown
        if (isPlayerInTeleportCooldown(player)) {
            int remainingTime = getRemainingTeleportCooldown(player);
            MessageUtils.sendMessage(player, "messages.teleport-cooldown", new String[][]{{"{time}", String.valueOf(remainingTime)}});
            return false;
        }

        // 2. Verifica Local Seguro
        if (!safetyChecker.isSafeLocation(player, warp.getLocation(), warp.getOwnerId())) {
            // SafetyChecker já envia a mensagem
            if (player.hasPermission("playershopwarp.admin.bypass.safety")) { // Permissão específica para bypass
                MessageUtils.sendMessage(player, "messages.unsafe-warp-admin-override");
            }
            return false;
        }

        // 3. Verifica e Debita Custo
        Economy econ = plugin.getEconomy();
        double cost = plugin.getConfigManager().getWarpTeleportCost();
        // Não cobra se for o dono ou se tiver permissão de bypass de custo
        if (econ != null && cost > 0 && !player.getUniqueId().equals(warp.getOwnerId()) && !player.hasPermission("playershopwarp.nocost.teleport")) {
            if (!econ.has(player, cost)) {
                MessageUtils.sendMessage(player, "messages.not-enough-money-teleport", new String[][]{{"{cost}", String.format("%.2f", cost)}});
                return false;
            }
            EconomyResponse response = econ.withdrawPlayer(player, cost);
             if (!response.transactionSuccess()) {
                 MessageUtils.sendMessage(player, "&cErro ao debitar taxa de teleporte: " + response.errorMessage);
                 return false; // Não teleporta se falhar ao cobrar
             }
            MessageUtils.sendMessage(player, "messages.money-taken", new String[][]{{"{amount}", String.format("%.2f", cost)}});
        }

        // 4. Teleporta
        if (player.teleport(warp.getLocation())) {
            // 5. Registra Cooldown (apenas se teleporte teve sucesso)
            registerTeleport(player);

            // 6. Mensagem de Sucesso
            MessageUtils.sendMessage(player, "messages.teleported", new String[][]{{"{name}", warp.getName()}});

            // 7. Título (se habilitado)
            if (plugin.getConfigManager().showTeleportTitle()) {
                sendTeleportTitle(player, warp);
            }
            return true;
        } else {
            MessageUtils.sendMessage(player, "&cO teleporte falhou por um motivo desconhecido.");
             // Devolve o dinheiro se foi cobrado e o teleporte falhou
             if (econ != null && cost > 0 && !player.getUniqueId().equals(warp.getOwnerId()) && !player.hasPermission("playershopwarp.nocost.teleport")) {
                  // Verifica se realmente debitamos antes de depositar
                  // (Como a cobrança acontece antes do teleport, assumimos que sim se chegou aqui)
                  econ.depositPlayer(player, cost);
                  MessageUtils.sendMessage(player, "&aA taxa de teleporte foi devolvida.");
             }
            return false;
        }
    }

    // Método auxiliar para enviar título
    private void sendTeleportTitle(Player player, ShopWarp warp) {
        String title = MessageUtils.colorize(plugin.getConfigManager().getMessagesConfig()
                .getString("messages.teleport-title", "&6Bem-vindo à loja"));
        String subtitle = MessageUtils.colorize(plugin.getConfigManager().getMessagesConfig()
                .getString("messages.teleport-subtitle", "&f{name}")
                .replace("{name}", warp.getName())
                .replace("{owner}", warp.getOwnerName())); // Adiciona {owner} se necessário

        player.sendTitle(
                title,
                subtitle,
                plugin.getConfigManager().getTitleFadeIn(),
                plugin.getConfigManager().getTitleStay(),
                plugin.getConfigManager().getTitleFadeOut()
        );
    }

    public boolean announceShop(Player player, String warpId, String message) {
        ShopWarp warp = allWarps.get(warpId);
        if (warp == null) {
            MessageUtils.sendMessage(player, "messages.warp-not-found");
            return false;
        }

        if (!warp.getOwnerId().equals(player.getUniqueId()) && !player.hasPermission("playershopwarp.admin.announce")) { // Permissão específica
            MessageUtils.sendMessage(player, "messages.not-owner");
            return false;
        }

        // Verifica cooldown de anúncio
        long now = System.currentTimeMillis();
        long lastTime = lastAnnouncement.getOrDefault(player.getUniqueId(), 0L);
        int cooldownSeconds = plugin.getConfigManager().getAnnouncementCooldown();
        if (cooldownSeconds > 0 && (now - lastTime) < (cooldownSeconds * 1000L) && !player.hasPermission("playershopwarp.nocooldown.announce")) { // Perm específica
            int remainingTime = Math.max(1, (int) Math.ceil(((cooldownSeconds * 1000L) - (now - lastTime)) / 1000.0));
            MessageUtils.sendMessage(player, "messages.announcement-cooldown", new String[][]{{"{time}", String.valueOf(remainingTime)}});
            return false;
        }

        // Formata e envia anúncio
        String announcementFormat = plugin.getConfigManager().getMessagesConfig().getString("messages.shop-announcement",
                "&8[&6Anúncio&8] &e{player} &7anuncia: &f{message} &7- &a/shopwarp tp {name}");

        String announcement = announcementFormat
                .replace("{player}", player.getName())
                .replace("{message}", message) // Considerar filtrar códigos de cor da mensagem do jogador?
                .replace("{name}", warp.getName());

        Bukkit.broadcastMessage(MessageUtils.colorize(announcement));

        // Atualiza cooldown se aplicável
        if (cooldownSeconds > 0 && !player.hasPermission("playershopwarp.nocooldown.announce")) {
            lastAnnouncement.put(player.getUniqueId(), now);
        }

        return true;
    }

    // --- Getters ---
    public List<ShopWarp> getPlayerWarps(UUID playerId) {
        return new ArrayList<>(playerWarps.getOrDefault(playerId, Collections.emptyList())); // Retorna cópia
    }

    public List<ShopWarp> getAllWarps() {
        return allWarps.values().stream()
                .sorted(Comparator.comparing(ShopWarp::getName, String.CASE_INSENSITIVE_ORDER)) // Ordena alfabeticamente
                .collect(Collectors.toList());
    }

     public ShopWarp getWarp(String warpId) {
        return allWarps.get(warpId);
    }

     public ShopWarp getWarpByName(String name) {
         // Busca ignorando case
         for (ShopWarp warp : allWarps.values()) {
             if (warp.getName().equalsIgnoreCase(name)) {
                 return warp;
             }
         }
         return null;
     }

      public ShopWarp getWarpByNameAndOwner(String name, UUID ownerId) {
         List<ShopWarp> pWarps = playerWarps.get(ownerId);
         if (pWarps != null) {
             for (ShopWarp warp : pWarps) {
                 if (warp.getName().equalsIgnoreCase(name)) {
                     return warp;
                 }
             }
         }
         return null;
     }

    public boolean isWarpNameTaken(String name) {
        return getWarpByName(name) != null;
    }

    public int getPlayerMaxWarps(Player player) {
        if (player.isOp()) { // OP pode ter limite infinito ou configurável
            // return Integer.MAX_VALUE; ou ler de config
        }
        // Itera de forma mais eficiente se as permissões forem muitas
         for (int i = 100; i >= 1; i--) { // Começa de um limite razoável
             if (player.hasPermission("playershopwarp.maxwarps." + i)) {
                 return i;
             }
         }
         // Permissão genérica para ilimitado
         if (player.hasPermission("playershopwarp.maxwarps.unlimited")) {
             return Integer.MAX_VALUE; // Ou um número muito grande
         }

        return plugin.getConfigManager().getConfig().getInt("settings.max-default-warps", 1);
    }
}