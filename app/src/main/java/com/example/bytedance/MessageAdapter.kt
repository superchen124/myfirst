package com.example.bytedance

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(
    messages: List<Message>,
    private var remarks: Map<String, String> = emptyMap(),
    private val onMessageClick: (Message) -> Unit,
    private val onAvatarClick: (Message, android.view.View) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private var messages: List<Message> = messages
    private var highlightKeyword: String = ""
    private val highlightCache = mutableMapOf<String, SpannableString>()

    fun updateMessages(newMessages: List<Message>) {
        val diffCallback = MessageDiffCallback(this.messages, newMessages)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.messages = newMessages
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateRemarks(newRemarks: Map<String, String>) {
        val changed = this.remarks != newRemarks
        this.remarks = newRemarks
        if (changed) {
            // 备注变化时，只刷新可能受影响的项
            notifyDataSetChanged()
        }
    }

    fun updateHighlight(keyword: String) {
        val changed = this.highlightKeyword != keyword
        this.highlightKeyword = keyword
        if (changed) {
            highlightCache.clear() // 清除缓存，重新计算高亮
            notifyDataSetChanged()
        }
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        val tvUnread: TextView = itemView.findViewById(R.id.tvUnread)
        val ivMessageImage: ImageView = itemView.findViewById(R.id.ivMessageImage)
        val btnCta: Button = itemView.findViewById(R.id.btnCta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.ivAvatar.setImageResource(message.avatarResId)
        val remark = remarks[message.userName]
        val displayName = remark?.takeIf { it.isNotEmpty() } ?: message.userName
        holder.tvUserName.text = applyHighlight(displayName)
        holder.tvContent.text = applyHighlight(message.content)
        holder.tvTimestamp.text = formatTimestamp(message)
        holder.tvUnread.visibility = if (message.isUnread) View.VISIBLE else View.GONE
        holder.tvContent.visibility = if (message.content.isNotBlank()) View.VISIBLE else View.GONE

        // 根据消息类型调整内容展示
        when (message.type) {
            MessageType.TEXT -> {
                holder.tvContent.visibility = if (message.content.isNotBlank()) View.VISIBLE else View.GONE
                holder.ivMessageImage.visibility = View.GONE
                holder.btnCta.visibility = View.GONE
            }
            MessageType.IMAGE -> {
                holder.tvContent.visibility = if (message.content.isNotBlank()) View.VISIBLE else View.GONE
                holder.ivMessageImage.visibility = View.VISIBLE
                holder.btnCta.visibility = View.GONE
                message.imageResId?.let { holder.ivMessageImage.setImageResource(it) }
            }
            MessageType.CTA -> {
                holder.tvContent.visibility = if (message.content.isNotBlank()) View.VISIBLE else View.GONE
                holder.ivMessageImage.visibility = View.GONE
                holder.btnCta.visibility = View.VISIBLE
                holder.btnCta.text = message.buttonText ?: "查看详情"
                holder.btnCta.setOnClickListener {
                    Toast.makeText(holder.itemView.context, "你领取了奖励", Toast.LENGTH_SHORT).show()
                }
            }
        }

        holder.itemView.setOnClickListener {
            onMessageClick(message)
        }
        holder.ivAvatar.setOnClickListener {
            onAvatarClick(message, holder.ivAvatar)
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun formatTimestamp(message: Message): String {
        val ts = message.timestampMillis ?: parseTimestamp(message.timestamp) ?: return message.timestamp
        val now = System.currentTimeMillis()
        val diff = now - ts
        if (diff < 0) return message.timestamp

        val oneMinute = TimeUnit.MINUTES.toMillis(1)
        val oneHour = TimeUnit.HOURS.toMillis(1)
        val oneDay = TimeUnit.DAYS.toMillis(1)

        val calNow = Calendar.getInstance()
        val calTs = Calendar.getInstance().apply { timeInMillis = ts }

        return when {
            diff < oneMinute -> "刚刚"
            diff < oneHour -> "${diff / oneMinute} 分钟前"
            isSameDay(calNow, calTs) -> formatTime(ts, "HH:mm")
            isYesterday(calNow, calTs) -> "昨天 ${formatTime(ts, "HH:mm")}"
            diff < 7 * oneDay -> "${diff / oneDay} 天前"
            else -> formatTime(ts, "MM-dd")
        }
    }

    private fun parseTimestamp(raw: String): Long? {
        if (raw.all { it.isDigit() }) return raw.toLongOrNull()
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "HH:mm:ss",
            "HH:mm"
        )
        for (p in patterns) {
            val sdf = SimpleDateFormat(p, Locale.getDefault())
            sdf.isLenient = false
            try {
                val date = sdf.parse(raw) ?: continue
                val cal = Calendar.getInstance()
                if (p.startsWith("HH")) {
                    // 无日期信息，视为今天
                    val today = Calendar.getInstance()
                    cal.time = date
                    cal.set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
                    return cal.timeInMillis
                }
                return date.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(yesterday, target)
    }

    private fun formatTime(millis: Long, pattern: String): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun applyHighlight(text: String): SpannableString {
        if (highlightKeyword.isBlank()) return SpannableString(text)
        
        // 使用缓存避免重复计算
        val cacheKey = "${text}_$highlightKeyword"
        highlightCache[cacheKey]?.let { return it }
        
        val lowerText = text.lowercase(Locale.getDefault())
        val lowerKey = highlightKeyword.lowercase(Locale.getDefault())
        val spannable = SpannableString(text)
        var start = lowerText.indexOf(lowerKey)
        while (start >= 0) {
            val end = start + lowerKey.length
            spannable.setSpan(
                ForegroundColorSpan(Color.parseColor("#FF5722")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            start = lowerText.indexOf(lowerKey, end)
        }
        
        // 缓存结果，限制缓存大小避免内存泄漏
        if (highlightCache.size > 100) {
            highlightCache.clear()
        }
        highlightCache[cacheKey] = spannable
        return spannable
    }

    // DiffUtil Callback 用于优化列表更新
    class MessageDiffCallback(
        private val oldList: List<Message>,
        private val newList: List<Message>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            val oldMsg = oldList[oldPos]
            val newMsg = newList[newPos]
            // 使用用户名+内容+时间戳作为唯一标识
            return oldMsg.userName == newMsg.userName &&
                   oldMsg.content == newMsg.content &&
                   oldMsg.timestamp == newMsg.timestamp
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            return oldList[oldPos] == newList[newPos]
        }
    }
}