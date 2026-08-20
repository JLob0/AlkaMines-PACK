# AlkaMinesPack

Fork do **AlkaMines** com o módulo de **Habilidade do Dragão** (`/mina dragao`). É um plugin
autônomo de minas/prison — quem roda ele **não** roda o AlkaMines original (são plugins
separados, mesmo `main` class `com.alka.mines.AlkaMines`, um OU outro na pasta `plugins/`).

Baseado no AlkaMines existente em produção (github.com/JLob0/AlkaMines) e no core
[AlkaCore](github.com/JLob0/AlkaCore).

## Recursos

- Minas públicas (reset sync via `BlockFillHook`, índice por chunk O(1))
- Minas particulares (PlotSquared, expansão volumétrica, schematics FAWE)
- Sistema de picareta com níveis (PlayerDataManager → banco AlkaCore)
- GUIs via `BaseGui` do AlkaCore
- **Habilidade do Dragão** (client-side, trajetória matemática, cache Caffeine):
  - Entidade fake ENDER_DRAGON 100% client-side via **ProtocolLib** (o servidor nunca
    sabe que ela existe — nenhum `world.spawnEntity()`).
  - Trajetória por curvas de **Bézier cúbicas** (círculo, mergulho, sweep).
  - Raycast do bafo que quebra blocos **visualmente** (pacotes) em área, acumulando
    drops num `RewardBatcher` e vendendo/entregando em lote no fim.
  - **Cache Caffeine** O(1) por chunk dos blocos de composição das minas (read-only).
  - Regeneração automática dos blocos queimados.
  - Cooldown, permissão, 1 sessão por jogador e cleanup garantido (quit/disable).

## Requisitos

| Dependência | Tipo | Motivo |
|---|---|---|
| [AlkaCore](https://github.com/JLob0/AlkaCore) | `depend` | DB, mensagens, GUIs, scheduler |
| FastAsyncWorldEdit (FAWE) | `depend` | seleção + reset em massa |
| **ProtocolLib 5.4+** | `softdepend` | entidades/pacotes client-side |
| AlkaShop / AlkaDrop | `softdepend` | auto-venda e coleta dos drops |

Java 21+.

## Build

```bash
./gradlew clean build
```

O jar sai em `build/libs/AlkaMinesPack-1.0.0.jar` (Caffeine embutido/shaded; ProtocolLib
fica de fora, vem do servidor).

## Instalação

1. Copie `AlkaMinesPack-1.0.0.jar` para `plugins/`.
2. **Remova qualquer `AlkaMines*.jar`** da pasta — é um OU outro.
3. Garanta o ProtocolLib instalado.
4. Reinicie o servidor (não use `/reload`).
5. Dê a permissão `alkamines.ability.dragon` aos jogadores.

## Comandos

- `/mina dragao` — invoca a habilidade do Dragão na mina atual (pública ou particular).
- `/mina debug ability` — sessões/entidades/tasks ativas (OP).
- `/mina debug cache` — estatísticas do cache Caffeine (hits/misses/tamanho) (OP).
- `/mina debug stress <n>` — simula N dragões por ~30s e reporta TPS/heap (OP).
- Os comandos originais do AlkaMines continuam: `/alkamines ...`, `/mina ir|sair|lista|ranking|particular ...`.

## Configuração

`config.yml` (seção da habilidade):

```yaml
abilities:
  dragon_breath:
    enabled: true
    permission: "alkamines.ability.dragon"
    cooldown-seconds: 60
    max-duration-ticks: 400
    trajectory:
      radius: 30.0
      height: 40.0
      duration-ticks: 200
    breath:
      range: 25
      check-interval-ticks: 2
      particles-per-check: 15
    regeneration:
      enabled: true
      delay-ticks: 100
```

Mensagens de chat em `messages.yml` (seção `mines.ability.*`).

## Estrutura do módulo

```
com.alka.mines
├── ability/   DragonBreathAbility, DragonBreathTask, MineAbilityRegenerator
├── cache/     MineCache (Caffeine), MineBlockData, CachedMineKey
├── entity/    FakeDragonEntity, FakeEntityRegistry
├── packet/    PacketFactory (ProtocolLib)
├── reward/    RewardBatcher (extraído do MineBreakListener)
├── trajectory/ BezierTrajectory, TrajectoryFactory, TrajectoryTask
└── lifecycle/ TaskManager, MineAbilitySession, MineAbilitySessionManager
```

## Anti-padrões evitados

- NUNCA `world.spawnEntity()` — o dragão é 100% client-side.
- NUNCA modificar o `MineCache` após o load — ele é read-only.
- NUNCA deixar sessão sem `endSession()` — sempre há caminho de cleanup.
- NUNCA enviar pacotes para jogador offline.
- Matemática da trajetória em thread async; só o envio de pacotes é sync.
