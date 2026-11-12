package dev.nagi.app.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

internal class ReadingScopes : ViewModel() {
    private var revision: Long? = null
    private var current = ViewModelStore()
    fun forRevision(value: Long): ViewModelStore {
        if (revision != value) {
            current.clear()
            current = ViewModelStore()
            revision = value
        }
        return current
    }
    override fun onCleared() { current.clear() }
}

@Composable
fun AccountReadingScope(revision: Long, content: @Composable () -> Unit) {
    val parent = checkNotNull(LocalViewModelStoreOwner.current) as HasDefaultViewModelProviderFactory
    val scopes: ReadingScopes = viewModel()
    val store = scopes.forRevision(revision)
    val owner = remember(store, parent) {
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
            override val viewModelStore = store
            override val defaultViewModelProviderFactory = parent.defaultViewModelProviderFactory
            override val defaultViewModelCreationExtras = parent.defaultViewModelCreationExtras
        }
    }
    key(revision) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
    }
}
