package com.etatech.hashiya.feature.paperdetails.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.etatech.hashiya.feature.paperdetails.PaperDetailsScreen
import com.etatech.hashiya.feature.paperdetails.PaperDetailsViewModel

/**
 * Details as the detail pane beside the Library's list on wide windows. [onClose] clears the selection (the paper
 * is gone); [onRemove] is "Remove from library", which the list does with its Undo.
 */
@Composable
fun PaperDetailsPane(
    openAlexId: String,
    onClose: () -> Unit,
    onRemove: (openAlexId: String) -> Unit,
    onReadPdf: (openAlexId: String) -> Unit
) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) { "PaperDetailsPane needs a ViewModelStoreOwner" }
    val stores = viewModel(viewModelStoreOwner = parent) { DetailPaneStore() }
    val owner = remember(stores, parent, openAlexId) { stores.ownerFor(openAlexId, parent) }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        PaperDetailsScreen(
            openAlexId = openAlexId,
            onBack = onClose,
            onRemove = onRemove,
            onReadPdf = onReadPdf,
            inPane = true,
            viewModel = hiltViewModel<PaperDetailsViewModel, PaperDetailsViewModel.Factory>(
                viewModelStoreOwner = owner,
                key = openAlexId,
                creationCallback = { factory -> factory.create(openAlexId) }
            )
        )
    }
}

/**
 * Keeps the shown paper's ViewModels in a store of their own, inside the list screen's entry, so they survive
 * rotation and a trip to the Reader. Showing another paper clears the last one's, which saves its notes.
 */
internal class DetailPaneStore : ViewModel() {
    private var shownId: String? = null
    private var store = ViewModelStore()

    fun ownerFor(openAlexId: String, parent: ViewModelStoreOwner): ViewModelStoreOwner {
        if (openAlexId != shownId) {
            store.clear()
            store = ViewModelStore()
            shownId = openAlexId
        }
        val paneStore = store
        val defaults = parent as HasDefaultViewModelProviderFactory
        return object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
            override val viewModelStore: ViewModelStore = paneStore
            override val defaultViewModelProviderFactory: ViewModelProvider.Factory = defaults.defaultViewModelProviderFactory
            override val defaultViewModelCreationExtras: CreationExtras = defaults.defaultViewModelCreationExtras
        }
    }

    override fun onCleared() = store.clear()
}
