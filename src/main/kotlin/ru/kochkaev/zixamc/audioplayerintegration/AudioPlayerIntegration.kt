package ru.kochkaev.zixamc.audioplayerintegration

import de.maxhenkel.audioplayer.AudioManager
import kotlinx.coroutines.runBlocking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import ru.kochkaev.zixamc.api.sql.SQLCallback
import ru.kochkaev.zixamc.api.sql.SQLProcess
import ru.kochkaev.zixamc.api.sql.SQLUser
import ru.kochkaev.zixamc.api.sql.callback.CallbackCanExecute
import ru.kochkaev.zixamc.api.sql.callback.CancelCallbackData
import ru.kochkaev.zixamc.api.sql.callback.TgCBHandlerResult
import ru.kochkaev.zixamc.api.sql.callback.TgMenu
import ru.kochkaev.zixamc.api.sql.process.ProcessData
import ru.kochkaev.zixamc.api.telegram.Menu
import ru.kochkaev.zixamc.api.telegram.ServerBot
import ru.kochkaev.zixamc.api.telegram.model.TgCallbackQuery
import ru.kochkaev.zixamc.api.telegram.model.TgChatMemberStatuses
import ru.kochkaev.zixamc.api.telegram.model.TgMessage
import ru.kochkaev.zixamc.api.telegram.model.TgReplyMarkup
import ru.kochkaev.zixamc.api.telegram.model.TgReplyParameters
import java.nio.file.Path
import java.util.UUID

object AudioPlayerIntegration {

    val ruEnMap: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    fun resolveId(
        path: Path,
        server: MinecraftServer = FabricLoader.getInstance().gameInstance as MinecraftServer
    ): UUID {
        val uuid = UUID.randomUUID()
        AudioManager.saveSound(server, uuid, path)
        return uuid
    }

    fun resolveName(current: String): String {
        val dotIndex = current.lastIndexOf('.')
        val base = current.substring(0, dotIndex)
        val extension = current.substring(dotIndex).lowercase()
        val ruToEnBase = StringBuilder()
        base.toCharArray().forEach {
            if (ruEnMap.contains(it)) {
                val en = ruEnMap[it.lowercaseChar()] ?: ""
                ruToEnBase.append(if (it.isUpperCase()) en.replaceFirstChar { it1 -> it1.uppercaseChar() } else en)
            } else ruToEnBase.append(it)
        }
        val sanitizedBase = ruToEnBase.replace(Regex("[^a-z0-9_ \\-]", RegexOption.IGNORE_CASE), "")
        return sanitizedBase + extension
    }

    suspend fun callbackProcessor(cbq: TgCallbackQuery, sql: SQLCallback<Menu.MenuCallbackData<Menu.MenuCallbackData.DummyAdditional>>): TgCBHandlerResult {
        val user = cbq.from.id.let { SQLUser.get(it) } ?: return TgCBHandlerResult.SUCCESS
        ServerBot.bot.editMessageText(
            chatId = cbq.message.chat.id,
            messageId = cbq.message.messageId,
            text = Config.config.messageUpload,
        )
        val message = ServerBot.bot.editMessageReplyMarkup(
            chatId = cbq.message.chat.id,
            messageId = cbq.message.messageId,
            replyMarkup = TgMenu(
                listOf(
                    listOf(
                        CancelCallbackData(
                            cancelProcesses = listOf(AudioPlayerUploadProcess),
                            asCallbackSend = CancelCallbackData.CallbackSend(
                                type = "menu",
                                data = Menu.MenuCallbackData.of("back"),
                                result = TgCBHandlerResult.DELETE_LINKED,
                            ),
                            canExecute = CallbackCanExecute(
                                statuses = listOf(TgChatMemberStatuses.CREATOR, TgChatMemberStatuses.ADMINISTRATOR),
                                users = listOf(user.id),
                                display = user.nickname ?: "",
                            )
                        ).build(),
                    )
                )
            ),
        )
        SQLProcess.get(cbq.message.chat.id, AudioPlayerUploadProcess)?.also {
            it.data?.run {
                try { ServerBot.bot.editMessageReplyMarkup(
                    chatId = cbq.message.chat.id,
                    messageId = this.messageId,
                    replyMarkup = TgReplyMarkup()
                ) } catch (_: Exception) {}
                SQLCallback.dropAll(cbq.message.chat.id, this.messageId)
            }
        } ?.drop()
        SQLProcess.of(
            type = AudioPlayerUploadProcess,
            data = ProcessData(message.messageId)
        ).pull(cbq.message.chat.id)
        return TgCBHandlerResult.DELETE_LINKED
    }

    suspend fun messageProcessor(msg: TgMessage, process: SQLProcess<ProcessData>, data: ProcessData) = runBlocking {
//        if (msg.replyToMessage==null || msg.replyToMessage.messageId != data.messageId) return@runBlocking
        var done = false
        val user = msg.from?.id?.let { SQLUser.get(it) } ?: return@runBlocking
        SQLCallback.dropAll(msg.chat.id, data.messageId)
        ServerBot.bot.editMessageReplyMarkup(
            chatId = msg.chat.id,
            messageId = data.messageId,
            replyMarkup = TgReplyMarkup()
        )
        SQLCallback.dropAll(msg.chat.id, data.messageId)
        val message = ServerBot.bot.sendMessage(
            chatId = msg.chat.id,
            text = Config.config.messagePreparing,
            replyParameters = TgReplyParameters(msg.messageId)
        )
        process.data = ProcessData(message.messageId)
        var filename: String? = null
        if (msg.audio != null || msg.document != null) {
            val tgFilename =
                if (msg.audio != null)
                    msg.audio?.file_name ?: "${msg.audio?.performer}_-_${msg.audio?.title}.mp3"
                else msg.document!!.file_name ?: ""
            val extension = tgFilename.substring(tgFilename.lastIndexOf('.') + 1).lowercase()
            if (extension == "mp3" || extension == "wav")
                filename =
                    saveAudioPlayerFile(
                        msg.audio?.file_id ?: msg.document!!.file_id, tgFilename
                    )
            else {
                ServerBot.bot.editMessageText(
                    chatId = message.chat.id,
                    messageId = message.messageId,
                    text = Config.config.messageIncorrectExtension,
                )
                done = true
            }
        } else {
            ServerBot.bot.editMessageText(
                chatId = message.chat.id,
                messageId = message.messageId,
                text = Config.config.messageIncorrectExtension,
            )
            done = true
        }
        if (filename.isNullOrEmpty() && !done) {
            ServerBot.bot.editMessageText(
                chatId = message.chat.id,
                messageId = message.messageId,
                text = Config.config.messageErrorUpload,
            )
            done = true
        }
        if (!done) {
            ServerBot.bot.editMessageText(
                chatId = message.chat.id,
                messageId = message.messageId,
                text = Config.config.messageDone.replace(
                    "{filename}",
                    filename!!
                ),
            )
            ServerBot.bot.editMessageReplyMarkup(
                chatId = message.chat.id,
                messageId = message.messageId,
                replyMarkup = TgMenu(listOf(listOf(Menu.getBackButtonExecuteOnly(user, true))))
            )
            process.drop()
        } else {
            ServerBot.bot.editMessageReplyMarkup(
                chatId = message.chat.id,
                messageId = message.messageId,
                replyMarkup = TgMenu(
                    listOf(
                        listOf(
                            CancelCallbackData(
                                cancelProcesses = listOf(AudioPlayerUploadProcess),
                                asCallbackSend = CancelCallbackData.CallbackSend(
                                    type = "menu",
                                    data = Menu.MenuCallbackData.of("back"),
                                    result = TgCBHandlerResult.DELETE_LINKED,
                                ),
                                canExecute = CallbackCanExecute(
                                    statuses = listOf(TgChatMemberStatuses.CREATOR, TgChatMemberStatuses.ADMINISTRATOR),
                                    users = listOf(user.id),
                                    display = user.nickname ?: "",
                                )
                            ).build(),
                        )
                    )
                ),
            )
        }
//        ServerBot.bot.editMessageReplyMarkup(
//            chatId = message.chat.id,
//            messageId = message.messageId,
//            replyMarkup = if () TgMenu(listOf(listOf(CancelCallbackData(
//                cancelProcesses = listOf(ProcessTypes.MENU_AUDIO_PLAYER_UPLOAD),
//                asCallbackSend = CancelCallbackData.CallbackSend(
//                    type = "menu",
//                    data = Menu.MenuCallbackData.of("back"),
//                    result = TgCBHandlerResult.DELETE_MARKUP,
//                ),
//                canExecute = CallbackCanExecute(
//                    statuses = null,
//                    user = LinkedUser(user.id),
//                    display = user.nickname ?: "",
//                )
//            ).build()))),
//        )
    }

    private suspend fun saveAudioPlayerFile(fileId: String, filename: String): String {
        val path = FabricLoader.getInstance().gameDir.toAbsolutePath()
        val path1 = path.resolve("$path/audioplayer_uploads/")
        path1.toFile().mkdirs()
        val resolvedFilename =
            resolveName(filename)
        val downloaded = ServerBot.bot.saveFile(fileId, "$path1/$resolvedFilename")
        val uuid = resolveId(
            path1.resolve(downloaded)
        )
        return if (downloaded.isNotEmpty()) uuid.toString() else ""
    }
}