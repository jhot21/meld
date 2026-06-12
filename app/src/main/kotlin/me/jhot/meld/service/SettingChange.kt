package me.jhot.meld.service

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingChange(val namespace: String, val key: String, val value: Int) : Parcelable
