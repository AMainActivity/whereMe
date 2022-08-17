package ru.ama.whereme.data.database


data class SettingsDataModel(
  val days: List<String>,
  val start: String,
  val end: String
)