package dev.yashgarg.qbit.ui.rss

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.yashgarg.qbit.ui.navigation.AppNavigator
import dev.yashgarg.qbit.ui.navigation.NavCommand
import dev.yashgarg.qbit.ui.server.TooltipIconButton
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
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

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
                    TooltipIconButton(
                        label = if (searchOpen) "Close search" else "Search",
                        icon = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        },
                        position = TooltipAnchorPosition.Below,
                    )
                    TooltipIconButton(
                        label = "Refresh",
                        icon = Icons.Filled.Refresh,
                        onClick = { viewModel.refreshItem(itemPath) },
                        position = TooltipAnchorPosition.Below,
                    )
                    TooltipIconButton(
                        label = "Mark all as read",
                        icon = Icons.Filled.MarkEmailRead,
                        onClick = { viewModel.markAsRead(itemPath) },
                        position = TooltipAnchorPosition.Below,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchOpen) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text("Search articles") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            val filtered =
                if (feed == null) emptyList()
                else if (query.isBlank()) feed.articles
                else feed.articles.filter { it.title.contains(query.trim(), ignoreCase = true) }
            when {
                feed == null -> CircularProgressIndicator(Modifier.fillMaxSize().padding(48.dp))
                feed.articles.isEmpty() ->
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            "No articles yet",
                            Modifier.align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                filtered.isEmpty() ->
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            "No matching articles",
                            Modifier.align(Alignment.Center)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { it.id }) { article ->
                            val alreadyAdded =
                                article.magnetHash()?.let { it in uiState.existingTorrentHashes } ==
                                    true
                            ArticleCard(
                                article = article,
                                alreadyAdded = alreadyAdded,
                                onOpen = {
                                    viewModel.markAsRead(itemPath, article.id)
                                    if (article.link.isNotBlank()) {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, article.link.toUri())
                                        )
                                    }
                                },
                                onAddTorrent =
                                    if (!article.torrentURL.isNullOrBlank() && !alreadyAdded) {
                                        { confirmAddArticle = article }
                                    } else null,
                            )
                        }
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
private fun ArticleCard(
    article: RssArticle,
    alreadyAdded: Boolean,
    onOpen: () -> Unit,
    onAddTorrent: (() -> Unit)?,
) {
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
            when {
                onAddTorrent != null ->
                    IconButton(onClick = onAddTorrent) {
                        Icon(Icons.Filled.Download, contentDescription = "Add torrent")
                    }
                alreadyAdded ->
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Already added",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
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
