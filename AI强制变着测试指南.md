# AI强制变着测试指南

## 测试目的
验证AI在遇到重复局面、长将、长捉等情况时，是否能正确强制变着

## 测试场景

### 测试1：AI重复长将测试
**测试步骤**：
1. 开始人机对战（AI为黑方）
2. 让AI连续将军红方3次
3. 观察AI是否在第3次将军后改变着法

**预期结果**：
- 第3次将军时，系统检测到长将
- 启用强制变着模式：`forceVariation = true`
- AI改变着法，不再继续将军
- 显示提示："长将，必须变着"

### 测试2：AI重复长捉测试
**测试步骤**：
1. 开始人机对战（AI为红方）
2. 让AI连续攻击同一棋子3次
3. 观察AI是否在第3次攻击后改变着法

**预期结果**：
- 第3次攻击时，系统检测到长捉
- 启用强制变着模式：`forceVariation = true`
- AI改变着法，不再攻击同一棋子
- 显示提示："长捉，必须变着"

### 测试3：AI重复局面测试
**测试步骤**：
1. 开始人机对战
2. 走成某个局面，然后回到同一局面3次
3. 观察AI是否在第3次时改变着法

**预期结果**：
- 第3次重复局面时，系统检测到三次重复
- 启用强制变着模式：`forceVariation = true`
- AI改变着法，避免重复局面
- 显示提示："重复局面，AI重新计算着法..."

## 关键代码检查点

### 1. 强制变着模式启用
```java
// 检查是否启用强制变着
if (chessInfo.forceVariation) {
    LogUtils.i("PvMActivityAI", "强制变着模式已启用，随机性=" + chessInfo.variationRandomness);
}
```

### 2. AI着法历史记录
```java
// AI着法历史记录
String moveKey = fromPos.x + "," + fromPos.y + "->" + toPos.x + "," + toPos.y;
aiMoveHistory.add(moveKey);
```

### 3. 着法重复性检查
```java
// 检查着法是否与历史相同
if (moveKey.equals(lastMove)) {
    LogUtils.i("PvMActivityAI", "着法与历史相同，需要重新计算");
}
```

### 4. 强制选择不同着法
```java
// 强制选择不同着法
if (retryCount >= maxRetryCount && this.activity.chessInfo.forceVariation) {
    move = forceSelectDifferentMove(allPossibleMoves, triedMoves);
}
```

## 测试日志查看

### 1. Logcat标签
- **"PvMActivityAI"**：AI计算相关日志
- **"PikafishAI"**：AI引擎相关日志
- **"Move"**：走棋记录日志
- **"PvMActivityControls"**：强制变着相关日志

### 2. 关键日志信息
```
// 强制变着模式启用
PikafishAI: 强制变着模式：深度=10, 时间=1000ms, 随机性=3
PikafishAI: 强制变着模式：设置MultiPV=3

// 着法重复检测
PvMActivityAI: 着法与历史相同，需要重新计算

// 强制选择不同着法
PvMActivityAI: 达到最大重试次数，强制选择不同着法
```

## 测试验证方法

### 1. 界面观察
- **提示信息**：是否显示"长将，必须变着"、"长捉，必须变着"等提示
- **AI着法**：AI着法是否确实改变
- **游戏状态**：是否从强制变着模式恢复正常

### 2. 状态变量检查
```java
// 检查强制变着状态
boolean forceVariation = chessInfo.forceVariation;
int variationRandomness = chessInfo.variationRandomness;

// 检查长将/长捉计数
int consecutiveCheckRed = chessInfo.consecutiveCheckRed;
int consecutiveCheckBlack = chessInfo.consecutiveCheckBlack;
int consecutiveAttackRed = chessInfo.consecutiveAttackRed;
int consecutiveAttackBlack = chessInfo.consecutiveAttackBlack;
```

### 3. 着法记录检查
```java
// 检查AI着法历史
List<String> aiMoveHistory = pvMActivityAI.getAiMoveHistory();
// 检查最后几个着法是否相同
```

## 常见问题排查

### 1. AI没有强制变着
**可能原因**：
- 强制变着模式没有正确启用
- AI引擎没有响应强制变着设置
- 着法历史记录不正确

**排查方法**：
- 检查`chessInfo.forceVariation`是否为true
- 检查PikafishAI日志中是否有"强制变着模式"相关日志
- 检查AI着法历史记录

### 2. AI仍然重复着法
**可能原因**：
- 所有可能着法都会导致重复
- 强制选择机制没有生效
- 随机性设置不够

**排查方法**：
- 检查`allPossibleMoves`是否收集到所有可能着法
- 检查`forceSelectDifferentMove`方法是否被调用
- 增加`variationRandomness`值

### 3. 强制变着后AI水平下降
**可能原因**：
- 随机性设置过高
- 搜索深度降低过多
- 选择非最佳着法

**解决方法**：
- 调整`variationRandomness`范围（1-5）
- 设置最小搜索深度限制
- 确保强制变着后恢复正常模式

## 测试建议

### 1. 逐步测试
1. 先测试单次强制变着
2. 测试连续多次强制变着
3. 测试不同棋局下的强制变着

### 2. 日志记录
- 开启详细日志记录
- 保存测试日志
- 分析关键事件时间线

### 3. 性能测试
- 测试强制变着响应时间
- 测试AI计算时间
- 测试内存使用情况

## 预期效果

### 1. **正常情况**
- AI在强制变着模式下给出不同的着法
- 着法改变后，重复局面/长将/长捉计数重置
- AI恢复正常计算模式

### 2. **边界情况**
- 所有可能着法都会导致重复时，AI随机选择一个着法
- 强制变着后，AI避免再次导致重复
- 用户得到清晰的提示信息

### 3. **用户体验**
- 强制变着响应时间在可接受范围内
- 提示信息清晰明了
- AI着法合理，不会明显降低AI水平

通过以上测试，可以验证AI强制变着功能是否正常工作，确保象棋规则的正确执行。