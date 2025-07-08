package ru.kochkaev.zixamc.audioplayerintegration

import net.fabricmc.api.ModInitializer
import ru.kochkaev.zixamc.api.config.ConfigManager
import ru.kochkaev.zixamc.api.sql.process.ProcessTypes
import ru.kochkaev.zixamc.api.telegram.Menu

class ZixaMCAudioPlayerIntegration: ModInitializer {

    override fun onInitialize() {
        ConfigManager.registerConfig(Config)
        ProcessTypes.registerType(AudioPlayerUploadProcess)
        Menu.addIntegration(Menu.Integration.of(
            callbackName = "audioPlayer",
            menuDisplay = Config.config.buttonMenu,
            processor = AudioPlayerIntegration::callbackProcessor,
            filter = { chatId, userId -> chatId == userId },
        ))
    }

}