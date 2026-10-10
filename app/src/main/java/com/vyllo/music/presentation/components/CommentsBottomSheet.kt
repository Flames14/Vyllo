package com.vyllo.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.network.OkHttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    item: MusicItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var comments by remember { mutableStateOf<List<CommentsInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentInfo by remember { mutableStateOf<CommentsInfo?>(null) }
    var nextPage by remember { mutableStateOf<org.schabi.newpipe.extractor.Page?>(null) }
    
    val coroutineScope = rememberCoroutineScope()
    val loadFailedMessage = stringResource(R.string.comments_load_failed)
    val listState = rememberLazyListState()

    LaunchedEffect(item.url) {
        isLoading = true
        error = null
        // NewPipe is normally initialized by the data sources, but a track opened
        // straight from downloads/playlist may reach comments first. Initializing
        // here (idempotent) avoids a null-downloader crash path entirely.
        try {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(OkHttpDownloader(), Localization.DEFAULT, ContentCountry.DEFAULT)
            }
        } catch (e: Exception) {
            com.vyllo.music.core.security.SecureLogger.e("Comments", "NewPipe init failed", e)
            error = e.localizedMessage ?: loadFailedMessage
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val fetched = withContext(Dispatchers.IO) {
                val urlToFetch = if (item.url.startsWith("http")) item.url else "https://www.youtube.com/watch?v=${item.url}"
                CommentsInfo.getInfo(ServiceList.YouTube, urlToFetch)
            }
            currentInfo = fetched
            nextPage = fetched.nextPage
            comments = fetched.relatedItems ?: emptyList()
        } catch (e: Exception) {
            com.vyllo.music.core.security.SecureLogger.e("Comments", "Failed to load comments", e)
            error = e.localizedMessage ?: loadFailedMessage
        } finally {
            isLoading = false
        }
    }

    fun loadMore() {
        if (isLoadingMore || nextPage == null || currentInfo == null) return
        
        isLoadingMore = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val pageResult = CommentsInfo.getMoreItems(ServiceList.YouTube, currentInfo, nextPage)
                withContext(Dispatchers.Main) {
                    nextPage = pageResult.nextPage
                    val newItems = pageResult.items ?: emptyList()
                    comments = comments + newItems
                }
            } catch (e: Exception) {
                com.vyllo.music.core.security.SecureLogger.e("Comments", "Failed to load more comments", e)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoadingMore = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val count = currentInfo?.commentsCount ?: -1
                val countText = if (count > 0) {
                    if (count >= 1_000_000) String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
                    else if (count >= 1_000) String.format(java.util.Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K")
                    else "$count"
                } else null

                Text(
                    text = if (countText != null) {
                        stringResource(R.string.comments_title_count, countText)
                    } else {
                        stringResource(R.string.comments_title)
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.comments_error, error ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (comments.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.comments_none),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Single pagination trigger driven by scroll position — never a
                // side-effect inside item content (that pattern refires on every
                // recomposition and can storm the extractor).
                LaunchedEffect(listState, comments.size, nextPage, isLoadingMore) {
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    }.distinctUntilChanged().collect { lastVisible ->
                        if (lastVisible >= 0 &&
                            lastVisible >= comments.size - 5 &&
                            nextPage != null && !isLoadingMore
                        ) {
                            loadMore()
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(
                        // Index-suffixed: the extractor can return comments that share
                        // one URL (missing comment IDs) and duplicate keys crash Lazy.
                        count = comments.size,
                        key = { index -> "${comments.getOrNull(index)?.url ?: "comment"}#$index" }
                    ) { index ->
                        val comment = comments.getOrNull(index) ?: return@items
                        CommentRow(comment)
                    }
                    
                    if (isLoadingMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentRow(comment: CommentsInfoItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = comment.uploaderAvatars?.firstOrNull()?.url,
            contentDescription = stringResource(R.string.cd_avatar),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.uploaderName ?: "Unknown",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = comment.textualUploadDate ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = comment.commentText?.content ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = stringResource(R.string.cd_likes),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = comment.textualLikeCount
                        ?: if (comment.likeCount > 0) comment.likeCount.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
