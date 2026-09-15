# MVP — AlkaMines-Pack: Features Custom (mina pessoal evolutiva)

> Escopo: o segundo plugin de minas — mina pessoal 100% virtual (client-side/packet,
> anti-lag), picareta evolutiva, encantamentos, prestígio, skins, aluguel. Estabilidade
> de TPS do AlkaMines "padrão" tem documento próprio; aqui o foco é robustez das partes
> custom (economia, anti-exploit, qualidade das features) já que a arquitetura de fundo
> (blocos virtuais) já resolve o problema de TPS por design.

## O que já existe (não é pouca coisa)

- `virtual/VirtualBlockSystem` + `virtual/VirtualMineGrid` + `packet/PacketFactory`:
  mina inteira em memória, mundo real só tem bedrock+ar, quebra é só pacote (visual +
  som + partícula) — mesma família de solução que o X-PrivateMines usa
  (`VirtualMineStore`), já resolvendo o problema de custo de bloco real por design.
- `pickaxe/PickaxeLevelManager` com curva `base/exponent/maxLevel` configurável —
  progressão exponencial, já alinhado com o padrão de matemática do estúdio.
- `pickaxe/PrestigeSystem`, `pickaxe/PickaxeSkin`, `rental/ToolRentalSystem`.
- 6 encantamentos custom (`enchantment/EnchantmentType`): `DRAGON` (invoca dragão que
  quebra blocos — via `entity/FakeDragonEntity` + `trajectory/BezierTrajectory`),
  `EXPLOSION` (3x3), `KEYFINDER`, `FORTUNE_BOOST`, `SPEED_MINING`, `MEMORIZE` (bônus por
  padrão de blocos). Valores (chance/custo/cooldown/nível de desbloqueio) já
  configuráveis via `EnchantmentConfig`, com fallback no enum.

Comparado ao X-Prison (`pickaxelevels`/`pickaxeskins`/`enchants`) e ao X-PrivateMines
(motor virtual), a AlkaMines-Pack já cobre o mesmo terreno — em alguns pontos com mais
personalidade (o encantamento Dragão é próprio, não existe equivalente no X-Prison).

## Achados confirmados (lido no código)

### 1. Zero validação de velocidade/ritmo de quebra — P0 (segurança econômica)
`listener/PersonalMineListener.java#handleDig()` intercepta o pacote
`BLOCK_DIG`/`START_DESTROY_BLOCK` via ProtocolLib e chama `virtualBlockSystem.handleBreak`
direto, sem NENHUMA checagem de quão rápido os pacotes estão chegando nem de velocidade
de quebra esperada pra ferramenta/bloco. Cada dig processado deposita dinheiro
(`depositSellValue`) e avança a picareta. Um client modificado/macro mandando pacotes
`START_DESTROY_BLOCK` mais rápido que uma quebra legítima permitiria **fazer farm de
dinheiro e nível de picareta acima do ritmo pretendido** — é literalmente a categoria de
bug de "economia duplicando/inflando" que já mordeu vocês antes em outros plugins (ver
auditoria de duplicação 2026-09-11), só que pela porta de trás do packet em vez do
evento Bukkit normal.

O X-PrivateMines resolve isso com um `DigCommandQueue` + `packetMaxDigsPerTick`
(rate-limit explícito de quantos digs um jogador pode gerar por tick) — não precisa
copiar a implementação deles, mas o princípio (limitar digs/tick por jogador e/ou
exigir um intervalo mínimo compatível com a dureza do bloco) precisa entrar aqui.

### 2. Moeda hardcoded "GOLD" — P1
`virtual/VirtualBlockSystem.java#depositSellValue()`:
`economy.deposit(player.getUniqueId(), "GOLD", amount)` — string fixa, não vem de
config. Bate direto com a regra do estúdio de moeda sempre resolvida via config
(nunca hardcode) — se um servidor quiser vender minério pessoal em outra moeda
(ex: `nacar`), hoje não dá sem recompilar.

### 3. `generateMine()` também é um loop síncrono real-world — P1
`personal/PersonalMineManager.java#generateMine()` faz um loop triplo
`x → z → y` chamando `world.getBlockAt(x,y,z).setType(Material.AIR/BEDROCK,false)` pra
preparar o chão real (bedrock) e limpar o volume antes de gerar a grade virtual. Roda
toda vez que a mina de um jogador é criada OU expande de nível
(`expandIfNeeded → regenerateMine → generateMine`). Como é por jogador (não a mina
inteira do servidor), o risco é bem menor que o do AlkaMines padrão, mas se vários
jogadores subirem de nível ao mesmo tempo (ex: depois de um evento com boost de XP/
drops), os loops podem empilhar na mesma tick. Vale um orçamento por tick aqui também,
ou pelo menos confirmar que os bounds típicos são pequenos o bastante pra nunca doer.

## Itens do MVP (ordem de prioridade)

### P0 — Rate-limit / validação de ritmo no dig
Adicionar no `PersonalMineListener#handleDig` um limite de digs processados por
jogador por tick (ou por janela curta de tempo), e/ou exigir que o tempo entre
`START_DESTROY_BLOCK` e a quebra efetiva seja compatível com a ferramenta (picareta
nível X deveria ter uma velocidade mínima esperada, não infinita). Sem isso, todo o
resto do sistema (economia, XP de picareta, encantamentos) está exposto a farm
automatizado.

### P1 — Moeda configurável no venda automática
Trocar o `"GOLD"` hardcoded em `VirtualBlockSystem#depositSellValue` por uma currency
ID vinda de config (mesmo padrão dos outros plugins Alka).

### P1 — Orçamento por tick em `generateMine()`
Mesma técnica do MVP do AlkaMines padrão (fill incremental), aplicada ao loop de
preparo real-world da mina pessoal — ou, no mínimo, medir se os bounds reais usados em
produção já são pequenos o bastante pra nunca precisar disso (nesse caso, documentar o
limite seguro em vez de implementar algo sem necessidade).

### P2 — Auditoria de balanceamento dos encantamentos
Não é bug, é tuning: comparar chance/custo/cooldown dos 6 encantamentos (`EnchantmentType`)
contra o que X-Prison usa nos dele, pra checar se algum está fora de curva (ex: DRAGON
com 5% de chance base e cooldown de 30s parece generoso pra o efeito que causa — validar
com o dono do design, não decidir sozinho).

## Definição de pronto

- Um cliente/macro mandando pacotes `START_DESTROY_BLOCK` em rajada não consegue
  ganhar dinheiro/XP de picareta acima do ritmo de uma quebra legítima.
- Moeda da venda automática da mina pessoal é configurável, não fixa em "GOLD".
- Vários jogadores subindo de nível/expandindo mina ao mesmo tempo não produz spike de
  tick mensurável (ou o limite seguro está documentado e dentro do uso real).
