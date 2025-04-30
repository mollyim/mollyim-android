package org.signal.cashu.model

import com.google.gson.annotations.SerializedName

data class Token(
    @SerializedName("token")
    val token: List<BlindedMessage>,
    @SerializedName("mint")
    val mint: String,
    @SerializedName("proofs")
    val proofs: List<Proof>
)

data class BlindedMessage(
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("B_")
    val blindedMessage: String,
    @SerializedName("id")
    val id: String
)

data class Proof(
    @SerializedName("amount")
    val amount: Long,
    @SerializedName("secret")
    val secret: String,
    @SerializedName("C")
    val commitment: String,
    @SerializedName("id")
    val id: String
)