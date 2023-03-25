package im.molly.unifiedpush.model

data class MollySocketDevice(
  val uuid: String,
  val deviceId: Int,
  val password: String,
)
