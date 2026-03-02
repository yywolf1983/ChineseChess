# OBK 开局库规则说明

## 1. OBK 格式概述

OBK（Opening Book）是中国象棋开局库的一种常见格式，用于存储和管理象棋开局着法。

### 1.1 OBK 文件结构
- OBK 文件本质上是一个 SQLite 数据库文件
- 主要包含 `bhobk` 表，存储开局着法信息
- 表结构包含以下字段：
  - `vkey`: 棋盘状态的哈希值（64位整数）
  - `vmove`: 着法编码（16位整数）
  - `vscore`: 着法评分（整数）
  - `vmemo`: 着法备注（字符串）

## 2. 着法编码规则

### 2.1 着法编码格式
- OBK 使用 16 位整数编码着法
- 高 8 位表示起始位置
- 低 8 位表示目标位置

### 2.2 位置编码
- 棋盘坐标：9列 × 10行，左上角为 (0,0)，右下角为 (8,9)
- 位置编码公式：`pos = y * 9 + x`
- 示例：
  - 位置 (0,0) 编码为 0
  - 位置 (8,9) 编码为 89

### 2.3 着法解码
```python
def decode_obk_move(move_code):
    if move_code > 0:
        from_pos = (move_code >> 8) & 0xFF
        to_pos = move_code & 0xFF
        
        if from_pos < 90 and to_pos < 90:
            from_x = from_pos % 9
            from_y = from_pos // 9
            to_x = to_pos % 9
            to_y = to_pos // 9
            return f"{from_x},{from_y},{to_x},{to_y}"
    return None
```

## 3. 哈希值计算

### 3.1 Zobrist 哈希算法
- 使用 Zobrist 哈希计算棋盘状态
- 为每个位置的每种棋子类型生成一个随机数
- 通过异或操作计算整个棋盘的哈希值

### 3.2 初始局面哈希值
- 初始局面的哈希值：`15178025835508123735`
- 这个值在 Rust 代码和 Python 导入脚本中保持一致

## 4. 数据库结构

### 4.1 表结构
```sql
CREATE TABLE IF NOT EXISTS openings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    board_hash TEXT,
    best_move TEXT NOT NULL,
    score INTEGER NOT NULL,
    memo TEXT
);

CREATE INDEX IF NOT EXISTS idx_board_hash ON openings(board_hash);
```

### 4.2 字段说明
- `board_hash`: 棋盘状态的哈希值（文本形式存储）
- `best_move`: 着法字符串，格式为 `from_x,from_y,to_x,to_y`
- `score`: 着法评分，影响着法选择的概率
- `memo`: 着法备注信息

## 5. 着法选择逻辑

### 5.1 加权随机选择
- 根据着法的评分计算总分数
- 按照评分权重随机选择着法
- 评分越高，被选中的概率越大

### 5.2 选择流程
1. 计算当前棋盘状态的哈希值
2. 在数据库中查询该哈希值对应的着法
3. 按评分排序
4. 根据评分权重随机选择一个着法
5. 如果没有找到着法，使用AI搜索生成着法

## 6. 导入流程

### 6.1 导入步骤
1. 创建/清空 openings 表
2. 为初始局面添加常见开局着法
3. 遍历 OBK 文件目录
4. 逐个导入 OBK 文件中的记录
5. 验证数据库记录数

### 6.2 初始局面着法
为初始局面添加了以下常见着法：
- 中炮（左炮）：1,7,4,7（评分：10000）
- 中炮（右炮）：7,7,4,7（评分：10000）
- 跳马：3,9,4,7（评分：5000）
- 跳马：5,9,4,7（评分：5000）
- 进边兵：2,6,3,6（评分：100）
- 进边兵：6,6,5,6（评分：100）
- 进边兵：0,6,1,6（评分：100）
- 进边兵：8,6,7,6（评分：100）

## 7. 使用方法

### 7.1 代码调用
```rust
// 首先检查开局库中是否有最佳着法
if let Some(db) = get_openings_database() {
    let zobrist_key = board.get_position_hash();
    if let Some(mv) = db.get_best_move(zobrist_key) {
        return Some(mv);
    }
}

// 如果开局库中没有着法，使用AI搜索
let (_, best_move) = minimax(board, depth, i32::MIN, i32::MAX, board.is_red_turn, &start_time, max_time, &mut move_history, &mut tt, &mut history, &mut killers);
```

### 7.2 运行导入脚本
```bash
python3 convert_obk_to_openings.py
```

## 8. 验证方法

### 8.1 检查数据库记录数
```bash
sqlite3 chinese_chess_rust/openings.db "SELECT COUNT(*) FROM openings;"
```

### 8.2 检查初始局面着法
```bash
sqlite3 chinese_chess_rust/openings.db "SELECT best_move, score FROM openings WHERE board_hash = '15178025835508123735' ORDER BY score DESC;"
```

## 9. 常见问题

### 9.1 哈希值不匹配
- 检查 Zobrist 哈希算法是否与 Rust 代码一致
- 确保使用相同的种子值（42）
- 验证棋盘坐标系统是否一致

### 9.2 着法解码失败
- 检查着法编码是否在有效范围内（0-89）
- 确保坐标转换逻辑正确

### 9.3 数据库操作错误
- 检查 SQLite 数据库权限
- 确保文件路径正确
- 验证 SQL 语句语法

## 10. 优化建议

1. **增加着法多样性**：为同一局面添加更多不同的着法
2. **调整评分权重**：根据开局的流行程度调整评分
3. **定期更新开局库**：添加新的开局变化
4. **优化查询性能**：为常用查询添加索引

## 11. 参考资料

- [OBK 格式解析](https://zhuanlan.zhihu.com/p/658513137)
- [中国象棋开局库构建方法](https://www.chess.com/article/view/chinese-chess-opening-theory)
- [Zobrist 哈希算法](https://en.wikipedia.org/wiki/Zobrist_hashing)
