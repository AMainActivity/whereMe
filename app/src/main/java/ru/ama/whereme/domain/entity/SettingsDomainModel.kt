package ru.ama.whereme.data.database


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SettingsDomainModel(
  val days: List<String>,
  val start: String,
  val end: String
) : Parcelable