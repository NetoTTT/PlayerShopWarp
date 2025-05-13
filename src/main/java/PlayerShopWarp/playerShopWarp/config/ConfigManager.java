package PlayerShopWarp.playerShopWarp.config;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class ConfigManager {

    private final PlayerShopWarp plugin;
    private FileConfiguration config;
    private FileConfiguration warpsConfig;
    private FileConfiguration messagesConfig;

    private File warpsFile;
    private File messagesFile; // Este será o arquivo de idioma carregado
    private String currentLanguageCode; // Ex: "pt_BR", "en"

    public ConfigManager(PlayerShopWarp plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        // 1. Salva o config.yml padrão do JAR se não existir na pasta do plugin
        plugin.saveDefaultConfig();

        // 2. Carrega o config.yml (da pasta do plugin ou o recém-salvo)
        config = plugin.getConfig();

        // 3. Define os valores padrão em memória e adiciona novas chaves ao config.yml existente
        setupDefaultConfigValues(); // Renomeado de setupDefaultConfig

        // 4. Carrega o arquivo de warps
        warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        if (!warpsFile.exists()) {
            try {
                if (warpsFile.createNewFile()) {
                    plugin.getLogger().info("Arquivo warps.yml criado.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível criar o arquivo warps.yml: " + e.getMessage());
            }
        }
        warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);

        // 5. Carrega o arquivo de idioma
        loadLanguageFile();
    }

    // Chamado após setup()
    public void loadAllConfigs() {
        // Carrega o config.yml (já deve ter sido tratado pelo setup e reloadConfig)
        config = plugin.getConfig();

        // Carrega o arquivo de warps (já deve existir)
        if (warpsFile == null) warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        if (!warpsFile.exists()) {
             try { warpsFile.createNewFile(); } catch (IOException e) { /* log */ }
        }
        warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);

        // Carrega o arquivo de idioma (já deve existir e currentLanguageCode estar definido)
        if (messagesFile == null || !messagesFile.exists()) {
            loadLanguageFile(); // Tenta recarregar/recriar se sumiu
        } else {
             messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        }
    }


    public void reloadAllConfigs() {
        plugin.reloadConfig(); // Recarrega config.yml da pasta do plugin
        config = plugin.getConfig();
        setupDefaultConfigValues(); // Garante que todos os defaults estão presentes em memória e no arquivo

        // Recarrega warps.yml
        if (warpsFile == null) warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);

        // Recarrega o arquivo de idioma
        loadLanguageFile(); // Isso irá reler o idioma da config e recarregar/atualizar o messages.yml

        plugin.getLogger().info("Configurações e idioma recarregados.");
    }

    private void setupDefaultConfigValues() {
        // Configurações gerais
        config.addDefault("settings.max-default-warps", 1);
        config.addDefault("settings.warp-creation-cost", 1000.0);
        config.addDefault("settings.warp-teleport-cost", 0.0);
        config.addDefault("settings.announcement-cooldown", 300);
        config.addDefault("settings.teleport-cooldown", 5);
        config.addDefault("settings.show-teleport-title", true);
        config.addDefault("settings.teleport-title-fade-in", 10);
        config.addDefault("settings.teleport-title-stay", 70);
        config.addDefault("settings.teleport-title-fade-out", 20);
        config.addDefault("settings.language", "pt_BR"); // << Idioma padrão ao criar o config.yml pela primeira vez

        // Anti-Trap
        config.addDefault("anti-trap.enabled", true);
        config.addDefault("anti-trap.check-solid-ground", true);
        config.addDefault("anti-trap.ground-check-distance", 5);
        config.addDefault("anti-trap.check-dangerous-blocks", true);
        config.addDefault("anti-trap.dangerous-blocks-radius", 2);
        config.addDefault("anti-trap.dangerous-blocks", Arrays.asList("LAVA", "TNT", "FIRE", "MAGMA_BLOCK", "CACTUS", "WITHER_ROSE", "CAMPFIRE", "SOUL_CAMPFIRE"));
        config.addDefault("anti-trap.check-grief-prevention", true);
        config.addDefault("anti-trap.only-owner-claims", true);

        // Scanner
        config.addDefault("scanner.radius", 8);

        // GUI
        config.addDefault("gui.title", "&8Lojas dos Jogadores");
        config.addDefault("gui.rows", 6);
        config.addDefault("gui.item-name-format", "&e{shop_name}");
        config.addDefault("gui.item-lore", Arrays.asList("&7Dono: &f{owner}", "&7Descrição: &f{description}", "", "&aClique para teleportar!"));
        config.addDefault("gui.fallback-item", "ENDER_PEARL"); // Para createWarpItem

        // Categories
        config.addDefault("categories.enabled", false);
        config.addDefault("categories.list", Arrays.asList("Ferramentas", "Comida", "Decoração", "Redstone", "Outros"));

        // ClaimBlocks
        config.addDefault("claimblocks.enabled", true);
        config.addDefault("claimblocks.buy_command", "buyclaimblocks {amount}");
        config.addDefault("claimblocks.sell_command", "sellclaimblocks {amount}");
        // Não adicionamos 'custom_buy_price_per_block' e 'custom_sell_price_per_block' aqui
        // porque eles devem vir da config do GriefPrevention.
        // As 'options' para claimblocks também são mais complexas para addDefault, idealmente viriam do config.yml padrão.

        config.options().copyDefaults(true); // Adiciona os defaults ao arquivo se não existirem
        plugin.saveConfig(); // Salva o config.yml com quaisquer defaults adicionados
    }

    private void loadLanguageFile() {
        String langCodeFromConfig = config.getString("settings.language", "pt_BR");
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Lista de idiomas a tentar, em ordem de preferência
        List<String> langTryOrder = new ArrayList<>(Arrays.asList(langCodeFromConfig, "pt_BR", "en"));
        // Remove duplicatas mantendo a ordem
        langTryOrder = langTryOrder.stream().distinct().collect(Collectors.toList());


        File chosenLangFile = null;
        String chosenLangCode = null;

        for (String langCode : langTryOrder) {
            File potentialFile = new File(langFolder, langCode + ".yml");
            String resourcePath = "lang/" + langCode + ".yml";

            if (potentialFile.exists()) {
                chosenLangFile = potentialFile;
                chosenLangCode = langCode;
                plugin.getLogger().info("Usando arquivo de idioma existente: " + chosenLangFile.getName());
                break;
            } else if (plugin.getResource(resourcePath) != null) {
                plugin.getLogger().info("Arquivo de idioma '" + langCode + ".yml' não encontrado na pasta, copiando do JAR...");
                plugin.saveResource(resourcePath, false); // Copia do JAR
                chosenLangFile = new File(langFolder, langCode + ".yml"); // Agora deve existir
                chosenLangCode = langCode;
                plugin.getLogger().info("Idioma '" + chosenLangCode + "' copiado e selecionado.");
                break;
            } else {
                plugin.getLogger().warning("Arquivo de idioma '" + langCode + ".yml' não encontrado na pasta nem no JAR.");
            }
        }

        // Se nenhum arquivo foi encontrado ou copiado
        if (chosenLangFile == null) {
            plugin.getLogger().severe("Nenhum arquivo de idioma válido pôde ser carregado ou copiado. Criando 'pt_BR.yml' padrão programaticamente...");
            chosenLangCode = "pt_BR"; // Fallback final
            chosenLangFile = new File(langFolder, chosenLangCode + ".yml");
            createDefaultMessagesProgrammatically(chosenLangFile, chosenLangCode); // Cria pt_BR como último recurso
        }

        this.messagesFile = chosenLangFile;
        this.currentLanguageCode = chosenLangCode;
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Arquivo de mensagens carregado: " + messagesFile.getName() + " (Idioma: " + currentLanguageCode + ")");

        updateMessagesFileFromJar(); // Tenta adicionar novas chaves do JAR ao arquivo existente
    }

    // Cria programaticamente se nenhum arquivo puder ser copiado do JAR
    private void createDefaultMessagesProgrammatically(File fileToCreate, String langCode) {
        YamlConfiguration messages = new YamlConfiguration();
        if ("en".equalsIgnoreCase(langCode)) {
            createEnglishMessages(messages);
        } else { // pt_BR ou qualquer outro fallback
            createPortugueseMessages(messages);
        }
        try {
            messages.save(fileToCreate);
            plugin.getLogger().info("Arquivo de idioma padrão '" + fileToCreate.getName() + "' criado programaticamente.");
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o arquivo de idioma padrão programático: " + e.getMessage());
        }
    }

    private void updateMessagesFileFromJar() {
        if (currentLanguageCode == null || messagesFile == null || !messagesFile.exists()) {
            plugin.getLogger().warning("Não foi possível atualizar o arquivo de idioma: idioma atual ou arquivo não definido.");
            return;
        }

        String resourcePath = "lang/" + currentLanguageCode + ".yml";
        InputStream jarLangStream = plugin.getResource(resourcePath);

        if (jarLangStream == null) {
            plugin.getLogger().warning("Arquivo de idioma '" + resourcePath + "' não encontrado no JAR. Não é possível verificar atualizações.");
            // Se não existe no JAR, verifica se o pt_BR existe para pelo menos ter algumas mensagens padrão
            if (!currentLanguageCode.equals("pt_BR")) {
                resourcePath = "lang/pt_BR.yml";
                jarLangStream = plugin.getResource(resourcePath);
                if (jarLangStream != null) {
                    plugin.getLogger().info("Tentando atualizar com base no pt_BR.yml do JAR como fallback.");
                } else {
                    plugin.getLogger().warning("pt_BR.yml também não encontrado no JAR para atualização de fallback.");
                    return;
                }
            } else {
                return;
            }
        }

        YamlConfiguration jarMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(jarLangStream, StandardCharsets.UTF_8));
        FileConfiguration diskMessages = YamlConfiguration.loadConfiguration(messagesFile); // Recarrega do disco para ter a versão mais recente
        boolean updated = false;

        for (String key : jarMessages.getKeys(true)) {
            if (!jarMessages.isConfigurationSection(key)) { // Só adiciona valores, não seções inteiras de uma vez
                if (!diskMessages.contains(key)) {
                    diskMessages.set(key, jarMessages.get(key));
                    updated = true;
                    plugin.getLogger().info("Adicionando nova chave de mensagem '" + key + "' ao arquivo " + messagesFile.getName());
                }
            }
        }

        if (updated) {
            try {
                diskMessages.save(messagesFile);
                this.messagesConfig = diskMessages; // Atualiza a config em memória
                plugin.getLogger().info("Arquivo de idioma " + messagesFile.getName() + " atualizado com novas chaves do JAR.");
            } catch (IOException e) {
                plugin.getLogger().severe("Erro ao salvar o arquivo de idioma atualizado " + messagesFile.getName() + ": " + e.getMessage());
            }
        }
    }


    private void createEnglishMessages(YamlConfiguration config) {
        config.set("messages.no-permission", "&cYou don't have permission to use this command.");
        config.set("messages.create-usage", "&cUsage: /shopwarp create <name> [description]");
        config.set("messages.delete-usage", "&cUsage: /shopwarp delete <name>");
        config.set("messages.teleport-usage", "&cUsage: /shopwarp tp <name>");
        config.set("messages.announce-usage", "&cUsage: /shopwarp announce <name> <message>");
        config.set("messages.create-prompt", "&eType the name and description of your shop in the format: &fname|description");
        config.set("messages.announce-prompt", "&eType the message to announce the shop &f{name}&e:");
        config.set("messages.max-warps-reached", "&cYou have reached the maximum number of warps allowed: {max}");
        config.set("messages.warp-name-taken", "&cA warp with this name already exists.");
        config.set("messages.not-enough-money", "&cYou don't have enough money. Cost: ${cost}");
        config.set("messages.not-enough-money-teleport", "&cYou don't have enough money to teleport. Cost: ${cost}");
        config.set("messages.money-taken", "&a${amount} has been withdrawn from your account.");
        config.set("messages.warp-created", "&aWarp &f{name} &acreated successfully!");
        config.set("messages.warp-deleted", "&aWarp &f{name} &adeleted successfully!");
        config.set("messages.warp-not-found", "&cWarp not found.");
        config.set("messages.not-owner", "&cYou are not the owner of this warp.");
        config.set("messages.teleported", "&aTeleported to shop &f{name}&a.");
        config.set("messages.teleport-cooldown", "&cYou need to wait {time} more seconds to teleport again.");
        config.set("messages.announcement-cooldown", "&cYou need to wait {time} more seconds to announce again.");
        config.set("messages.no-warps", "&cYou don't have any shop warps.");
        config.set("messages.config-reloaded", "&aConfigurations reloaded successfully!");
        config.set("messages.warp-list-header", "&eYour shop warps:");
        config.set("messages.shop-announcement", "&8[&6Announcement&8] &e{player} &7announces: &f{message} &7- &a/shopwarp tp {name}");
        config.set("messages.unsafe-location", "&cThis location is not safe to create a warp. Check if there is solid ground and no dangerous blocks nearby.");
        config.set("messages.unsafe-warp-location", "&cThis warp is not safe to teleport to. The owner needs to reposition it in a safe location.");
        config.set("messages.unsafe-warp-admin-override", "&eYou are an administrator and can use &a/shopwarp forcetp <name> &eto ignore the safety check.");
        config.set("messages.forcetp-usage", "&cUsage: /shopwarp forcetp <name>");
        config.set("messages.force-teleported", "&aYou were forcibly teleported to shop &f{name}&a. Be careful!");
        config.set("messages.scan-usage", "&cUsage: /shopwarp scan");
        config.set("messages.scan-in-progress", "&aScanning area... Please wait.");
        config.set("messages.teleport-title", "&6Welcome to the shop");
        config.set("messages.teleport-subtitle", "&f{name}");
        config.set("messages.create-warp-name-prompt", "&eEnter the name of your shop:");
        config.set("messages.create-warp-description-prompt", "&eEnter the description of your shop:");
        config.set("messages.description-updated", "&aDescription of shop &f{name} &aupdated successfully!");
        config.set("messages.category-updated", "&aCategory of shop &f{name} &aupdated to &f{category}&a!");
        config.set("messages.invalid-category", "&cThis category is not valid.");
        config.set("messages.place-sign", "&ePlease place a sign and write the name of your shop on the first line.");
        config.set("messages.sign-given", "&aYou received a sign to create your shop.");
        config.set("messages.warp-name-required", "&cYou need to set a name for the shop first!");
        config.set("messages.name-set-feedback", "&aShop name set to: &f{name}");
        config.set("messages.description-set-feedback", "&aShop description set!");
        
        // ClaimBlock GUI messages
        config.set("messages.claimblock-gui-title", "&8Buy/Sell Claim Blocks");
        config.set("messages.claimblock-predefined-buy-title", "&aBuy {amount} Blocks");
        config.set("messages.claimblock-predefined-sell-title", "&cSell {amount} Blocks");
        config.set("messages.claimblock-lore-amount", "&7Amount: &f{amount}");
        config.set("messages.claimblock-lore-cost", "&7Cost: {price_color}${price}");
        config.set("messages.claimblock-lore-receive", "&7You'll receive: {price_color}${price}");
        config.set("messages.claimblock-lore-click-buy", "&eClick to buy!");
        config.set("messages.claimblock-lore-click-sell", "&eClick to sell!");
        config.set("messages.claimblock-custom-buy-title", "&6Buy Custom Amount");
        config.set("messages.claimblock-custom-sell-title", "&6Sell Custom Amount");
        config.set("messages.claimblock-price-per-block", "&ePrice per block: &f${price}");
        config.set("messages.claimblock-value-per-block", "&eValue per block: &f${price}");
        config.set("messages.claimblock-back-button", "&cBack / Close");
        config.set("messages.claimblock-invalid-amount", "&cInvalid amount. Must be a number greater than zero.");
        config.set("messages.claimblock-prices-not-loaded", "&cClaim block prices could not be loaded. Please try again later.");
        config.set("messages.claimblock-confirm-buy", "&eDo you want to buy &f{amount}&e blocks for {price_color}${total_price}&e? (Type '&ayes&e' to confirm or '&cno&e' to cancel)");
        config.set("messages.claimblock-confirm-sell", "&eDo you want to sell &f{amount}&e blocks and receive {price_color}${total_price}&e? (Type '&ayes&e' to confirm or '&cno&e' to cancel)");
        config.set("messages.claimblock-not-enough-money-feedback", "&c(You don't seem to have enough money.)");
        config.set("messages.claimblock-not-a-number", "&c'{input}' is not a valid number. Please enter only numbers.");
        config.set("messages.claimblock-prompt-buy-amount", "&eEnter the amount of claim blocks you want to BUY:");
        config.set("messages.claimblock-prompt-sell-amount", "&eEnter the amount of claim blocks you want to SELL:");
        config.set("messages.claimblock-transaction-cancelled", "&cTransaction cancelled.");
        config.set("messages.claimblock-command-sending-buy", "&eSending command to buy {amount} blocks...");
        config.set("messages.claimblock-command-sending-sell", "&eSending command to sell {amount} blocks...");
    }

    private void createPortugueseMessages(YamlConfiguration defaultMessages) {
        defaultMessages.set("messages.no-permission", "&cVocê não tem permissão para usar este comando.");
        defaultMessages.set("messages.create-usage", "&cUso: /shopwarp create <nome> [descrição]");
        defaultMessages.set("messages.delete-usage", "&cUso: /shopwarp delete <nome>");
        defaultMessages.set("messages.teleport-usage", "&cUso: /shopwarp tp <nome>");
        defaultMessages.set("messages.announce-usage", "&cUso: /shopwarp announce <nome> <mensagem>");
        defaultMessages.set("messages.create-prompt", "&eDigite o nome e descrição da sua loja no formato: &fnome|descrição");
        defaultMessages.set("messages.announce-prompt", "&eDigite a mensagem para anunciar a loja &f{name}&e:");
        defaultMessages.set("messages.max-warps-reached", "&cVocê atingiu o número máximo de warps permitido: {max}");
        defaultMessages.set("messages.warp-name-taken", "&cJá existe uma warp com este nome.");
        defaultMessages.set("messages.not-enough-money", "&cVocê não tem dinheiro suficiente. Custo: ${cost}");
        defaultMessages.set("messages.not-enough-money-teleport", "&cVocê não tem dinheiro suficiente para teleportar. Custo: ${cost}");
        defaultMessages.set("messages.money-taken", "&aR${amount} foram retirados da sua conta.");
        defaultMessages.set("messages.warp-created", "&aWarp &f{name} &acriada com sucesso!");
        defaultMessages.set("messages.warp-deleted", "&aWarp &f{name} &adeletada com sucesso!");
        defaultMessages.set("messages.warp-not-found", "&cWarp não encontrada.");
        defaultMessages.set("messages.not-owner", "&cVocê não é o dono desta warp.");
        defaultMessages.set("messages.teleported", "&aTeleportado para a loja &f{name}&a.");
        defaultMessages.set("messages.teleport-cooldown", "&cVocê precisa esperar mais {time} segundos para teleportar novamente.");
        defaultMessages.set("messages.announcement-cooldown", "&cVocê precisa esperar mais {time} segundos para anunciar novamente.");
        defaultMessages.set("messages.no-warps", "&cVocê não possui warps de loja.");
        defaultMessages.set("messages.config-reloaded", "&aConfigurações recarregadas com sucesso!");
        defaultMessages.set("messages.warp-list-header", "&eSuas warps de loja:");
        defaultMessages.set("messages.shop-announcement", "&8[&6Anúncio&8] &e{player} &7anuncia: &f{message} &7- &a/shopwarp tp {name}");
        defaultMessages.set("messages.unsafe-location", "&cEste local não é seguro para criar uma warp. Verifique se há chão sólido e se não há blocos perigosos por perto.");
        defaultMessages.set("messages.unsafe-warp-location", "&cEsta warp não é segura para teleportar. O dono precisa reposicioná-la em um local seguro.");
        defaultMessages.set("messages.unsafe-warp-admin-override", "&eVocê é um administrador e pode usar &a/shopwarp forcetp <nome> &epara ignorar a verificação de segurança.");
        defaultMessages.set("messages.forcetp-usage", "&cUso: /shopwarp forcetp <nome>");
        defaultMessages.set("messages.force-teleported", "&aVocê foi forçadamente teleportado para a loja &f{name}&a. Tenha cuidado!");
        defaultMessages.set("messages.scan-usage", "&cUso: /shopwarp scan");
        defaultMessages.set("messages.scan-in-progress", "&aEscaneando área... Por favor, aguarde.");
        defaultMessages.set("messages.teleport-title", "&6Bem-vindo à loja");
        defaultMessages.set("messages.teleport-subtitle", "&f{name}");
        defaultMessages.set("messages.create-warp-name-prompt", "&eDigite o nome da sua loja:");
        defaultMessages.set("messages.create-warp-description-prompt", "&eDigite a descrição da sua loja:");
        defaultMessages.set("messages.description-updated", "&aDescrição da loja &f{name} &aatualizada com sucesso!");
        defaultMessages.set("messages.category-updated", "&aCategoria da loja &f{name} &aatualizada para &f{category}&a!");
        defaultMessages.set("messages.invalid-category", "&cEsta categoria não é válida.");
        defaultMessages.set("messages.place-sign", "&eColoque uma placa e escreva o nome da sua loja na primeira linha.");
        defaultMessages.set("messages.sign-given", "&aVocê recebeu uma placa para criar sua loja.");
        defaultMessages.set("messages.warp-name-required", "&cVocê precisa definir um nome para a loja primeiro!");
        defaultMessages.set("messages.name-set-feedback", "&aNome da loja definido como: &f{name}");
        defaultMessages.set("messages.description-set-feedback", "&aDescrição da loja definida!");
        
        // ClaimBlock GUI messages
        defaultMessages.set("messages.claimblock-gui-title", "&8Comprar/Vender Blocos de Claim");
        defaultMessages.set("messages.claimblock-predefined-buy-title", "&aComprar {amount} Blocos");
        defaultMessages.set("messages.claimblock-predefined-sell-title", "&cVender {amount} Blocos");
        defaultMessages.set("messages.claimblock-lore-amount", "&7Quantidade: &f{amount}");
        defaultMessages.set("messages.claimblock-lore-cost", "&7Custo: {price_color}${price}");
        defaultMessages.set("messages.claimblock-lore-receive", "&7Receberá: {price_color}${price}");
        defaultMessages.set("messages.claimblock-lore-click-buy", "&eClique para comprar!");
        defaultMessages.set("messages.claimblock-lore-click-sell", "&eClique para vender!");
        defaultMessages.set("messages.claimblock-custom-buy-title", "&6Comprar Quantidade Customizada");
        defaultMessages.set("messages.claimblock-custom-sell-title", "&6Vender Quantidade Customizada");
        defaultMessages.set("messages.claimblock-price-per-block", "&ePreço por bloco: &f${price}");
        defaultMessages.set("messages.claimblock-value-per-block", "&eValor por bloco: &f${price}");
        defaultMessages.set("messages.claimblock-back-button", "&cVoltar / Fechar");
        defaultMessages.set("messages.claimblock-invalid-amount", "&cQuantidade inválida. Deve ser um número maior que zero.");
        defaultMessages.set("messages.claimblock-prices-not-loaded", "&cOs preços de blocos de claim não puderam ser carregados. Tente novamente mais tarde.");
        defaultMessages.set("messages.claimblock-confirm-buy", "&eVocê deseja comprar &f{amount}&e blocos por {price_color}${total_price}&e? (Digite '&asim&e' para confirmar ou '&cnão&e' para cancelar)");
        defaultMessages.set("messages.claimblock-confirm-sell", "&eVocê deseja vender &f{amount}&e blocos e receber {price_color}${total_price}&e? (Digite '&asim&e' para confirmar ou '&cnão&e' para cancelar)");
        defaultMessages.set("messages.claimblock-not-enough-money-feedback", "&c(Você não parece ter dinheiro suficiente.)");
        defaultMessages.set("messages.claimblock-not-a-number", "&c'{input}' não é um número válido. Digite apenas números.");
        defaultMessages.set("messages.claimblock-prompt-buy-amount", "&eDigite a quantidade de blocos de claim que deseja COMPRAR:");
        defaultMessages.set("messages.claimblock-prompt-sell-amount", "&eDigite a quantidade de blocos de claim que deseja VENDER:");
        defaultMessages.set("messages.claimblock-transaction-cancelled", "&cTransação cancelada.");
        defaultMessages.set("messages.claimblock-command-sending-buy", "&eEnviando comando para comprar {amount} blocos...");
        defaultMessages.set("messages.claimblock-command-sending-sell", "&eEnviando comando para vender {amount} blocos...");
    }

    private void setupDefaultConfig() {
        // Configurações gerais
        config.addDefault("settings.max-default-warps", 1);
        config.addDefault("settings.warp-creation-cost", 1000.0);
        config.addDefault("settings.warp-teleport-cost", 0.0);
        config.addDefault("settings.announcement-cooldown", 300); // em segundos
        config.addDefault("settings.teleport-cooldown", 5); // em segundos
        config.addDefault("settings.show-teleport-title", true);
        config.addDefault("settings.teleport-title-fade-in", 10); // em ticks
        config.addDefault("settings.teleport-title-stay", 70); // em ticks
        config.addDefault("settings.teleport-title-fade-out", 20); // em ticks
        config.addDefault("settings.language", "pt_BR"); // Idioma padrão

        // Configurações da GUI
        config.addDefault("gui.title", "&8Lojas dos Jogadores");
        config.addDefault("gui.rows", 6);
        config.addDefault("gui.item-name-format", "&e{shop_name}");
        config.addDefault("gui.item-lore",
                java.util.Arrays.asList(
                        "&7Dono: &f{owner}",
                        "&7Descrição: &f{description}",
                        "",
                        "&aClique para teleportar!"));

        // Configurações de categorias
        config.addDefault("categories.enabled", false);
        config.addDefault("categories.list",
                java.util.Arrays.asList("Ferramentas", "Comida", "Decoração", "Redstone", "Outros"));

        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public void saveWarpsConfig() {
        try {
            warpsConfig.save(warpsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar o arquivo warps.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getWarpsConfig() {
        return warpsConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public int getMaxWarps(String permission) {
        if (permission == null || permission.isEmpty()) {
            return config.getInt("settings.max-default-warps", 1);
        }

        try {
            return Integer.parseInt(permission.split("\\.")[3]);
        } catch (Exception e) {
            return config.getInt("settings.max-default-warps", 1);
        }
    }

    public double getWarpCreationCost() {
        return config.getDouble("settings.warp-creation-cost", 1000.0);
    }

    public double getWarpTeleportCost() {
        return config.getDouble("settings.warp-teleport-cost", 0.0);
    }

    public int getAnnouncementCooldown() {
        return config.getInt("settings.announcement-cooldown", 300);
    }

    public int getTeleportCooldown() {
        return config.getInt("settings.teleport-cooldown", 5);
    }

    public boolean showTeleportTitle() {
        return config.getBoolean("settings.show-teleport-title", true);
    }

    public int getTitleFadeIn() {
        return config.getInt("settings.teleport-title-fade-in", 10);
    }

    public int getTitleStay() {
        return config.getInt("settings.teleport-title-stay", 70);
    }

    public int getTitleFadeOut() {
        return config.getInt("settings.teleport-title-fade-out", 20);
    }

    public boolean areCategoriesEnabled() {
        return config.getBoolean("categories.enabled", false);
    }

    public List<String> getCategories() {
        return config.getStringList("categories.list");
    }
}