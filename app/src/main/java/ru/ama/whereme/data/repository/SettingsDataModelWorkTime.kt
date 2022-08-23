package ru.ama.whereme.data.database


data class SettingsDataModelWorkTime(
  val days: List<String>,
  val start: String,
  val end: String
)