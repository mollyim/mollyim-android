/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.countrycode

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.IconButtons.IconButton
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Scaffolds
import org.thoughtcrime.securesms.R

/**
 * Screen that allows someone to search and select a country code from a supported list of countries.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CountryCodeSelectScreen(
  state: CountryCodeState,
  title: String,
  onSearch: (String) -> Unit = {},
  onDismissed: () -> Unit = {},
  onClick: (Country) -> Unit = {}
) {
  Scaffold(
    topBar = {
      Scaffolds.DefaultTopAppBar(
        title = title,
        titleContent = { _, title ->
          Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        onNavigationClick = onDismissed,
        navigationIcon = ImageVector.vectorResource(R.drawable.symbol_x_24),
        navigationContentDescription = stringResource(R.string.Material3SearchToolbar__close)
      )
    }
  ) { padding ->
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
      state = listState,
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(padding)
    ) {
      stickyHeader {
        SearchBar(
          text = state.query,
          onSearch = onSearch
        )
      }

      if (state.countryList.isEmpty()) {
        item {
          CircularProgressIndicator(
            modifier = Modifier.size(56.dp)
          )
        }
      } else if (state.query.isEmpty()) {
        if (state.commonCountryList.isNotEmpty()) {
          items(state.commonCountryList) { country ->
            CountryItem(country, onClick)
          }

          item {
            Dividers.Default()
          }
        }

        items(state.countryList) { country ->
          CountryItem(country, onClick)
        }
      } else {
        items(state.filteredList) { country ->
          CountryItem(country, onClick, state.query)
        }
      }
    }

    LaunchedEffect(state.startingIndex) {
      coroutineScope.launch {
        listState.scrollToItem(index = state.startingIndex)
      }
    }
  }
}

@Composable
private fun CountryItem(
  country: Country,
  onClick: (Country) -> Unit = {},
  query: String = ""
) {
  val emoji = country.emoji
  val name = country.name
  val code = "+${country.countryCode}"
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .padding(horizontal = 24.dp)
      .fillMaxWidth()
      .defaultMinSize(minHeight = 56.dp)
      .clickable { onClick(country) }
  ) {
    Text(
      text = emoji,
      modifier = Modifier.size(24.dp)
    )

    if (query.isEmpty()) {
      Text(
        text = name.ifEmpty { stringResource(R.string.CountryCodeFragment__unknown_country) },
        modifier = Modifier
          .padding(start = 24.dp)
          .weight(1f),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = code,
        modifier = Modifier.padding(start = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    } else {
      val annotatedName = buildAnnotatedString {
        val startIndex = name.indexOf(query, ignoreCase = true)

        if (startIndex >= 0) {
          append(name.substring(0, startIndex))

          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(name.substring(startIndex, startIndex + query.length))
          }

          append(name.substring(startIndex + query.length))
        } else {
          append(name)
        }
      }

      val annotatedCode = buildAnnotatedString {
        val startIndex = code.indexOf(query, ignoreCase = true)

        if (startIndex >= 0) {
          append(code.substring(0, startIndex))

          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append(code.substring(startIndex, startIndex + query.length))
          }

          append(code.substring(startIndex + query.length))
        } else {
          append(code)
        }
      }

      Text(
        text = annotatedName,
        modifier = Modifier
          .padding(start = 24.dp)
          .weight(1f),
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = annotatedCode,
        modifier = Modifier.padding(start = 24.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun SearchBar(
  text: String,
  modifier: Modifier = Modifier,
  hint: String = stringResource(R.string.CountryCodeFragment__search_by),
  onSearch: (String) -> Unit = {}
) {
  val focusRequester = remember { FocusRequester() }
  var showKeyboard by remember { mutableStateOf(false) }

  TextField(
    value = text,
    onValueChange = { onSearch(it) },
    placeholder = { Text(hint) },
    trailingIcon = {
      if (text.isNotEmpty()) {
        IconButton(onClick = { onSearch("") }) {
          Icon(
            imageVector = ImageVector.vectorResource(R.drawable.symbol_x_24),
            contentDescription = null
          )
        }
      } else {
        IconButton(onClick = {
          showKeyboard = !showKeyboard
          focusRequester.requestFocus()
        }) {
          if (showKeyboard) {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.symbol_keyboard_24),
              contentDescription = null
            )
          } else {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.symbol_number_pad_24),
              contentDescription = null
            )
          }
        }
      }
    },
    keyboardOptions = KeyboardOptions(
      keyboardType = if (showKeyboard) {
        KeyboardType.Number
      } else {
        KeyboardType.Text
      }
    ),
    shape = RoundedCornerShape(32.dp),
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .padding(bottom = 18.dp, start = 16.dp, end = 16.dp)
      .fillMaxWidth()
      .height(54.dp)
      .focusRequester(focusRequester),
    visualTransformation = VisualTransformation.None,
    colors = TextFieldDefaults.colors(
      // TODO move to SignalTheme
      focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent
    )
  )
}

@DayNightPreviews
@Composable
private fun ScreenPreview() {
  Previews.Preview {
    CountryCodeSelectScreen(
      state = CountryCodeState(
        countryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 1, "US"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", 2, "CA"),
          Country("\uD83C\uDDF2\uD83C\uDDFD", "Mexico", 3, "MX")
        ),
        commonCountryList = mutableListOf(
          Country("\uD83C\uDDFA\uD83C\uDDF8", "United States", 4, "US"),
          Country("\uD83C\uDDE8\uD83C\uDDE6", "Canada", 5, "CA")
        )
      ),
      title = "Your country"
    )
  }
}

@DayNightPreviews
@Composable
private fun LoadingScreenPreview() {
  Previews.Preview {
    CountryCodeSelectScreen(
      state = CountryCodeState(
        countryList = emptyList()
      ),
      title = "Your country"
    )
  }
}
