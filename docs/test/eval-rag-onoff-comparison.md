# RAG 检索增强 on/off 评估对比

> 目的：在相同提示词、相同模型通道（qwen）、相同 16 个评估用例下，对比后端 **开 RAG**（`RAG_ENABLED=true`）与 **关 RAG**（`RAG_ENABLED=false`）的生成质量与耗时差异。
>
> 评估脚本：`spring-backend/eval/eval.mjs`（逐用例 chat → 编译 → 静态违例检测）；对比脚本：`spring-backend/eval/compare.mjs`。
> 结果源文件：`eval/results/v3.0-qwen-rag-on.json`（开） / `eval/results/v3.0-qwen-rag-off.json`（关）。

---

## 1. 总指标对比

| 指标 | on（开 RAG） | off（关 RAG） | 差值 (on−off) |
|------|:----:|:----:|:----:|
| 用例总数 | 16 | 16 | 0 |
| 生成成功率 | 1.000 | 1.000 | 0 |
| 可编译率（占生成） | 1.000 | 1.000 | 0 |
| 首轮通过率 | 1.000 | 1.000 | 0 |
| 零违例通过率 | 1.000 | 0.938 | **+0.062** |
| 平均耗时（chat） | 6,687 ms | 10,967 ms | **−4,280 ms** |
| P95 耗时（chat） | 10,969 ms | 29,417 ms | **−18,448 ms** |

## 2. 逐用例对比

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

## 3. 结论

1. **通过率层面两种开关无实质差异**：16 例全部「首轮通过」，生成率 / 可编译率 / 首轮通过率均为 1.000，无通过与否翻转的用例。
2. **零违例率差异**：开 RAG 为 1.000，关 RAG 为 0.938——关 RAG 时 `dual_axis` 出现 1 例 `R1_LEGEND_POS`（图例位置）静态违例，RAG 召回示例帮助规避了该例。
3. **耗时差异显著，RAG 明显更快**：平均省约 4.3s（约 39%），P95 省约 18.4s。关 RAG 时 `bar_quarter` / `error_bar` / `line_trend` / `stacked_area` 等用例耗时异常偏高（15.7s–29.4s），是 P95 差距的主要来源。

> 说明：这是**单次抽样**的横向对比。部分用例在 off 下耗时异常高可能受模型波动影响；若需更稳健的结论，建议同一 tag 多轮取均值，并统计耗时分布而非仅均值/P95。

## 4. 复现方法

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