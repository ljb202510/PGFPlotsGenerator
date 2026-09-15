# RAG 检索增强 on/off 评估对比

> 目的：在相同提示词、相同模型通道（qwen）下，对比后端 **开 RAG**（`RAG_ENABLED=true`）与 **关 RAG**（`RAG_ENABLED=false`）的生成质量与耗时。
>
> 评估脚本：`spring-backend/eval/eval.mjs`（逐用例 chat → 编译 → 静态违例检测）；对比脚本：`spring-backend/eval/compare.mjs`。
> 结果源文件位于 `spring-backend/eval/results/`。

---

## 0. 全部批次总览

已有多批次评估（不同时间 / 用例集大小）,本节以最新 v3.0（16 用例）为准，历史批次作趋势参考。

| 批次（时间） | on 文件 | off 文件 | 用例 | on avg | off avg | on first | off first | on 零违例 | off 零违例 |
|---|---|---|---|---|---|---|---|---|---|
| 旧版 09-13 | rag-on | rag-off | 10 | 16,794 | 15,387 | 1.000 | 1.000 | —（旧格式无检测） | — |
| v2 09-14 | v2-qwen-rag-on | v2-qwen-rag-off | 16 | 9,519 | 7,567 | 0.938 | 0.938 | 0.938 | 0.938 |
| **v3.0 09-15** | v3.0-qwen-rag-on | v3.0-qwen-rag-off | 16 | **6,687** | **10,967** | **1.000** | **1.000** | **1.000** | **0.938** |

注：`first`/`零违例` 为 0.938 均源于同一条用例（v2 为 `bar_dense` 编译失败、v3.0 为 `dual_axis` 图例位置违例）；旧版未做静态违例检测，故无零违例列。

### 关键趋势
- **on/off 在「首轮通过率」上无系统差异**：三批次均一致（1.000/0.938 一一对应），负样本均非 RAG 开关所致。
- **耗时胜负随时间翻转，结论不稳**：旧版 / v2 批次里 **on（开 RAG）更慢**（约 +9% / +26%），只有 v3.0 批次 on 更快（−39%）。单次抽样 + 模型波动下，无法稳健断言「RAG 省时」。

---

## 1. v3.0 最新批次：总指标对比（16 用例，2026-09-15）

| 指标 | on（开 RAG） | off（关 RAG） | 差值 (on−off) |
|------|:----:|:----:|:----:|
| 用例总数 | 16 | 16 | 0 |
| 生成成功率 | 1.000 | 1.000 | 0 |
| 可编译率（占生成） | 1.000 | 1.000 | 0 |
| 首轮通过率 | 1.000 | 1.000 | 0 |
| 零违例通过率 | 1.000 | 0.938 | **+0.062** |
| 平均耗时（chat） | 6,687 ms | 10,967 ms | **−4,280 ms** |
| P95 耗时（chat） | 10,969 ms | 29,417 ms | **−18,448 ms** |

## 2. v3.0 最新批次：逐用例对比

- `T` = 该列通过，`.` = 未通过（gen=生成、code=有代码、comp=编译成功、violf=零违例）
- `lat` = chat 请求耗时（毫秒），`Δ` = on 耗时减 off 耗时。

| 用例 | on: gen code comp violf | off: gen code comp violf | lat(on) | lat(off) | Δ |
|------|:-: :-: :-: :-:|:-: :-: :-: :-:|--:|--:|--:|
| ambiguous_trend | T T T T | T T T T | 5085 | 5635 | −550 |
| bar_ambiguity | T T T T | T T T T | 9904 | 6416 | +3488 |
| bar_dense | T T T T | T T T T | 7302 | 5858 | +1444 |
| bar_negative | T T T T | T T T T | 8806 | 9913 | −1107 |
| bar_quarter | T T T T | T T T T | 6641 | 19314 | −12673 |
| bar_stacked | T T T T | T T T T | 8212 | 6835 | +1377 |
| combo_errorbar_multi | T T T T | T T T T | 5765 | 4629 | +1136 |
| dense_legend_outside | T T T T | T T T T | 6792 | 5626 | +1166 |
| **dual_axis** | T T T T | T T T **.** | 5134 | 9354 | −4220 |
| error_bar | T T T T | T T T T | 5734 | 17545 | −11811 |
| line_multi | T T T T | T T T T | 5828 | 8202 | −2374 |
| line_trend | T T T T | T T T T | 6299 | 21984 | −15685 |
| missing_data | T T T T | T T T T | 6027 | 5075 | +952 |
| pie_share | T T T T | T T T T | 1141 | 11272 | −10131 |
| scatter | T T T T | T T T T | 7347 | 8395 | −1048 |
| stacked_area | T T T T | T T T T | 10969 | 29417 | −18448 |

## 3. 历史批次差异（供参考）

| 批次 | 差异要点 |
|------|----------|
| 旧版 09-13（10 用例） | 生成/可编译/首轮通过率 on/off 均 1.000，无差异；on avg 16,794 > off avg 15,387（+9%），P95 on 41,664 > off 40,232。 |
| v2 09-14（16 用例） | 通过率 on/off 均 0.938（同一 `bar_dense` 编译失败，与 RAG 无关）；零违例率双双 0.938；on avg 9,519 > off avg 7,567（+26%），但 P95 on 14,310 < off 17,200。 |
| v3.0 09-15（16 用例） | 见 §1/§2：通过率齐平、on 零违例 +0.062、on avg 更快（−39%）。 |

## 4. 结论

1. **开关不影响通过率（弱结论）**：评估集内 on/off 首轮通过率、可编译率三批次均一一对应且一致，无翻转用例；零违例率的反复由单条用例（`bar_dense` / `dual_axis`）引起，不构成系统差异。
2. **耗时优势不稳健**：v3.0 当日 on 更快（avg −39% / P95 更明显），但旧版与 v2 批次 on 反而更慢（+9% / +26%）。RAG 的 few-shot 召回应是「低延迟、省一次流式重试」的合理机制，但首轮生成本就有模型波动，**单次抽样无法确证**。
3. **需要用分布而非单次值裁量**：两批 16 用例的差异多来自个别高耗用例（v3.0 off 的 `bar_quarter`/`error_bar`/`line_trend`/`stacked_area` 达 15.7–29.4s）。若要下稳定结论，应同 tag 多轮取均值并对齐任意固定超时之外的波动。

> 建议：若关注点在于「RAG 是否值得常开」，可对同一 16 用例跑 N 轮（如 3 轮）on 与 off，统计 avg / P95 的箱型或中位数，并关注 dual_axis 这类零违例受益案例；仅凭当前单轮结果不足以判定开 RAG 更快。

## 5. 复现方法

```bash
# 1. 起后端（开 RAG）
set RAG_ENABLED=true && mvn spring-boot:run
node eval/eval.mjs --tag=v3.0-qwen-rag-on --model=qwen

# 2. 停后端起后端（关 RAG）
set RAG_ENABLED=false && mvn spring-boot:run
node eval/eval.mjs --tag=v3.0-qwen-rag-off --model=qwen

# 3. 对比（脚本默认从 eval/results 读，传文件名即可）
node eval/compare.mjs --on=v3.0-qwen-rag-on.json --off=v3.0-qwen-rag-off.json
```