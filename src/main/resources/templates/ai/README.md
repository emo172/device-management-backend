# AI Prompt 模板说明

本目录存放 AI 默认 Prompt 模板文件，也是后续 Qwen provider 继续复用的模板真相源。

- `default-intent-recognition.txt`：未从数据库读取到启用模板时使用，用于识别固定意图边界并判断缺失字段。
- `default-info-extraction.txt`：未从数据库读取到启用模板时使用，用于按统一 JSON Schema 输出结构化字段。
- `default-result-feedback.txt`：未从数据库读取到启用模板时使用，用于成功反馈或补充信息提示。
- `default-conflict-recommendation.txt`：未从数据库读取到启用模板时使用，用于业务冲突、取消失败或拒绝执行场景。

首发阶段的上游策略已固定为：**单轮输入、不拼接历史对话**；`sessionId` 只用于本地历史归组，不会作为多轮上下文发送给上游模型。

结构化提取阶段若开启 JSON Mode，最终发往上游的 Prompt 必须显式包含 `JSON` 关键词；默认模板不会单独承担全部约束，运行时代码会在组合 Prompt 时补齐统一 schema 与 JSON Mode 守卫。

说明：这些 `.txt` 文件会被运行时代码原样读取，为避免污染模板正文，不在模板文件内部追加注释，而把维护说明集中放在本 README 中。
