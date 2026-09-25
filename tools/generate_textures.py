#!/usr/bin/env python3
"""Mooncake Overflow —— 贴图程序化生成器。

设计原则（见 docs/absurd_ideas.md 的性能红线）：
贴图一律程序化生成 + 调色板染色，绝不为每个变体手画一张。
这个脚本负责生成"基准"贴图；后续的氧化度 / 涂蜡 / 馅料变体
应该在资源重载阶段对基准贴图做调色板替换，而不是新增图片。

关于可辨识度，这里遵守三条：
  1. 所有物品尽量铺满画布（半径 7 左右），不要缩在中间
  2. 每样东西用**不同的形状语言**：圆球 / 扁饼 / 带馅扁饼 / 工具 / 带花纹圆盘 / 深色酱
  3. 统一加深色描边 —— 物品栏背景明暗不定，描边是性价比最高的可读性手段

用法：  python3 tools/generate_textures.py
"""

from __future__ import annotations

import math
import os
import random

from PIL import Image

MODID = "mooncake_overflow"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", MODID, "textures")

# ---------------------------------------------------------------- 调色板

CRUST_DARK = (114, 66, 26, 255)
CRUST_MID = (150, 93, 40, 255)
CRUST_LIGHT = (189, 130, 66, 255)
CRUST_HI = (214, 160, 92, 255)

FACE_DARK = (176, 114, 54, 255)
FACE_MID = (205, 143, 74, 255)
FACE_LIGHT = (228, 173, 104, 255)

DOUGH_DARK = (193, 170, 130, 255)
DOUGH_MID = (226, 208, 172, 255)
DOUGH_LIGHT = (242, 229, 202, 255)
DOUGH_HI = (253, 246, 231, 255)

PASTE_DARK = (44, 24, 13, 255)
PASTE_MID = (78, 44, 25, 255)
PASTE_LIGHT = (112, 66, 38, 255)
PASTE_HI = (146, 94, 56, 255)

IRON_DARK = (104, 108, 118, 255)
IRON_MID = (164, 169, 178, 255)
IRON_LIGHT = (212, 217, 224, 255)

WOOD_DARK = (90, 61, 32, 255)
WOOD_MID = (134, 95, 50, 255)
WOOD_LIGHT = (170, 126, 71, 255)

# 统一描边色：偏暖的深褐，比纯黑柔和，但足够把剪影从背景里拉出来
OUTLINE = (56, 36, 20, 255)

# 方块上压印纹路的专用色：必须比面团明显深，否则图案读不出来
IMPRINT_DARK = (168, 133, 84, 255)
IMPRINT_DEEP = (133, 99, 55, 255)

CLEAR = (0, 0, 0, 0)

SIZE = 16
C = 7.5  # 圆心


# ---------------------------------------------------------------- 工具


def new_image() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), CLEAR)


def put(img: Image.Image, x: int, y: int, color) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), color)


def dist(x: float, y: float, sx: float = 1.0, sy: float = 1.0) -> float:
    """到画布中心的椭圆距离。"""
    dx = (x - C) / sx
    dy = (y - C) / sy
    return math.hypot(dx, dy)


def shade(dark, light, t: float):
    """按 t∈[0,1] 在暗色与亮色之间取色，t 越大越亮。"""
    return tuple(int(dark[i] + (light[i] - dark[i]) * t) for i in range(4))


def disc(img, r, colors, squash=1.0, jitter=0.0, seed=0, light_at=(4.5, 4.0)):
    """画一个带光照的椭圆盘：默认左上偏亮。"""
    rng = random.Random(seed)
    dark, light = colors
    lx, ly = light_at
    for y in range(SIZE):
        for x in range(SIZE):
            if dist(x, y, 1.0, squash) > r:
                continue
            if jitter and rng.random() < jitter:
                continue
            t = 1.0 - (math.hypot(x - lx, y - ly) / 11.0)
            put(img, x, y, shade(dark, light, max(0.0, min(1.0, t))))


def ring(img, r_in, r_out, color, squash=1.0):
    for y in range(SIZE):
        for x in range(SIZE):
            if r_in <= dist(x, y, 1.0, squash) <= r_out:
                put(img, x, y, color)


def add_outline(img: Image.Image, color=OUTLINE) -> None:
    """给所有不透明像素外圈补一层描边（只填原本透明的位置）。"""
    src = img.copy()
    for y in range(SIZE):
        for x in range(SIZE):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and src.getpixel((nx, ny))[3] != 0:
                    put(img, x, y, color)
                    break


def scallop(img, r, squash, color, step=30):
    """花瓣状花边：月饼的经典外观。"""
    for ang in range(0, 360, step):
        rad = math.radians(ang)
        put(img,
            int(round(C + r * math.cos(rad))),
            int(round(C + r * squash * math.sin(rad))),
            color)


# ---------------------------------------------------------------- 像素画工具

#: 4×4 Bayer 有序抖动矩阵。渐变靠它做，**不要用随机噪声** ——
#: 随机噪声在 16×16 上就是"脏"，有序抖动才像手画的像素画。
BAYER4 = ((0, 8, 2, 10), (12, 4, 14, 6), (3, 11, 1, 9), (15, 7, 13, 5))


def dither(x: int, y: int, t: float, levels):
    """在 levels 之间按 t（0..1）用有序抖动挑一个颜色。"""
    n = len(levels)
    if t <= 0.0:
        return levels[0]
    if t >= 1.0:
        return levels[-1]
    pos = t * (n - 1)
    i = min(int(pos), n - 2)
    return levels[i + 1] if (pos - i) > ((BAYER4[y % 4][x % 4] + 0.5) / 16.0) else levels[i]


def circle(img: Image.Image, cx: float, cy: float, r: int, color) -> None:
    """中点画圆（Bresenham）—— 1 像素宽的干净圆。

    别用"距离落在某个区间里就涂"那种做法：在 16×16 上会得到一圈断续的糊点。
    """
    x, y, d = r, 0, 1 - r
    while x >= y:
        for px, py in ((x, y), (y, x), (-x, y), (-y, x),
                       (x, -y), (y, -x), (-x, -y), (-y, -x)):
            put(img, int(cx + px), int(cy + py), color)
        y += 1
        if d < 0:
            d += 2 * y + 1
        else:
            x -= 1
            d += 2 * (y - x) + 1


def blob(img, cx: float, cy: float, size: int, color) -> None:
    """在 (cx,cy) 压一个 size×size 方块 —— 16×16 下单像素点是看不见的。"""
    x0, y0 = int(round(cx)), int(round(cy))
    for dx in range(size):
        for dy in range(size):
            put(img, x0 + dx, y0 + dy, color)


def mooncake_pattern(img, main, accent, squash=1.0, scale=1.0):
    """月饼表面的压纹：中心花 + 外圈点。"""
    petals = [(-3, 0), (-2, 0), (2, 0), (3, 0),
              (0, -3), (0, -2), (0, 2), (0, 3),
              (-2, -2), (2, -2), (-2, 2), (2, 2)]
    for dx, dy in petals:
        put(img, int(C + dx * scale), int(C + dy * scale * squash), main)
    for dx, dy in [(0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)]:
        put(img, int(C + dx * scale), int(C + dy * scale * squash), accent)
    for ang in range(0, 360, 45):
        rad = math.radians(ang)
        put(img,
            int(round(C + 4.7 * scale * math.cos(rad))),
            int(round(C + 4.7 * scale * squash * math.sin(rad))),
            main)


# ---------------------------------------------------------------- 物品贴图


def _cocoa_bean(img, cx, cy, base=PASTE_HI, crease=PASTE_DARK):
    """一颗可可豆：小椭圆 + 中间一道沟。"""
    pts = [(1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (3, 1),
           (0, 2), (1, 2), (2, 2), (3, 2), (1, 3), (2, 3)]
    for dx, dy in pts:
        put(img, cx + dx, cy + dy, base)
    put(img, cx + 1, cy + 2, crease)
    put(img, cx + 2, cy + 1, crease)


def item_paste() -> Image.Image:
    """可可豆豆沙：深色酱堆 + 上面摆几颗可可豆，一眼认出是可可。"""
    img = new_image()
    disc(img, 7.0, (PASTE_DARK, PASTE_LIGHT), squash=0.92, jitter=0.10, seed=1,
         light_at=(5.0, 4.5))
    _cocoa_bean(img, 3, 4)
    _cocoa_bean(img, 8, 3)
    _cocoa_bean(img, 6, 8)
    add_outline(img)
    return img


def item_dough() -> Image.Image:
    """月饼面团：一个饱满的圆球，表面光滑无花纹。

    形状语言 = 「球」。和后面的「扁饼」形成第一层区分。
    """
    img = new_image()
    disc(img, 7.2, (DOUGH_DARK, DOUGH_HI), squash=1.0, jitter=0.0, seed=2,
         light_at=(4.8, 4.2))
    # 高光
    for x, y in [(4, 4), (5, 3), (5, 4), (3, 5), (6, 3)]:
        put(img, x, y, DOUGH_HI)
    # 底部阴影
    for x, y in [(9, 11), (10, 11), (11, 10), (8, 12), (11, 9), (10, 12)]:
        put(img, x, y, DOUGH_DARK)
    add_outline(img)
    return img


def item_filled_dough() -> Image.Image:
    """带馅面饼：压扁的饼，捏了一圈辫状花边，底部挤出一小块馅。

    形状语言 = 「扁」。和面团的「球」放在一起绝对不会混。
    刻意只在一处露馅 —— 露两处会被看成两只眼睛，整张图变成一张脸。
    """
    img = new_image()
    squash = 0.58
    disc(img, 7.4, (DOUGH_DARK, DOUGH_HI), squash=squash, jitter=0.0, seed=3,
         light_at=(5.0, 4.5))
    # 辫状花边：深浅交替，做出"捏出来"的珠边
    scallop(img, 7.0, squash, DOUGH_DARK, step=30)
    scallop(img, 6.3, squash, DOUGH_LIGHT, step=30)
    scallop(img, 5.6, squash, DOUGH_DARK, step=30)
    # 底部挤出来的一小块馅
    for x, y in [(6, 10), (7, 10), (8, 10), (9, 10), (7, 11), (8, 11)]:
        put(img, x, y, PASTE_MID)
    put(img, 7, 11, PASTE_DARK)
    put(img, 8, 11, PASTE_DARK)
    add_outline(img)
    return img


def item_mold(square: bool = False) -> Image.Image:
    """月饼模具：铁印头 + 木柄。

    圆模和方模只在**印头花纹**上区分 —— 一个压出圆纹，一个压出方纹。
    """
    img = new_image()
    # 木柄
    for y in range(7, 15):
        for x in range(7, 9):
            put(img, x, y, WOOD_MID if x == 7 else WOOD_DARK)
    put(img, 7, 14, WOOD_DARK)
    put(img, 8, 14, WOOD_DARK)
    put(img, 7, 7, WOOD_LIGHT)
    # 铁印头
    for y in range(1, 7):
        for x in range(2, 14):
            if y == 1:
                put(img, x, y, IRON_LIGHT)
            elif y == 6:
                put(img, x, y, IRON_DARK)
            elif x in (2, 3):
                put(img, x, y, IRON_LIGHT)
            elif x in (12, 13):
                put(img, x, y, IRON_DARK)
            else:
                put(img, x, y, IRON_MID)
    # 印头下沿的花纹：圆模 = 一排点，方模 = 方框
    if square:
        for x in range(4, 12):
            put(img, x, 4, IRON_DARK if x in (4, 11) else IRON_MID)
            put(img, x, 6, IRON_LIGHT if x in (4, 11) else IRON_MID)
        for y in range(4, 7):
            put(img, 4, y, IRON_DARK)
            put(img, 11, y, IRON_DARK)
    else:
        for x in (4, 6, 8, 10, 12):
            put(img, x, 6, IRON_LIGHT)
        for x in (3, 5, 7, 9, 11):
            put(img, x, 5, IRON_DARK)
    add_outline(img)
    return img


def item_mooncake() -> Image.Image:
    """月饼的物品贴图。

    注意：成品月饼现在是**方块物品**，物品栏里渲染的是方块模型，
    所以这张贴图目前只作为备用（例如以后要做"月饼形状的烟花"图标）。
    """
    img = new_image()
    disc(img, 7.3, (CRUST_DARK, CRUST_HI), squash=0.96, jitter=0.0, seed=5,
         light_at=(5.0, 4.5))
    for y in range(SIZE):
        for x in range(SIZE):
            if dist(x, y, 1.0, 0.96) <= 5.6:
                t = 1.0 - (math.hypot(x - 5.0, y - 4.5) / 12.0)
                put(img, x, y, shade(FACE_DARK, FACE_LIGHT, max(0.0, min(1.0, t))))
    ring(img, 5.5, 5.9, CRUST_MID, squash=0.96)
    scallop(img, 7.0, 0.96, CRUST_DARK, step=45)
    mooncake_pattern(img, CRUST_DARK, CRUST_LIGHT, squash=0.96)
    for x, y in [(4, 4), (5, 3), (3, 5)]:
        put(img, x, y, CRUST_HI)
    add_outline(img)
    return img


# ---------------------------------------------------------------- 方块贴图


def block_dough(stamped: bool) -> Image.Image:
    """月饼面团方块的一面（放置在地上的那块面饼）。"""
    img = new_image()
    rng = random.Random(10 if stamped else 11)

    if stamped:
        for y in range(SIZE):
            for x in range(SIZE):
                n = rng.random()
                put(img, x, y, DOUGH_LIGHT if n > 0.88 else DOUGH_MID)
        for i in range(SIZE):
            put(img, i, 0, DOUGH_DARK)
            put(img, i, SIZE - 1, DOUGH_DARK)
            put(img, 0, i, DOUGH_DARK)
            put(img, SIZE - 1, i, DOUGH_DARK)
        ring(img, 5.8, 6.5, IMPRINT_DARK)
        for dx, dy in [(-2, 0), (2, 0), (0, -2), (0, 2),
                       (-1, -1), (1, -1), (-1, 1), (1, 1)]:
            put(img, int(C + dx), int(C + dy), IMPRINT_DEEP)
        for dx, dy in [(0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)]:
            put(img, int(C + dx), int(C + dy), IMPRINT_DARK)
        for ang in range(0, 360, 60):
            rad = math.radians(ang)
            put(img,
                int(round(C + 4.6 * math.cos(rad))),
                int(round(C + 4.6 * math.sin(rad))),
                IMPRINT_DARK)
        return img

    for y in range(SIZE):
        for x in range(SIZE):
            n = rng.random()
            col = DOUGH_MID
            if n < 0.16:
                col = DOUGH_DARK
            elif n > 0.86:
                col = DOUGH_LIGHT
            put(img, x, y, col)
    for i in range(SIZE):
        put(img, i, 0, DOUGH_DARK)
        put(img, i, SIZE - 1, DOUGH_DARK)
        put(img, 0, i, DOUGH_DARK)
        put(img, SIZE - 1, i, DOUGH_DARK)
    return img


def block_mooncake_dough_side() -> Image.Image:
    """月饼面团方块的侧面：4 像素高，一条面团边。"""
    img = new_image()
    for y in range(SIZE):
        for x in range(SIZE):
            col = DOUGH_MID
            if y < 5:
                col = DOUGH_LIGHT
            elif y > 10:
                col = DOUGH_DARK
            put(img, x, y, col)
    for x in range(0, SIZE, 4):
        put(img, x, 7, DOUGH_DARK)
        put(img, x, 8, DOUGH_DARK)
    return img


def block_raw_mooncake_top() -> Image.Image:
    """生月饼方块的顶面：实心方形，中间是压好的纹路。"""
    img = new_image()
    rng = random.Random(20)
    for y in range(SIZE):
        for x in range(SIZE):
            n = rng.random()
            put(img, x, y, DOUGH_LIGHT if n > 0.9 else DOUGH_MID)
    for i in range(SIZE):
        put(img, i, 0, DOUGH_DARK)
        put(img, i, SIZE - 1, DOUGH_DARK)
        put(img, 0, i, DOUGH_DARK)
        put(img, SIZE - 1, i, DOUGH_DARK)
    ring(img, 6.0, 6.7, IMPRINT_DEEP)
    mooncake_pattern(img, IMPRINT_DEEP, IMPRINT_DARK)
    return img


def block_raw_mooncake_side() -> Image.Image:
    """生月饼方块的侧面：3 像素高，做成一条饼边。"""
    img = new_image()
    for y in range(SIZE):
        for x in range(SIZE):
            col = DOUGH_MID
            if y < 4:
                col = DOUGH_LIGHT
            elif y > 11:
                col = DOUGH_DARK
            put(img, x, y, col)
    for x in range(0, SIZE, 5):
        put(img, x, 7, DOUGH_DARK)
        put(img, x, 8, DOUGH_DARK)
    return img


def _sc(v: float, s: float) -> int:
    """把坐标按 s 围绕中心缩放 —— 纹样要缩小才塞得进铜圈里。"""
    return int(round(C + (v - C) * s))


#: 圆形饼模：12×12 的手写像素图，印章式同心纹。
#: `#` = 压线（暗），`o` = 亮线 —— 外暗内亮是浮雕感的来源。
#: 用显式像素图而不是几何算法：16×16 上"距离落在区间里"那种画法必然走样。
MAP_ROUND = (
    "...######...",
    ".##......##.",
    ".#..####..#.",
    "##.##oo##.##",
    "#..#oooo#..#",
    "#.#.oooo.#.#",
    "#.#.oooo.#.#",
    "#..#oooo#..#",
    "##.##oo##.##",
    ".#..####..#.",
    ".##......##.",
    "...######...",
)


def stamp_map(img, rows, s: float = 1.0) -> None:
    """把 12×12 的像素图盖在饼面正中（坐标按 s 缩放，铜圈里的纹样要缩小让位）。"""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            color = CRUST_DARK if ch == "#" else (CRUST_HI if ch == "+" else CRUST_LIGHT)
            px = _sc(2 + x, s)
            py = _sc(2 + y, s)
            if 0 <= px < SIZE and 0 <= py < SIZE:
                put(img, px, py, color)


def _pattern_round(img, s: float = 1.0) -> None:
    """圆形：印章式同心纹（手写像素图）。"""
    stamp_map(img, MAP_ROUND, s)
    put(img, _sc(7.5, s), _sc(7.5, s), CRUST_HI)


def _pattern_square(img, s: float = 1.0) -> None:
    """方形：三层同心方框 + 花心，和圆纹同一套"外暗内亮"的画法。"""
    for lo, hi, color in ((1, 12, CRUST_DARK), (3, 10, CRUST_LIGHT),
                          (4, 9, CRUST_DARK), (6, 7, CRUST_LIGHT)):
        for i in range(_sc(2 + lo, s), _sc(2 + hi, s) + 1):
            for v in (_sc(2 + lo, s), _sc(2 + hi, s)):
                put(img, i, v, color)
                put(img, v, i, color)
    put(img, _sc(7.5, s), _sc(7.5, s), CRUST_HI)


def _pattern_flower(img, s: float = 1.0) -> None:
    """花形：五瓣（3×3 的块，太小的花瓣在 16×16 上看不见）+ 花心。"""
    for cx, cy in ((7.5, 3.5), (4.4, 6.2), (10.6, 6.2), (5.6, 10.4), (9.4, 10.4)):
        blob(img, _sc(2 + cx - 2, s), _sc(2 + cy - 2, s), 3, CRUST_DARK)
    blob(img, _sc(6.0, s), _sc(6.0, s), 3, CRUST_LIGHT)
    put(img, _sc(7.5, s), _sc(7.5, s), CRUST_HI)


# ---------------------------------------------------------------- 氧化

# 四个氧化阶段：越往后越往青绿（铜绿 / verdigris）偏，并撒上斑块。
# 用「向目标色混合」而不是「乘一个系数」—— 因为橙金色乘任何系数都变不出青绿（蓝通道本来就最低）。
OXIDATION_TARGET = {
    "copper": (0, 0, 0),
    "tarnished": (152, 148, 104),
    "rusted": (114, 150, 108),
    "oxidized": (86, 150, 132),
}
OXIDATION_MIX = {"copper": 0.0, "tarnished": 0.35, "rusted": 0.60, "oxidized": 0.80}
PATINA_DENSITY = {"copper": 0.0, "tarnished": 0.10, "rusted": 0.22, "oxidized": 0.34}
PATINA = (74, 138, 112, 255)

OXIDATIONS = ("copper", "tarnished", "rusted", "oxidized")


# ---------------------------------------------------------------- 铜圈

# 「包了一圈铜」的视觉：饼面外面套一圈**有金属反光**的铜边。
# 关键是和饼皮区分开 —— 饼皮本身就是橙金色的，所以铜圈必须更饱和、更亮、有高光和压深线。
COPPER_HI = (250, 200, 150, 255)
COPPER_LIGHT = (226, 146, 88, 255)
COPPER_MID = (183, 96, 50, 255)
COPPER_DARK = (124, 60, 30, 255)
COPPER_EDGE = (70, 33, 16, 255)

#: 铜圈厚度（像素）。饼面上的纹样会按 COPPER_PATTERN_SCALE 缩小给它让位
BAND = 2
#: 纹样缩放。试过 0.72 —— 八瓣花会挤成一团糊，0.88 是还能看清纹样的下限
COPPER_PATTERN_SCALE = 0.88


def copper_band(img: Image.Image) -> None:
    """在饼面外面套一圈铜边（原地修改）。

    光照和饼面保持一致（左上打光）：上/左亮、下/右暗。
    """
    for i in range(SIZE):
        # 外圈：上/左受光，下/右背光
        put(img, i, 0, COPPER_LIGHT)
        put(img, 0, i, COPPER_LIGHT)
        put(img, i, SIZE - 1, COPPER_DARK)
        put(img, SIZE - 1, i, COPPER_DARK)
        # 内一圈
        put(img, i, 1, COPPER_MID)
        put(img, 1, i, COPPER_MID)
        put(img, i, SIZE - 2, COPPER_DARK)
        put(img, SIZE - 2, i, COPPER_DARK)

    # 高光点：让铜圈看起来是一圈金属而不是一条色带
    for x, y in [(0, 0), (SIZE - 1, 0), (0, SIZE - 1), (SIZE - 1, SIZE - 1)]:
        put(img, x, y, COPPER_HI)
    for i in range(4, SIZE - 4, 2):
        put(img, i, 0, COPPER_HI)
        put(img, 0, i, COPPER_HI)

    # 内圈压深，把铜圈和饼面分开
    for i in range(BAND, SIZE - BAND):
        put(img, i, BAND, COPPER_EDGE)
        put(img, i, SIZE - 1 - BAND, COPPER_EDGE)
        put(img, BAND, i, COPPER_EDGE)
        put(img, SIZE - 1 - BAND, i, COPPER_EDGE)


def oxidize(img: Image.Image, stage: str, seed: int = 7) -> Image.Image:
    """按氧化阶段调色 + 撒铜绿斑点。"""
    mix = OXIDATION_MIX[stage]
    target = OXIDATION_TARGET[stage]
    rng = random.Random(seed)

    out = new_image()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            r = int(r * (1 - mix) + target[0] * mix)
            g = int(g * (1 - mix) + target[1] * mix)
            b = int(b * (1 - mix) + target[2] * mix)
            put(out, x, y, (r, g, b, a))

    density = PATINA_DENSITY[stage]
    if density > 0:
        for y in range(SIZE):
            for x in range(SIZE):
                base = out.getpixel((x, y))
                if base[3] == 0 or rng.random() >= density:
                    continue
                put(out, x, y, tuple(int(p * 0.45 + q * 0.55) for p, q in zip(base, PATINA)))
    return out


def block_mooncake_top(pattern: str, s: float = 1.0) -> Image.Image:
    """月饼方块顶面 —— 作者化像素画，不是算法噪声。

    三层结构（从外到内）：
      1. 外圈 1 像素压深（饼模的外沿）
      2. 2 像素**褶边**：每 2 像素一瓣，上/左受光、下/右背光 —— 真月饼的裙边
      3. 饼面：中心略鼓的柔和渐变（**有序抖动**，没有随机噪声）+ 压纹

    {@code s} 是纹样缩放系数 —— 铜月饼的饼面被铜圈占掉一圈，纹样要缩小让位。
    """
    img = new_image()

    levels = (FACE_DARK, FACE_MID, FACE_LIGHT)
    for y in range(SIZE):
        for x in range(SIZE):
            t = 0.62 - (dist(x, y, 1.0, 1.0) - 3.0) / 11.0 - ((x + y) - 15) / 90.0
            put(img, x, y, dither(x, y, t, levels))

    for i in range(SIZE):
        put(img, i, 0, CRUST_DARK)
        put(img, i, SIZE - 1, CRUST_DARK)
        put(img, 0, i, CRUST_DARK)
        put(img, SIZE - 1, i, CRUST_DARK)

    for i in range(1, SIZE - 1):
        pleat = (i // 2) % 2 == 0
        put(img, i, 1, CRUST_LIGHT if pleat else CRUST_MID)
        put(img, 1, i, CRUST_LIGHT if pleat else CRUST_MID)
        put(img, i, SIZE - 2, CRUST_MID if pleat else CRUST_DARK)
        put(img, SIZE - 2, i, CRUST_MID if pleat else CRUST_DARK)

    for i in range(2, SIZE - 2):
        put(img, i, 2, CRUST_MID)
        put(img, 2, i, CRUST_MID)
        put(img, i, SIZE - 3, CRUST_MID)
        put(img, SIZE - 3, i, CRUST_MID)

    {"round": _pattern_round, "square": _pattern_square, "flower": _pattern_flower}[pattern](img, s)
    return img


def block_mooncake_top_copper(pattern: str) -> Image.Image:
    """铜月饼顶面：先在饼皮上压好缩小版纹样，再套铜圈。

    顺序很重要 —— 先纹样后铜圈，铜圈才能干净地盖在外面不吃到纹样。
    """
    img = block_mooncake_top(pattern, s=COPPER_PATTERN_SCALE)
    copper_band(img)
    return img


def block_mooncake_side() -> Image.Image:
    """月饼方块侧面：一条金黄的饼边，带竖褶（不是几道横杠）。

    上 2 像素接着顶面的褶边，中间是竖褶，下 2 像素压深。
    """
    img = new_image()
    for y in range(SIZE):
        for x in range(SIZE):
            if y < 2:
                col = CRUST_LIGHT if (x // 2) % 2 == 0 else CRUST_MID
            elif y > SIZE - 3:
                col = CRUST_DARK
            else:
                lit = x % 2 == 0
                k = max(0.0, (y - SIZE // 2) / float(SIZE // 2))
                col = shade(FACE_LIGHT if lit else FACE_MID, CRUST_DARK, k * 0.55)
            put(img, x, y, col)
    return img


def block_mooncake_side_copper() -> Image.Image:
    """铜月饼侧面：整条金属铜边（上亮下暗），顶部留一条饼皮。"""
    img = new_image()
    for y in range(SIZE):
        for x in range(SIZE):
            if y < 3:
                col = FACE_MID if y > 0 else CRUST_MID
            elif y < 5:
                col = COPPER_LIGHT
            elif y < 13:
                col = COPPER_MID
            else:
                col = COPPER_DARK
            put(img, x, y, col)
    # 金属高光 + 接缝，避免看起来只是一块纯色
    for x in range(1, SIZE - 1, 3):
        put(img, x, 5, COPPER_HI)
        put(img, x, 6, COPPER_LIGHT)
    for x in range(0, SIZE, 5):
        put(img, x, 12, COPPER_DARK)
    return img


def block_mooncake_composite_top() -> Image.Image:
    """缝合月饼的顶面：四个象限各取一种氧化度拼起来，一眼就看得出是"拼的"。"""
    img = new_image()
    stages = ("copper", "tarnished", "rusted", "oxidized")
    for i, stage in enumerate(stages):
        src = oxidize(block_mooncake_top("round"), stage)
        x0 = (i % 2) * (SIZE // 2)
        y0 = (i // 2) * (SIZE // 2)
        for y in range(SIZE // 2):
            for x in range(SIZE // 2):
                put(img, x0 + x, y0 + y, src.getpixel((x0 + x, y0 + y)))
    return img


def block_mooncake_filling() -> Image.Image:
    """切面：豆沙馅 + 一点碎屑。

    给"四分之一块"**切开的那两个面**用 —— 一眼看出是切开的，而不是一块小月饼。
    馅不跟着氧化，所以只用一套。
    """
    img = new_image()
    levels = (PASTE_DARK, PASTE_MID, PASTE_LIGHT)
    for y in range(SIZE):
        for x in range(SIZE):
            put(img, x, y, dither(x + 3, y + 5, 0.30 + (x + y) / 40.0, levels))
    for x, y in ((3, 4), (10, 3), (5, 11), (12, 9), (7, 7), (2, 9), (13, 5)):
        put(img, x, y, PASTE_HI)
    return img


def save(img: Image.Image, rel: str) -> str:
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def main() -> None:
    outputs = {
        "item/cocoa_bean_paste.png": item_paste(),
        "item/mooncake_dough.png": item_dough(),
        "item/filled_mooncake_dough.png": item_filled_dough(),
        "item/mooncake_mold.png": item_mold(False),
        "item/square_mooncake_mold.png": item_mold(True),
        "item/mooncake.png": item_mooncake(),
        "block/mooncake_dough_block.png": block_dough(False),
        "block/mooncake_dough_block_stamped.png": block_dough(True),
        "block/mooncake_dough_side.png": block_mooncake_dough_side(),
        "block/raw_mooncake_top.png": block_raw_mooncake_top(),
        "block/raw_mooncake_side.png": block_raw_mooncake_side(),
        "block/mooncake_dough_side.png": block_mooncake_dough_side(),
    }

    # 普通月饼：3 纹样，**不氧化**，所以每个纹样只有一张贴图
    for pattern in ("round", "square", "flower"):
        outputs[f"block/mooncake_top_{pattern}_plain.png"] = block_mooncake_top(pattern)
    outputs["block/mooncake_side_plain.png"] = block_mooncake_side()
    # 缝合月饼（四块拼回来那一块）的顶面
    outputs["block/mooncake_composite_top.png"] = block_mooncake_composite_top()
    # 切面（四分之一块的内侧两面）
    outputs["block/mooncake_filling.png"] = block_mooncake_filling()

    # 铜月饼：饼面外面包了一圈铜，所以是 3 纹样 × 4 氧化度
    for pattern in ("round", "square", "flower"):
        base_copper = block_mooncake_top_copper(pattern)
        for stage in OXIDATIONS:
            outputs[f"block/copper_mooncake_top_{pattern}_{stage}.png"] = oxidize(
                base_copper, stage, seed=hash(pattern) % 97)
    base_copper_side = block_mooncake_side_copper()
    for stage in OXIDATIONS:
        outputs[f"block/copper_mooncake_side_{stage}.png"] = oxidize(base_copper_side, stage)

    for rel, img in outputs.items():
        print("wrote", os.path.relpath(save(img, rel), ROOT))

    # 放大预览，方便人眼检查可辨识度
    scale = 8
    sheet = Image.new("RGBA", (SIZE * scale * len(outputs), SIZE * scale), (30, 30, 36, 255))
    for i, img in enumerate(outputs.values()):
        big = img.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
        sheet.alpha_composite(big, (i * SIZE * scale, 0))
    preview = "/tmp/mooncake_textures_preview.png"
    sheet.save(preview)
    print("preview ->", preview)


if __name__ == "__main__":
    main()
