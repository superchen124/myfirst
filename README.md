# **抖音简版的消息列表**



## 功能列表

### 消息列表功能

#### 1. 消息展示

- ✅ 消息列表展示（RecyclerView）

- ✅ 支持三种消息类型

- ✅ 未读消息标识（红色圆点）

- ✅ 用户头像展示（42 个头像资源随机分配）

- ✅ 备注名称显示（如有备注则显示备注，否则显示用户名）

#### 2. 搜索功能

- ✅ 实时搜索（300ms 防抖优化）

- ✅ 多字段搜索：

- ✅ 关键词高亮显示（橙色高亮）

- ✅ 高亮结果缓存（避免重复计算）

#### 3. 时间显示

- ✅ 智能时间格式化

- ✅ 支持多种时间戳格式解析

#### 4. 消息交互

- ✅ 点击消息项 → 标记为已读

- ✅ 点击头像 → 进入备注编辑页面（共享元素转场动画）

- ✅ 点击新消息横幅 → 滚动到顶部

#### 5. 新消息功能

- ✅ 定时生成新消息（每 20 秒自动生成）

- ✅ 新消息提示横幅（顶部动画提示，5 秒后自动隐藏）

- ✅ 新消息自动保存到数据库

#### 6. 数据持久化

- ✅ 应用启动时从数据库加载消息

- ✅ 首次运行自动插入示例数据（5 条示例消息）

- ✅ 返回主界面时自动刷新数据

------

### 备注功能

#### 1. 备注编辑

- ✅ 编辑用户备注

- ✅ 显示已有备注（进入页面时自动加载）

- ✅ 保存备注到数据库

- ✅ 备注支持空值（可清空备注）

#### 2. 转场动画

- ✅ 页面进入动画（渐变 + 卡片放大）

- ✅ 页面退出动画（下滑动画）



## 代码运行截图

![image-20251207171352394](C:\Users\LX\AppData\Roaming\Typora\typora-user-images\image-20251207171352394.png)



## **项目结构**

```
bytedance/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/bytedance/
│   │   │   ├── MainActivity.kt          # 主界面，消息列表展示
│   │   │   ├── MessageAdapter.kt        # RecyclerView 适配器
│   │   │   ├── Message.kt               # 消息数据模型
│   │   │   ├── MessageDatabaseHelper.kt # SQLite 数据库操作
│   │   │   └── RemarkActivity.kt        # 备注编辑页面
│   │   ├── res/
│   │   │   ├── layout/                  # 布局文件
│   │   │   ├── drawable/               # 图片资源
│   │   │   └── values/                 # 颜色、字符串等资源
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── gradle/
```

**核心模块：**
- **MainActivity**: 消息列表主界面，包含搜索、新消息提示、定时消息生成
- **MessageAdapter**: RecyclerView 适配器，处理列表展示、高亮、时间格式化
- **MessageDatabaseHelper**: SQLite 数据库封装，消息和备注的 CRUD
- **RemarkActivity**: 备注编辑页面

## **技术方案**

### **1. 数据层**

- **数据库**: SQLite（`SQLiteOpenHelper`）
  - `messages` 表：存储消息（JSON ）
  - `remarks` 表：存储用户备注
  - 版本管理：支持数据库升级

- **数据序列化**: JSON
  - `Message` ↔ `JSONObject` 扩展函数
  - 使用 JSON 存储复杂对象结构

### **2. UI 层**

- **RecyclerView + DiffUtil**
  - `MessageDiffCallback` 实现差异计算
  - 只更新变化的项，提升性能
  - 支持插入/删除/移动动画

- **搜索功能**
  - 实时搜索（300ms 防抖）
  - 支持用户名、备注、内容搜索
  - 关键词高亮（`SpannableString` + 缓存）

- **时间格式化**
  - 智能时间显示（刚刚、X分钟前、昨天、X天前、MM-dd）
  - 支持多种时间戳格式解析

### **3. 交互设计**
- **共享元素转场动画**
  - 头像点击进入备注页
- **新消息提示**
  - 顶部横幅动画
  - 点击动画自动跳转到消息顶部
  - 自动隐藏（5秒）
- **消息类型**
  - TEXT：普通文本消息
  - IMAGE：带图片消息
  - CTA：带按钮的运营消息

### **4. 性能优化**
- **DiffUtil**: 精确更新列表项
- **ViewHolder 复用**: RecyclerView 缓存机制
- **高亮缓存**: 避免重复计算 `SpannableString`
- **数据库事务**: 批量插入使用事务提升性能
- **搜索防抖**: 减少频繁过滤操作

## **难点分析**

### **1. DiffUtil 的精确实现**
**难点：**
- 如何准确识别消息的唯一性
- 如何判断消息内容是否变化

**解决方案：**
```kotlin
// 使用用户名+内容+时间戳作为唯一标识
override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
    return oldMsg.userName == newMsg.userName &&
           oldMsg.content == newMsg.content &&
           oldMsg.timestamp == newMsg.timestamp
}

// 使用 data class 的 equals 判断内容是否相同
override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
    return oldList[oldPos] == newList[newPos]
}
```

**价值：**
- 搜索过滤时只更新变化的项
- 新增消息时只创建必要的 ViewHolder
- 标记已读时只更新变化的项

### **2. 数据库版本升级**
**难点：**
- 旧版本用户升级时需要添加新表
- 不能影响已有数据

**解决方案：**
```kotlin
override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
        // 使用 IF NOT EXISTS 避免重复创建
        val createRemarksTableSql = """
            CREATE TABLE IF NOT EXISTS $TABLE_REMARKS (...)
        """.trimIndent()
        db.execSQL(createRemarksTableSql)
    }
}
```

### **3. 消息状态更新（标记已读）**
**难点：**
- Message 是不可变对象（data class + val）
- 需要更新数据库中的 JSON 数据

**解决方案：**
```kotlin
// 使用 copy() 创建新对象
val updated = stored.copy(isUnread = false)
val updatedJson = updated.toJson().toString()
// 更新数据库
db.update(TABLE_MESSAGES, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
```

### **4. 搜索高亮性能优化**
**难点：**
- 每次滚动都可能触发高亮计算
- `SpannableString` 创建成本较高

**解决方案：**
```kotlin
// 使用缓存避免重复计算
private val highlightCache = mutableMapOf<String, SpannableString>()

private fun applyHighlight(text: String): SpannableString {
    val cacheKey = "${text}_$highlightKeyword"
    highlightCache[cacheKey]?.let { return it }  // 缓存命中
    
    // 计算高亮...
    // 限制缓存大小避免内存泄漏
    if (highlightCache.size > 100) {
        highlightCache.clear()
    }
    highlightCache[cacheKey] = spannable
    return spannable
}
```

### **5. 时间格式化逻辑**
**难点：**
- 支持多种时间戳格式
- 智能显示相对时间

**解决方案：**
```kotlin
// 1. 解析多种时间格式
private fun parseTimestamp(raw: String): Long? {
    // 支持：纯数字、yyyy-MM-dd HH:mm:ss、HH:mm 等
}

// 2. 智能格式化
return when {
    diff < oneMinute -> "刚刚"
    diff < oneHour -> "${diff / oneMinute} 分钟前"
    isSameDay(calNow, calTs) -> formatTime(ts, "HH:mm")
    isYesterday(calNow, calTs) -> "昨天 ${formatTime(ts, "HH:mm")}"
    diff < 7 * oneDay -> "${diff / oneDay} 天前"
    else -> formatTime(ts, "MM-dd")
}
```

### **6. 共享元素转场动画**
**难点：**
- 头像从列表项到详情页的平滑过渡
- 多个动画的协调

**解决方案：**
```kotlin
// MainActivity: 设置共享元素
val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
    this,
    Pair.create(avatarView, getString(R.string.transition_avatar))
)

// RemarkActivity: 配置转场动画
window.sharedElementEnterTransition = ChangeBounds().apply {
    duration = 400
    interpolator = AccelerateDecelerateInterpolator()
}
```

### **7. 实时搜索性能**
**难点：**
- 用户输入时频繁触发搜索
- 需要防抖和高效过滤

**解决方案：**
```kotlin
// 300ms 防抖
etSearch.addTextChangedListener {
    searchHandler.removeCallbacks(searchRunnable)
    searchHandler.postDelayed(searchRunnable, 300)  // 延迟执行
}

// 高效过滤（支持用户名、备注、内容）
val filtered = source.filter { msg ->
    val nameMatch = msg.userName.lowercase().contains(lower)
    val remarkMatch = remarks[msg.userName]?.lowercase()?.contains(lower) == true
    val contentMatch = msg.content.lowercase().contains(lower)
    nameMatch || remarkMatch || contentMatch
}
```

