package com.example.bytedance

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONObject

private const val DATABASE_NAME = "messages.db"
private const val DATABASE_VERSION = 2

private const val TABLE_MESSAGES = "messages"
private const val COLUMN_ID = "id"
private const val COLUMN_JSON = "json"

private const val TABLE_REMARKS = "remarks"
private const val COLUMN_USER_NAME = "userName"
private const val COLUMN_REMARK = "remark"

class MessageDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val createMessagesTableSql = """
            CREATE TABLE $TABLE_MESSAGES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_JSON TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createMessagesTableSql)

        val createRemarksTableSql = """
            CREATE TABLE $TABLE_REMARKS (
                $COLUMN_USER_NAME TEXT PRIMARY KEY,
                $COLUMN_REMARK TEXT
            )
        """.trimIndent()
        db.execSQL(createRemarksTableSql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            val createRemarksTableSql = """
                CREATE TABLE IF NOT EXISTS $TABLE_REMARKS (
                    $COLUMN_USER_NAME TEXT PRIMARY KEY,
                    $COLUMN_REMARK TEXT
                )
            """.trimIndent()
            db.execSQL(createRemarksTableSql)
        }
    }

    fun getAllMessages(): List<Message> {
        val messages = mutableListOf<Message>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            arrayOf(COLUMN_ID, COLUMN_JSON),
            null,
            null,
            null,
            null,
            "$COLUMN_ID DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val jsonString = it.getString(it.getColumnIndexOrThrow(COLUMN_JSON))
                val jsonObject = JSONObject(jsonString)
                messages.add(jsonObject.toMessage())
            }
        }
        return messages
    }

    fun insertMessages(messages: List<Message>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            messages.forEach { message ->
                val jsonObject = message.toJson()
                val values = ContentValues().apply {
                    put(COLUMN_JSON, jsonObject.toString())
                }
                val rowId = db.insert(TABLE_MESSAGES, null, values)
                Log.d("MessageDB", "💾 插入消息到数据库，rowId=$rowId, 用户=${message.userName}")
            }
            db.setTransactionSuccessful()
            Log.d("MessageDB", "✅ 成功保存 ${messages.size} 条消息到数据库")
        } catch (e: Exception) {
            Log.e("MessageDB", "❌ 保存消息失败", e)
        } finally {
            db.endTransaction()
        }
    }

    fun markMessageAsRead(message: Message) {
        val db = writableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            arrayOf(COLUMN_ID, COLUMN_JSON),
            null,
            null,
            null,
            null,
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                val jsonString = it.getString(it.getColumnIndexOrThrow(COLUMN_JSON))
                val jsonObject = JSONObject(jsonString)
                val stored = jsonObject.toMessage()
                val sameTimestamp = when {
                    stored.timestampMillis != null && message.timestampMillis != null ->
                        stored.timestampMillis == message.timestampMillis
                    else -> stored.timestamp == message.timestamp
                }
                if (stored.userName == message.userName &&
                    stored.content == message.content &&
                    sameTimestamp
                ) {
                    val updated = stored.copy(isUnread = false)
                    val updatedJson = updated.toJson().toString()
                    val values = ContentValues().apply {
                        put(COLUMN_JSON, updatedJson)
                    }
                    db.update(
                        TABLE_MESSAGES,
                        values,
                        "$COLUMN_ID = ?",
                        arrayOf(id.toString())
                    )
                    break
                }
            }
        }
    }

    fun saveRemark(userName: String, remark: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_USER_NAME, userName)
            put(COLUMN_REMARK, remark)
        }
        db.insertWithOnConflict(
            TABLE_REMARKS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getRemark(userName: String): String? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_REMARKS,
            arrayOf(COLUMN_REMARK),
            "$COLUMN_USER_NAME = ?",
            arrayOf(userName),
            null,
            null,
            null
        )
        cursor.use {
            return if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(COLUMN_REMARK))
            } else {
                null
            }
        }
    }

    fun getAllRemarks(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_REMARKS,
            arrayOf(COLUMN_USER_NAME, COLUMN_REMARK),
            null,
            null,
            null,
            null,
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                val userName = it.getString(it.getColumnIndexOrThrow(COLUMN_USER_NAME))
                val remark = it.getString(it.getColumnIndexOrThrow(COLUMN_REMARK))
                result[userName] = remark
            }
        }
        return result
    }
}

private fun Message.toJson(): JSONObject {
    return JSONObject().apply {
        put("userName", userName)
        put("content", content)
        put("timestamp", timestamp)
        put("isUnread", isUnread)
        put("avatarResId", avatarResId)
        put("type", type.name)
        imageResId?.let { put("imageResId", it) }
        buttonText?.let { put("buttonText", it) }
        timestampMillis?.let { put("timestampMillis", it) }
    }
}

private fun JSONObject.toMessage(): Message {
    val typeValue = optString("type", MessageType.TEXT.name)
    val parsedType = runCatching { MessageType.valueOf(typeValue) }.getOrDefault(MessageType.TEXT)
    val millis = if (has("timestampMillis")) optLong("timestampMillis") else null
    return Message(
        userName = getString("userName"),
        content = getString("content"),
        timestamp = getString("timestamp"),
        isUnread = optBoolean("isUnread", false),
        avatarResId = optInt("avatarResId", 0),
        type = parsedType,
        imageResId = if (has("imageResId")) optInt("imageResId") else null,
        buttonText = optString("buttonText", null),
        timestampMillis = millis
    )
}

