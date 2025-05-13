package PlayerShopWarp.playerShopWarp;

import PlayerShopWarp.playerShopWarp.commands.ShopWarpCommand;
import PlayerShopWarp.playerShopWarp.config.ConfigManager;
import PlayerShopWarp.playerShopWarp.gui.ShopWarpGUI;
import PlayerShopWarp.playerShopWarp.managers.ShopWarpManager;
import PlayerShopWarp.playerShopWarp.utils.MessageUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class PlayerShopWarp extends JavaPlugin {

    private static PlayerShopWarp instance;
    private ConfigManager configManager;
    private ShopWarpManager shopWarpManager;
    private ShopWarpGUI shopWarpGUI;
    private Economy economy;

    @Override
    public void onEnable() {
        // Salva a instância
        instance = this;

        // Inicializa o gerenciador de configuração
        configManager = new ConfigManager(this);

        // Extrai os arquivos de idioma do JAR para a pasta do plugin
        extractLanguageFiles();

        // Carrega as configurações
        this.configManager.setup();
        // Inicializa o gerenciador de warps
        shopWarpManager = new ShopWarpManager(this);

        // Inicializa a GUI
        shopWarpGUI = new ShopWarpGUI(this);

        // Registra os comandos
        getCommand("shopwarp").setExecutor(new ShopWarpCommand(this));

        // Configura o Vault se estiver disponível
        setupEconomy();

        // Mensagem de inicialização
        getLogger().info("PlayerShopWarp foi ativado com sucesso!");
    }

    /**
     * Extrai os arquivos de idioma do JAR para a pasta do plugin
     */
    /**
     * Extrai os arquivos de idioma do JAR para a pasta do plugin
     */
    private void extractLanguageFiles() {
        // Cria a pasta de idiomas
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        File langFolder = new File(getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdir();
        }

        // Extrai os arquivos de idioma padrão se não existirem
        File ptBrFile = new File(langFolder, "pt_BR.yml");
        if (!ptBrFile.exists()) {
            saveResource("lang/pt_BR.yml", false);
            getLogger().info("Arquivo de idioma pt_BR.yml extraído.");
        }

        File enFile = new File(langFolder, "en.yml");
        if (!enFile.exists()) {
            saveResource("lang/en.yml", false);
            getLogger().info("Arquivo de idioma en.yml extraído.");
        }

        getLogger().info("Verificação de arquivos de idioma concluída.");
    }

    @Override
    public void onDisable() {
        // Salva os dados antes de desligar
        if (shopWarpManager != null) {
            shopWarpManager.saveAllWarps();
        }

        // Mensagem de desligamento
        getLogger().info("PlayerShopWarp foi desativado.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault não encontrado! Funcionalidades econômicas desativadas.");
            return false;
        }

        getLogger().info("Vault encontrado, procurando por provedor de economia...");

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning(
                    "Nenhum provedor de economia encontrado! Você precisa instalar um plugin de economia compatível com o Vault, como EssentialsX.");
            return false;
        }

        economy = rsp.getProvider();
        getLogger().info("Economia configurada com sucesso usando o provedor: " + economy.getName());
        return true;
    }

    public static PlayerShopWarp getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ShopWarpManager getShopWarpManager() {
        return shopWarpManager;
    }

    public ShopWarpGUI getShopWarpGUI() {
        return shopWarpGUI;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public void reloadPlugin() {
        // Recarrega as configurações

        // Recarrega o idioma
        configManager.reloadAllConfigs();

        getLogger().info("Plugin recarregado com sucesso!");
    }
}