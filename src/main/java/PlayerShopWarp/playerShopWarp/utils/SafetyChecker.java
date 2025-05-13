package PlayerShopWarp.playerShopWarp.utils;

import PlayerShopWarp.playerShopWarp.PlayerShopWarp;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.DataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SafetyChecker {

    private final PlayerShopWarp plugin;
    private final boolean enabled;
    private final boolean checkSolidGround;
    private final int groundCheckDistance;
    private final boolean checkDangerousBlocks;
    private final int dangerousBlocksRadius;
    private final List<Material> dangerousBlocks;
    private final boolean checkGriefPrevention;
    private final boolean onlyOwnerClaims;

    public SafetyChecker(PlayerShopWarp plugin) {
        this.plugin = plugin;
        
        // Carrega configurações
        this.enabled = plugin.getConfigManager().getConfig().getBoolean("anti-trap.enabled", true);
        this.checkSolidGround = plugin.getConfigManager().getConfig().getBoolean("anti-trap.check-solid-ground", true);
        this.groundCheckDistance = plugin.getConfigManager().getConfig().getInt("anti-trap.ground-check-distance", 5);
        this.checkDangerousBlocks = plugin.getConfigManager().getConfig().getBoolean("anti-trap.check-dangerous-blocks", true);
        this.dangerousBlocksRadius = plugin.getConfigManager().getConfig().getInt("anti-trap.dangerous-blocks-radius", 2);
        this.checkGriefPrevention = plugin.getConfigManager().getConfig().getBoolean("anti-trap.check-grief-prevention", true);
        this.onlyOwnerClaims = plugin.getConfigManager().getConfig().getBoolean("anti-trap.only-owner-claims", true);
        
        // Carrega lista de blocos perigosos
        List<String> dangerousBlockNames = plugin.getConfigManager().getConfig().getStringList("anti-trap.dangerous-blocks");
        this.dangerousBlocks = new ArrayList<>();
        
        for (String blockName : dangerousBlockNames) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                dangerousBlocks.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Material inválido na configuração anti-trap: " + blockName);
            }
        }
    }

    /**
     * Verifica se um local é seguro para teleporte
     * 
     * @param player Jogador que será teleportado
     * @param location Local para verificar
     * @param ownerUUID UUID do dono da warp
     * @return true se o local for seguro, false caso contrário
     */
    public boolean isSafeLocation(Player player, Location location, UUID ownerUUID) {
        if (!enabled) {
            return true; // Se o sistema estiver desativado, considera seguro
        }
        
        // Verifica se há chão sólido
        if (checkSolidGround && !hasSolidGround(location)) {
            return false;
        }
        
        // Verifica blocos perigosos
        if (checkDangerousBlocks && hasDangerousBlocks(location)) {
            return false;
        }
        
        // Verifica GriefPrevention
        if (checkGriefPrevention && !isInValidClaim(location, ownerUUID)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Verifica se há chão sólido abaixo do local
     */
    private boolean hasSolidGround(Location location) {
        Location checkLoc = location.clone();
        
        for (int i = 0; i <= groundCheckDistance; i++) {
            checkLoc.subtract(0, 1, 0);
            Block block = checkLoc.getBlock();
            
            if (block.getType().isSolid() && !block.getType().name().contains("LEAVES")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Verifica se há blocos perigosos na área
     */
    private boolean hasDangerousBlocks(Location location) {
        for (int x = -dangerousBlocksRadius; x <= dangerousBlocksRadius; x++) {
            for (int y = -dangerousBlocksRadius; y <= dangerousBlocksRadius; y++) {
                for (int z = -dangerousBlocksRadius; z <= dangerousBlocksRadius; z++) {
                    Block block = location.getBlock().getRelative(x, y, z);
                    
                    if (dangerousBlocks.contains(block.getType())) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Verifica se o local está em uma claim válida do GriefPrevention
     */
    private boolean isInValidClaim(Location location, UUID ownerUUID) {
        Plugin gpPlugin = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");

        if (gpPlugin == null || !gpPlugin.isEnabled()) {
            // Se GP não estiver lá, o comportamento depende da configuração onlyOwnerClaims
            // Se onlyOwnerClaims=true, um local sem claim não é válido.
            // Se onlyOwnerClaims=false, um local sem claim É válido.
            plugin.getLogger().fine("GriefPrevention não encontrado ou desabilitado. Verificação de claim pulada. Resultado baseado em onlyOwnerClaims: " + !onlyOwnerClaims);
            return !onlyOwnerClaims;
        }

        try {
            // Usa a API do GriefPrevention diretamente
            GriefPrevention griefPrevention = GriefPrevention.instance;
            DataStore dataStore = griefPrevention.dataStore;

            // CORREÇÃO: Adiciona os parâmetros necessários para o método getClaimAt
            // ignoreHeight=true para verificar claims independente da altura
            // cachedClaim=null pois não temos uma claim em cache
            Claim claim = dataStore.getClaimAt(location, true, null);

            // Se não houver claim, retorna com base na configuração
            if (claim == null) {
                plugin.getLogger().fine("Nenhuma claim encontrada em " + location + ". Resultado baseado em onlyOwnerClaims: " + !onlyOwnerClaims);
                return !onlyOwnerClaims; // Se onlyOwnerClaims for true, retorna false (precisa estar numa claim)
                                         // Se onlyOwnerClaims for false, retorna true (não precisa estar numa claim)
            }

            // Verifica se o dono da warp é o dono da claim (se necessário)
            if (onlyOwnerClaims) {
                UUID claimOwnerUUID = claim.getOwnerID();

                boolean isOwnerMatch = claimOwnerUUID != null && claimOwnerUUID.equals(ownerUUID);
                plugin.getLogger().fine("Claim encontrada. Dono da Claim: " + claimOwnerUUID + ". Dono da Warp: " + ownerUUID + ". onlyOwnerClaims=true. Resultado: " + isOwnerMatch);
                return isOwnerMatch; // Retorna true APENAS se o dono da warp for o dono da claim
            }

            plugin.getLogger().fine("Claim encontrada. onlyOwnerClaims=false. Local considerado válido.");
            return true; // Se onlyOwnerClaims for false, e encontrou uma claim, é válido
        } catch (Exception e) {
            plugin.getLogger().warning("Erro ao verificar claims do GriefPrevention: " + e.getMessage());
            e.printStackTrace(); // Ajuda a debugar
            // Em caso de erro, retorna baseado na configuração para segurança
            plugin.getLogger().fine("Erro ao verificar claim. Resultado baseado em onlyOwnerClaims: " + !onlyOwnerClaims);
            return !onlyOwnerClaims;
        }
    }
}