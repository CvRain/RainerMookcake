#!/usr/bin/env python3
"""Mooncake Overflow —— 方块模型 / 方块状态生成器。

月饼的形态 = **形状** × **纹样**，两个独立的轴：
  形状(2)：round（切四角，看起来圆润） / square（完整方块）
  纹样(3)：round / square / flower  —— 决定用哪张顶面贴图
共 6 种形态，放在月饼方块的四格里。

方块是"2×2 四个格子、每格独立记形态"，所以模型拆成**单格模型**，
再用 blockstate 的 `multipart` 按格拼起来（而不是列 7⁴ 种组合）。

两种月饼堆：
  mooncake_block          —— 普通月饼，不能吃铜锈（3 纹样 = 3 套贴图）
  copper_mooncake_block   —— 包了一圈铜的月饼，会氧化（3 纹样 × 4 氧化度）

⚠️ 踩过的坑：顶面 UV 必须**相对这一块**归一化到 0..16。
   直接写方块坐标的话，7×7 的格子只会采到 16×16 贴图的左上角一小块，
   结果就是"每块月饼只有四分之一个图案，四块拼起来才是完整的"。
   十字形的两个盒子同理 —— UV 要按各自在这块里的相对位置切，才会拼成一张完整的图。

用法：  python3 tools/generate_block_models.py
"""

from __future__ import annotations

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "mooncake_overflow")

FACES = ("down", "up", "north", "south", "west", "east")

SHAPES = ("round", "square")
PATTERNS = ("round", "square", "flower")
# 形态 id = 形状_纹样，和 Java 那边 MooncakeKind 的序列化名一一对应
KINDS = tuple(f"{s}_{p}" for s in SHAPES for p in PATTERNS)

# 氧化四阶段：铜 → 斑驳 → 锈蚀 → 氧化
OXIDATIONS = ("copper", "tarnished", "rusted", "oxidized")

# 月饼方块：2×2 四个格子，每格 7×7
CELLS = {"nw": (1, 1), "ne": (8, 1), "sw": (1, 8), "se": (8, 8)}
PIECE = 7
CENTER = 4
CENTER_SIZE = 8
HEIGHT = 3

# 面团方块 / 生月饼饼：占地 8×8 与 12×12
DOUGH_SIZE = 8
PATTY_SIZE = 12


def boxes(x0: int, z0: int, size: int, square: bool) -> list[tuple[int, int, int, int]]:
    """一块月饼由哪些长方体组成 —— 只由**形状**决定，和纹样无关。"""
    x1, z1 = x0 + size, z0 + size
    if square:
        return [(x0, z0, x1, z1)]
    # 圆形：切掉四角（横条 + 竖条）
    return [
        (x0, z0 + 1, x1, z1 - 1),
        (x0 + 1, z0, x1 - 1, z1),
    ]


def element(px0: int, pz0: int, px1: int, pz1: int,
            x0: int, z0: int, x1: int, z1: int,
            height: int, top: str, side: str) -> dict:
    """(px0,pz0)-(px1,pz1) 是这一块的包围盒，用来把顶面 UV 归一化到 0..16。"""
    sx = 16.0 / (px1 - px0)
    sz = 16.0 / (pz1 - pz0)
    uv = [round((x0 - px0) * sx, 2), round((z0 - pz0) * sz, 2),
          round((x1 - px0) * sx, 2), round((z1 - pz0) * sz, 2)]

    faces = {}
    for face in FACES:
        if face == "up":
            entry = {"uv": uv, "texture": top}
        else:
            entry = {"uv": [0, 0, 16, 16], "texture": side}
        if face == "down":
            entry["cullface"] = "down"
        faces[face] = entry
    return {"from": [x0, 0, z0], "to": [x1, height, z1], "faces": faces}


def model(x0: int, z0: int, size: int, height: int, square: bool,
          top: str, side: str) -> dict:
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "particle": top,
            "top": top,
            "side": side,
        },
        "elements": [
            element(x0, z0, x0 + size, z0 + size, *b, height, "#top", "#side")
            for b in boxes(x0, z0, size, square)
        ],
    }


def write(rel: str, data: dict) -> None:
    if not rel.endswith(".json"):
        rel += ".json"
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print("wrote", os.path.relpath(path, ROOT))


def main() -> None:
    # ---------- 普通月饼堆 ----------
    # 普通月饼**不氧化**（氧化是外面那圈铜的事），所以每个形态只要一套贴图。
    for kind in KINDS:
        pattern = kind.split("_", 1)[1]
        top = f"mooncake_overflow:block/mooncake_top_{pattern}_plain"
        side = "mooncake_overflow:block/mooncake_side_plain"
        square = kind.startswith("square")
        for cell, (x, z) in CELLS.items():
            write(f"models/block/mooncake_piece_{cell}_{kind}",
                  model(x, z, PIECE, HEIGHT, square, top, side))
        write(f"models/block/mooncake_item_{kind}",
              model(CENTER, CENTER, CENTER_SIZE, HEIGHT, square, top, side))

    # ---------- 月饼堆的方块状态：只记几何 ----------
    # 四个格子各是 空/圆/方，3^4 = 81 种。**必须能从状态推出来**：
    # 26.1 会给每个方块状态缓存碰撞箱和遮挡箱，所以几何不能依赖方块实体。
    # 方块自己 INVISIBLE，所以这 81 个状态统统指向一个空模型。
    cells = ("none", "round", "square")
    variants = {}
    for nw in cells:
        for ne in cells:
            for sw in cells:
                for se in cells:
                    key = f"nw={nw},ne={ne},sw={sw},se={se}"
                    variants[key] = {"model": "mooncake_overflow:block/mooncake_pile_empty"}
    write("blockstates/mooncake_block", {"variants": variants})

    # ---------- 技术方块 mooncake_piece：渲染器的模型表 ----------
    # 26.1 没有"按名字取方块模型"的接口，方块几何只能通过方块状态查到，
    # 所以每种「格子 × 形态 × 铜不铜 × 氧化度」都得有一个状态指向对应的单格模型。
    # 放在一个从不被放置的方块上，免得和月饼堆的几何状态相乘。
    piece_variants = {}
    for cell in ("none", *CELLS):
        for kind in ("none", *KINDS):
            for copper in (False, True):
                for ox in OXIDATIONS:
                    key = f"cell={cell},kind={kind},copper={str(copper).lower()},oxidation={ox}"
                    if cell == "none" or kind == "none":
                        target = "mooncake_overflow:block/mooncake_pile_empty"
                    elif copper:
                        target = f"mooncake_overflow:block/copper_mooncake_piece_{cell}_{kind}_{ox}"
                    else:
                        target = f"mooncake_overflow:block/mooncake_piece_{cell}_{kind}"
                    piece_variants[key] = {"model": target}
    write("blockstates/mooncake_piece", {"variants": piece_variants})

    # 空模型：月饼堆的 81 个状态都指向它（方块是 INVISIBLE，本来也不画）
    write("models/block/mooncake_pile_empty", {
        "textures": {"particle": "mooncake_overflow:block/mooncake_top_round_plain"},
    })

    # 物品模型：一层 select，按形态切换
    write("items/mooncake", {"model": {
        "type": "minecraft:select",
        "property": "minecraft:component",
        "component": "mooncake_overflow:mooncake_kind",
        "cases": [
            {"when": kind, "model": {"type": "minecraft:model",
                                     "model": f"mooncake_overflow:block/mooncake_item_{kind}"}}
            for kind in KINDS if kind != "round_round"
        ],
        "fallback": {"type": "minecraft:model",
                     "model": "mooncake_overflow:block/mooncake_item_round_round"},
    }})

    # ---------- 铜月饼的模型 ----------
    # 「月饼外面包了一圈铜」才氧化，所以这一套比普通月饼多一个氧化度的轴。
    for kind in KINDS:
        pattern = kind.split("_", 1)[1]
        square = kind.startswith("square")
        for ox in OXIDATIONS:
            top = f"mooncake_overflow:block/copper_mooncake_top_{pattern}_{ox}"
            side = f"mooncake_overflow:block/copper_mooncake_side_{ox}"
            for cell, (x, z) in CELLS.items():
                write(f"models/block/copper_mooncake_piece_{cell}_{kind}_{ox}",
                      model(x, z, PIECE, HEIGHT, square, top, side))
            write(f"models/block/copper_mooncake_item_{kind}_{ox}",
                  model(CENTER, CENTER, CENTER_SIZE, HEIGHT, square, top, side))

    # 物品模型：两层嵌套 select —— 外层按形态、内层按氧化度
    def ox_select(kind: str) -> dict:
        return {
            "type": "minecraft:select",
            "property": "minecraft:component",
            "component": "mooncake_overflow:mooncake_oxidation",
            "cases": [
                {"when": ox, "model": {"type": "minecraft:model",
                                       "model": f"mooncake_overflow:block/copper_mooncake_item_{kind}_{ox}"}}
                for ox in OXIDATIONS if ox != "copper"
            ],
            "fallback": {"type": "minecraft:model",
                         "model": f"mooncake_overflow:block/copper_mooncake_item_{kind}_copper"},
        }

    write("items/copper_mooncake", {"model": {
        "type": "minecraft:select",
        "property": "minecraft:component",
        "component": "mooncake_overflow:mooncake_kind",
        "cases": [
            {"when": kind, "model": ox_select(kind)}
            for kind in KINDS if kind != "round_round"
        ],
        "fallback": ox_select("round_round"),
    }})

    # ---------- 面团方块：形状 × 是否压印 = 4 个模型 ----------
    top_raw = "mooncake_overflow:block/mooncake_dough_block"
    top_stamped = "mooncake_overflow:block/mooncake_dough_block_stamped"
    dough_side = "mooncake_overflow:block/mooncake_dough_side"
    for shape in SHAPES:
        square = shape == "square"
        write(f"models/block/mooncake_dough_block_{shape}",
              model(4, 4, DOUGH_SIZE, 4, square, top_raw, dough_side))
        write(f"models/block/mooncake_dough_block_{shape}_stamped",
              model(4, 4, DOUGH_SIZE, 3, square, top_stamped, dough_side))

    variants = {}
    for shape in SHAPES:
        for stamped in ("false", "true"):
            suffix = "_stamped" if stamped == "true" else ""
            variants[f"shape={shape},stamped={stamped}"] = {
                "model": f"mooncake_overflow:block/mooncake_dough_block_{shape}{suffix}"
            }
    write("blockstates/mooncake_dough_block", {"variants": variants})

    # ---------- 生月饼饼：形状 = 2 个模型 ----------
    patty_top = "mooncake_overflow:block/raw_mooncake_top"
    patty_side = "mooncake_overflow:block/raw_mooncake_side"
    for shape in SHAPES:
        write(f"models/block/raw_mooncake_{shape}",
              model(2, 2, PATTY_SIZE, 2, shape == "square", patty_top, patty_side))

    write("blockstates/raw_mooncake", {
        "variants": {f"shape={shape}": {
            "model": f"mooncake_overflow:block/raw_mooncake_{shape}"} for shape in SHAPES}
    })
    # 生月饼的两个物品：入口在 items/，指向对应形状的方块模型
    write("items/raw_mooncake", {"model": {
        "type": "minecraft:model", "model": "mooncake_overflow:block/raw_mooncake_round"}})
    write("items/square_raw_mooncake", {"model": {
        "type": "minecraft:model", "model": "mooncake_overflow:block/raw_mooncake_square"}})


if __name__ == "__main__":
    main()
