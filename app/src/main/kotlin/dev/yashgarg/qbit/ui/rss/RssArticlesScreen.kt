package dev.yashgarg.qbit.ui.rss

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import qbittorrent.models.RssArticle
import qbittorrent.models.RssFeed
import qbittorrent.models.RssFolder
import qbittorrent.models.RssItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssArticlesScreen(appNavigator: AppNavigator, viewModel: RssViewModel = hiltViewModel()) {
    val itemPath = viewModel.itemPath.orEmpty()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val feed = remember(uiState.items, itemPath) { findFeed(uiState.items, itemPath) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmAddArticle by remember { mutableStateOf<RssArticle?>(null) }

    LaunchedEffect(Unit) { viewModel.status.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(feed?.name ?: "Articles") },
                navigationIcon = {
                    IconButton(onClick = { appNavigator.navigate(NavCommand.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshItem(itemPath) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { viewModel.markAsRead(itemPath) }) {
                        Icon(Icons.Filled.MarkEmailRead, contentDescription = "Mark all as read")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            feed == null ->
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(48.dp)
                )
            feed.articles.isEmpty() ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        "No articles yet",
                        Modifier.fillMaxWidth().padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(feed.articles, key = { it.id }) { article ->
                        ArticleCard(
                            article = article,
                            onOpen = {
                                viewModel.markAsRead(itemPath, article.id)
                                if (article.link.isNotBlank()) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, article.link.toUri())
                                    )
                                }
                            },
                            onAddTorrent =
                                if (!article.torrentURL.isNullOrBlank()) {
                                    { confirmAddArticle = article }
                                } else null,
                        )
                    }
                }
        }
    }

    confirmAddArticle?.let { article ->
        AlertDialog(
            onDismissRequest = { confirmAddArticle = null },
            title = { Text("Add torrent?") },
            text = { Text(article.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTorrentFromArticle(requireNotNull(article.torrentURL))
                        confirmAddArticle = null
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAddArticle = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleCard(article: RssArticle, onOpen: () -> Unit, onAddTorrent: (() -> Unit)?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    article.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    article.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onAddTorrent != null) {
                IconButton(onClick = onAddTorrent) {
                    Icon(Icons.Filled.Download, contentDescription = "Add torrent")
                }
            }
        }
    }
}

private fun findFeed(items: List<RssItem>, path: String): RssFeed? {
    items.forEach { item ->
        when (item) {
            is RssFeed -> if (item.path == path) return item
            is RssFolder ->
                findFeed(item.children, path)?.let {
                    return it
                }
        }
    }
    return null
}
