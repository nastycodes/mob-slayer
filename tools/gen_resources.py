"""Generates the JSON resources for the Mob Slayer mod that are tedious to write by hand.

Usage: python tools/gen_resources.py <project root>
Writes the block state (6 facings x active), both block models and the item model.
"""
import json
import os
import sys

ROTATIONS = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent="\t", ensure_ascii=False)
        f.write("\n")


def main(root):
    assets = os.path.join(root, "src/main/resources/assets/mobslayer")

    variants = {}
    for facing in ["down", "up", "north", "south", "west", "east"]:
        for active in ["false", "true"]:
            variant = {"model": "mobslayer:block/mob_slayer" + ("_active" if active == "true" else "")}
            variant.update(ROTATIONS[facing])
            variants[f"active={active},facing={facing}"] = variant
    dump(os.path.join(assets, "blockstates/mob_slayer.json"), {"variants": variants})

    # The blade side is north in the model; the block state rotates it to the block's facing.
    for name, front in [("mob_slayer", "mob_slayer_front"), ("mob_slayer_active", "mob_slayer_front_active")]:
        dump(os.path.join(assets, f"models/block/{name}.json"), {
            "parent": "minecraft:block/cube",
            "textures": {
                "particle": "mobslayer:block/mob_slayer_side",
                "north": f"mobslayer:block/{front}",
                "south": "mobslayer:block/mob_slayer_back",
                "east": "mobslayer:block/mob_slayer_side",
                "west": "mobslayer:block/mob_slayer_side",
                "up": "mobslayer:block/mob_slayer_side",
                "down": "mobslayer:block/mob_slayer_side"
            }
        })

    dump(os.path.join(assets, "items/mob_slayer.json"), {
        "model": {"type": "minecraft:model", "model": "mobslayer:block/mob_slayer"}
    })


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
