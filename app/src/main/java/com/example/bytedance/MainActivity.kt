package com.example.bytedance

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var newMessageBanner: TextView
    private lateinit var etSearch: EditText
    private lateinit var dbHelper: MessageDatabaseHelper
    private var allMessagesCache: List<Message> = emptyList()
    private var latestRemarks: Map<String, String> = emptyMap()
    private var searchQuery: String = ""

    private val uiHandler = Handler(Looper.getMainLooper())
    private val searchHandler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { applyFilter(scrollToTop = false) }
    private val messageScheduler = object : Runnable {
        override fun run() {
            val newMessage = createSimulatedMessage()
            dbHelper.insertMessages(listOf(newMessage))
            Log.d(TAG, "✅ 新消息已保存到数据库: ${newMessage.userName} - ${newMessage.content}")
            refreshMessagesAndRemarks()
            showNewMessageBanner()
            uiHandler.postDelayed(this, MESSAGE_INTERVAL_MS)
        }
    }
    private val hideBannerRunnable = Runnable { hideNewMessageBanner() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MainActivity 是启动 Activity，不需要转场动画配置
        setContentView(R.layout.activity_main)

        dbHelper = MessageDatabaseHelper(this)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        newMessageBanner = findViewById(R.id.tvNewMessageBanner)
        newMessageBanner.setOnClickListener {
            uiHandler.removeCallbacks(hideBannerRunnable)
            hideNewMessageBanner()
            recyclerView.scrollToPosition(0)
        }
        etSearch = findViewById(R.id.etSearch)
        etSearch.addTextChangedListener {
            searchQuery = it?.toString().orEmpty()
            // 防抖：300ms 后才执行搜索，减少频繁过滤
            searchHandler.removeCallbacks(searchRunnable)
            searchHandler.postDelayed(searchRunnable, 300)
        }

        val now = System.currentTimeMillis()
        // 从数据库加载数据，如果首次运行数据库为空，则插入一批示例数据
        var messages = dbHelper.getAllMessages()
        if (messages.isEmpty()) {
            messages = listOf(
                Message("系统助手", "欢迎来到消息中心", formatBaseTime(now - TimeUnit.MINUTES.toMillis(2)), true, R.drawable.ic_avatar_default, MessageType.TEXT, timestampMillis = now - TimeUnit.MINUTES.toMillis(2)),
                Message("摄影师阿明", "这张照片不错，看看？", formatBaseTime(now - TimeUnit.HOURS.toMillis(1)), false, R.drawable.ic_avatar_default, MessageType.IMAGE, imageResId = R.drawable.ic_avatar_default, timestampMillis = now - TimeUnit.HOURS.toMillis(1)),
                Message("运营小李", "限时福利，点击领取", formatBaseTime(now - TimeUnit.DAYS.toMillis(1)), true, R.drawable.ic_avatar_default, MessageType.CTA, buttonText = "领取奖励", timestampMillis = now - TimeUnit.DAYS.toMillis(1)),
                Message("产品经理", "记得参加明天的会议", formatBaseTime(now - TimeUnit.DAYS.toMillis(3)), false, R.drawable.ic_avatar_default, MessageType.TEXT, timestampMillis = now - TimeUnit.DAYS.toMillis(3)),
                Message("好友小王", "周末一起看电影？", formatBaseTime(now - TimeUnit.DAYS.toMillis(9)), true, R.drawable.ic_avatar_default, MessageType.TEXT, timestampMillis = now - TimeUnit.DAYS.toMillis(9))
            )
            dbHelper.insertMessages(messages)
        }

        val remarks = dbHelper.getAllRemarks()
        allMessagesCache = messages
        latestRemarks = remarks
        
        Log.d(TAG, "🚀 应用启动，从数据库加载了 ${messages.size} 条消息")

        messageAdapter = MessageAdapter(
            messages,
            remarks = remarks,
            onMessageClick = { message ->
                dbHelper.markMessageAsRead(message)
                refreshMessagesAndRemarks()
            },
            onAvatarClick = { message, avatarView ->
                val intent = Intent(this, RemarkActivity::class.java).apply {
                    putExtra(RemarkActivity.EXTRA_USER_NAME, message.userName)
                }
                // 使用共享元素转场动画：渐变 + 卡片跟手放大
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this,
                    Pair.create(
                        avatarView,
                        getString(R.string.transition_avatar)
                    )
                )
                ContextCompat.startActivity(this, intent, options.toBundle())
            }
        )
        recyclerView.adapter = messageAdapter

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        swipeRefreshLayout.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        // 每次返回主界面时，刷新消息和备注
        allMessagesCache = dbHelper.getAllMessages()
        val remarks = dbHelper.getAllRemarks()
        latestRemarks = remarks
        applyFilter(scrollToTop = false)
        startMessageScheduler()
    }

    override fun onPause() {
        super.onPause()
        stopMessageScheduler()
        searchHandler.removeCallbacks(searchRunnable)
    }

    private fun refreshMessagesAndRemarks(scrollToTop: Boolean = false) {
        allMessagesCache = dbHelper.getAllMessages()
        latestRemarks = dbHelper.getAllRemarks()
        applyFilter(scrollToTop)
    }

    private fun startMessageScheduler() {
        uiHandler.removeCallbacks(messageScheduler)
        uiHandler.postDelayed(messageScheduler, MESSAGE_INTERVAL_MS)
    }

    private fun stopMessageScheduler() {
        uiHandler.removeCallbacks(messageScheduler)
        uiHandler.removeCallbacks(hideBannerRunnable)
    }

    private fun formatBaseTime(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun applyFilter(scrollToTop: Boolean) {
        val query = searchQuery.trim()
        val remarks = latestRemarks
        val source = allMessagesCache
        val filtered = if (query.isBlank()) {
            source
        } else {
            val lower = query.lowercase(Locale.getDefault())
            source.filter { msg ->
                val nameMatch = msg.userName.lowercase(Locale.getDefault()).contains(lower)
                val remarkMatch = remarks[msg.userName]?.lowercase(Locale.getDefault())?.contains(lower) == true
                val contentMatch = msg.content.lowercase(Locale.getDefault()).contains(lower)
                nameMatch || remarkMatch || contentMatch
            }
        }
        messageAdapter.updateHighlight(query)
        messageAdapter.updateMessages(filtered)
        messageAdapter.updateRemarks(remarks)
        if (scrollToTop) {
            recyclerView.scrollToPosition(0)
        }
    }

    private fun createSimulatedMessage(): Message {
        // 100 个电影角色名字（去掉括号及来源）
        val names = listOf(
            "安迪·杜佛兰",
            "瑞德",
            "小丑·杰克",
            "布鲁斯·韦恩",
            "托尼·史塔克",
            "美国队长·史蒂夫",
            "雷神托尔",
            "洛基",
            "彼得·帕克",
            "奇异博士",
            "灭霸",
            "布朗博士",
            "马蒂",
            "阿甘",
            "珍妮",
            "莱昂",
            "玛蒂尔达",
            "尼奥",
            "墨菲斯",
            "崔妮蒂",
            "多米尼克·托莱多",
            "布莱恩·奥康纳",
            "哈利·波特",
            "赫敏·格兰杰",
            "罗恩·韦斯莱",
            "邓布利多",
            "伏地魔",
            "杰克·道森",
            "露丝",
            "小李飞刀",
            "楚门",
            "杰克·斯派洛",
            "詹姆斯·邦德",
            "印第安纳·琼斯",
            "汉·索洛",
            "卢克·天行者",
            "达斯·维德",
            "尤达大师",
            "李云龙",
            "张无忌",
            "托马斯·谢尔比",
            "金并",
            "小丑女哈莉",
            "死侍",
            "金·凯瑞",
            "蜘蛛侠·迈尔斯",
            "神奇女侠",
            "黑寡妇",
            "猎鹰山姆",
            "冬兵巴基",
            "罗伯特·兰登",
            "约翰·威克",
            "杰森·伯恩",
            "伊森·亨特",
            "Friday",
            "韩路",
            "程序猿小王",
            "产品经理小李",
            "导演老王",
            "摄影师阿明",
            "剪辑师小周",
            "小丑·亚瑟",
            "彼得·奎尔",
            "卡魔拉",
            "火箭浣熊",
            "格鲁特",
            "叶问",
            "甄子丹",
            "黄飞鸿",
            "阿祖",
            "刘建明",
            "托比·马奎尔",
            "安德鲁·加菲尔德",
            "汤姆·赫兰德",
            "小兰",
            "柯南",
            "灰原哀",
            "琴酒",
            "赤井秀一",
            "宫园薰",
            "新海诚路人甲",
            "千寻",
            "白龙",
            "无脸男",
            "龙猫",
            "波妞",
            "悟空",
            "江流儿",
            "哪吒",
            "敖丙",
            "苏菲",
            "哈尔",
            "娜乌西卡",
            "杰克",
            "娜美",
            "路飞",
            "索隆",
            "鸣人",
            "佐助",
            "卡卡西",
            "巴斯光年",
            "胡迪",
            "史莱克",
            "驴子"
        )

        // 100 句电影台词（去掉后面的出处）
        val contents = listOf(
            "希望让人自由。",
            "忙着活，或者忙着死。",
            "为什么这么严肃？",
            "要么作为英雄死去，要么活得足够久看到自己变成反派。",
            "我是钢铁侠。",
            "天哪，我竟然成了超级英雄。",
            "愿原力与你同在。",
            "飞向宇宙，浩瀚无垠！",
            "人生就像一盒巧克力，你永远不知道下一块是什么味道。",
            "世界上只有一种真正的英雄主义，那就是认清生活的真相之后依然热爱生活。",
            "你相信有奇迹吗？",
            "我等这一天，等了很久。",
            "我会回来的。",
            "再见不是结束，而是另一段旅程的开始。",
            "不是枪杀死了人，而是人杀死了人。",
            "人生在世，要么忍，要么狠，要么滚。",
            "你永远不知道自己的极限，除非你去试一试。",
            "车子是家人。",
            "家庭，永远是第一位的。",
            "船要沉了，但爱还在。",
            "我画你，是为了记住这一刻。",
            "你知道风是从哪里来的吗？",
            "我一直在等一个人，等一个能和我一起吃早饭的人。",
            "有些鸟儿注定是关不住的，它们的每一片羽毛都闪耀着自由的光辉。",
            "真相只有一个。",
            "正义可能会迟到，但永远不会缺席。",
            "你所热爱的，就是你的生活。",
            "人类的本质是复读机。",
            "你看那个人，好像一条狗啊。",
            "我命由我不由天。",
            "一日为师，终身为父。",
            "出来混，迟早要还的。",
            "我曾经也想过一了百了。",
            "既然认准这条路，何必问它是山路还是水路。",
            "给我一杯忘情水。",
            "这个世界不止眼前的苟且，还有诗和远方。",
            "我从来没想过要拯救世界，只是想保护我在乎的人。",
            "能力越大，责任越大。",
            "先生，你掉的是这把金斧头还是银斧头？",
            "我想带你去看海。",
            "如果真相会伤人，那就让谎言永远沉睡。",
            "你不是一个人在战斗。",
            "有些事情不是看到希望才去坚持，而是坚持了才会看到希望。",
            "人类的伟大在于我们总在试图超越自己。",
            "我要把这个世界，变成我想要的样子。",
            "当你凝视深渊的时候，深渊也在凝视你。",
            "我不怕千万人阻挡，只怕自己投降。",
            "你要相信，这个世界总有人在偷偷爱着你。",
            "谁又能想到，我只是想点一份炸鸡。",
            "人生就是不断地告别。",
            "我在这里等风，也在等你。",
            "你是我这一生，最美的意外。",
            "我没有输，只是还没赢。",
            "做人呢，最重要的就是开心。",
            "我要这天，再遮不住我眼；要这地，再埋不了我心。",
            "你以为你以为的就是你以为的吗？",
            "我偏不！",
            "我想起那天夕阳下的奔跑，那是我逝去的青春。",
            "你不能因为害怕失去，就不去拥有。",
            "生活不会因为你是好人就对你手下留情。",
            "人都要为自己的选择付出代价。",
            "你看这烟花，多像我们的梦想。",
            "再不疯狂，我们就老了。",
            "终有一天，你会遇到那个和你并肩看完这部电影的人。",
            "暂时的低谷，不代表人生的失败。",
            "我不相信命运，我只相信我自己。",
            "这个世界，总有人在偷偷爱着你。",
            "只要心里还燃烧着火焰，就不算老。",
            "你就是你，不必取悦所有人。",
            "越长大，越知道：不是所有人都值得你掏心掏肺。",
            "别回头，前面才有光。",
            "你永远可以相信队友会犯错。",
            "我们终将成为我们讨厌的大人。",
            "但在成为大人之前，请先学会善良。",
            "一想到人生是单程车，我就想把油门踩到底。",
            "不是因为看见了希望才坚持，而是因为坚持了才看见希望。",
            "就算全世界都否定你，我也要站在你身后。",
            "别怕，我们一起。",
            "每一次告别，最好用力一点。",
            "你之所以觉得时间过得快，是因为你在变好。",
            "谢谢你出现在我的人生电影里。"
        )
        val userName = names.random()
        val content = contents.random()
        val timestampMillis = System.currentTimeMillis()
        val timestamp = formatBaseTime(timestampMillis)
        val avatarResId = AVATAR_RES_IDS.random()

        // 随机分配消息类型
        return when ((0..2).random()) {
            0 -> Message(userName, content, timestamp, true, avatarResId, MessageType.TEXT, timestampMillis = timestampMillis)
            1 -> Message(
                userName,
                content,
                timestamp,
                true,
                avatarResId,
                MessageType.IMAGE,
                imageResId = AVATAR_RES_IDS.random(), // 这里复用头像作为示例图片
                timestampMillis = timestampMillis
            )
            else -> Message(
                userName,
                content,
                timestamp,
                true,
                avatarResId,
                MessageType.CTA,
                buttonText = "领取奖励",
                timestampMillis = timestampMillis
            )
        }
    }

    private fun showNewMessageBanner() {
        uiHandler.removeCallbacks(hideBannerRunnable)
        newMessageBanner.visibility = View.VISIBLE
        val offset = (newMessageBanner.height.takeIf { it > 0 }
            ?: newMessageBanner.resources.displayMetrics.density * 48).toFloat()
        newMessageBanner.alpha = 0f
        newMessageBanner.translationY = -offset
        newMessageBanner.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()
        uiHandler.postDelayed(hideBannerRunnable, BANNER_VISIBLE_MS)
    }

    private fun hideNewMessageBanner() {
        val offset = (newMessageBanner.height.takeIf { it > 0 }
            ?: newMessageBanner.resources.displayMetrics.density * 48).toFloat()
        newMessageBanner.animate()
            .alpha(0f)
            .translationY(-offset)
            .setDuration(250)
            .withEndAction { newMessageBanner.visibility = View.GONE }
            .start()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val MESSAGE_INTERVAL_MS = 20_000L
        private const val BANNER_VISIBLE_MS = 5_000L
        private val AVATAR_RES_IDS = intArrayOf(
            R.drawable.avatar_0,
            R.drawable.avatar_1,
            R.drawable.avatar_2,
            R.drawable.avatar_3,
            R.drawable.avatar_4,
            R.drawable.avatar_5,
            R.drawable.avatar_6,
            R.drawable.avatar_7,
            R.drawable.avatar_8,
            R.drawable.avatar_9,
            R.drawable.avatar_10,
            R.drawable.avatar_11,
            R.drawable.avatar_12,
            R.drawable.avatar_13,
            R.drawable.avatar_14,
            R.drawable.avatar_15,
            R.drawable.avatar_16,
            R.drawable.avatar_17,
            R.drawable.avatar_18,
            R.drawable.avatar_19,
            R.drawable.avatar_20,
            R.drawable.avatar_21,
            R.drawable.avatar_22,
            R.drawable.avatar_23,
            R.drawable.avatar_24,
            R.drawable.avatar_25,
            R.drawable.avatar_26,
            R.drawable.avatar_27,
            R.drawable.avatar_28,
            R.drawable.avatar_29,
            R.drawable.avatar_30,
            R.drawable.avatar_31,
            R.drawable.avatar_32,
            R.drawable.avatar_33,
            R.drawable.avatar_34,
            R.drawable.avatar_35,
            R.drawable.avatar_36,
            R.drawable.avatar_37,
            R.drawable.avatar_38,
            R.drawable.avatar_39,
            R.drawable.avatar_40,
            R.drawable.avatar_41,
        )
    }
}