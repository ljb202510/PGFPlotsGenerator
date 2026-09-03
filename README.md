# PGFPlotsGenerator
PGFPlotsGenerator 是一个「智能图表生成系统」：用户用自然语言描述需求（可附带 Excel/CSV/文本数据集），后端调用大语言模型生成 LaTeX/PGFPlots 代码，再通过 XeLaTeX 编译为 PDF 供在线预览与下载；生成过程持久化到 MySQL，支持历史回溯、多轮对话保存与反馈通知。
