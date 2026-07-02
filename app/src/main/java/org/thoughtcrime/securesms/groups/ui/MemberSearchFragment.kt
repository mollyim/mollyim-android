package org.thoughtcrime.securesms.groups.ui

import android.os.Bundle
import android.view.View
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.LocalFragmentManager
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.util.logging.Log
import org.signal.core.util.requireParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearch
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.contacts.paged.ContactSearchPagedDataSourceRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchRepository
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.conversation.RecipientSearchBar
import org.thoughtcrime.securesms.conversation.mutiselect.forward.SearchConfigurationProvider
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.search.SearchRepository
import org.thoughtcrime.securesms.util.fragments.findListener

/**
 * Fragment that shows all members in a group (including self)
 */
class MemberSearchFragment : ComposeFragment(), RecipientBottomSheetDialogFragment.Callback {

  companion object {

    private val TAG = Log.tag(MemberSearchFragment::class.java)
    private const val ARG_GROUP_ID = "group_id"

    fun newInstance(groupId: GroupId.V2): MemberSearchFragment {
      return MemberSearchFragment().apply {
        arguments = Bundle().apply {
          putParcelable(ARG_GROUP_ID, groupId)
        }
      }
    }
  }

  private val groupId: GroupId.V2 by lazy {
    requireArguments().requireParcelableCompat(ARG_GROUP_ID, GroupId.V2::class.java)
  }

  private val contactViewModel: ContactSearchViewModel by viewModels {
    ContactSearchViewModel.Factory(
      selectionLimits = SelectionLimits(0, 0),
      isMultiSelect = false,
      repository = ContactSearchRepository(),
      performSafetyNumberChecks = false,
      arbitraryRepository = findListener<SearchConfigurationProvider>()?.getArbitraryRepository(),
      searchRepository = SearchRepository(requireContext().getString(R.string.Recipient_you)),
      contactSearchPagedDataSourceRepository = ContactSearchPagedDataSourceRepository(requireContext(), requireContext().getString(R.string.Recipient_you))
    )
  }

  private val memberSearchViewModel: MemberSearchViewModel by viewModels()

  override fun onRecipientBottomSheetDismissed() {
    contactViewModel.refreshGroupData()
  }

  override fun onMessageClicked() = Unit

  @Composable
  override fun FragmentContent() {
    val memberFilter by memberSearchViewModel.memberFilter.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalFragmentManager provides childFragmentManager) {
      MemberSearchScreen(
        contactViewModel = contactViewModel,
        memberFilter = memberFilter,
        onFilterSelected = { memberSearchViewModel.setFilter(it) },
        mapStateToConfiguration = { state -> getConfiguration(state, memberFilter) },
        contactSearchCallbacks = remember {
          SearchCallbacks(
            fragmentManager = childFragmentManager,
            groupId = groupId
          )
        }
      )
    }
  }

  class SearchCallbacks(
    private val fragmentManager: FragmentManager,
    private val groupId: GroupId.V2
  ) : ContactSearchCallbacks.Simple() {
    override fun onBeforeContactsSelected(view: View?, contactSearchKeys: Set<ContactSearchKey>): Set<ContactSearchKey> {
      val recipientId = contactSearchKeys.filterIsInstance<ContactSearchKey.RecipientSearchKey>().firstOrNull()?.recipientId
      if (recipientId != null) {
        RecipientBottomSheetDialogFragment.show(fragmentManager, recipientId, groupId)
      }
      return emptySet()
    }
  }

  private fun getConfiguration(contactSearchState: ContactSearchState, memberFilter: MemberSearchViewModel.MemberFilter): ContactSearchConfiguration {
    return findListener<SearchConfigurationProvider>()?.getSearchConfiguration(childFragmentManager, contactSearchState) ?: ContactSearchConfiguration.build {
      query = contactSearchState.query

      addSection(
        ContactSearchConfiguration.Section.GroupMembers(
          includeHeader = false,
          includeLetterHeaders = true,
          groupId = groupId,
          showGroupsInCommon = false,
          showSelfAsYou = true,
          roleFilter = when (memberFilter) {
            MemberSearchViewModel.MemberFilter.ALL -> ContactSearchConfiguration.MemberRole.ALL
            MemberSearchViewModel.MemberFilter.ADMINS -> ContactSearchConfiguration.MemberRole.ADMINS
            MemberSearchViewModel.MemberFilter.CONTACTS -> ContactSearchConfiguration.MemberRole.CONTACTS
          }
        )
      )

      withEmptyState {
        addSection(ContactSearchConfiguration.Section.Empty)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSearchScreen(
  contactViewModel: ContactSearchViewModel,
  memberFilter: MemberSearchViewModel.MemberFilter,
  onFilterSelected: (MemberSearchViewModel.MemberFilter) -> Unit,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  contactSearchCallbacks: MemberSearchFragment.SearchCallbacks
) {
  val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  var showFilterDialog by remember { mutableStateOf(false) }

  if (showFilterDialog) {
    val filters = MemberSearchViewModel.MemberFilter.entries

    Dialogs.RadioListDialog(
      onDismissRequest = { showFilterDialog = false },
      title = stringResource(R.string.MemberSearchFragment__filter),
      labels = stringArrayResource(R.array.filter_search_entries),
      values = filters.map { it.name }.toTypedArray(),
      selectedIndex = filters.indexOf(memberFilter),
      onSelected = { index -> onFilterSelected(filters[index]) }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.MemberSearchFragment__search_members)) },
        navigationIcon = {
          IconButton(onClick = { onBackPressedDispatcher?.onBackPressed() }) {
            Icon(
              imageVector = SignalIcons.ArrowStart.imageVector,
              contentDescription = stringResource(R.string.DSLSettingsToolbar__navigate_up)
            )
          }
        },
        actions = {
          val filterActive = memberFilter != MemberSearchViewModel.MemberFilter.ALL
          IconButton(onClick = { showFilterDialog = true }) {
            Icon(
              imageVector = ImageVector.vectorResource(R.drawable.symbol_filter_24),
              tint = if (filterActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
              contentDescription = stringResource(R.string.MemberSearchFragment__filter)
            )
          }
        }
      )
    }
  ) {
    MemberSearchContent(
      contactViewModel = contactViewModel,
      memberFilter = memberFilter,
      mapStateToConfiguration = mapStateToConfiguration,
      contactSearchCallbacks = contactSearchCallbacks,
      modifier = Modifier.padding(it)
    )
  }
}

@Composable
private fun MemberSearchContent(
  contactViewModel: ContactSearchViewModel,
  memberFilter: MemberSearchViewModel.MemberFilter,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  modifier: Modifier = Modifier,
  contactSearchCallbacks: MemberSearchFragment.SearchCallbacks
) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  val currentMapStateToConfiguration by rememberUpdatedState(mapStateToConfiguration)
  LaunchedEffect(memberFilter) {
    contactViewModel.setConfiguration(
      currentMapStateToConfiguration(contactViewModel.configurationState.value)
    )
  }

  Column(
    modifier = modifier.fillMaxSize()
  ) {
    val query by contactViewModel.query.collectAsStateWithLifecycle()
    RecipientSearchBar(
      hint = stringResource(R.string.MemberSearchFragment__search_members),
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp)
        .focusRequester(focusRequester)
        .horizontalGutters(),
      query = query ?: "",
      onQueryChange = { contactViewModel.setQuery(it) },
      onSearch = { contactViewModel.setQuery(it) }
    )

    ContactSearch(
      viewModel = contactViewModel,
      mapStateToConfiguration = mapStateToConfiguration,
      displayOptions = remember {
        ContactSearchAdapter.DisplayOptions(
          displaySecondaryInformation = ContactSearchAdapter.DisplaySecondaryInformation.NEVER
        )
      },
      callbacks = contactSearchCallbacks,
      modifier = Modifier.weight(1f)
    )
  }
}
