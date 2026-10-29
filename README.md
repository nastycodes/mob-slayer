# Mob Slayer

[🇩🇪 Deutsche Version](README.de.md)

Fabric mod for **Minecraft 1.21.11** (Fabric Loader 0.20.1+, Fabric API). Adds the **Mob Slayer**: a block with a
circular saw that damages mobs in front of its blade side automatically – no ammunition, no fuel. It is switched with
redstone, enchanted like a sword and stores the experience of its kills for you to collect.

## Installation

Put `mobslayer-1.0.0.jar` and [Fabric API](https://modrinth.com/mod/fabric-api) into the `mods` folder – on the
server **and** on every client. [Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds the in-game
settings screen.

## How to play

1. Craft the Mob Slayer:

   ```
   Iron Sword   Iron Sword   Iron Sword
   Iron Ingot   Redstone     Iron Ingot
   Iron Ingot   Iron Block   Iron Ingot
   ```

   (Creative: "Redstone Blocks" tab, right after the dispenser.)
2. Place it: like a dispenser, the **blade side** (the saw) faces you. All 6 directions work, up and down too.
3. Right-click opens its settings.

### Attacking

- Everything in the block **in front of the blade side** is hit (further with Sweeping Edge, see below), once per
  second by default. The sides, the back and diagonals are safe.
- **Base damage** is set per block in the GUI (−5 / −1 / +1 / +5, 2 damage = 1 heart). Starts at 6, range 1–40
  (config).
- Hits count as **player kills**: mobs also drop the loot that normally needs a player, e.g. iron from zombies.
  Mobs do **not** get angry (no zombified piglin hordes), and the block does not push them away, so they stay in
  front of the saw.
- **Protected** (config): players, tamed animals, mobs with a name tag, baby animals (baby zombies are not), villagers
  and wandering traders. Armor stands are never hit.

### Upgrades: enchant it like a sword

Enchant the Mob Slayer at an **enchanting table** or with books on an **anvil**, just like a sword. The enchantments
work exactly like on a sword:

| Enchantment | Effect |
|---|---|
| Sharpness / Smite / Bane of Arthropods | more damage (e.g. Sharpness V: +3) |
| Fire Aspect | sets mobs on fire |
| Looting | more and rarer drops |
| Knockback | pushes mobs away from the block (e.g. into lava or water) |
| Sweeping Edge | +1 block of reach per level (config) |

When the block is mined, its enchantments (and an anvil name) stay on the item and go back into the block when it is
placed again. The GUI lists them under "Upgrades"; its tooltip explains what they do.

### Redstone

The button at the top of the GUI switches between three modes:

- **runs with signal** (default): only attacks while the block receives a redstone signal.
- **runs without signal**: only attacks while there is no signal (a lever turns it off).
- **ignored**: always attacks.

The top right corner shows whether it is running ("● Active" / "● Off"). While running, the saw spins and glows red.

### Experience reservoir

- The experience a mob would drop for a player goes into the block's reservoir instead of onto the floor (same
  amount as in vanilla, e.g. 5 per zombie, 1–3 per cow; babies give nothing, as in vanilla).
- **Collect** gives you everything at once. The GUI shows the points and up to which level they get you from 0.
- When the block is mined or blown up, the stored experience drops as orbs – nothing is lost.

## Settings

With Mod Menu: **Mods → Mob Slayer → settings button**. Every option has a tooltip, a reset button and is checked
before saving; changes apply immediately. Without Mod Menu, edit `config/mobslayer.json` (created on first start)
and run `/mobslayer reload` (operator level 2).

The settings belong to whoever runs the world: in singleplayer and LAN worlds your own settings count, on a dedicated
server the server's `config/mobslayer.json`.

| Key | Default | Meaning |
|---|---|---|
| `defaultDamage` | 6.0 | base damage of newly placed blocks |
| `minDamage` / `maxDamage` | 1.0 / 40.0 | range for the GUI buttons (both equal = fixed damage) |
| `attackIntervalTicks` | 20 | ticks between two attacks (below 10 the mobs' invulnerability time protects them) |
| `reach` | 1 | blocks in front of the blade side |
| `reachPerSweepingEdgeLevel` | 1 | extra blocks per level of Sweeping Edge |
| `maxReach` | 8 | upper limit of the reach |
| `maxTargetsPerAttack` | 16 | most mobs per attack (nearest first) |
| `damagePlayers` | false | hit players (only if PvP is allowed on the server) |
| `damageTamedAnimals` | false | hit tamed animals |
| `damageNamedMobs` | false | hit mobs with a name tag |
| `damageBabyAnimals` | false | hit baby animals |
| `excludedEntities` | villager, wandering trader | entity ids that are never hit |
| `xpMultiplier` | 1.0 | factor for the stored experience |
| `maxStoredXp` | 0 | reservoir limit per block (0 = unlimited); what does not fit drops as orbs |
| `defaultRedstoneMode` | `"high"` | mode of new blocks: `high`, `low` or `ignored` |
| `attackEffects` | true | sweep particles and sound on every attack |

## Languages

English and German (Deutsch). The game language decides; the config screen is translated too.

## Development

```
gradlew build              # builds build/libs/mobslayer-1.0.0.jar, runs the server game tests and the translation check
gradlew runClient          # test client (with Mod Menu)
gradlew runGameTest        # server game tests of the mechanics
gradlew runClientGameTest  # end-to-end test in a real client, screenshots in build/run/clientGameTest/screenshots
python tools/gen_textures.py .    # regenerates the textures and the icon
python tools/gen_resources.py .   # regenerates the blockstate and models
```

The server game tests check: only the mob in front of the blade side is hit (upwards too), the redstone modes, hits
count as player hits without angering mobs, Sharpness V (+3 damage) and Fire Aspect, Looting III (more leather than
possible without), Sweeping Edge (reach), protected mobs, the GUI buttons including their limits, XP above 65 535,
saving/loading, mining (XP orbs, the item keeps its enchantments) and that the enchanting table and the anvil offer
the sword enchantments. The client game test (English, 1920×1080) opens the block with a right click, clicks the
buttons and checks the values on the server, then changes settings in the config screen behind Mod Menu's button,
including the list editor, invalid input and the "discard changes?" prompt.

How it works: hits are dealt by a Fabric `FakePlayer` holding the enchanted block item, so vanilla applies Sharpness,
Fire Aspect, Looting and so on by itself (sword enchantments of other mods too). The damage type `mobslayer:blade` is
in the tags `no_knockback` and `no_anger`. The experience is caught with Fabric's `ALLOW_DEATH` event before vanilla
drops the orbs.

## License

[MIT](LICENSE)
