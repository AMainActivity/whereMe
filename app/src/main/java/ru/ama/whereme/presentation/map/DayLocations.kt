package ru.ama.whereme.presentation.map

/** Keeps the query date and its prepared map payload together when the selected date changes quickly. */
data class DayLocations(val date: String, val json: String)
