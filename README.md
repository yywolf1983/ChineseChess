# 中国象棋应用

## 项目简介

这是一个功能完整的中国象棋应用，支持双人对战、人机对战、摆棋模式等多种游戏模式，具有棋谱管理、AI支招等功能。

## 功能特点

### 核心功能
- **多种游戏模式**：双人对战、人机对战（玩家红/黑）、双机对战
- **摆棋模式**：支持自由摆放棋子，保存摆棋局面
- **棋谱管理**：加载、保存、查看棋谱
- **AI对战**：集成PikafishAI引擎，提供智能对战
- **记谱系统**：标准中国象棋记谱法，支持PGN格式

### 技术特点
- **棋盘坐标系统**：采用标准中国象棋坐标系统
- **棋子移动规则**：完整实现中国象棋规则
- **FEN支持**：支持FEN格式保存棋盘状态
- **SAF文件操作**：使用Storage Access Framework进行文件操作
- **UTF-8编码**：确保中文记谱正确显示

## 项目结构

```
├── app/                  # 主应用目录
│   ├── src/main/java/    # Java源代码
│   │   ├── AICore/       # AI核心模块
│   │   ├── ChessMove/     # 棋子移动规则
│   │   ├── CustomDialog/  # 自定义对话框
│   │   ├── CustomView/    # 自定义视图
│   │   ├── Info/          # 数据模型
│   │   ├── Utils/         # 工具类
│   │   └── top/nones/chessgame/  # 主活动
│   ├── src/main/res/      # 资源文件
│   └── build.gradle       # 应用构建配置
├── Pikafish.2026-01-02/   # PikafishAI引擎
├── obk/                  # 开局库
├── build.gradle           # 项目构建配置
└── settings.gradle        # 项目设置
```

## 构建与运行

### 使用 build.sh 脚本构建
项目提供了 `build.sh` 脚本，方便进行各种构建操作：

**脚本功能：**
- `./build.sh` - 默认构建Debug版本
- `./build.sh build` - 构建Debug版本
- `./build.sh build-release` - 构建Release版本
- `./build.sh install` - 构建并安装Debug版本到设备
- `./build.sh install-release` - 构建并安装Release版本到设备
- `./build.sh clean` - 清理构建缓存
- `./build.sh test` - 运行测试
- `./build.sh apk` - 生成Debug APK文件
- `./build.sh bundle` - 生成Android App Bundle
- `./build.sh info` - 显示项目信息
- `./build.sh help` - 显示帮助信息

**使用示例：**
```bash
# 构建Debug版本
./build.sh

# 构建并安装到设备
./build.sh install

# 清理构建缓存
./build.sh clean
```

### 传统构建方式
1. 克隆项目到本地
   ```bash
   git clone git@github.com:yywolf1983/ChineseChess.git
   ```

2. 打开Android Studio，导入项目

3. 同步Gradle依赖

4. 构建项目
   ```bash
   ./gradlew assembleDebug
   ```

### 运行方式
- **直接运行**：在Android Studio中点击运行按钮
- **安装APK**：将构建生成的APK文件安装到Android设备
- **使用脚本安装**：`./build.sh install`

## 游戏操作

### 基本操作
- **点击棋子**：选择要移动的棋子
- **点击目标位置**：移动选中的棋子
- **悔棋**：点击悔棋按钮撤销上一步
- **重新开始**：点击重新开始按钮重置游戏

### 摆棋模式
1. 点击摆棋按钮进入摆棋模式
2. 从棋子选择区域选择棋子
3. 点击棋盘放置棋子
4. 摆棋完成后，选择开局方开始游戏

### 棋谱管理
1. 点击保存按钮保存当前棋谱
2. 点击加载按钮加载已保存的棋谱
3. 支持从任意位置加载和保存棋谱

## 技术实现

### 棋子移动规则
- 实现了完整的中国象棋移动规则
- 支持将军、应将、将死等规则判断

### AI引擎
- 集成PikafishAI引擎
- 支持不同难度级别

### 记谱系统
- 标准中国象棋记谱法
- 支持PGN格式保存和加载
- 支持特殊走法标记（如"前卒"、"后炮"）

### 文件操作
- 使用Storage Access Framework进行文件操作
- 支持从任意位置加载和保存棋谱
- 确保UTF-8编码，避免中文乱码

## 注意事项

1. **权限要求**：需要存储权限以保存和加载棋谱
2. **性能优化**：AI计算可能会消耗较多资源，建议在性能较好的设备上使用
3. **文件大小**：项目包含PikafishAI引擎和开局库，文件较大

## 贡献

欢迎提交Issue和Pull Request，帮助改进这个项目。

## 许可证

本项目使用**WTFPL许可证**（DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE），这是一种极其宽松的许可证，允许您：

- ✅ 商业使用
- ✅ 修改代码
- ✅ 分发软件
- ✅ 私人使用
- ✅ 任何其他您想做的事情

**核心条款**：
> 0. You just DO WHAT THE FUCK YOU WANT TO.

简单来说，您可以完全自由地使用、修改和分发本软件，没有任何限制。

详见LICENSE文件。

## 项目声明

### AI完成声明
本项目是由**人工智能**（AI）完成的，包括但不限于：
- 代码编写与优化
- 功能设计与实现
- 错误修复与调试
- 文档编写与更新
- 构建脚本与配置