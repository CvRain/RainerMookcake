#!/usr/bin/env python3
"""Mooncake Overflow —— 资源一致性检查器。

背景：Minecraft 26.1 起，物品模型的**入口**从 `models/item/<id>.json`
换成了 `items/<id>.json`（里面写一个 `minecraft:model` 指向真正的模型）。
只写旧的 `models/item/` 不会报错，但物品在游戏里会渲染成紫黑格 ——
这个坑已经踩过一次，所以写个脚本把它钉死。

检查项：
  1. 每个注册的物品都有 assets/<ns>/items/<id>.json
  2. 每个注册的方块都有 assets/<ns>/blockstates/<id>.json
  3. items/ 与 blockstates/ 里引用的模型文件真实存在
  4. 模型里引用的贴图文件真实存在
  5. lang 文件里每个物品/方块都有对应的键

用法：  python3 tools/check_resources.py
退出码非 0 表示有问题。
"""

from __future__ import annotations

import json
import os
import re
import sys

MODID = "mooncake_overflow"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(SRC, "assets", MODID)

REGISTER_RE = re.compile(r'register\(\s*"([a-z0-9_]+)"')
# public static final RegistryObject<Block> FOO = BLOCKS.register("foo_bar", ...)
BLOCK_CONST_RE = re.compile(
    r'RegistryObject<\s*Block\s*>\s+([A-Z0-9_]+)\s*=\s*BLOCKS\.register\(\s*"([a-z0-9_]+)"')
# ITEMS.register("my_item", () -> new BlockItem(ModBlocks.FOO.get(), ...))
# 也要匹配自定义子类，例如 new MooncakeBlockItem(ModBlocks.BAR.get(), ...)
BLOCK_ITEM_RE = re.compile(
    r'ITEMS\.register\(\s*"([a-z0-9_]+)"\s*,\s*\(\)\s*->\s*new\s+\w*BlockItem\(\s*ModBlocks\.([A-Z0-9_]+)')

errors: list[str] = []
warnings: list[str] = []


def read(path: str) -> str:
    with open(path, encoding="utf-8") as fh:
        return fh.read()


def find_registered(java_rel: str) -> list[str]:
    path = os.path.join(ROOT, "src", "main", "java", "org", "cvrain",
                        "mooncakeoverflow", java_rel)
    if not os.path.exists(path):
        return []
    return REGISTER_RE.findall(read(path))


def collect_model_refs(node) -> list[str]:
    """递归收集物品模型定义里所有 `"model": "ns:path"` 形式的引用。

    26.1 的物品模型可以是 `select` / `condition` / `composite` 等嵌套结构
    （例如按组件值切换外观），所以不能只读顶层那个 model 字段。
    """
    refs: list[str] = []
    if isinstance(node, dict):
        for key, value in node.items():
            if key == "model" and isinstance(value, str):
                refs.append(value)
            else:
                refs.extend(collect_model_refs(value))
    elif isinstance(node, list):
        for entry in node:
            refs.extend(collect_model_refs(entry))
    return refs


def java_file(java_rel: str) -> str:
    return read(os.path.join(ROOT, "src", "main", "java", "org", "cvrain",
                             "mooncakeoverflow", java_rel))


def block_item_map() -> dict[str, str]:
    """物品 id -> 它挂载的方块 id。

    BlockItem 的显示名走 `block.<ns>.<方块id>`，而方块 id 未必等于物品 id
    （例如物品 filled_mooncake_dough 挂的是 mooncake_dough_block），
    所以必须把这段关系解析出来，否则语言文件检查会误报。
    """
    const_to_block = dict(
        (const, bid) for const, bid in BLOCK_CONST_RE.findall(java_file("registry/ModBlocks.java")))
    mapping: dict[str, str] = {}
    for item_id, const in BLOCK_ITEM_RE.findall(java_file("registry/ModItems.java")):
        block_id = const_to_block.get(const)
        if block_id:
            mapping[item_id] = block_id
    return mapping


def load_json(path: str):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def model_path(ref: str) -> str:
    """把 `namespace:path` 或 `path` 解析成模型文件的绝对路径。"""
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = "minecraft", ref
    return os.path.join(SRC, "assets", ns, "models", path + ".json")


def texture_path(ref: str) -> str:
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = "minecraft", ref
    return os.path.join(SRC, "assets", ns, "textures", path + ".png")


def check_model_exists(ref: str, where: str, allow_vanilla_missing: bool = True) -> None:
    path = model_path(ref)
    if os.path.exists(path):
        return
    if ref.startswith("minecraft:") and allow_vanilla_missing:
        return  # 原版模型在 client-extra 里，这里不校验
    errors.append(f"{where}: 引用的模型不存在 -> {ref}  ({os.path.relpath(path, ROOT)})")


def check_textures(ref: str, where: str) -> None:
    path = texture_path(ref)
    if os.path.exists(path):
        return
    if ref.startswith("minecraft:"):
        return
    errors.append(f"{where}: 引用的贴图不存在 -> {ref}  ({os.path.relpath(path, ROOT)})")


def main() -> int:
    items = find_registered("registry/ModItems.java")
    blocks = find_registered("registry/ModBlocks.java")

    if not items:
        errors.append("没能从 ModItems.java 解析出任何物品，脚本可能失效了")

    print(f"物品 {len(items)} 个: {', '.join(items)}")
    print(f"方块 {len(blocks)} 个: {', '.join(blocks)}")
    print()

    # --- 1. items/<id>.json 必须存在（26.1 的物品模型入口）---
    items_dir = os.path.join(ASSETS, "items")
    for item in items:
        entry = os.path.join(items_dir, f"{item}.json")
        if not os.path.exists(entry):
            errors.append(f"物品 {item}: 缺少 {MODID}:items/{item}.json（26.1 必需，否则渲染成紫黑格）")
            continue
        refs = collect_model_refs(load_json(entry))
        if not refs:
            errors.append(f"items/{item}.json: 没找到任何模型引用")
            continue
    for ref in refs:
            check_model_exists(ref, f"items/{item}.json")

    # --- 2. blockstates/<id>.json 必须存在 ---
    for block in blocks:
        entry = os.path.join(ASSETS, "blockstates", f"{block}.json")
        if not os.path.exists(entry):
            errors.append(f"方块 {block}: 缺少 blockstates/{block}.json")
            continue
        data = load_json(entry)
        for variant, body in data.get("variants", {}).items():
            models = body if isinstance(body, list) else [body]
            for m in models:
                if "model" in m:
                    check_model_exists(m["model"], f"blockstates/{block}.json({variant})")
        # multipart 形式（按方块状态分片拼模型，月饼方块用的就是这种）
        for i, part in enumerate(data.get("multipart", [])):
            apply = part.get("apply")
            entries = apply if isinstance(apply, list) else [apply]
            for m in entries:
                if isinstance(m, dict) and "model" in m:
                    check_model_exists(m["model"], f"blockstates/{block}.json(multipart#{i})")

    # --- 3. items/ 下有没有多余的（注册名对不上）---
    if os.path.isdir(items_dir):
        for fname in sorted(os.listdir(items_dir)):
            if fname.endswith(".json") and fname[:-5] not in items:
                warnings.append(f"items/{fname} 没有对应的已注册物品（是否重命名后忘了删？）")

    # --- 4. 递归检查模型里的贴图引用 ---
    checked: set[str] = set()

    def walk(ref: str) -> None:
        path = model_path(ref)
        if not os.path.exists(path) or path in checked:
            return
        checked.add(path)
        data = load_json(path)
        parent = data.get("parent")
        if parent:
            if not parent.startswith("minecraft:"):
                walk(parent)
            else:
                mp = model_path(parent)
                if os.path.exists(mp):
                    walk(parent)
        for key, value in (data.get("textures") or {}).items():
            if isinstance(value, str) and not value.startswith("#"):
                check_textures(value, os.path.relpath(path, ROOT))

        # 面里的贴图**必须**写成 #别名。
        # 直接写路径即使文件存在，26.1 的模型加载器也不认 ——
        # 表现是物品图标变成紫黑格子（四分之一块就这么翻过一次车）。
        for element in data.get("elements", []):
            for face_name, face in (element.get("faces") or {}).items():
                raw = face.get("texture", "")
                if raw and not raw.startswith("#"):
                    errors.append(
                        f"{os.path.relpath(path, ROOT)}: 面 {face_name} 直接写了贴图 {raw}，"
                        f"必须改成 #别名（否则渲染成紫黑格）")

    # 兜底：把 models/ 下**每一个**模型都走一遍。
    #
    # 只查"items/*.json 能引用到的"是不够的 —— select / condition 分支里的模型
    # （四分之一块就是）从入口根本走不到，坏了也查不出来。
    # 四分之一块的贴图丢失就是这么溜过去的。
    for sub in ("block", "item"):
        folder = os.path.join(ASSETS, "models", sub)
        for root, _dirs, files in os.walk(folder):
            for fname in files:
                if not fname.endswith(".json"):
                    continue
                rel = os.path.relpath(os.path.join(root, fname), folder)
                walk(f"{MODID}:{sub}/{rel[:-5]}")

    # 物品图标必须全部是**平铺贴图**（item/ 下的 item/generated 模型）。
    #
    # 原版的食物都是平铺贴图；立体方块模型在 16×16 的格子里只占中间一小块，
    # 和月饼图标混在一起风格就不统一了。
    #
    # 注意：select / condition 的**每一个分支都要走到**，包括 fallback ——
    # "默认圆形月饼"恰恰就在 fallback 里，漏过它一次（而且当时的自查脚本
    # 递归写错了，报了个假的 ✅）。所以这里直接遍历整棵 JSON 树。
    def icon_models(node, out: list) -> list:
        if isinstance(node, dict):
            if node.get("type") == "minecraft:model" and node.get("model"):
                out.append(node["model"])
            for key, value in node.items():
                if key == "model" and node.get("type") != "minecraft:model":
                    icon_models(value, out)
                elif key in ("on_true", "on_false", "fallback"):
                    icon_models(value, out)
                elif key == "cases":
                    for case in value or []:
                        icon_models(case.get("model"), out)
        return out

    for item in items:
        path = os.path.join(items_dir, f"{item}.json")
        if not os.path.exists(path):
            continue
        for model_ref in dict.fromkeys(icon_models(load_json(path), [])):
            if ":block/" in model_ref:
                errors.append(f"物品 {item}: 图标用了立体模型 {model_ref}，"
                              f"应改成 item/ 下的平铺图标（否则和月饼图标风格不统一）")

    for item in items:
        entry = os.path.join(items_dir, f"{item}.json")
        if os.path.exists(entry):
            ref = load_json(entry).get("model", {}).get("model")
            if ref:
                walk(ref)
    for block in blocks:
        entry = os.path.join(ASSETS, "blockstates", f"{block}.json")
        if os.path.exists(entry):
            data = load_json(entry)
            for body in data.get("variants", {}).values():
                for m in (body if isinstance(body, list) else [body]):
                    if "model" in m:
                        walk(m["model"])
            for part in data.get("multipart", []):
                apply = part.get("apply")
                for m in (apply if isinstance(apply, list) else [apply]):
                    if isinstance(m, dict) and "model" in m:
                        walk(m["model"])

    # --- 5. lang 键 ---
    block_items = block_item_map()
    for lang in ("en_us", "zh_cn"):
        path = os.path.join(ASSETS, "lang", f"{lang}.json")
        if not os.path.exists(path):
            errors.append(f"缺少语言文件 {lang}.json")
            continue
        data = load_json(path)
        for item in items:
            # 26.1：方块物品的名字前缀由 Item.Properties#useBlockDescriptionPrefix() 决定，
            # 但键里的 path 用的是【物品自己的 id】，不是方块 id。
            # 例如物品 filled_mooncake_dough 的键是 block.<ns>.filled_mooncake_dough，
            # 而不是 block.<ns>.mooncake_dough_block。
            key = f"block.{MODID}.{item}" if item in block_items else f"item.{MODID}.{item}"
            if key not in data:
                errors.append(f"{lang}.json 缺少名称键: {key}")
        for block in blocks:
            key = f"block.{MODID}.{block}"
            if key not in data:
                errors.append(f"{lang}.json 缺少名称键: {key}")

    # --- 6. 语言文件里的死键 ---
    live: set[str] = {f"item.{MODID}.{i}" for i in items if i not in block_items}
    live |= {f"block.{MODID}.{i}" for i in block_items}   # 方块物品的名字键
    live |= {f"block.{MODID}.{b}" for b in blocks}        # 方块自己的名字键
    live.add(f"itemGroup.{MODID}.mooncakes")
    # 形态名是代码里拼出来的键（MooncakeKind#nameKey / #copperNameKey），检查器看不到，显式登记
    live |= {f"{MODID}.mooncake.kind.{k}" for k in (
        "round_round", "round_square", "round_flower",
        "square_round", "square_square", "square_flower")}
    live |= {f"{MODID}.copper_mooncake.kind.{k}" for k in (
        "round_round", "round_square", "round_flower",
        "square_round", "square_square", "square_flower")}
    # 氧化前缀（MooncakeOxidation#prefixKey）和涂蜡前缀（MooncakeBlockItem#getName）
    live |= {f"{MODID}.oxidation.{k}" for k in ("tarnished", "rusted", "oxidized")}
    live.add(f"{MODID}.waxed")
    # 四分之一块的角名和名字模板（MooncakeQuarterItem / PileCell#translationKey）
    live |= {f"{MODID}.corner.{c}" for c in ("nw", "ne", "sw", "se")}
    live.add(f"{MODID}.quarter")
    live.add(f"{MODID}.five_kernel")
    live.add(f"{MODID}.quadrant")
    for lang in ("en_us", "zh_cn"):
        path = os.path.join(ASSETS, "lang", f"{lang}.json")
        if not os.path.exists(path):
            continue
        for key in load_json(path):
            if key not in live:
                warnings.append(f"{lang}.json 里有没人用的键: {key}")

    print(f"检查了 {len(checked)} 个模型文件")
    print()
    for w in warnings:
        print("  [警告]", w)
    for e in errors:
        print("  [错误]", e)
    if not errors and not warnings:
        print("✅ 全部通过")
    elif not errors:
        print("✅ 没有致命问题（只有警告）")
    else:
        print(f"❌ {len(errors)} 个致命问题")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
