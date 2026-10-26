package com.example.pettranslator

import android.app.Application
import java.io.File

class PetTranslatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Runs once per process, before any new capture can exist. Clean only our own orphan PCM files.
        File(cacheDir, "pet_recordings").listFiles()?.filter {
            it.isFile && it.name.startsWith("capture_") && it.extension == "pcm"
        }?.forEach { it.delete() }
    }
}
