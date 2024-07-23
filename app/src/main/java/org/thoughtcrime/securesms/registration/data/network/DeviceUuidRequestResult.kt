package org.thoughtcrime.securesms.registration.data.network

import org.whispersystems.signalservice.api.NetworkResult
import org.whispersystems.signalservice.internal.push.ProvisioningUuid

sealed class DeviceUuidRequestResult(cause: Throwable?) : RegistrationResult(cause) {
  companion object {
    fun from(networkResult: NetworkResult<ProvisioningUuid>): DeviceUuidRequestResult {
      return when (networkResult) {
        is NetworkResult.Success -> Success(networkResult.result.uuid)
        is NetworkResult.ApplicationError -> UnknownError(networkResult.throwable)
        is NetworkResult.NetworkError -> UnknownError(networkResult.exception)
        is NetworkResult.StatusCodeError -> UnknownError(networkResult.exception)
      }
    }
  }

  class Success(val uuid: String) : DeviceUuidRequestResult(null)
  class UnknownError(cause: Throwable) : DeviceUuidRequestResult(cause)
}
