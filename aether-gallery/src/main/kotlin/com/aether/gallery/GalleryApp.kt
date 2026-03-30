package com.aether.gallery

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aether.core.AetherIntents
import com.aether.core.ui.components.*
import com.aether.core.ui.theme.AetherColors
import com.aether.core.ui.theme.AetherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
// MODEL
// ════════════════════════════════════════════════════════════════════════════

data class MediaItem(
    val id:       Long,
    val uri:      Uri,
    val name:     String,
    val bucketName: String,
    val dateAdded: Long,
    val size:     Long,
    val mimeType: String,
    val width:    Int = 0,
    val height:   Int = 0,
    val duration: Long = 0L,  // ms, pour vidéos
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val formattedDate: String get() = SimpleDateFormat("dd MMM yyyy", Locale.FRENCH).format(Date(dateAdded * 1000))
}

data class MediaAlbum(
    val name:       String,
    val coverUri:   Uri,
    val count:      Int,
    val bucketId:   Long,
)

// ════════════════════════════════════════════════════════════════════════════
// REPOSITORY
// ════════════════════════════════════════════════════════════════════════════

class GalleryRepository(private val context: Context) {

    private val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    fun hasPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        return perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    suspend fun loadAlbums(): List<MediaAlbum> = withContext(Dispatchers.IO) {
        val albums = mutableMapOf<Long, Triple<String, Uri, Int>>()
        val proj = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media._ID,
        )
        context.contentResolver.query(imageUri, proj, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val bid  = c.getLong(0)
                val name = c.getString(1) ?: "Sans titre"
                val id   = c.getLong(2)
                val uri  = ContentUris.withAppendedId(imageUri, id)
                val curr = albums[bid]
                albums[bid] = Triple(name, curr?.second ?: uri, (curr?.third ?: 0) + 1)
            }
        }
        albums.entries.map { (bid, v) -> MediaAlbum(v.first, v.second, v.third, bid) }
            .sortedByDescending { it.count }
    }

    suspend fun loadAll(sortNewest: Boolean = true): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        items += loadImages(sortNewest)
        items += loadVideos(sortNewest)
        if (sortNewest) items.sortByDescending { it.dateAdded }
        else items.sortBy { it.dateAdded }
        items
    }

    suspend fun loadAlbumItems(bucketId: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        loadImages(true, bucketId)
    }

    private fun loadImages(sortNewest: Boolean, bucketId: Long? = null): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val sel   = if (bucketId != null) "${MediaStore.Images.Media.BUCKET_ID}=?" else null
        val args  = if (bucketId != null) arrayOf(bucketId.toString()) else null
        val order = if (sortNewest) "${MediaStore.Images.Media.DATE_ADDED} DESC" else "${MediaStore.Images.Media.DATE_ADDED} ASC"
        val proj  = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE, MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
        )
        context.contentResolver.query(imageUri, proj, sel, args, order)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                items.add(MediaItem(
                    id = id, uri = ContentUris.withAppendedId(imageUri, id),
                    name = c.getString(1) ?: "", bucketName = c.getString(2) ?: "",
                    dateAdded = c.getLong(3), size = c.getLong(4), mimeType = c.getString(5) ?: "image/jpeg",
                    width = c.getInt(6), height = c.getInt(7),
                ))
            }
        }
        return items
    }

    private fun loadVideos(sortNewest: Boolean): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val order = if (sortNewest) "${MediaStore.Video.Media.DATE_ADDED} DESC" else "${MediaStore.Video.Media.DATE_ADDED} ASC"
        val proj  = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE, MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
        )
        context.contentResolver.query(videoUri, proj, null, null, order)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                items.add(MediaItem(
                    id = id, uri = ContentUris.withAppendedId(videoUri, id),
                    name = c.getString(1) ?: "", bucketName = c.getString(2) ?: "",
                    dateAdded = c.getLong(3), size = c.getLong(4), mimeType = c.getString(5) ?: "video/mp4",
                    width = c.getInt(6), height = c.getInt(7), duration = c.getLong(8),
                ))
            }
        }
        return items
    }
}

// ════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ════════════════════════════════════════════════════════════════════════════

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo     = GalleryRepository(app)
    private val _items   = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _albums  = MutableStateFlow<List<MediaAlbum>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _selected= MutableStateFlow<Set<Long>>(emptySet())
    private val _view    = MutableStateFlow<GalleryView>(GalleryView.GRID)

    val items:    StateFlow<List<MediaItem>> = _items.asStateFlow()
    val albums:   StateFlow<List<MediaAlbum>>= _albums.asStateFlow()
    val loading:  StateFlow<Boolean>         = _loading.asStateFlow()
    val selected: StateFlow<Set<Long>>       = _selected.asStateFlow()
    val view:     StateFlow<GalleryView>     = _view.asStateFlow()
    val hasPermission: Boolean get()         = repo.hasPermission()

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        _items.value  = repo.loadAll()
        _albums.value = repo.loadAlbums()
        _loading.value = false
    }

    fun toggleSelect(id: Long) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }
    fun clearSelection()       { _selected.value = emptySet() }
    fun setView(v: GalleryView){ _view.value = v }

    fun getSelectedUris(): List<Uri> = _items.value
        .filter { selected.value.contains(it.id) }
        .map { it.uri }
}

enum class GalleryView { GRID, ALBUMS }

// ════════════════════════════════════════════════════════════════════════════
// UI
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryApp(vm: GalleryViewModel) {
    val items    by vm.items.collectAsStateWithLifecycle()
    val albums   by vm.albums.collectAsStateWithLifecycle()
    val loading  by vm.loading.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val view     by vm.view.collectAsStateWithLifecycle()
    val context  = LocalContext.current
    var preview      by remember { mutableStateOf<MediaItem?>(null) }
    var videoPreview by remember { mutableStateOf<MediaItem?>(null) }

    if (!vm.hasPermission) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            AetherEmptyState(Icons.Rounded.PhotoLibrary, "Accès galerie requis",
                "Autorisez l'accès aux photos dans les paramètres")
        }
        return
    }

    // Lecteur vidéo
    if (videoPreview != null) {
        VideoPlayerScreen(uri = videoPreview!!.uri, onClose = { videoPreview = null })
        return
    }

    // Plein écran image
    if (preview != null) {
        FullScreenViewer(item = preview!!, onClose = { preview = null },
            onShare = { shareMedia(context, listOf(preview!!.uri), preview!!.mimeType) },
            onSms   = { sendToSms(context, listOf(preview!!.uri)) })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected.isEmpty()) "Galerie" else "${selected.size} sélectionné(s)") },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = {
                            sendToSms(context, vm.getSelectedUris())
                            vm.clearSelection()
                        }) { Icon(Icons.Rounded.Message, "Envoyer par SMS", tint = AetherColors.SmsViolet) }
                        IconButton(onClick = {
                            shareMedia(context, vm.getSelectedUris(), "image/*")
                            vm.clearSelection()
                        }) { Icon(Icons.Rounded.Share, "Partager") }
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Rounded.Close, "Désélectionner")
                        }
                    } else {
                        IconButton(onClick = { vm.setView(if (view == GalleryView.GRID) GalleryView.ALBUMS else GalleryView.GRID) }) {
                            Icon(if (view == GalleryView.GRID) Icons.Rounded.PhotoAlbum else Icons.Rounded.GridView, "Changer vue")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else when (view) {
            GalleryView.GRID -> MediaGrid(
                items = items, selected = selected, modifier = Modifier.padding(padding),
                onTap = { item ->
                    when {
                        selected.isNotEmpty() -> vm.toggleSelect(item.id)
                        item.isVideo          -> videoPreview = item
                        else                  -> preview = item
                    }
                },
                onLongPress = { vm.toggleSelect(it.id) }
            )
            GalleryView.ALBUMS -> AlbumsView(
                albums = albums, modifier = Modifier.padding(padding),
                onAlbumClick = { /* ouvrir l'album */ }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items:       List<MediaItem>,
    selected:    Set<Long>,
    modifier:    Modifier,
    onTap:       (MediaItem) -> Unit,
    onLongPress: (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        columns          = GridCells.Fixed(3),
        modifier         = modifier.fillMaxSize(),
        contentPadding   = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement   = Arrangement.spacedBy(2.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .combinedClickable(onClick = { onTap(item) }, onLongClick = { onLongPress(item) }),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri).crossfade(true).size(300).build(),
                    contentDescription = item.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
                // Overlay sélection
                if (selected.contains(item.id)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(.4f)))
                    Icon(Icons.Rounded.CheckCircle, null,
                        tint     = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp))
                }
                // Badge vidéo
                if (item.isVideo) {
                    Box(Modifier.align(Alignment.BottomStart).padding(4.dp)
                        .background(Color.Black.copy(.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(formatDuration(item.duration),
                            style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsView(albums: List<MediaAlbum>, modifier: Modifier, onAlbumClick: (MediaAlbum) -> Unit) {
    LazyVerticalGrid(
        columns        = GridCells.Fixed(2),
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement   = Arrangement.spacedBy(10.dp),
    ) {
        items(albums, key = { it.bucketId }) { album ->
            Card(onClick = { onAlbumClick(album) }, shape = RoundedCornerShape(14.dp)) {
                Column {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.coverUri).crossfade(true).build(),
                        contentDescription = album.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Column(Modifier.padding(10.dp)) {
                        Text(album.name, style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${album.count} élément${if (album.count > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenViewer(item: MediaItem, onClose: () -> Unit, onShare: () -> Unit, onSms: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.uri).crossfade(true).build(),
            contentDescription = item.name,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier.fillMaxSize(),
        )
        // Barre du haut
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Color.Black.copy(.5f)).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        }
        // Barre du bas
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(.5f)).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AetherAppChip("Envoyer SMS", Icons.Rounded.Message, AetherColors.SmsViolet, onSms)
            AetherAppChip("Partager", Icons.Rounded.Share, Color.White, onShare)
        }
    }
}

private fun sendToSms(context: Context, uris: List<Uri>) {
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type  = "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        setPackage(AetherIntents.PKG_SMS)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    else context.startActivity(intent.apply { setPackage(null) })
}

private fun shareMedia(context: Context, uris: List<Uri>, mime: String) {
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uris[0]) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = mime; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Partager").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "0:${"%02d".format(s)}" else "${s / 60}:${"%02d".format(s % 60)}"
}

// ════════════════════════════════════════════════════════════════════════════
// ACTIVITY + APP
// ════════════════════════════════════════════════════════════════════════════

class GalleryApp : Application()

class MainActivity : ComponentActivity() {
    private lateinit var vm: GalleryViewModel
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { vm.load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[GalleryViewModel::class.java]

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permLauncher.launch(perms)

        setContent { AetherTheme { Surface(Modifier.fillMaxSize()) { GalleryApp(vm) } } }
    }
}
