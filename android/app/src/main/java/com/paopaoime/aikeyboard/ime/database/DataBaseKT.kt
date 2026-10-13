package com.paopaoime.aikeyboard.ime.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.paopaoime.aikeyboard.ime.application.Launcher
import com.paopaoime.aikeyboard.ime.database.dao.ClipboardDao
import com.paopaoime.aikeyboard.ime.database.dao.KbDirectoryDao
import com.paopaoime.aikeyboard.ime.database.dao.KbDocumentDao
import com.paopaoime.aikeyboard.ime.database.dao.PhraseDao
import com.paopaoime.aikeyboard.ime.database.dao.SideSymbolDao
import com.paopaoime.aikeyboard.ime.database.dao.SkbFunDao
import com.paopaoime.aikeyboard.ime.database.dao.UsedSymbolDao
import com.paopaoime.aikeyboard.ime.database.entry.Clipboard
import com.paopaoime.aikeyboard.ime.database.entry.KbDirectory
import com.paopaoime.aikeyboard.ime.database.entry.KbDocument
import com.paopaoime.aikeyboard.ime.database.entry.Phrase
import com.paopaoime.aikeyboard.ime.database.entry.SideSymbol
import com.paopaoime.aikeyboard.ime.database.entry.SkbFun
import com.paopaoime.aikeyboard.ime.database.entry.UsedSymbol
import com.paopaoime.aikeyboard.ime.prefs.behavior.SkbMenuMode
import com.paopaoime.aikeyboard.ime.utils.thread.ThreadPoolUtils

//@Database(entities = [SideSymbol::class, Clipboard::class, UsedSymbol::class], version = 1, exportSchema = false)
@Database(entities = [SideSymbol::class, Clipboard::class, UsedSymbol::class, Phrase::class, SkbFun::class, KbDirectory::class, KbDocument::class], version = 8, exportSchema = false)
abstract class DataBaseKT : RoomDatabase() {
    abstract fun sideSymbolDao(): SideSymbolDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun usedSymbolDao(): UsedSymbolDao
    abstract fun phraseDao(): PhraseDao
    abstract fun skbFunDao(): SkbFunDao
    abstract fun kbDirectoryDao(): KbDirectoryDao
    abstract fun kbDocumentDao(): KbDocumentDao
    companion object {

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS phrase (content TEXT PRIMARY KEY NOT NULL, isKeep INTEGER NOT NULL, t9 TEXT NOT NULL, qwerty TEXT NOT NULL, lx17 TEXT NOT NULL, time INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS skbfun (name TEXT KEY NOT NULL, isKeep INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY (name, isKeep))")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT INTO skbfun (name, isKeep, position) VALUES ('TextEdit', 0, 15)")
                db.execSQL("INSERT INTO skbfun (name, isKeep, position) VALUES ('TextEdit', 1, 0)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE phrase ADD COLUMN imageUris TEXT")
                db.execSQL("ALTER TABLE phrase ADD COLUMN fileUris TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS kb_directory (id TEXT NOT NULL, name TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, deletable INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE TABLE IF NOT EXISTS kb_document (id TEXT NOT NULL, directoryId TEXT NOT NULL, fileName TEXT NOT NULL, mimeType TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_kb_document_directoryId ON kb_document(directoryId)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kb_document ADD COLUMN parseStatus INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE kb_document ADD COLUMN parseProgress INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE kb_document ADD COLUMN parsedTextPath TEXT")
                db.execSQL("ALTER TABLE kb_document ADD COLUMN parseError TEXT")
                db.execSQL("ALTER TABLE kb_document ADD COLUMN parseUpdatedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun hasColumn(name: String): Boolean {
                    val cursor = db.query("PRAGMA table_info(kb_document)")
                    cursor.use {
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIndex >= 0 && cursor.getString(nameIndex) == name) return true
                        }
                    }
                    return false
                }
                if (!hasColumn("vectorStatus")) {
                    db.execSQL("ALTER TABLE kb_document ADD COLUMN vectorStatus INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn("vectorSize")) {
                    db.execSQL("ALTER TABLE kb_document ADD COLUMN vectorSize INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn("vectorError")) {
                    db.execSQL("ALTER TABLE kb_document ADD COLUMN vectorError TEXT")
                }
                if (!hasColumn("vectorUpdatedAtMillis")) {
                    db.execSQL("ALTER TABLE kb_document ADD COLUMN vectorUpdatedAtMillis INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        val instance = Room.databaseBuilder(Launcher.instance.context, DataBaseKT::class.java, "ime_db")
            .allowMainThreadQueries()
            .addMigrations(MIGRATION_1_2)
            .addMigrations(MIGRATION_2_3)
            .addMigrations(MIGRATION_3_4)
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addCallback(object :Callback(){
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    ThreadPoolUtils.executeSingleton {
                        initDb()
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    ThreadPoolUtils.executeSingleton {
                        initPhrasesDb()
                        initKnowledgeBaseDb()
                    }
                }
            })
            .build()
        private fun initDb() {  //初始化数据库数据
            val symbolPinyin = listOf("，", "。", "？", "！", "……", "；", "：", ".").map {  symbolKey->
                SideSymbol(symbolKey, symbolKey)
            }
            instance.sideSymbolDao().insertAll(symbolPinyin)
            val symbolNumber = listOf("%", "/", "-", "+", "*", "#", "@").map {  symbolKey->
                SideSymbol(symbolKey, symbolKey, "number")
            }
            instance.sideSymbolDao().insertAll(symbolNumber)
        }

        private fun initPhrasesDb() {  //鍒濆鍖栧父鐢ㄨ鏁版嵁鏁版嵁
            if(instance.skbFunDao().getAllMenu().isEmpty()) {
                val skbFuns = listOf(
                    SkbFun(name = SkbMenuMode.ClipBoard.name, isKeep = 1),
                    SkbFun(name = SkbMenuMode.Emojicon.name, isKeep = 1),
                    SkbFun(name = SkbMenuMode.TextEdit.name, isKeep = 1),
                    SkbFun(name = SkbMenuMode.Emojicon.name, isKeep = 0, position = 0),
                    SkbFun(name = SkbMenuMode.SwitchKeyboard.name, isKeep = 0, position = 1),
                    SkbFun(name = SkbMenuMode.KeyboardHeight.name, isKeep = 0, position = 2),
                    SkbFun(name = SkbMenuMode.ClipBoard.name, isKeep = 0, position = 3),
                    SkbFun(name = SkbMenuMode.Phrases.name, isKeep = 0, position = 4),
                    SkbFun(name = SkbMenuMode.DarkTheme.name, isKeep = 0, position = 5),
                    SkbFun(name = SkbMenuMode.Feedback.name, isKeep = 0, position = 6),
                    SkbFun(name = SkbMenuMode.OneHanded.name, isKeep = 0, position = 7),
                    SkbFun(name = SkbMenuMode.NumberRow.name, isKeep = 0, position = 8),
                    SkbFun(name = SkbMenuMode.JianFan.name, isKeep = 0, position = 9),
                    SkbFun(name = SkbMenuMode.Mnemonic.name, isKeep = 0, position = 10),
                    SkbFun(name = SkbMenuMode.FloatKeyboard.name, isKeep = 0, position = 11),
                    SkbFun(name = SkbMenuMode.FlowerTypeface.name, isKeep = 0, position = 12),
                    SkbFun(name = SkbMenuMode.Custom.name, isKeep = 0, position = 13),
                    SkbFun(name = SkbMenuMode.Settings.name, isKeep = 0, position = 14),
                    SkbFun(name = SkbMenuMode.TextEdit.name, isKeep = 0, position = 15),
                )
                instance.skbFunDao().insertAll(skbFuns)
            }
        }

        private fun initKnowledgeBaseDb() {
            val id = "personal_default"
            if (instance.kbDirectoryDao().getById(id) == null) {
                val now = System.currentTimeMillis()
                instance.kbDirectoryDao().insert(
                    KbDirectory(
                        id = id,
                        name = "个人知识库",
                        createdAtMillis = now,
                        updatedAtMillis = now,
                        deletable = 0,
                    )
                )
            }
        }
    }
}

