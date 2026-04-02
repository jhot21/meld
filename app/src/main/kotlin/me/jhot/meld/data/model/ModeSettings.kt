package me.jhot.meld.data.model

import androidx.room.TypeConverter
import com.google.gson.Gson

enum class ModeType { PRIMARY, SECONDARY }
enum class RingerMode { SILENT, VIBRATE, SOUND }
enum class DndMode { OFF, PRIORITY_ONLY, ALARMS_ONLY, TOTAL_SILENCE }
enum class ImmersiveMode { OFF, STATUS_BAR, NAV_BAR, BOTH }
enum class LocationMode { OFF, HIGH_ACCURACY, BATTERY_SAVER, DEVICE_ONLY }

data class ModeSettings(
    val volumeNotification: Int? = null,
    val volumeMedia: Int? = null,
    val volumeMediaOverride: Boolean? = null,
    val ringerMode: RingerMode? = null,
    val dnd: DndMode? = null,
    val brightness: Int? = null,
    val brightnessAuto: Boolean? = null,
    val displayTimeout: Int? = null,
    val screenRotation: Boolean? = null,
    val darkMode: Boolean? = null,
    val nightLight: Boolean? = null,
    val extraDim: Boolean? = null,
    val immersiveMode: ImmersiveMode? = null,
    val grayscale: Boolean? = null,
    val hapticFeedback: Boolean? = null,
    val keyboardVibration: Boolean? = null,
    val batterySaver: Boolean? = null,
    val locationMode: LocationMode? = null,
    val bluetooth: Boolean? = null,
)

/** Merges two ModeSettings — non-null values in [other] win over values in [this]. */
fun ModeSettings.mergeWith(other: ModeSettings) = ModeSettings(
    volumeNotification = other.volumeNotification ?: volumeNotification,
    volumeMedia = other.volumeMedia ?: volumeMedia,
    volumeMediaOverride = other.volumeMediaOverride ?: volumeMediaOverride,
    ringerMode = other.ringerMode ?: ringerMode,
    dnd = other.dnd ?: dnd,
    brightness = other.brightness ?: brightness,
    brightnessAuto = other.brightnessAuto ?: brightnessAuto,
    displayTimeout = other.displayTimeout ?: displayTimeout,
    screenRotation = other.screenRotation ?: screenRotation,
    darkMode = other.darkMode ?: darkMode,
    nightLight = other.nightLight ?: nightLight,
    extraDim = other.extraDim ?: extraDim,
    immersiveMode = other.immersiveMode ?: immersiveMode,
    grayscale = other.grayscale ?: grayscale,
    hapticFeedback = other.hapticFeedback ?: hapticFeedback,
    keyboardVibration = other.keyboardVibration ?: keyboardVibration,
    batterySaver = other.batterySaver ?: batterySaver,
    locationMode = other.locationMode ?: locationMode,
    bluetooth = other.bluetooth ?: bluetooth,
)

class ModeSettingsConverter {
    // NOTE: Gson silently nullifies unknown enum values during deserialization
    // (e.g. a value from a future app version). Affected fields will appear as
    // null in ModeSettings, which mergeWith treats as "unset". This is safe but
    // can produce surprising results if enum names change between app versions.
    private val gson = Gson()

    @TypeConverter
    fun fromJson(json: String?): ModeSettings =
        if (json == null) ModeSettings() else gson.fromJson(json, ModeSettings::class.java)

    @TypeConverter
    fun toJson(settings: ModeSettings?): String? = settings?.let { gson.toJson(it) }
}
