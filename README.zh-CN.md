<p align="center">
  <img src="docs/imgs/dashboard.png" alt="Dashboard Overview" width="800" />
</p>

[English](./README.md) | 中文版

<h2 align="center">每一次代码提交，都藏着一个故事。现在，你可以看见它。</h2>

## Entire Dashboard

**Entire Dashboard** 是一个基于 Web 的数据分析与可视化平台，用于展示由 [`entireio/cli`](https://github.com/entireio/cli) 生成的数据。

它提供了一个自托管的仪表板，帮助团队探索和分析由 Entire 记录的 **AI 辅助开发活动**。

### 背景

现代开发越来越依赖 AI 编程助手。Git 记录了**什么被修改**，但没有捕捉到**为什么修改**或 AI 工具如何参与了修改。

**Entire 平台**通过记录 AI 辅助编码会话并通过 **Checkpoints** 将它们链接到 Git 提交来解决这个问题。这些 Checkpoints 存储了丰富的元数据，包括：

- AI 提示和响应
- Agent Transcript
- 工具调用和文件编辑
- Token 使用量
- 会话上下文

所有这些信息都由 `entireio/cli` 本地捕获，并与您的仓库存储在一起。

### Entire Dashboard 的功能

Entire Dashboard 将原始数据转换为**交互式分析平台**。它摄取由 `entireio/cli` 生成的数据，并提供关于 AI 如何在仓库中被使用的可视化洞察。

使用 Entire Dashboard，您可以：

- 📊 分析跨仓库的 AI 编码活动
- 🤖 追踪 AI 与人工贡献
- 🧠 探索 AI 会话和 Checkpoint 历史
- 📈 监控 Token 使用量和工具调用
- 🔍 检查提交上下文和开发趋势
- 🗂 聚合多个仓库的洞察

这使得团队更容易**理解、审计和优化 AI 辅助开发工作流程**。

### 支持的平台

Entire Dashboard 支持托管在以下平台上的仓库：

- GitHub
- GitLab
- Gitee

### 典型使用场景

- 采用 AI 编程工具的工程团队
- 希望了解 AI 辅助开发情况的组织
- 分析跨仓库生产力趋势的团队
- 探索代码变更历史和背后原因的开发者

### 系统架构

<p align="center">
  <img src="docs/imgs/structure.png" alt="系统架构" width="600" />
</p>

整体工作流程：从 CLI 捕获对话和事件，通过核心服务处理并存储到数据库，然后通过仪表板进行可视化分析。

## 🔧 安装与运行

### 0. 安装 Entire CLI

Entire Dashboard 依赖于 Entire CLI 生成的数据。请先从[官方网站](https://entire.io/home)安装 Entire CLI：

```bash
curl -fsSL https://entire.io/install.sh | bash
```

安装后，在目标仓库中配置 CLI 以开始捕获 AI Agent 会话数据。详细信息请参阅 [entireio/cli](https://github.com/entireio/cli)。

### 使用 Docker 运行

1. **克隆仓库**：
   ```bash
   git clone https://github.com/sunmh207/entire-dashboard.git
   cd entire-dashboard
   ```

2. **启动服务**：
   ```bash
   docker compose up -d
   ```

3. **访问仪表板**：在浏览器中打开 [http://localhost:81](http://localhost:81)。

4. **登录**：默认用户名为 `admin`，默认密码为 `admin`。

## 🚀 快速开始

系统启动运行后：

1. 进入 **Repositories**，添加一个仓库并同步数据。
2. 访问 **Overview / Checkpoints** 探索查看数据。

> 备注：仅当启用 entire 并通过 AI Agent（如 Cursor / OpenCode / Claude Code）提交记录后，对应的数据方可显示。

## 📸 截图

<p align="center">
  <img src="docs/imgs/snapshot_overview.png" alt="仪表板概览" width="800" />
</p>
<p align="center">
  <img src="docs/imgs/snapshot_sessions.png" alt="会话" width="800" />
</p>

仪表板提供了 AI Agent 会话的全面视图，展示了 Checkpoint 历史、提交活动和仓库统计信息。
