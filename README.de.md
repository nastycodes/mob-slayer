# Mob Slayer (Mob-Schlächter)

[🇬🇧 English version](README.md)

Fabric-Mod für **Minecraft 1.21.11** (Fabric Loader 0.20.1+, Fabric API). Fügt den **Mob-Schlächter** hinzu: einen
Block mit einer Kreissäge, der Mobs vor seiner Klingenseite automatisch Schaden zufügt – ohne Munition oder
Treibstoff. Er lässt sich per Redstone schalten, wie ein Schwert verzaubern und sammelt die Erfahrung seiner Kills in
einem Speicher, den man abholen kann.

## Installation

`mobslayer-1.0.0.jar` zusammen mit der [Fabric API](https://modrinth.com/mod/fabric-api) in den `mods`-Ordner legen –
auf dem Server **und** bei jedem Client. [Mod Menu](https://modrinth.com/mod/modmenu) ist optional und bringt den
Einstellungsbildschirm im Spiel.

## Spielen

1. Mob-Schlächter craften:

   ```
   Eisenschwert  Eisenschwert  Eisenschwert
   Eisenbarren   Redstone      Eisenbarren
   Eisenbarren   Eisenblock    Eisenbarren
   ```

   (Kreativ: Reiter „Redstone“, direkt hinter dem Werfer.)
2. Platzieren: Wie beim Werfer zeigt die **Klingenseite** (die Säge) zu dir. Das geht in alle 6 Richtungen, also
   auch nach oben oder unten.
3. Rechtsklick öffnet die Einstellungen.

### Angriff

- Getroffen wird alles im Block **vor der Klingenseite** (mit Schwungkraft auch weiter, siehe unten), standardmäßig
  einmal pro Sekunde. Seiten, Rückseite und Diagonalen sind sicher.
- **Grundschaden** pro Block in der GUI einstellbar (−5 / −1 / +1 / +5, 2 Schaden = 1 Herz). Startwert 6, Bereich
  1–40 (Config).
- Die Treffer zählen wie **Spieler-Kills**: Mobs lassen auch die Drops fallen, die es sonst nur gibt, wenn ein
  Spieler sie tötet, z. B. Eisen bei Zombies. Die Mobs werden dabei **nicht** wütend (keine Zombie-Piglin-Horden),
  und der Block stößt sie nicht weg, sodass sie vor der Säge bleiben.
- **Geschützt** (Config): Spieler, gezähmte Tiere, Mobs mit Namensschild, Tierbabys (Baby-Zombies dagegen nicht),
  Dorfbewohner und fahrende Händler. Rüstungsständer werden nie getroffen.

### Upgrades: Verzaubern wie ein Schwert

Der Mob-Schlächter lässt sich am **Zaubertisch** verzaubern oder am **Amboss** mit Büchern aufwerten, wie ein Schwert.
Die Verzauberungen wirken genau wie beim Schwert:

| Verzauberung | Wirkung |
|---|---|
| Schärfe / Bann / Nemesis der Gliederfüßer | mehr Schaden (z. B. Schärfe V: +3) |
| Verbrennung | setzt Mobs in Brand |
| Plünderung | mehr und seltenere Drops |
| Rückstoß | stößt Mobs vom Block weg (z. B. in Lava oder Wasser) |
| Schwungkraft | +1 Block Reichweite pro Stufe (Config) |

Beim Abbauen bleiben die Verzauberungen (und ein Amboss-Name) am Item erhalten; beim erneuten Platzieren gehen sie
wieder in den Block. Die GUI listet sie unter „Upgrades“; ein Tooltip dort erklärt die Wirkungen.

### Redstone

Der Button oben in der GUI wechselt zwischen drei Modi:

- **läuft mit Signal** (Standard): greift nur an, solange der Block ein Redstone-Signal bekommt.
- **läuft ohne Signal**: greift nur an, solange kein Signal anliegt (ein Hebel schaltet ihn ab).
- **ignoriert**: greift immer an.

Oben rechts steht, ob er gerade läuft („● Aktiv“ / „● Aus“). Im Betrieb dreht sich die Säge und glüht rot.

### XP-Speicher

- Die Erfahrung, die ein Mob für einen Spieler fallen lassen würde, landet im Speicher des Blocks statt als Kugeln
  auf dem Boden (gleiche Menge wie in Vanilla, z. B. 5 pro Zombie, 1–3 pro Kuh; Babys geben wie in Vanilla nichts).
- **Abholen** gibt dir alles auf einmal. Die GUI zeigt die Punkte und bis zu welchem Level sie von 0 aus reichen.
- Wird der Block abgebaut oder gesprengt, fällt der Speicherinhalt als Erfahrungskugeln heraus – es geht nichts
  verloren.

## Einstellungen

Mit Mod Menu: **Mods → Mob-Schlächter → Einstellungs-Button**. Jede Option hat einen Tooltip und einen
„Standard“-Button, Eingaben werden vor dem Speichern geprüft, Änderungen gelten sofort. Ohne Mod Menu
`config/mobslayer.json` bearbeiten (wird beim ersten Start angelegt) und `/mobslayer reload` ausführen (OP-Level 2).

Es gelten die Einstellungen dessen, der die Welt betreibt: in Einzelspieler- und LAN-Welten deine eigenen, auf einem
Server die `config/mobslayer.json` des Servers.

| Schlüssel | Standard | Bedeutung |
|---|---|---|
| `defaultDamage` | 6.0 | Grundschaden neu platzierter Blöcke |
| `minDamage` / `maxDamage` | 1.0 / 40.0 | Bereich für die GUI-Buttons (beide gleich = fester Schaden) |
| `attackIntervalTicks` | 20 | Ticks zwischen zwei Angriffen (unter 10 schützt die Unverwundbarkeitszeit der Mobs) |
| `reach` | 1 | Blöcke vor der Klingenseite |
| `reachPerSweepingEdgeLevel` | 1 | zusätzliche Blöcke pro Stufe Schwungkraft |
| `maxReach` | 8 | Obergrenze der Reichweite |
| `maxTargetsPerAttack` | 16 | höchstens so viele Mobs pro Angriff (die nächsten zuerst) |
| `damagePlayers` | false | Spieler treffen (nur wenn PvP auf dem Server erlaubt ist) |
| `damageTamedAnimals` | false | gezähmte Tiere treffen |
| `damageNamedMobs` | false | Mobs mit Namensschild treffen |
| `damageBabyAnimals` | false | Tierbabys treffen |
| `excludedEntities` | Dorfbewohner, fahrender Händler | Entity-IDs, die nie getroffen werden |
| `xpMultiplier` | 1.0 | Faktor auf die gespeicherte Erfahrung |
| `maxStoredXp` | 0 | Speichergrenze pro Block (0 = unbegrenzt); was nicht passt, fällt als Kugeln heraus |
| `defaultRedstoneMode` | `"high"` | Modus neuer Blöcke: `high`, `low` oder `ignored` |
| `attackEffects` | true | Schwung-Partikel und -Sound bei jedem Angriff |

## Sprachen

Englisch und Deutsch. Es gilt die Spielsprache; auch der Einstellungsbildschirm ist übersetzt.

## Entwickeln

```
gradlew build              # baut build/libs/mobslayer-1.0.0.jar, inkl. Server-Gametests und Übersetzungsprüfung
gradlew runClient          # Testclient (mit Mod Menu)
gradlew runGameTest        # Server-Gametests der Mechanik
gradlew runClientGameTest  # End-to-End-Test im echten Client, Screenshots in build/run/clientGameTest/screenshots
python tools/gen_textures.py .    # erzeugt die Texturen und das Icon neu
python tools/gen_resources.py .   # erzeugt Blockstate und Modelle neu
```

`checkTranslations` (Teil von `gradlew build`) bricht ab, wenn `en_us.json` und `de_de.json` nicht dieselben
Schlüssel haben. Der Client-Gametest läuft auf Englisch in 1920×1080 und prüft auch den Einstellungsbildschirm
(Listen-Editor, ungültige Eingaben,
„Änderungen verwerfen?“).

## Lizenz

[MIT](LICENSE)
