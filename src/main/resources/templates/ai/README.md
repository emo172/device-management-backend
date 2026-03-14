# AI Prompt 模板说明

本目录存放 AI 规则降级模式下的默认 Prompt 模板文件。

- `default-intent-recognition.txt`：未从数据库读取到启用的意图识别模板时使用。
- `default-info-extraction.txt`：未从数据库读取到启用的信息提取模板时使用。
- `default-result-feedback.txt`：未从数据库读取到启用的结果反馈模板时使用。
- `default-conflict-recommendation.txt`：未从数据库读取到启用的冲突推荐模板时使用。

说明：这些 `.txt` 文件会被运行时代码原样读取，为避免污染模板正文，不在模板文件内部追加注释，而把维护说明集中放在本 README 中。
