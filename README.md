# PlayerShopWarp

PlayerShopWarp é um plugin para servidores Minecraft que permite aos jogadores criarem e gerenciarem warps (teleportes) para suas lojas, facilitando o comércio e a navegação no servidor.

## Características Principais

### Sistema de Warps de Loja
- **Criação de Warps**: Jogadores podem criar warps para suas lojas com nome e descrição personalizados
- **Teleporte**: Qualquer jogador pode se teleportar para lojas públicas
- **Gerenciamento**: Donos podem excluir, editar descrições e categorizar suas warps
- **Limites Configuráveis**: Defina quantas warps cada jogador pode ter (com suporte a permissões)

### Interface Gráfica
- **GUI Intuitiva**: Interface gráfica para visualizar e acessar todas as lojas disponíveis
- **Categorização**: Organização opcional de lojas por categorias (Ferramentas, Comida, Decoração, etc.)
- **Personalização**: Títulos, descrições e aparência totalmente configuráveis

### Sistema de Economia
- **Integração com Vault**: Cobrar pela criação de warps e teleportes
- **Preços Configuráveis**: Defina custos para criação de warps e teleportes

### Sistema Anti-Trap
- **Proteção contra Armadilhas**: Verificação de segurança para evitar teleportes para armadilhas
- **Verificação de Terreno**: Garante que há chão sólido no destino
- **Detecção de Blocos Perigosos**: Evita teleportes para locais com lava, TNT, etc.
- **Integração com GriefPrevention**: Opção para permitir teleportes apenas para claims do dono da loja

### Anúncios de Loja
- **Sistema de Anúncios**: Jogadores podem anunciar suas lojas no chat global
- **Cooldowns**: Evita spam com tempo de espera configurável entre anúncios

### Recursos Adicionais
- **Sistema de Blocos de Claim**: Interface para compra e venda de blocos de claim (integração com GriefPrevention)
- **Títulos de Teleporte**: Exibe mensagens de boas-vindas ao teleportar para uma loja
- **Cooldowns de Teleporte**: Evita abusos com tempo de espera entre teleportes

## Internacionalização
- **Suporte Multi-idioma**: Inclui português (pt_BR) e inglês (en) por padrão
- **Facilmente Expansível**: Adicione novos idiomas criando arquivos na pasta lang/
- **Configuração Simples**: Mude o idioma com uma única linha no arquivo de configuração

## Comandos

- `/shopwarp create <nome> [descrição]` - Cria uma nova warp de loja
- `/shopwarp delete <nome>` - Deleta uma warp de loja
- `/shopwarp tp <nome>` - Teleporta para uma warp de loja
- `/shopwarp list` - Lista suas warps de loja
- `/shopwarp gui` - Abre a interface gráfica de lojas
- `/shopwarp announce <nome> <mensagem>` - Anuncia sua loja no chat global
- `/shopwarp reload` - Recarrega as configurações do plugin
- `/shopwarp scan` - Escaneia a área ao redor para verificar segurança
- `/shopwarp forcetp <nome>` - Força teleporte para uma loja (ignora verificações de segurança)

## Permissões

- `playershopwarp.create` - Permite criar warps de loja
- `playershopwarp.delete` - Permite deletar suas próprias warps
- `playershopwarp.tp` - Permite teleportar para warps de loja
- `playershopwarp.list` - Permite listar suas warps
- `playershopwarp.gui` - Permite acessar a GUI de lojas
- `playershopwarp.announce` - Permite anunciar lojas
- `playershopwarp.reload` - Permite recarregar o plugin
- `playershopwarp.admin` - Concede todas as permissões administrativas
- `playershopwarp.warps.X` - Define o limite máximo de warps (substitua X pelo número)

## Configuração

O plugin oferece configurações extensivas em `config.yml`:

- Limites de warps por jogador
- Custos de criação e teleporte
- Cooldowns para anúncios e teleportes
- Configurações de segurança anti-trap
- Personalização da interface gráfica
- Seleção de idioma
- Configurações para compra/venda de blocos de claim

## Requisitos

- Servidor Spigot 1.20.6
- Vault (para funcionalidades econômicas)
- Plugin de economia compatível com Vault (como EssentialsX)
- GriefPrevention (opcional, para integração com sistema de claims)

## Instalação

1. Faça o download do plugin
2. Coloque o arquivo .jar na pasta plugins do seu servidor
3. Reinicie o servidor ou use `/reload`
4. Configure o plugin em `plugins/PlayerShopWarp/config.yml`
5. Use `/shopwarp reload` para aplicar as alterações

---

PlayerShopWarp é uma solução completa para gerenciamento de lojas em servidores Minecraft, combinando facilidade de uso para jogadores com ampla personalização para administradores.
