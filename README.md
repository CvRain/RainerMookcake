# Mooncake Overflow

> **347 Variants, None of Them Good.**
>
> 月饼溢出 —— 把中秋月饼塞进《我的世界》，然后把它氧化、涂蜡、切制、切开四份、再拼回去。

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1-62b47a)](#环境要求)
[![Forge](https://img.shields.io/badge/Forge-62.0.9-dfb469)](#环境要求)
[![License](https://img.shields.io/badge/License-MIT-blue)](#许可)

---

## 目录

- [这是什么](#这是什么)
- [核心设计：正交轴](#核心设计正交轴)
- [阶段一：月饼本体](#阶段一月饼本体已实现)
- [阶段二：氧化与涂蜡](#阶段二氧化与涂蜡已实现)
- [阶段三：方块实体 + 渲染器](#阶段三方块实体--渲染器已实现)
- [阶段四：切四片与缝合](#阶段四切四片与缝合已实现)
- [开发状态](#开发状态)
- [环境要求](#环境要求)
- [构建](#构建)
- [运行](#运行)
- [Linux / Wayland 已知问题](#linux--wayland-已知问题)
- [目录结构](#目录结构)
- [性能原则](#性能原则)
- [资源与命名约定](#资源与命名约定)
- [文档](#文档)
- [许可](#许可)

---

## 这是什么

一个搞怪向的 Minecraft Forge 模组，主题是**月饼**。

灵感来自 MC 圈那个经典段子：

> 你说得对，竖半砖的加入会带来无数种变体，会对内存和帧率带来影响……
> 但是铜块、斑驳的铜块、锈蚀的铜块、氧化的铜块、涂蜡的铜块、涂蜡的斑驳铜块……
> （下略 80 余种，最后以"不会"结尾）

既然铜块能有 300 多种变体，**月饼凭什么不能**。

玩法核心是把月饼做成一套**正交轴**，让名字、贴图和效果自动组合出来；
再叠加中秋神话（玉兔、嫦娥、后羿）和一点元游戏式的自嘲。
最终形态是一个"看起来在认真做月饼，实际在认真玩梗"的模组。

## 核心设计：正交轴

| 轴 | 取值 | 作用 |
|---|---|---|
| **馅料** | 可可豆 / 南瓜 / 莲蓉 / 五仁 / 豆沙 / … | 基础食用效果 |
| **包铜** | 是 / 否 | **只有包了铜的月饼才会氧化**；普通月饼放多久都不会坏 |
| **氧化度** | 铜 / 斑驳 / 锈蚀 / 氧化 | 影响硬度（扣血量）与饱食度 |
| **涂蜡** | 是 / 否 | 蜜脾右键，锁死氧化度 |
| **加工形态** | 面团 / 生胚 / 烤熟 / 切制 / 竖半砖 / 月饼块 / 台阶 / 楼梯 / … | 复刻铜块全家桶 |
| **象限** | 1/4 ～ 4/4，每象限独立记录馅料与氧化度 | 切开再拼合 |
| **内容物** | 任意物品（`DataComponent`） | 月饼是容器 |

名字由轴组合**自动生成**：

```
涂蜡的斑驳的切制莲蓉半月铜月饼
氧化五仁竖半砖铜月饼
```

轴一多，名字长度就会指数增长 —— **放任它溢出 tooltip 宽度**，并给一个成就「名称溢出」。

关键实现原则：**轴走数据包驱动**，不手写几百个 `register()`。
这样玩家可以自己注册馅料，变体数量无限而代码零增长。

## 阶段一：月饼本体（已实现）

完整的工艺链，全部可在生存模式里跑通：

```
可可豆 + 装水的炼药锅      →  可可豆豆沙
4 小麦 + 糖 + 鸡蛋          →  4× 月饼面团
4 月饼面团 + 豆沙           →  4× 带馅面饼（四分之一个方块大小，可放置）
手持【圆形模具】右键压印    →  圆形的生月饼面团（压扁一点）
手持【方形模具】右键压印    →  方形的生月饼面团
空手右键压印好的面团        →  生圆月饼 / 生方月饼（形状在这里定型）
熔炉 / 烟熏炉 / 营火烤      →  月饼（形状带进成品）
                              右键吃（随时可吃）
                              Shift + 右键摆到地上，一格最多叠 4 块
切石机（月饼 → 月饼）       →  6 种形态任选：形状 × 纹样
```

然后进入阶段二：

```
月饼 + 铜锭                 →  铜月饼（形态原样保留）
切石机（铜月饼 → 铜月饼）   →  ✗ 没有这条 —— 见下方「为什么铜月饼不能切」
```

### 两个轴

| 轴 | 取值 | 决定什么 | 在哪设置 |
|---|---|---|---|
| **形状** | 圆 / 方 | 方块本身的**几何外形** | **模具**（压印时定型） |
| **纹样** | 圆纹 / 方纹 / 花形纹 | 表面的**花纹贴图** | **切石机**（烤好之后随时改） |

2 × 3 = **6 种形态**，所以「方纹的圆月饼」和「圆纹的方月饼」都存在。

**为什么生月饼要拆成两个物品，成品却不用：**

- 熔炉配方**读不到输入物品的组件**，所以形状想活过熔炉，只能让**原料本身不同**
  → `raw_mooncake` / `square_raw_mooncake` 两个物品
- 但配方**结果可以带组件**，所以两次熔炉各自产出同一个 `mooncake`，
  只是带上不同的 `mooncake_kind` → 成品不用拆

### 内容清单

| 类型 | ID | 中文名 | English |
|---|---|---|---|
| 物品 | `cocoa_bean_paste` | 可可豆豆沙 | Cocoa Bean Paste |
| 物品 | `mooncake_dough` | 月饼面团 | Mooncake Dough |
| 方块 + 物品 | `filled_mooncake_dough` | 带馅面饼 | Filled Mooncake Dough |
| 物品 | `mooncake_mold` | 月饼模具 | Mooncake Mold |
| 方块 + 物品 | `raw_mooncake` | 生月饼 | Raw Mooncake |
| 方块 + 物品 | `mooncake` | 月饼（不会氧化） | Mooncake |
| 方块 | `mooncake_dough_block` | 带馅面饼（`stamped` 状态） | Filled Mooncake Dough |
| 方块 | `mooncake_block` | 普通月饼堆（四格形态） | Mooncake |
| 方块 + 物品 | `copper_mooncake` | 铜月饼（**会氧化**） | Copper Mooncake |
| 方块 | `copper_mooncake_block` | 铜月饼堆（四格形态 + 氧化 + 涂蜡） | Copper Mooncake |

### 交互细节

- **炼药锅**：手持可可豆右键装水的炼药锅，得到豆沙，**水位下降一级**（空了变回空锅）。
  所以一桶水最多出 3 份豆沙。任意水位的炼药锅都可以用。
- **模具**：有 64 点耐久，每次压印消耗 1 点。
- **带馅面饼占地四分之一个方块**（8×8 居中），未压印厚 4 像素，
  **压印后被压扁到 3 像素**，一眼能看出被"压"过。
- **方块掉落**：未压印掉「带馅面饼」，已压印掉「生月饼」（战利品表按 `stamped` 状态区分）。
- **生月饼可以平放**（12×2×12 的矮饼，有自定义碰撞箱），也可以直接丢进熔炉。
- **月饼随时可吃**：食物属性带 `alwaysEdible()`。
  原版食物默认只在饥饿值不满时能进食，所以不加这个会出现"创造能吃、生存满饱食度吃不了"。
- **月饼既能吃也能摆**，用 Shift 区分：
  - **右键** → 进食
  - **Shift + 右键** → 摆到地上；对着已有的月饼堆按可以继续叠，**一格最多 4 块**（2×2 摆开，仿原版海泡菜）
  - 按住 Shift 时不会进食，免得"想摆一块结果吃掉了"
  - 挖掉月饼堆掉落**相应数量**的月饼
- **只想拿一块，不想把整堆挖下来** —— 有两条路，都按 Shift：
  - **Shift + 左键**点月饼堆 → 拿走**最上面那一块**，方块留在原地
  - **Shift + 空手右键** → 同上
  - **不按 Shift 左键** → 原版行为，整堆挖掉，四块一起掉
  - 拿到的月饼**直接进背包**（背包满了才掉在脚下），并且保留它自己的形态/氧化度/涂蜡
  - 拿的是**准星指着的那一格**（靠命中点算，不是固定拿最上面那块）
  - **拿空了整个方块会消失** —— 否则会剩下一个看不见又占位的"幽灵方块"
    （形状空、方块 INVISIBLE，所以看得见的地方什么都没有，但那个位置已经不可放置）
  - 为什么左键那条要用 `PlayerInteractEvent.LeftClickBlock` 而不是 `BlockEvent.BreakEvent`：
    后者是在「已经开始挖」之后才取消的，客户端已经预测过一次破坏，
    方块会先消失再被服务端同步回来 —— 取四块就是闪四次。
    `LeftClickBlock.setUseBlock(DENY)` 是**在挖之前**拦下来，客户端不会预测，也就不会闪
- **月饼有两个互相独立的轴**，别混为一谈：

  | 轴 | 取值 | 决定什么 | 在哪设置 |
  |---|---|---|---|
  | **形状** | 圆 / 方 | 方块本身的**几何外形** | — |
  | **纹样** | 圆纹 / 方纹 / 花形纹 | 表面的**花纹贴图** | 切石机 |

  两轴组合 = **6 种形态**，所以「方纹的圆月饼」和「圆纹的方月饼」都存在。
- **两个模具**：`mooncake_mold`（圆形模具，配方竖排 `I`/`S`）和
  `square_mooncake_mold`（方形模具，配方横排 `IS`）—— 就是"竖着 / 横着"的关系。
  压印时决定**形状**，形状会一路带到成品。
- **月饼堆是「四格独立」的**：一个方块分 2×2 四格，**每格单独记形态**，可以混着摆。
  - 用 blockstate 的 **`multipart`** 拼模型：6 形态 × 4 位置 = 24 个单格模型 + 24 条规则，
    而不是把 7⁴ 种组合各写一个模型
  - 几何只由**形状**决定：方 = 完整方块；圆 = 切掉四角（十字形）。
    原版模型的 `elements` **只支持轴对齐长方体**，做不出真正的圆柱
  - 掉落**不走战利品表**，而是覆写 `getDrops` 四格逐一还原（原版 `ShulkerBoxBlock` 同理）
- **形态用切石机切换**：月饼放进切石机，会看到 6 个选项（`圆形月饼` / `圆月饼·方纹` / …）。
  - 形态存在 `mooncake_overflow:mooncake_kind` 组件上，**物品始终只有一个月饼**，不会物品爆炸
  - **为什么是切石机而不是模具**：熔炉配方不继承输入物品的组件。模具作用在烤制**之前**，
    形状活不过熔炉；切石机作用在烤制**之后**，于是完全绕开了这个限制
  - **默认形态（圆形状+圆纹样）不写组件** —— 否则熔炉烤出来的（无组件）和切石机切的
    （带 `kind=round_round`）组件不同，会无法堆叠
  - 形态会一路带到：物品名 → 物品模型 → 摆下去的方块外观 → 挖出来的掉落物
- **音效**：月饼堆、生月饼、面团都用 `SoundType.WOOL` —— 和原版蛋糕一致。
  一开始用的是 `SLIME_BLOCK`（"啪叽"那种），听久了很难受
- **道歉信**：启动时打印，可在 `run/config/mooncake_overflow-common.toml` 里用 `logApology = false` 关掉。

### 尚未实现

- 月饼 + 烟花 → 月饼形状的烟花（原案里有，留到阶段一之后）
- 除可可豆以外的馅料

## 阶段二：氧化与涂蜡（已实现）

模组的正题终于来了 —— **月饼会坏**。

但有一个前提，而且它是整个机制的核心：

> **只有外面包了一圈铜的月饼才会氧化。**
> 普通月饼就是面粉和馅，放多久都不会坏；包了铜才有东西可以生锈。

所以阶段二先加了一个新物品：**铜月饼**（`copper_mooncake`），
以及它对应的**铜月饼堆**（`copper_mooncake_block`）。

```
月饼 + 铜锭          →  铜月饼（形态原样保留，铜是"包"上去的）
铜月饼 摆在地上      →  随机刻氧化：铜 → 斑驳 → 锈蚀 → 氧化（和铜块同一节奏）
铜月饼 放在背包里    →  自己慢慢变质，默认两天一档
蜜脾右键铜月饼堆     →  涂蜡，永久锁死氧化度（外观不变）
蜜脾 + 铜月饼 合成   →  涂蜡的物品，同样锁死
斧头右键铜月饼堆     →  有蜡先刮蜡，没蜡则氧化度退一档
```

**四个氧化等级全都可以摆在地上**，而且摆下去就是那一级的铜月饼堆 ——
从创造栏拿一个「氧化的圆铜月饼」放下，方块直接就是氧化后的样子；
挖掉一堆「涂蜡的锈蚀的」，掉出来的还是「涂蜡的锈蚀的」。
物品和方块是同一套组件，来回搬不会丢信息。

### 四个阶段

| 阶段 | 名字 | 中文名 |
|---|---|---|
| `copper` | （无前缀） | 圆铜月饼 |
| `tarnished` | Tarnished | 斑驳的圆铜月饼 |
| `rusted` | Rusted | 锈蚀的圆铜月饼 |
| `oxidized` | Oxidized | 氧化的圆铜月饼 |

涂蜡再套一层前缀（`Waxed %s` / `涂蜡的%s`），于是名字可以长成
**「涂蜡的锈蚀的圆铜月饼·花形纹」**。

前缀是**两层可翻译文本套出来的**（`"锈蚀的%s"` 套在名字外面），
不是给 4 氧化度 × 2 涂蜡 × 6 形态 = 48 种组合各写一条语言键。

### 为什么最后换成了「方块实体 + 渲染器」

一开始是**两个方块**：普通月饼堆只有四格形态（2401 种状态），
铜月饼堆才有氧化度和涂蜡（19208 种），两者不能混装到同一堆里。

但"任意月饼放在一起"这个要求把这条路堵死了：每格要独立记
「形态 × 是不是铜月饼 × 氧化度 × 涂蜡」，而**方块状态是四格相乘的** ——

```
7⁴（形态） × 4⁴（氧化度） × 2⁴（涂蜡） ≈ 980 万
```

所以内容搬进了方块实体，一个方块装下所有组合：

| | 方块状态数 |
|---|---|
| 两个方块的老方案 | 2401 + 19208 = **21609** |
| 压扁成一个方块（每格全轴相乘） | ≈ **980 万**（必炸） |
| **方块实体 + 渲染器（现在）** | **280**（只是给渲染器查模型的表） |

副产品：普通月饼和铜月饼现在能混在同一堆里了 ——
因为它们不再是两个方块，哪一格是什么记在方块实体上。

细节在[阶段三](#阶段三方块实体--渲染器已实现)。

### 为什么铜月饼不能再用切石机改纹样

**原版的切石机配方不保留输入物品的组件**（`SingleItemRecipe#assemble` 拿到
`ItemStackTemplate` 之后直接 `create()`，压根不看输入那摞东西）。
给铜月饼加 6 条切石机配方，就会变成"锈蚀的铜月饼一切，氧化度没了" ——
一个静默吞数据的陷阱，甚至能被当成"洗氧化度"的漏洞。

所以纹样要在**包铜之前**用切石机切好，顺序是：

```
烤 → 切石机切纹样 → 包铜 → 等它氧化 / 涂蜡锁住
```

包铜用的 `crafting_transmute` 反而**会**保留组件，所以「包铜」这一步不会丢纹样。

### 两条氧化线，两种机制

| | 方块 | 物品 |
|---|---|---|
| 驱动 | `randomTick`（原版随机刻调度器） | `Item#inventoryTick` |
| 频率 | 每随机刻 5.689% 概率（照抄铜块的 `ChangeOverTimeBlock`） | 默认 48000 刻（两天）一档 |
| 范围 | 整堆共用 | 每一摞独立 |
| 开关 | `blockOxidationEnabled` | `itemOxidationEnabled` + `itemOxidationTicksPerStage` |

- **方块走 `randomTick`**，不是自己开 `tick()` —— 随机刻由原版调度，
  我们不需要为它维护任何每刻遍历，符合"零每刻世界扫描"的红线。
- **物品走 `inventoryTick`**，26.1 里这个钩子**只在服务端被调用**
  （签名是 `inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)`），
  所以连端都不用判。改动会由 `AbstractContainerMenu#broadcastChanges` 自动同步给客户端。
- **物品只在玩家背包里变质**，塞进箱子就停 —— 箱子不 tick 物品，这是原版行为，不是 bug。

### 为什么氧化和涂蜡是「整堆共用」，形态却是「一格一个」

| 属性 | 粒度 | 取值 | 乘进去 |
|---|---|---|---|
| `nw` / `ne` / `sw` / `se` | 每格 | 7（空 + 6 形态） | 7⁴ = 2401 |
| `oxidation` | 整块 | 4 | × 4 |
| `waxed` | 整块 | 2 | × 2 |
| | | **合计** | **19208** |

氧化度**按格拆开会是 2401 × 4⁴ ≈ 61 万**，那才是真的会卡；
现在 19208 和原版一些大型模组比还算克制。形态必须按格（玩家实测要求"混着摆"），
氧化度和涂蜡按格没有任何玩法收益，所以按整块。

副作用：**往一堆里加铜月饼时，氧化度和涂蜡状态必须和这一堆一致**，
否则"塞进去一块新鲜的、拿出来变成锈的"这种亏谁都受不了 —— 不一致就返回 `PASS`。
普通月饼堆没有这个限制（它压根没有这两个属性）。

### 涂蜡为什么不改外观

原版涂蜡铜块是**换一个方块**（走 `HoneycombItem.WAXABLES` 那张表和一份新材质）。
我们这里涂蜡只是 `waxed=true` **一个方块状态位，不换方块、不换模型、不换贴图**：

- 方块：在 `CopperMooncakeBlock#useItemOn` 里直接 `setValue(WAXED, true)`
- 物品：`Item#getName` 里加前缀

斧头刮蜡也一并在这里处理。这里有个**对我们有利的细节**：原版的交互顺序是
先调**方块的** `useItemOn`，只有它没吃掉这次交互才轮到 `ItemStack#useOn`
（`ServerPlayerGameMode` 里 `BlockState.useItemOn` 的调用在 `ItemStack.useOn` 之前）。
所以我们返回 `SUCCESS` 就能把原版的 `HoneycombItem` / `AxeItem` 整个挡掉，
不会双重处理，也不依赖 `WAXABLES` 那两张表。

### 涂蜡 / 包铜用的是 `crafting_transmute`

```json
{
  "type": "minecraft:crafting_transmute",
  "input": "mooncake_overflow:copper_mooncake",
  "material": "minecraft:honeycomb",
  "result": { "id": "mooncake_overflow:copper_mooncake",
              "components": { "mooncake_overflow:mooncake_waxed": true } }
}
```

26.1 的 `TransmuteRecipe` 会把**输入物品的组件补丁原样搬给产物**
（`ItemStackTemplate#apply(int, DataComponentPatch)`：先拿输入的补丁建栈，再盖模板自己的组件），
所以形态和氧化度**原封不动**，只在上面加一个 `waxed=true`。包铜那一步用的是同一个配方类型：

```json
{ "input": "mooncake_overflow:mooncake",
  "material": "minecraft:copper_ingot",
  "result": { "id": "mooncake_overflow:copper_mooncake" } }
```

> 已知小瑕疵：`Ingredient` 在 26.1 里只是一个 `HolderSet<Item>`，**不支持组件谓词**，
> 所以拿已经涂过蜡的铜月饼再合成一次也会匹配 —— 结果是白扔一块蜜脾。
> 原版的染色配方（`#minecraft:bundles` + 同色染料）其实也是这个行为，
> 想彻底堵住得写一个自定义 `CustomRecipe`，暂时不值这个复杂度。

### 内容清单（阶段二新增）

| 类型 | ID | 说明 |
|---|---|---|
| 方块 + 物品 | `copper_mooncake` | 铜月饼（会氧化的那一个） |
| 方块 | `copper_mooncake_block` | 铜月饼堆，多 `oxidation` / `waxed` 两个属性 |
| 组件 | `mooncake_oxidation` | 氧化度，`copper` 时不写 |
| 组件 | `mooncake_waxed` | 涂蜡标记，`false` 时不写 |
| 配置 | `blockOxidationEnabled` | 方块是否氧化 |
| 配置 | `itemOxidationEnabled` | 物品是否变质 |
| 配置 | `itemOxidationTicksPerStage` | 物品多少刻氧化一档 |
| 配方 | `copper_mooncake` | `crafting_transmute`：月饼 + 铜锭 |
| 配方 | `copper_mooncake_waxed` | `crafting_transmute`：铜月饼 + 蜜脾 |

创造模式物品栏里额外放了**整条氧化链的样本**和**一个涂蜡样本**，
不然想直接看效果得真等几个小时。
**普通月饼只有铜阶段，没有氧化度** —— 它不会坏。

## 阶段三：方块实体 + 渲染器（已实现）

月饼堆的**内容**现在住在方块实体里，渲染交给方块实体渲染器。
这一步是为了让「四格各放各的」在数学上成立 —— 见
[阶段二那节](#为什么最后换成了方块实体--渲染器)。

### 数据在方块实体里

`MooncakePileBlockEntity` 就一个长度 4 的数组，每格是：

```java
record Piece(MooncakeKind kind, boolean copper, MooncakeOxidation oxidation, boolean waxed)
```

`copper=false` 就是普通月饼（永远不会氧化），`true` 才是铜月饼。
同一堆里两种可以混着放，每格各记各的。

存读走 26.1 的 `ValueOutput` / `ValueInput`（`saveAdditional` / `loadAdditional`），
同步走 `getUpdateTag` + `getUpdatePacket`，改完调 `setChanged()` + `sendBlockUpdated`。

### ⚠️ 26.1 最硬的一个坑：碰撞箱是按**方块状态**缓存的

`BlockStateBase` 内部有一个 `Cache`，里面存着 `collisionShape`、
`largeCollisionShape`、`isCollisionShapeFullBlock`、`faceSturdy` ——
而且是在**构造成员时用 `EmptyBlockGetter` + `BlockPos.ZERO` 算一次**。

也就是说：

> **`getCollisionShape` 不能依赖坐标，也不能读方块实体。**
> 想按格区分碰撞箱，几何就必须能**只从方块状态推出来**。

我第一版把 `getShape` 写成读方块实体，方块实体拿不到时退化成整块；
那个"整块"被缓存进了碰撞箱和遮挡箱，于是：

- 地上多出一圈阴影（遮挡箱是整块 → 环境光遮蔽把邻居方块压暗了）
- 没放月饼的格子也走不过去（碰撞箱是整块）

所以现在的分工是：**几何进方块状态，外观和内容进方块实体**。

### 几何：四个 `CellShape`（81 种状态）

| | 住哪 | 管什么 |
|---|---|---|
| `nw` / `ne` / `sw` / `se`（空/圆/方） | 方块状态 | 碰撞箱、准星轮廓、踩上去的高度 |
| 纹样 / 是不是铜月饼 / 氧化度 / 涂蜡 | 方块实体 | 外观、掉落、氧化、涂蜡 |

几何只关心"圆还是方"，所以四格 3⁴ = **81** 种状态就够（原版海泡菜也是这个路子）。
`getOcclusionShape()` 直接返回空 —— 3/16 高的月饼本来就不该在邻居方块上投阴影。

### 外观：模型表放在一个技术方块上

26.1 **删掉了「按 Identifier 取方块模型」的接口** ——
`ModelManager` 只剩 `getItemModel(Identifier)`，方块几何只能通过
`BlockStateModelSet.get(BlockState)` 拿到。**想要一个模型，得先有一个方块状态。**

本来可以把模型表的属性挂在月饼堆自己身上，但那样状态数是
`81 × 280 = 22680`，还要生成一份几 MB 的 blockstate 文件。
所以模型表挪到一个**从不被放置的技术方块** `mooncake_piece` 上：

| 方块 | 状态数 | 作用 |
|---|---|---|
| `mooncake_block` | 81 | 真实摆放的月饼堆（几何） |
| `mooncake_piece` | 280 | 模型表：格子 × 形态 × 铜不铜 × 氧化度 |

`mooncake_piece` 没有物品、创造栏里没有、`RenderShape` 也是 `INVISIBLE`，
正常玩法碰不到它。渲染器每格干两件事：

```java
// 1. 用技术方块编一个"查表用"的状态
BlockState model = pieceBlock.modelStateFor(cell, kind, copper, oxidation);
// 2. 交给原版那条"活塞推方块"的路去画
collector.submitMovingBlock(poseStack, movingBlockState);
```

用 `submitMovingBlock` 而不是自己拼 `submitBlockModel`，是因为它帮我们把
模型查找、光照、渲染类型都处理好了（照抄 `PistonHeadRenderer#createMovingBlock`）。

### 26.1 的三个坑（都踩过）

1. **`BlockEntityType` 的构造器和 `register` 都是 private**，Forge 也没补公开工厂。
   模组想自己造只能开 Access Transformer：
   `META-INF/accesstransformer.cfg` 里把构造器改成 public。
2. ⚠️ **AT 文件里不能写注释** —— 解析器把每一行都当规则，
   一行 `#` 开头就直接 `Invalid AccessTransformer config` 启动失败。
3. **`EntityRenderersEvent.RegisterRenderers` 不在 MOD 总线组上**，
   它在默认那个（`Mod.EventBusSubscriber.Bus.FORGE`）。
   写成 `Bus.MOD` 会报 "is on the default BusGroup but you are asking to register"。
4. ⚠️ **碰撞箱 / 遮挡箱是按方块状态缓存的**（见上面那节）——
   位置相关或读方块实体的形状会被算错并永久缓存，现象是"看不见的整块"。

### 瞄准哪一块就取哪一块

Shift + 空手右键 / Shift + 左键拿的是**准星指着的那一格**，而不是固定"最上面那块"。
格子靠命中点算：把世界坐标转成方块内的相对坐标，x 和 z 各以 0.5 为界，
正好对应左上 / 右上 / 左下 / 右下。空手右键那条路有原版给的精确命中点；
左键那条路（`LeftClickBlock` 事件不给命中点）用 `player.pick(...)` 自己补一个。

## 阶段四：切四片与缝合（已实现）

和**西瓜**一个道理：一整块月饼切成四片，四片又能拼回一整块。
数量是**平衡**的 —— 切一次消耗 1 块、出 4 片；4 片拼回 1 块。

```
切石机（月饼 / 铜月饼）      →  4× 四分之一块（组件原样继承）
四分之一块                   →  弩的弹药（挂在 minecraft:arrows 上）
对着月饼堆右键               →  塞进空格子
工作台：任意 4 片            →  1 块月饼
```

### 四分之一块

- 继承 `ArrowItem` 再挂上 `minecraft:arrows` 物品标签，所以弩能拿它当弹药
  （射出去是普通的箭，但消耗的是一片月饼 —— 这已经足够搞怪了）
- 组件比一整块只多一个：`mooncake_copper`（是不是从铜月饼上切下来的）。
  它是必须的 —— 铜月饼的"铜阶段"本来就不写氧化度组件，
  光看有没有氧化度分不出"普通月饼的四分之一"和"刚包好铜的四分之一"
- **刻意不能吃**：右键被"塞进月饼堆"占了，而且四分之一块也不够塞牙缝
- **不区分是哪一角**：一开始做成"切一次只出一角、四角各不相同"，
  那样 1 块月饼只能换 1 片，等于把 3/4 扔了。现在是四片一样的，
  谁落在哪一格由**在工作台里的摆放顺序**决定（按行优先读）

### ⚠️ 切石机不保留组件 —— 所以自己写了一个配方类型

原版的 `StonecutterRecipe`（其实是 `SingleItemRecipe`）的 `assemble` 就是
`result.create()`，**完全不看输入那一摞东西**。直接用它切月饼的后果是
"锈蚀的铜月饼一切，氧化度没了"：静默吞数据，还能被当成"洗氧化度"的漏洞。

所以 `ComponentStonecuttingRecipe` 继承 `StonecutterRecipe`，只改 `assemble`：

```java
return TransmuteRecipe.createWithOriginalComponents(result(), input.getItem(0));
```

关键是 `getType()` 仍然继承自 `StonecutterRecipe`（也就是 `RecipeType.STONECUTTING`），
所以**它照样出现在切石机界面里** —— `StonecutterMenu` 是按这个 RecipeType 收集配方的，
而拿产物走的是 `assemble`，覆写会生效（都反编译确认过）。
出 4 片就是把 `result.count` 写成 4（`TransmuteRecipe.createWithOriginalComponents`
会带上模板的数量）。

类型上有个小坑：`StonecutterRecipe#getSerializer()` 把返回类型收窄成了
`RecipeSerializer<StonecutterRecipe>`，子类没法返回 `RecipeSerializer<自己的类型>`（泛型不变），
所以序列化器字段得声明成父类类型 —— codec 的工厂引用照样构造子类实例。

### 五仁月饼

任意 4 片放进工作台 → **一块「五仁月饼」**（四片一样也是）。
它就是**一个普通的月饼物品**（跟整块月饼同一个物品），
名字用**原版的自定义名组件**（`minecraft:custom_name`，就是铁砧改名那个）承载：

```
五仁月饼（左上·圆月饼 + 右上·氧化的方铜月饼·花形纹 + 左下·涂蜡的锈蚀圆铜月饼 + 右下·方月饼）
```

> **它不是"一个月饼堆"** —— 五仁月饼跟普通月饼一样，摆下去只占一格。
>
> ⚠️ **为什么用 `custom_name` 而不是自己定义的组件**：这里连踩两次坑 ——
> `List<Piece>` 和纯字符串两种自定义组件都出现"服务端明明设上了、客户端收到的却没有"，
> 名字就退回成普通的"圆铜月饼"（看起来毫无规律）。
> `custom_name` 是原版到处在用的组件（铁砧改名），同步一定有保障。
> 存进去的仍然是一棵**可翻译组件树**，所以照样跟着玩家语言走。
>
> 副作用：自定义名在提示框里会渲染成**斜体**（原版就这么干的）。
>
> ⚠️ 自定义名还得**存进方块实体**（`Piece` 的 `name` 字段）：
> 不然"把五仁月饼摆到地上再挖/拿回来"就会掉属性 ——
> 而自定义名正是五仁月饼的全部特征。氧化度/涂蜡那些组件本来就存着，
> 所以只有名字会丢，现象就是"拿起来变成普通月饼了"。
> 名字里的"左上/右上"说的是它是**从哪个角切下来的**，不是它占了四格。
> 类比就是原版的**小麦 → 干草堆**：材料拼成产物，产物就是普通的一个物品。

四片属性不一样时取最"保守"的那一份，**免得能靠拼合洗掉氧化度**：

| | 取法 |
|---|---|
| 是不是铜的 | 有一片是铜的，成品就是铜的 |
| 氧化度 | 取**最锈**的那一片（枚举顺序就是 铜→斑驳→锈蚀→氧化） |
| 涂蜡 | 四片**都**涂过才算涂过 |
| 形态 | 取第一片的 |

### ⚠️ 切石机选项列表里的名字是"通用"的（原版限制）

切石机里那四个选项（左上/右上/左下/右下）显示的是**静态的产物模板**，
看不到你实际放进去的是哪块月饼 —— 所以它只会显示「圆铜月饼·左上」这种，
**斑驳/涂蜡那些前缀不会出现**。这是原版 `SelectableRecipe` 的结构决定的
（它的显示是按"原料种类"预生成、随配方包同步的，跟具体那一摞物品无关）。

**但是产物是对的**：真正拿出来的那个走的是 `assemble`，
会把输入的组件原样继承过去，所以名字、氧化度、贴图都正确。

### ⚠️ 「一角」和「一整块」画得不一样 —— 靠 `partial` 区分

这是这套东西里最容易被忽略的一点。同一格里放的可能是：

| | 什么意思 | 渲染时画什么 |
|---|---|---|
| `partial = false` | **一整块**月饼（直接摆下去的） | **整个纹样** —— 四块摆一起就是"四块月饼" |
| `partial = true` | 某个月饼的**一角**（切开的片、五仁月饼的四格） | **只画它那一象限的纹样** —— 四块拼起来才是"一个完整的月饼" |

所以模型有两套（一共 240 个单格模型）：

- `mooncake_piece_<格>_<形态>`：把这一格的包围盒 UV 归一化到 0..16 → 显示完整纹样
- `mooncake_piece_part_<格>_<形态>`：直接用方块坐标当 UV → 只显示那一象限

（这正好是阶段一踩过的那个坑的两面：当时"每块只显示四分之一"是 bug，
因为一格放的是一整块月饼；现在做五仁月饼反而需要那个行为，所以两套并存。）

**`partial` 还兼着防复制的作用**：挖掉一堆月饼时，
`partial` 的格子还原成**片**、否则还原成**整块**。
没有这条的话，"1 块月饼 → 切 4 片 → 摆进堆里 → 挖掉"会掉出 4 块整月饼，凭空翻四倍。

其它：

- 物品模型用 `minecraft:condition` + `has_component` 换成专门的样子
  （四个象限各取一种氧化度的顶面拼起来，一眼看得出是"拼的"）

为什么必须自己写配方：原版配方的产物是固定模板，**读不到原料的组件**
（`Ingredient` 只是个 `HolderSet<Item>`），只有 `CustomRecipe` 能在 `assemble` 里自己拼数据。
它是个"特殊配方"（`isSpecial()`），不进配方书，也不需要任何 JSON 参数。

## 待办

### 1. 把弩的箭换成月饼

现在四分之一块挂在 `minecraft:arrows` 标签上、又继承了 `ArrowItem`，
所以弩**能**用它当弹药，但射出去的是一支普通的箭。

要做成"射出去的是月饼"，已经查清的路子：

- ⚠️ **原版 `ProjectileWeaponItem.createProjectile` 硬编码走 `ArrowItem.createArrow`**
  （反编译：`stack.getItem() instanceof ArrowItem ? ... : Items.ARROW`，然后
  `arrow.createArrow(...)` 拿一个 `AbstractArrow`）。
  也就是说**自定义投射物必须是 `AbstractArrow` 的子类**，光实现 `ProjectileItem` 是没用的
- 所以做法是：四分之一块继续继承 `ArrowItem`，但覆写 `createArrow` 返回自己的实体；
  那个实体 extends `AbstractArrow`，再覆写：
  - `onHitEntity` → 造成伤害后 `discard()`（**不留箭**）
  - `onHitBlock` → `discard()` + 把月饼作为掉落物吐出来（**不插在墙上**）
  - `pickup` 设成 `DISALLOWED`
- 渲染要自己写一个渲染器（画"对应的那块月饼"）。原版 `ItemEntityRenderer`
  是"用物品模型渲染实体"的现成参考

另外记一笔：以后可以让不同形态 / 氧化度的月饼块**射出去带不同效果**
（用户已确认列为后续开发）。

## 开发状态

🟢 **月饼系统完成** —— 构建、资源、交互全部跑通，并经玩家在客户端逐项实测确认。

- [x] ForgeGradle 7 构建链路
- [x] `runClient` / `runServer` / `runData` 全部可用
- [x] **工艺链**：豆沙 → 面团 → 带馅面饼 → 压印 → 生月饼 → 烤制 → 月饼
- [x] **形态系统**：形状（圆/方）× 纹样（圆/方/花）= 6 种，切石机切换，组件承载
- [x] **四格混装**：一个方块 2×2 四格各自独立；**普通月饼和铜月饼也能混放在同一堆**
- [x] 食用（随时可吃）、Shift 摆放、取暖、掉落、中英文本地化
- [x] **铜月饼**（阶段二）：月饼 + 铜锭，形态原样保留；**只有它才会氧化**
- [x] **氧化**（阶段二）：方块走随机刻、物品走 `inventoryTick`，四个等级都能摆成月饼堆
- [x] **涂蜡**（阶段二）：蜜脾右键方块 / 蜜脾合成物品，锁死氧化度且**不改外观**
- [x] **刮除**（阶段二）：斧头右键方块，有蜡刮蜡、没蜡退一档氧化
- [x] **方块实体 + 渲染器**（阶段三）：内容进方块实体，方块状态从 21609 掉到 280
- [x] **切开 / 拼合**（阶段四）：1 块 ↔ 4 片（数量平衡）、切石机保留组件、
      四分之一块当弩弹药、4 片拼回一整块（有差别就是「缝合月饼」）
- [ ] **月饼块全家桶**（阶段四）：数据包驱动自动生成形态矩阵
- [ ] **月亮系统**（阶段五）：吃月亮
- [ ] **玉兔 / 五仁 / 礼盒 / 元游戏嘲讽**（阶段六）
- [ ] 月饼形状的烟花（要自定义 `FireworkShape`）
- [ ] 仓库里还缺一个 `LICENSE` 文件

完整点子集见 [`docs/absurd_ideas.md`](docs/absurd_ideas.md)。

## 环境要求

| 组件 | 版本 | 备注 |
|---|---|---|
| Minecraft | 26.1 | |
| Forge | 62.0.9 | |
| Java（编译目标） | 25 | 由 Gradle toolchain 自动下载，无需手动装 |
| Gradle | 9.3.1 | 通过 wrapper 使用，无需手动装 |
| ForgeGradle | 7.x | `[7.0.17,8)` |

> **首次构建会比较久。** ForgeGradle 7 会自动下载
> **JDK 25**（编译工具链，约 140 MB）和 **JDK 8**（Mavenizer 处理 access transformer 用，约 104 MB），
> 并完成一次 Minecraft/Forge 的 Mavenizer 处理。
> 本机实测首次完整构建约 2 分钟（不含 JDK 下载），之后都有缓存。

## 构建

```bash
./gradlew build
```

产物：`build/libs/mooncake_overflow-0.1.0-SNAPSHOT.jar`

## 运行

```bash
./gradlew runClient         # 启动客户端
./gradlew runServer         # 启动服务端（需先同意 EULA，见下）
./gradlew runData           # 数据生成，输出到 src/generated/resources
./gradlew runGameTestServer # 运行 gametest
```

**关于 `runServer`**：首次运行会在 `run/eula.txt` 写入 `eula=false` 并退出。
需要你**自己**把它改成 `eula=true` —— 这代表你本人同意 [Mojang EULA](https://aka.ms/MinecraftEULA)。

## Linux / Wayland 已知问题

如果在 Wayland 桌面环境下 `runClient` 崩溃并报：

```
GLFW error before init: [0x1000C]Wayland: The platform does not provide the window position
```

这是 **Forge 的已知 bug**，不是项目配置问题。

- 参考：[MinecraftForge#10062](https://github.com/MinecraftForge/MinecraftForge/issues/10062)、[MinecraftForge#10823](https://github.com/MinecraftForge/MinecraftForge/issues/10823)
- **原因**：MC 26.1 的 `GLX._initGlfw` 本来会执行 `glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)` 强制走 X11，
  但 **Forge 的早期加载窗口 `fmlearlywindow` 先一步调用了 `glfwInit()`**，此时 hint 尚未生效，
  GLFW 3.4 便选了 Wayland；早期窗口在 Wayland 上调用 `glfwGetWindowPos` 会产生**非致命**错误
  `GLFW_FEATURE_UNAVAILABLE (0x1000C)`，随后被 MC 的 `checkGlfwError` 当成致命错误抛出。
- **环境变量救不了**：GLFW 二进制里没有 `GLFW_PLATFORM` 这个变量；取消 `WAYLAND_DISPLAY` 后
  它会回退到默认的 `wayland-0`，设成无效值则直接 `glfwInit` 失败。

**绕过办法**：编辑 `run/config/fml.toml`

```toml
earlyWindowControl = false
```

这样 Forge 不再抢跑，MC 自己的 X11 hint 生效，走 XWayland。代价是没有了 Forge 的早期加载小窗。

**彻底修法**：升级到 **Minecraft 26.1.2 + Forge 64.1.3**，其 changelog 包含官方修复：

> `64.0.12 Handle glfw window pos errors gracefully for wayland (#10852)`

升级后即可把 `earlyWindowControl` 改回 `true`。

> 注意：`run/` 目录在 `.gitignore` 中，所以这个改动是**本机设置**，不会进仓库 —— 这是合理的，
> 因为它跟具体桌面环境绑定。

## 目录结构

```
.
├── build.gradle                          # ForgeGradle 7 构建脚本
├── gradle.properties                     # 版本号、mod_id、group
├── settings.gradle
├── gradlew / gradlew.bat
├── docs/
│   ├── some_ideas.md                     # 最初的构思与合成链
│   └── absurd_ideas.md                   # 完整点子集 + 性能红线 + MVP 顺序
├── tools/
│   ├── generate_textures.py              # 贴图程序化生成器（含铜圈 / 氧化调色）
│   ├── generate_block_models.py          # 两个月饼堆的模型 + multipart 生成器
│   └── check_resources.py                # 资源一致性检查（改完资源务必跑一次）
└── src/main/
    ├── java/org/cvrain/mooncakeoverflow/
    │   ├── MooncakeOverflow.java          # 主类：注册、炼药锅交互、道歉信
    │   ├── Config.java                    # 配置：道歉信 + 三个氧化开关
    │   ├── block/
    │   │   ├── MooncakeDoughBlock.java    # 可压印的月饼面团方块
    │   │   ├── PattyBlock.java            # 平放的饼（生月饼用）
    │   │   ├── MooncakePileBlock.java     # 月饼堆：内容在方块实体里，自己不画
    │   │   └── MooncakePileBlockEntity.java # 四格内容 + 整堆涂蜡/氧化/刮除
    │   ├── client/
    │   │   ├── ClientSetup.java           # 注册方块实体渲染器
    │   │   ├── MooncakePileRenderer.java  # 四格月饼全靠它画
    │   │   └── MooncakePileRenderState.java
    │   ├── item/
    │   │   ├── MooncakeBlockItem.java     # Shift 右键放置 / 右键进食 / 名字
    │   │   ├── CopperMooncakeBlockItem.java # 铜月饼：名字前缀 + 背包变质 + 混装校验
    │   │   └── ShapeBlockItem.java        # 生月饼物品，带形状字段
    │   ├── mooncake/
    │   │   ├── MooncakeShape.java         # 轴一：形状（圆 / 方）
    │   │   ├── MooncakePattern.java       # 轴二：纹样（圆 / 方 / 花）
    │   │   ├── MooncakeKind.java          # 两轴的组合，6 种（+ 空格子 NONE）
    │   │   ├── MooncakeOxidation.java     # 氧化度：铜 / 斑驳 / 锈蚀 / 氧化
    │   │   ├── PileCell.java              # 四格 + 渲染器的模型表槽位
    │   │   └── MooncakeData.java          # 物品侧组件的读写（默认值不写组件）
    │   └── registry/
    │       ├── ModBlocks.java
    │       ├── ModBlockEntities.java
    │       ├── ModItems.java
    │       ├── ModCreativeTabs.java
    │       └── ModDataComponents.java     # mooncake_kind / _oxidation / _waxed
    └── resources/
        ├── META-INF/mods.toml
        ├── META-INF/accesstransformer.cfg # 26.1 只能这样造 BlockEntityType
        ├── pack.mcmeta
        ├── assets/mooncake_overflow/      # items/ blockstates/ models/ textures/ lang/
        └── data/mooncake_overflow/        # recipe/ loot_table/
```

## 性能原则

**抽象是玩法层面的，不是性能层面的。**

这个模组可以很离谱，但不能让游戏变卡。三条硬约束：

1. **变体数据驱动** —— 不为每个变体写代码，也不给每个变体独立贴图（走调色板染色 + 程序化合成）
2. **零每 tick 逻辑** —— 氧化复用原版铜的**随机刻**；月饼块**不用 BlockEntity**；
   状态（形态/氧化度/涂蜡）存在物品侧的 `DataComponent` 里；计时用 `Level#scheduleTick`
3. **程序化贴图只在资源重载时算一次**并缓存，运行时只查表

**允许**：几百个变体、少量自定义实体、粒子效果、一次性高开销
**不允许**：每 tick 扫描全世界、每帧动态生成模型/贴图、会无限增殖的方块或实体

### 方块状态预算

方块状态是**唯一**会随轴数量指数膨胀、而且绕不过去的东西。
一个月饼堆每格要独立记「形态 × 是不是铜月饼 × 氧化度 × 涂蜡」，四格相乘就是天文数字：

| 方案 | 状态数 |
|---|---|
| 每格全轴相乘（形态⁴ × 氧化度⁴ × 涂蜡⁴） | ≈ **980 万** —— 必炸 |
| 两个方块、氧化度与涂蜡整块共用（老方案） | 2401 + 19208 = **21609** |
| **方块实体 + 渲染器（现在）** | 81（几何）+ 280（模型表，在技术方块上）= **361** |

所以现在这条红线是：**内容进方块实体，方块状态只留「渲染器查模型用的表」**。
代价是画东西要自己写渲染器，而且 26.1 查模型必须绕方块状态（见阶段三）。

> **关于加载耗时的更正**：铜月饼那次改动我一度报告"模型烘焙从 8 秒变成 20～23 秒"，
> 那个结论是**错的**。原因是我停 `runClient` 的过滤条件写错了
> （匹配的是 slime-launcher 包装进程，而不是真正的游戏 JVM），
> 于是每次测试都残留着 1～3 个旧客户端在后台抢 CPU ——
> 同一份代码能测出 8 秒、34 秒、68 秒、2.5 分钟。
>
> 正确的停进程过滤条件是命令行里含 `net.minecraftforge.launcher.Main`：
>
> ```bash
> for p in $(pgrep -x java); do
>   tr '\0' '\n' < /proc/$p/cmdline | grep -q net.minecraftforge.launcher.Main && kill $p
> done
> ```
>
> 结论：烘焙耗时基本由**机器当时的负载**决定（自己开着的应用也算），
> 而且它是一次性加载开销，不影响游戏内帧率。
> 换成方块实体之后状态数掉到 280，这一项基本可以不用再担心了。

## 资源与命名约定

- **`mod_id`**：`mooncake_overflow`（已锁定，改动会导致旧存档失效）
- **主包**：`org.cvrain.mooncakeoverflow`
- **显示名**：`Mooncake Overflow` / 中文 `月饼溢出`
- **副标题**：`347 Variants, None of Them Good`
- **贴图**：全部由 `tools/generate_textures.py` 生成，改完跑一次脚本即可：

  ```bash
  python3 tools/generate_textures.py
  ```

  脚本会同时输出一张 `/tmp/mooncake_textures_preview.png` 放大预览图方便人眼检查。

  「包了一圈铜」是**画出来**的：饼面按 `COPPER_PATTERN_SCALE` 缩小纹样，
  再套一圈 `copper_band()`（外圈受光 + 内圈压深 + 高光点）。
  铜圈厚度 `BAND = 2` 像素、纹样 0.88 倍 —— 试过 0.72，八瓣花会挤成一团糊，0.88 是还能看清纹样的下限。

- **26.1 API 备忘**（踩过的坑，别再用旧名字）：
  - `ResourceLocation` → **`Identifier`**
  - 物品 NBT → **`DataComponentType`**
  - ⚠️ **物品模型的入口是 `assets/<ns>/items/<id>.json`，不是 `models/item/<id>.json`！**
    后者只是被前者引用的几何文件。只写 `models/item/` 不会报任何错，
    但物品在游戏里会渲染成**紫黑格**。26.1 原版有 1506 个 `items/` 文件，
    其中 760 个（所有方块物品）没有对应的 `models/item/`。
    格式：`{"model": {"type": "minecraft:model", "model": "<ns>:item/<name>"}}`，
    方块物品则指向 `block/<name>`。
  - 方块属性必须显式 `.setId(...)`
  - 事件监听用 `SomeEvent.BUS.addListener(Consumer<T>)`（EventBus 7）
  - `ItemStack.is(Item)` 已改为接收 `Predicate<Holder<Item>>`，直接比 `getItem()` 更稳
  - `CauldronInteraction.Dispatcher.put()` 是包私有，炼药锅只能走 `PlayerInteractEvent.RightClickBlock`
  - `minecraft:copy_state` 现在是把方块状态塞进物品组件，**不能**用来设掉落数量；
    数量随方块状态变化要用多个带 `block_state_property` 条件的掉落池
  - ⚠️ **方块物品的显示名前缀是显式声明的**：`Item.getDescriptionId()` 是 `final`（不能覆写），
    前缀由 `Item.Properties#useBlockDescriptionPrefix()` 决定，默认是 `item.`。
    方块物品不调它就会显示成 `item.<ns>.<id>` 这种原始键名。
    而且键里的 path 用的是**物品自己的 id**，不是方块 id
    （物品 `filled_mooncake_dough` → 键 `block.<ns>.filled_mooncake_dough`）。
  - ⚠️ **`useWithoutItem` 只在 `useItemOn` 返回 `TRY_WITH_EMPTY_HAND` 时才会被调用**。
    返回 `PASS` 会让"空手交互"这段代码永远不执行。
    原版源码里的判断是 `if (result instanceof InteractionResult.TryEmptyHandInteraction && hand == MAIN_HAND)`
  - **配方结果可以带组件**：`ItemStackTemplate` 有 `components` 字段，
    所以 `"result": {"id": "...", "components": {"ns:comp": "值"}}` 是合法的
    （切石机 / 熔炉 / 合成都适用）
  - **物品模型可以按组件值切换**：`assets/<ns>/items/<id>.json` 里用
    `{"type": "minecraft:select", "property": "minecraft:component", "component": "<组件id>",
      "cases": [{"when": <组件值>, "model": {...}}], "fallback": {...}}`
    （注意：`has_component` 只能判断"有没有"，不能判断值；要按值选必须用 `component`）
  - ⚠️ **多盒子拼一个形状时，顶面 UV 必须相对「这一块」归一化到 0..16**，
    不能直接用方块坐标。用方块坐标的话，7×7 的格子只会采到 16×16 贴图的左上角一小块，
    现象就是"每块只有四分之一个图案，四块拼起来才是完整的"（踩过）
  - ⚠️ **`Item#inventoryTick` 的签名变了，而且只在服务端调用**：
    `inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)`
    （旧版是 `(ItemStack, Level, Entity, int, boolean)`）。`ItemStack#inventoryTick`
    里直接 `if (level instanceof ServerLevel)` 才转发过去，所以写的时候不用判端
  - `InteractionResult` 现在是**密封接口**，常量是 `SUCCESS` / `SUCCESS_SERVER` / `CONSUME` /
    `FAIL` / `PASS` / `TRY_WITH_EMPTY_HAND`（`TRY_WITH_EMPTY_HAND` 那个是原版用来
    "方块交互完了再走空手逻辑"的）
  - `randomTick` / `useItemOn` / `useWithoutItem` 都在 **`BlockBehaviour`** 上，
    不在 `Block` 里；`randomTick` 要生效必须给 `Properties#randomTicks()`
  - ⚠️ **`Ingredient` 只是一个 `HolderSet<Item>`，不支持组件谓词**。
    配方**原料**没法判断"有没有某个组件"，只有**产物**能带组件。
    所以"物品 A + 蜜脾 → 同款物品 A 但多个组件"这种配方，用 `crafting_transmute`：
    它会把输入的组件补丁原样搬到产物上，再盖模板自己的组件
    （`ItemStackTemplate#apply(int, DataComponentPatch)`）
  - ⚠️ **切石机（`StonecutterRecipe` / `SingleItemRecipe`）不保留输入物品的组件** ——
    `assemble` 直接 `result.create()`，输入那摞东西压根不看。
    所以"改外观但保留其他组件"的配方**不能**用切石机，只能用 `crafting_transmute`（合成台）。
    给带组件的物品加切石机配方 = 静默吞数据的陷阱（踩过，见「为什么铜月饼不能再用切石机改纹样」）
  - ⚠️ **原版先调方块的 `useItemOn`，再调 `ItemStack#useOn`**。
    `ServerPlayerGameMode#useItemOn` 里 `BlockState.useItemOn` 在前，只有
    `!result.consumesAction()` 时才继续走物品那一路。
    想在方块上拦截某个物品（蜜脾、斧头）时这很有利：方块直接返回 `SUCCESS`
    就能把原版的 `HoneycombItem` / `AxeItem` 逻辑整个挡掉
  - **一个方块类可以按实例注册出属性集不同的多个方块** ——
    `createBlockStateDefinition` 是实例方法，子类加属性、父类只加自己的那部分。
    但注意它是**从父类构造器里被虚调用**的，所以父类的 `registerDefaultState`
    只该设自己那部分属性；子类构造完成后要再 `registerDefaultState(defaultBlockState().setValue(...))`
    把新属性补上。（`StateDefinition#any()` 返回的是**第一个完整状态**，
    所有属性都已就位，所以中间态不会炸）
  - 原版涂蜡是**换方块**（`HoneycombItem.WAXABLES` 那张 `Block → Block` 表）
    + 斧头刮（`WAX_OFF_BY_BLOCK`）。如果涂蜡只是改一个状态位、不换方块，
    这些表都帮不上忙，得在方块自己的 `useItemOn` 里处理
  - 铜的随机刻氧化概率是 `ChangeOverTimeBlock#changeOverTime` 里的 `0.05688889F`，
    直接照抄；这套接口（`getNext` / `getChanceModifier` / `getAge`）也可以自己实现
  - `ItemStack#hurtAndBreak(int, LivingEntity, InteractionHand)` 是给"玩家用手里的东西"用的，
    做斧头刮蜡这种交互时不用自己去算 `EquipmentSlot`
  - ⚠️ **创造模式物品栏不允许重复条目**，同一个 `ItemStack` 放两次直接
    `IllegalStateException: Accidentally adding the same item stack twice`（踩过两次）
  - ⚠️ **26.1 的 `BlockEntityType` 构造器和 `register` 都是 private**，Forge 没补公开工厂。
    只能开 Access Transformer（`META-INF/accesstransformer.cfg`）。
    而且 **AT 文件里不能写注释**，一行 `#` 开头就 `Invalid AccessTransformer config` 启动失败
  - **方块实体存档换成了 Codec 那一套**：`saveAdditional(ValueOutput)` /
    `loadAdditional(ValueInput)`（`output.store("k", CODEC, v)` /
    `input.read("k", CODEC)`），同步还是 `getUpdateTag(HolderLookup.Provider)` + `getUpdatePacket()`
  - ⚠️ **26.1 没有「按 Identifier 取方块模型」的接口**。`ModelManager` 只剩
    `getItemModel(Identifier)`；方块几何只能 `BlockStateModelSet.get(BlockState)`。
    想在渲染器里画任意几何，就得先给它准备一个**方块状态当模型表的键**
  - **渲染器里画一个方块模型，抄 `PistonHeadRenderer`**：
    `extractRenderState` 里把目标状态包成 `MovingBlockRenderState`
    （`blockPos` / `blockState` / `biome` / `cardinalLighting` / `lightEngine`），
    `submit` 里 `collector.submitMovingBlock(poseStack, state)`。
    比自己去碰 `RenderType` 和 tint 数组省事得多
  - ⚠️ **`EntityRenderersEvent.RegisterRenderers` 在默认总线组上**，
    自动订阅要用 `@Mod.EventBusSubscriber(bus = Bus.FORGE)`；
    写成 `Bus.MOD` 会报 "is on the default BusGroup but you are asking to register"
  - `Level#isClientSide` 是**字段**且 private，判断端要用方法 `isClientSide()`
  - ⚠️ **DFU 的 `RecordCodecBuilder` 不允许字段的 codec 解码出 `null`**。
    它解码每个字段时会做 `Optional.of(值)`，所以"可空字段"
    （比如 `@Nullable Component` + `xmap(opt -> opt.orElse(null), …)`）在字段缺失时直接 NPE。
    字段类型就该用 `Optional<T>`，配 `optionalFieldOf("k").forGetter(...)`。
    **而且这个 NPE 的后果不对称**：服务端从区块读时原版会 catch 并记一条 ERROR，
    客户端收到方块实体同步包那条路**原版不 catch** —— 直接"Failed to handle packet"然后整个客户端崩。
    方块实体的 `loadAdditional` 里自己兜一层 try/catch 是值得的
  - ⚠️ **Access Transformer 文件里不能写注释** —— 解析器把每一行都当规则，
    一行 `#` 开头就 `Invalid AccessTransformer config` 启动失败

### 改完资源记得跑检查

```bash
python3 tools/check_resources.py
```

它会校验：每个注册的物品/方块是否有对应的 `items/` `blockstates/` 文件、
模型与贴图引用是否存在、语言键是否齐全且没有死键。
**上面那个 `items/` 的坑就是它抓出来的**，建议每次动资源后都跑一次。

## 文档

- [`docs/some_ideas.md`](docs/some_ideas.md) —— 最初的构思与合成链
- [`docs/absurd_ideas.md`](docs/absurd_ideas.md) —— 完整点子集（12 类）+ 性能红线 + MVP 实现顺序

## 许可

`mods.toml` 中声明为 **MIT**，但仓库中尚无 `LICENSE` 文件，待补充。

### 2. 物品贴图（用户提过，优先级低）

- **四分之一块**：四角通用一个小方块，看不出"这是四分之一"，也看不出是哪一角
- **五仁月饼**：固定的"四色拼盘"贴图，不反映它实际是哪四个角拼的
  （静态贴图做不到，除非把清单塞进能同步的组件 —— 目前只有原版 `custom_name` 靠得住）
