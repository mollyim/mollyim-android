/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.signal.core.ui.WindowBreakpoint
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.rememberWindowBreakpoint

object RegistrationScaffold {
  private val smallLayoutParams = Params.OnePane(
    topInset = 24.dp,
    bottomInset = 24.dp,
    paneVerticalInset = 24.dp,
    paneHorizontalInset = 24.dp,
    maxButtonWidth = 320.dp
  )

  private val mediumLayoutParams = Params.TwoPane(
    topInset = 64.dp,
    bottomInset = 24.dp,
    paneTopInset = 16.dp,
    paneBottomInset = 24.dp,
    paneOuterInset = 24.dp,
    paneInnerInset = 24.dp,
    maxButtonWidth = 320.dp
  )

  private val largeWidthLayoutParams = Params.TwoPane(
    topInset = 64.dp,
    bottomInset = 32.dp,
    paneTopInset = 64.dp,
    paneBottomInset = 64.dp,
    paneOuterInset = 128.dp,
    paneInnerInset = 64.dp,
    maxButtonWidth = 412.dp
  )

  private val largeHeightLayoutParams = Params.OnePane(
    topInset = 64.dp,
    bottomInset = 32.dp,
    paneVerticalInset = 64.dp,
    paneHorizontalInset = 128.dp,
    maxButtonWidth = 320.dp
  )

  sealed interface Params {
    val topInset: Dp
    val bottomInset: Dp
    val maxButtonWidth: Dp

    data class OnePane(
      override val topInset: Dp,
      private val paneVerticalInset: Dp,
      private val paneHorizontalInset: Dp,
      override val bottomInset: Dp,
      override val maxButtonWidth: Dp
    ) : Params {
      val panePadding = PaddingValues(
        top = topInset + paneVerticalInset,
        bottom = paneVerticalInset,
        start = paneHorizontalInset,
        end = paneHorizontalInset
      )
    }

    data class TwoPane(
      override val topInset: Dp,
      private val paneTopInset: Dp,
      private val paneBottomInset: Dp,
      private val paneOuterInset: Dp,
      private val paneInnerInset: Dp,
      override val bottomInset: Dp,
      override val maxButtonWidth: Dp
    ) : Params {
      val firstPanePadding: PaddingValues = PaddingValues(
        top = topInset + paneTopInset,
        bottom = paneBottomInset,
        start = paneOuterInset,
        end = paneInnerInset
      )

      val secondPanePadding: PaddingValues = PaddingValues(
        top = topInset + paneTopInset,
        bottom = paneBottomInset,
        start = paneInnerInset,
        end = paneOuterInset
      )
    }
  }

  @Composable
  fun rememberLayoutParams(): Params {
    return when (rememberWindowBreakpoint()) {
      WindowBreakpoint.SMALL -> smallLayoutParams
      WindowBreakpoint.MEDIUM -> mediumLayoutParams
      WindowBreakpoint.LARGE_WIDTH -> largeWidthLayoutParams
      WindowBreakpoint.LARGE_HEIGHT -> largeHeightLayoutParams
    }
  }
}

/**
 * Scaffold for registration flow screens.
 */
@Composable
fun RegistrationScaffold(
  content: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  header: (@Composable () -> Unit)? = null,
  footer: (@Composable () -> Unit)? = null
) {
  SubcomposeLayout(modifier = modifier.imePadding()) { constraints ->
    val footerPlaceables = footer?.let {
      subcompose("footer", it).map { m -> m.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    } ?: emptyList()
    val footerHeight = footerPlaceables.maxOfOrNull { it.height } ?: 0

    val headerPlaceables = header?.let {
      subcompose("header", it).map { m -> m.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    } ?: emptyList()
    val headerHeight = headerPlaceables.maxOfOrNull { it.height } ?: 0

    val contentHeight = (constraints.maxHeight - footerHeight - headerHeight).coerceAtLeast(0)
    val contentPlaceables = subcompose("content", content).map { m ->
      m.measure(constraints.copy(minHeight = contentHeight, maxHeight = contentHeight))
    }

    layout(constraints.maxWidth, constraints.maxHeight) {
      headerPlaceables.forEach { it.placeRelative(0, 0) }
      contentPlaceables.forEach { it.placeRelative(0, headerHeight) }
      footerPlaceables.forEach { it.placeRelative(0, contentHeight + headerHeight) }
    }
  }
}

/**
 * Two-pane variant of [RegistrationScaffold] for medium and large-width breakpoints.
 */
@Composable
fun TwoPaneRegistrationScaffold(
  modifier: Modifier = Modifier,
  params: RegistrationScaffold.Params.TwoPane,
  header: (@Composable () -> Unit)? = null,
  footer: (@Composable () -> Unit)? = null,
  firstPane: @Composable RowScope.(PaddingValues) -> Unit,
  secondPane: @Composable RowScope.(PaddingValues) -> Unit
) {
  RegistrationScaffold(
    modifier = modifier.fillMaxSize(),
    header = header,
    footer = footer,
    content = {
      Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier.fillMaxSize()
      ) {
        firstPane(params.firstPanePadding)
        secondPane(params.secondPanePadding)
      }
    }
  )
}

@Composable
private fun PreviewPane(
  label: String,
  paddingValues: PaddingValues,
  outerColor: Color,
  innerColor: Color,
  modifier: Modifier = Modifier
) {
  Text(
    modifier = modifier
      .fillMaxHeight()
      .background(outerColor)
      .padding(paddingValues)
      .background(innerColor)
      .wrapContentHeight(Alignment.CenterVertically),
    text = label,
    textAlign = TextAlign.Center,
    fontSize = 30.sp,
    color = Color.Black
  )
}

@AllDevicePreviews
@Composable
private fun RegistrationScaffoldPreview() = Previews.Preview {
  when (val params = RegistrationScaffold.rememberLayoutParams()) {
    is RegistrationScaffold.Params.OnePane -> {
      RegistrationScaffold(
        header = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "header",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        },
        content = {
          PreviewPane(
            label = "content",
            paddingValues = params.panePadding,
            outerColor = Color.Red,
            innerColor = Color.Yellow,
            modifier = Modifier.fillMaxWidth()
          )
        },
        footer = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "footer",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        }
      )
    }

    is RegistrationScaffold.Params.TwoPane -> {
      TwoPaneRegistrationScaffold(
        modifier = Modifier.fillMaxSize(),
        params = params,
        header = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "header",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        },
        firstPane = { paddingValues ->
          PreviewPane(
            label = "firstPane",
            paddingValues = paddingValues,
            outerColor = Color.Red,
            innerColor = Color.Yellow,
            modifier = Modifier.weight(1f)
          )
        },
        secondPane = { paddingValues ->
          PreviewPane(
            label = "secondPane",
            paddingValues = paddingValues,
            outerColor = Color.Blue,
            innerColor = Color.Yellow,
            modifier = Modifier.weight(1f)
          )
        },
        footer = {
          Text(
            modifier = Modifier
              .fillMaxWidth()
              .background(Color.Green)
              .padding(16.dp),
            text = "footer",
            textAlign = TextAlign.Center,
            fontSize = 24.sp,
            color = Color.Black
          )
        }
      )
    }
  }
}
