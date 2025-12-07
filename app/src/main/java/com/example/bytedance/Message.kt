package com.example.bytedance

data class Message(
    val userName: String,
    val content: String,
    val timestamp: String,
    val isUnread: Boolean,
    val avatarResId: Int,
    val type: MessageType = MessageType.TEXT,
    val imageResId: Int? = null,
    val buttonText: String? = null,
    val timestampMillis: Long? = null
)

enum class MessageType {
    TEXT,        // 系统或普通文本
    IMAGE,       // 携带图片
    CTA          // 携带按钮的运营类消息
}