package ru.kochkaev.zixamc.audioplayerintegration

import net.fabricmc.loader.api.FabricLoader
import ru.kochkaev.zixamc.api.config.ConfigFile
import java.io.File

data class Config(
    val modIsNodInstalled: String = "Похоже, AudioPlayer не установлен на сервере...",
    val buttonMenu: String = "Загрузить аудио в AudioPlayer 🎧",
    val messageUpload: String = "Отправьте аудио в этот чат.\nРазмер аудио не должен превышать 20МБ.",
    val messageErrorUpload: String = "Ошибка! Размер аудио не должен превышать 20МБ.",
    val messageIncorrectExtension: String = "Ошибка! Аудио должно иметь расширение \".mp3\" или \".wav\". Вы можете воспользоваться онлайн-конвертером для изменения формата.",
    val messageDone: String = "<b>Аудио успешно загружено на сервер!</b>\nТеперь вы можете использовать его в AudioPlayer.\n\n<b>UUID аудио »</b>\n<code>{filename}</code>\n\n<i>Что бы записать аудио на предмет, возьмите его в руку и выполните команду</i> ->\n<code>/audioplayer apply {filename}</code>",
    val messagePreparing: String = "<b>Пожалуйста, подождите...</b>"
) {
    companion object: ConfigFile<Config>(
        file = File(FabricLoader.getInstance().configDir.toFile(), "ZixaMC-AudioPlayerIntegration.json"),
        model = Config::class.java,
        supplier = ::Config
    )
}
