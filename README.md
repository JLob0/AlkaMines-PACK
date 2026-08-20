<div align="center">

# AlkaMinesPack

### Minas prison + Habilidade do Dragão

Fork do AlkaMines com a Habilidade do Dragão (100% client-side via ProtocolLib). Plug-in
autônomo de minas/prison — quem roda ele não roda o AlkaMines original (um OU outro).

![Java](https://img.shields.io/badge/Java-21-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8-green)
![Version](https://img.shields.io/badge/Version-1.0.0-blue)
![License](https://img.shields.io/badge/License-Proprietary-red)

</div>

---

## 📋 Sobre o Projeto

Sistema de minas/prison com seleção via WorldEdit, reset em massa via FAWE, hologramas,
GUIs, minas públicas e particulares, sistema de picareta com níveis — e a **Habilidade do
Dragão**, que invoca um Ender Dragon fake voando em curva de Bézier sobre a mina, queimando
blocos em área (visualmente, via pacotes) e acumulando drops para venda/entrega em lote.

Baseado no [AlkaMines](https://github.com/JLob0/AlkaMines) e no core
[AlkaCore](https://github.com/JLob0/AlkaCore).

## ✨ Funcionalidades Principais

- Minas públicas (reset sync via `BlockFillHook`, índice por chunk O(1))
- Minas particulares (PlotSquared, expansão volumétrica, schematics FAWE)
- Sistema de picareta com níveis (PlayerDataManager → banco AlkaCore)
- GUIs via `BaseGui` do AlkaCore
- **Habilidade do Dragão** (`/mina dragao`):
  - Entidade fake ENDER_DRAGON 100% client-side via ProtocolLib (nenhum `world.spawnEntity()`)
  - Trajetória por curvas de Bézier cúbicas (círculo, mergulho, sweep)
  - Bafo que quebra blocos visualmente em área e acumula drops num `RewardBatcher`
  - Cache Caffeine O(1) por chunk dos blocos de composição (read-only)
  - Regeneração automática dos blocos queimados
  - Cooldown, permissão, 1 sessão por jogador e cleanup garantido (quit/disable)

## 🎮 Comandos

| Comando | Descrição | Permissão |
|---|---|---|
| `/mina dragao` | Invoca a Habilidade do Dragão na mina atual | `alkamines.ability.dragon` |
| `/mina debug ability` | Sessões/entidades/tasks ativas | `op` |
| `/mina debug cache` | Estatísticas do cache Caffeine | `op` |
| `/mina debug stress <n>` | Simula N dragões por ~30s e reporta TPS/heap | `op` |
| `/alkamines ...` | Comandos administrativos originais | `alkaminas.admin.*` |
| `/mina ir\|sair\|lista\|ranking\|particular ...` | Comandos de jogador originais | — |

## 🔗 Integrações

| Integração | Tipo |
|---|---|
| [AlkaCore](https://github.com/JLob0/AlkaCore) | `depend` (DB, mensagens, GUIs, scheduler) |
| FastAsyncWorldEdit (FAWE) | `depend` (seleção + reset em massa) |
| ProtocolLib 5.4+ | `softdepend` (entidades/pacotes client-side) |
| AlkaShop / AlkaDrop | `softdepend` (auto-venda e coleta dos drops) |

## 🔧 Tecnologias Utilizadas

- Java 21 / Paper 1.21.8
- ProtocolLib (pacotes client-side)
- Caffeine (cache O(1))
- AlkaCore (infraestrutura)
- FastAsyncWorldEdit (reset em massa)

## ⚙️ Instalação

1. Copie `AlkaMinesPack-1.0.0.jar` para `plugins/`.
2. **Remova qualquer `AlkaMines*.jar`** da pasta — é um OU outro (mesmo `main` class).
3. Garanta o ProtocolLib instalado.
4. Reinicie o servidor (não use `/reload`).
5. Conceda a permissão `alkamines.ability.dragon`.

Build: `./gradlew clean build` → jar em `build/libs/` (Caffeine embutido; ProtocolLib vem do servidor).

## 🔐 Permissões

| Permissão | Descrição | Padrão |
|---|---|---|
| `alkamines.ability.dragon` | Usar `/mina dragao` | `false` |
| `alkaminas.admin.*` | Comandos administrativos | `op` |

## 📝 Licença

> ⚠️ **Projeto proprietário da AlkaStudio.**
>
> Código fonte destinado exclusivamente ao uso interno da rede `Alka*`.
> Reprodução, distribuição ou uso não autorizado não são permitidos.

## 🎯 Créditos

- Base original: [AlkaMines](https://github.com/JLob0/AlkaMines)
- Core: [AlkaCore](https://github.com/JLob0/AlkaCore)

---

<div align="center">

**Desenvolvido com ❤️ pela AlkaStudio**

[![AlkaStudio](https://img.shields.io/badge/AlkaStudio-JLob0-blue)](https://github.com/JLob0)

</div>
