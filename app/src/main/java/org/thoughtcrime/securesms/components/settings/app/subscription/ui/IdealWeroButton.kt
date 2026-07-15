package org.thoughtcrime.securesms.components.settings.app.subscription.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

@Composable
fun IdealWeroButton(
  onClick: () -> Unit,
  enabled: Boolean,
  modifier: Modifier = Modifier
) {
  Buttons.LargeTonal(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
  ) {
    Image(
      imageVector = ImageVector.vectorResource(R.drawable.logo_ideal_wero),
      contentDescription = stringResource(R.string.GatewaySelectorBottomSheet__ideal_wero)
    )
  }
}

@DayNightPreviews
@Composable
private fun IdealWeroButtonPreview() {
  Previews.Preview {
    IdealWeroButton(onClick = {}, enabled = true)
  }
}
