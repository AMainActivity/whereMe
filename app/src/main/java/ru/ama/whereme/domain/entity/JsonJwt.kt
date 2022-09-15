package ru.ama.whereme.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
@Parcelize
data class JsonJwt(
 val error: Boolean,
val message: String,
val tokenJwt: String,
 val posId: Int,
 val famId: Int,
val name: String?=null,
	val isActivate: Int
): Parcelable