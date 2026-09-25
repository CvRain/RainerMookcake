# 馅料扩展设计（6 种首发）

用户 2026-09-25 确认的范围:**先上 6 种 + 吃下去有效果**,图标**用叠层换色调**。

## 为什么不能只加几条 JSON

现在的链路:

```
面团 ×4 + 可可豆豆沙 → 带馅面团 ×4      （crafting_shapeless）
带馅面团（方块形态）用模具右键压印 → 生月饼   （Java：MooncakeDoughBlock）
生月饼 --smelting--> 月饼               （配方模板里写死 mooncake_kind）
```

原版配方（shapeless / 烧炼）的产物是**固定模板**，**不继承原料的组件** ——
所以馅料身份会在链路里丢掉，所有月饼最后长得一样。

## 关键简化：不需要为"加馅"写自定义配方

**每种馅料是不同的原料**，所以每条配方可以**写死自己的馅料组件**：

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": ["mooncake_overflow:mooncake_dough", "mooncake_overflow:mooncake_dough",
                  "mooncake_overflow:mooncake_dough", "mooncake_overflow:mooncake_dough",
                  "minecraft:sweet_berries"],
  "result": {"id": "mooncake_overflow:filled_mooncake_dough", "count": 4,
             "components": {"mooncake_overflow:mooncake_filling": "sweet_berries"}}
}
```

6 种馅料 = 6 条这样的 JSON，**零 Java**。

## 需要动 Java 的只有两处

### 1. 压印那一步（`MooncakeDoughBlock`）

它本来就在 Java 里产出 `raw_mooncake`（拿模具右键），
所以顺手把**带馅面团上的馅料组件抄到生月饼上**即可。

### 2. 烤制那一步（需要 1 个自定义 cooking 配方类）

`AbstractCookingRecipe` 的产物同样是模板。子类只改 `assemble`：
把输入（生月饼）的 `mooncake_filling` 抄到产物上。
**照 `ComponentStonecuttingRecipe` 的样子写就行**（那个类的注释里已经记了坑：
`getType()` 要继承父类，否则不会出现在正确的界面里）。

如果嫌多，也可以先把烤制换成**营火/烟熏之外的 Java 步骤**，
但推荐还是自定义 cooking —— 三个烤法（熔炉/烟熏/营火）都能自动继承。

## 吃下去的效果

在 `MooncakeBlockItem` 里重写 `finishUsingItem(ItemStack, Level, LivingEntity)` ——
**这是吃东西"吃完那一刻"的钩子**，比在 `use` 里判断靠谱（进食有进度条）。

| 馅料 | 原版物品 | 效果 |
|---|---|---|
| `sweet_berries` | 甜浆果 | 生命恢复 |
| `honey` | 蜂蜜瓶 | 解除中毒 |
| `carrot` | 胡萝卜 | 夜视 |
| `cookie` | 曲奇 | 饱和 |
| `pufferfish` | 河豚 | 水下呼吸 + 中毒 |
| `rotten_flesh` | 腐肉 | 饥饿 |

效果用 `MobEffectInstance(Holder<MobEffect>, duration, amplifier)`，
时长/等级按"这是个月饼"来给（比原版食物大方一点，毕竟是中秋）。

## 图标：叠层换色调，不做数量乘法

如果按老办法给每种馅料 ×每种形态 ×每种氧化度都出一张贴图，
数量会翻好几倍（3 纹样 × 4 氧化度 × 6 馅料 = 72 张起）。

改用 **`minecraft:composite`**：把"馅料色调"做成一张**带中心色块的透明 16×16**，
叠在现有图标上面。每种馅料只要 **1 张**（6 张），而且不动现有的任何图标。

模型结构（生成器里拼）：

```
select(形态) → select(铜) → select(氧化度)
                              → composite[ 基础图标, select(馅料) → 色调叠层 ]
```

## 名字

`mooncake_overflow.filling.sweet_berries` = `甜浆果` / `Sweet Berry`，
月饼名字拼成「甜浆果月饼」，铜月饼则是「氧化的圆铜月饼·甜浆果」之类 ——
**和现有的形态/氧化度前缀串起来**，正是这个模组"名称溢出"的主题。

## 待办

- [ ] `Filling` 枚举（6 个值 + 原版物品 + 效果 + 语言键）
- [ ] 组件 `mooncake_filling`（枚举 codec）
- [ ] 6 条带固定组件的 shapeless 配方
- [ ] `MooncakeDoughBlock` 压印时抄馅料
- [ ] 自定义 cooking 配方类（抄馅料）
- [ ] `finishUsingItem` 里给效果
- [ ] 6 张色调叠层贴图 + 生成器里的 composite 结构
- [ ] 语言键（中英）
- [ ] `check_resources.py` 里加一条：每种馅料都要有配方、贴图、语言键
