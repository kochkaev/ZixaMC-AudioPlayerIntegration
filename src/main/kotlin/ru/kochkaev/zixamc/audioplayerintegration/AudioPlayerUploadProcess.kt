package ru.kochkaev.zixamc.audioplayerintegration

import ru.kochkaev.zixamc.api.sql.process.ProcessData
import ru.kochkaev.zixamc.api.sql.process.ProcessType
import ru.kochkaev.zixamc.api.sql.process.ProcessorType

object AudioPlayerUploadProcess: ProcessType<ProcessData>(
    model = ProcessData::class.java,
    serializedName = "MENU_AUDIO_PLAYER_UPLOAD",
    processorType = ProcessorType.ANY_MESSAGE,
    processor = AudioPlayerIntegration::messageProcessor,
    cancelOnMenuSend = true,
)