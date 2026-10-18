package ru.ama.whereme.presentation.map

import ru.ama.whereme.domain.entity.LocationDb

/** Keeps the query date and its rows together when the selected date changes quickly. */
data class DayLocations(val date: String, val locations: List<LocationDb>)
