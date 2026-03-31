# AI重复局面处理测试

## 测试目的
验证AI在遇到重复局面时能够正确重新计算着法，避免导致三次重复局面

## 测试场景
1. **强制变着检测**：当检测到重复局面时，AI是否能启用强制变着模式
2. **着法重新计算**：如果AI给出的着法会导致重复局面，是否能重新计算
3. **随机性增加**：在强制变着模式下，AI着法的随机性是否增加

## 测试步骤

### 测试1：检测重复局面并启用强制变着模式
**步骤**：
1. 开始人机对战（AI为黑方）
2. 走成某个局面，然后通过悔棋和走棋回到同一局面3次
3. 观察是否启用强制变着模式

**预期结果**：
- AI检测到三次重复局面
- 启用强制变着模式：`forceVariation = true`
- 设置中等随机性：`variationRandomness = 3`
- 重置重复局面计数

### 测试2：AI着法导致重复局面时的重新计算
**步骤**：
1. 在一个已出现两次的局面下（距离三次重复局面还差一次）
2. 触发AI计算
3. 观察AI给出的着法是否会导致局面重复
4. 如果会导致重复，观察AI是否重新计算

**预期结果**：
- AI检测到着法会导致重复局面
- AI撤销导致重复的着法
- AI重新触发计算，给出不同的着法
- 增加随机性：`variationRandomness` 增加1

### 测试3：AI连续计算5次后仍导致重复的局面
**步骤**：
1. 设置一个复杂的局面，所有着法都会导致重复
2. 触发AI计算
3. 观察AI如何处理这种情况

**预期结果**：
- AI尝试最多5次重算
- 每次重算都增加随机性
- 如果所有着法都会导致重复，AI最终返回null
- 游戏根据规则判断是否将死

## 测试代码逻辑

### 1. 重复局面检测逻辑
```java
// 在calculateAIMoveWithDepthUpdate方法中
if (this.activity.chessInfo.isThreefoldRepetition() || this.activity.chessInfo.isPerpetualCheck() || 
    this.activity.chessInfo.getPerpetualAttackSide() != null) {
    // 启用强制变着模式
    this.activity.chessInfo.forceVariation = true;
    this.activity.chessInfo.variationRandomness = 3;
    // 重置重复局面计数
    String currentHash = this.activity.chessInfo.generatePositionHash();
    if (this.activity.chessInfo.positionHistory.containsKey(currentHash)) {
        this.activity.chessInfo.positionHistory.put(currentHash, 1);
    }
}
```

### 2. 着法重复性检查逻辑
```java
// 在calculateAIMoveWithDepthUpdate方法中
while (retryCount < maxRetryCount) {
    // 获取AI着法
    PikafishAI.MoveWithScore moveWithScore = this.activity.pikafishAI.getBestMoveWithScore(this.activity.chessInfo);
    
    // 检查着法是否会导致重复局面
    boolean leadsToRepetition = checkIfMoveLeadsToRepetition(move);
    String moveKey = fromPos.x + "," + fromPos.y + "->" + toPos.x + "," + toPos.y;
    
    if (!triedMoves.contains(moveKey) && !leadsToRepetition) {
        // 这个着法不会导致重复局面，可以使用
        break;
    } else {
        // 会导致重复局面，需要重新计算
        retryCount++;
        if (this.activity.chessInfo.forceVariation) {
            this.activity.chessInfo.variationRandomness = Math.min(5, this.activity.chessInfo.variationRandomness + 1);
        }
        // 短暂延迟后重新计算
        Thread.sleep(100);
    }
}
```

### 3. 着法执行后的重复性检查
```java
// 在executeAIMove方法中
if (checkIfMoveLeadsToRepetition(move)) {
    // 撤销这个着法，因为它会导致重复局面
    this.activity.chessInfo.piece[fromPos.y][fromPos.x] = piece;
    this.activity.chessInfo.piece[toPos.y][toPos.x] = tmp;
    
    // 重新触发AI计算
    new Thread(() -> {
        Thread.sleep(100);
        this.activity.runOnUiThread(() -> {
            checkAIMove();
        });
    }).start();
    
    return false;
}
```

## 测试验证方法

### 1. 日志输出
查看Logcat中以下标签的日志：
- **"Move"**：记录AI走棋信息
- **"PvMActivityControls"**：记录强制变着相关日志
- **"PvMActivityAI"**：记录AI计算相关日志

### 2. 状态检查
检查以下关键状态变量：
- `chessInfo.forceVariation`：是否为true
- `chessInfo.variationRandomness`：是否增加
- `chessInfo.positionHistory`：重复计数是否重置
- `chessInfo.consecutiveCheckRed/Black`：长将计数是否重置

### 3. 界面反馈
观察以下界面反馈：
- **Toast提示**：是否显示"重复局面，AI重新计算着法..."
- **浮窗提示**：是否显示"检测到重复局面，已强制变着"
- **AI着法**：着法是否与之前不同

## 预期效果

### 1. **正常情况**
- AI给出的着法不会导致重复局面
- 着法执行成功，游戏继续

### 2. **重复局面检测**
- 检测到重复局面时，启用强制变着模式
- AI着法的随机性增加
- 系统记录并重置重复局面计数

### 3. **重新计算机制**
- 如果AI着法会导致重复，AI会重新计算
- 每次重算都增加随机性
- 最多尝试5次重算

### 4. **最终处理**
- 如果所有可能着法都会导致重复，AI返回null
- 游戏根据规则判断是否将死

## 注意事项

1. **性能考虑**：AI重算会导致延迟，每次重算有100ms延迟

2. **随机性限制**：`variationRandomness`最大为5，避免过于随机导致AI水平下降

3. **重算次数限制**：最多重算5次，避免无限循环

4. **用户反馈**：提供清晰的提示信息，让用户知道AI正在重新计算

## 测试结论

通过以上测试，可以验证AI处理重复局面的逻辑是否正确：

1. **检测准确性**：AI是否能准确检测重复局面

2. **强制变着有效性**：在强制变着模式下，AI着法是否确实改变



3. **重算机制可靠性**：AI是否能正确处理导致重复的着法



4. **用户体验**：用户是否能得到清晰的反馈和合理的等待时间