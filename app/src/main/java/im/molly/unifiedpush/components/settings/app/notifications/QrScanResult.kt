package im.molly.unifiedpush.components.settings.app.notifications

sealed class QrScanResult {
  class Success(val data: String) : QrScanResult()

  class NotFound(val data: String) : QrScanResult()

  data object InvalidData : QrScanResult()

  data object NetworkError : QrScanResult()

  data object QrNotFound : QrScanResult()
}
