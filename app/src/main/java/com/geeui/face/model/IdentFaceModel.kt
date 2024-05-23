package com.geeui.face.model

import com.google.gson.annotations.SerializedName


data class IdentFaceModel(
    @SerializedName("areaPercent")
    val areaPercent: Double?,
    @SerializedName("faceNumber")
    val faceNumber: Int?,
    @SerializedName("isOwner")
    val isOwner: Boolean?
)