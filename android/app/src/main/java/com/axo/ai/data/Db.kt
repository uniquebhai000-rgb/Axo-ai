package com.axo.ai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Chat(
    @PrimaryKey val id: String, val title: String, val assistantId: String,
    val modelId: String, val archived: Boolean = false, val updatedAt: Long,
)

@Entity(
    foreignKeys = [ForeignKey(Chat::class, ["id"], ["chatId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("chatId")],
)
data class Msg(
    @PrimaryKey val id: String, val chatId: String, val role: String,
    val content: String, val rating: Int = 0, val createdAt: Long,
)

@Entity data class Assistant(
    @PrimaryKey val id: String, val name: String, val icon: String,
    val instructions: String, val builtIn: Boolean = false,
)

@Dao interface AxoDao {
    @Query("SELECT * FROM Msg WHERE chatId=:c ORDER BY createdAt") fun messages(c: String): Flow<List<Msg>>
    @Query("SELECT * FROM Msg WHERE chatId=:c ORDER BY createdAt") suspend fun messagesOnce(c: String): List<Msg>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMsg(m: Msg)
    @Query("DELETE FROM Msg WHERE id=:id") suspend fun deleteMsg(id: String)
    @Query("DELETE FROM Msg WHERE chatId=:c AND createdAt>:t") suspend fun deleteAfter(c: String, t: Long)
    @Query("UPDATE Msg SET content=:t WHERE id=:id") suspend fun updateMsg(id: String, t: String)
    @Query("UPDATE Msg SET rating=:r WHERE id=:id") suspend fun rate(id: String, r: Int)

    @Query("SELECT * FROM Chat WHERE id=:id") suspend fun chat(id: String): Chat?
    @Upsert suspend fun upsertChat(c: Chat)
    @Query("UPDATE Chat SET updatedAt=:t WHERE id=:id") suspend fun touch(id: String, t: Long)
    @Query("SELECT * FROM Chat WHERE archived=:arch AND title LIKE '%' || :q || '%' ORDER BY updatedAt DESC")
    fun chats(arch: Boolean, q: String): Flow<List<Chat>>
    @Query("SELECT * FROM Chat ORDER BY updatedAt DESC") suspend fun allChats(): List<Chat>
    @Query("UPDATE Chat SET title=:t WHERE id=:id") suspend fun rename(id: String, t: String)
    @Query("UPDATE Chat SET archived=:a WHERE id=:id") suspend fun archive(id: String, a: Boolean)
    @Query("DELETE FROM Chat WHERE id=:id") suspend fun deleteChat(id: String)
    @Query("DELETE FROM Chat") suspend fun deleteAllChats()

    @Query("SELECT * FROM Assistant ORDER BY builtIn DESC, name") fun assistants(): Flow<List<Assistant>>
    @Query("SELECT * FROM Assistant WHERE id=:id") suspend fun assistant(id: String): Assistant?
    @Query("SELECT COUNT(*) FROM Assistant") suspend fun assistantCount(): Int
    @Upsert suspend fun upsertAssistant(a: Assistant)
    @Upsert suspend fun upsertAssistants(a: List<Assistant>)
    @Query("DELETE FROM Assistant WHERE id=:id AND builtIn=0") suspend fun deleteAssistant(id: String)
    @Query("DELETE FROM Assistant WHERE builtIn=0") suspend fun deleteCustomAssistants()
}

@Database(entities = [Chat::class, Msg::class, Assistant::class], version = 1, exportSchema = false)
abstract class AxoDb : RoomDatabase() { abstract fun dao(): AxoDao }

object Seed {
    val assistants = listOf(
        Assistant("general", "General AI Assistant", "✨", "You are AXO AI, a helpful, accurate and concise assistant.", true),
        Assistant("coding", "Coding Assistant", "💻", "You are an expert software engineer. Give correct, idiomatic code in fenced code blocks and explain briefly.", true),
        Assistant("homework", "Homework Assistant", "📚", "You are a patient tutor. Explain step by step so the student learns, rather than only giving final answers.", true),
        Assistant("creative", "Creative Writer", "🎨", "You are a creative writing partner with vivid, original style. Ask about tone when it matters.", true),
        Assistant("gaming", "Gaming Assistant", "🎮", "You are a knowledgeable gaming guide: strategies, builds, lore and recommendations. Avoid spoilers unless asked.", true),
        Assistant("translator", "Translator", "🌍", "You are a professional translator. Detect the source language and translate faithfully; note nuances briefly.", true),
    )
}
