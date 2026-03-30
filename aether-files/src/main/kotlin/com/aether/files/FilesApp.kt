package com.aether.files

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aether.core.AetherIntents
import com.aether.core.ui.components.*
import com.aether.core.ui.theme.AetherColors
import com.aether.core.ui.theme.AetherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
// MODEL
// ════════════════════════════════════════════════════════════════════════════

data class AetherFile(
    val file:      File,
    val name:      String = file.name,
    val path:      String = file.absolutePath,
    val isDir:     Boolean = file.isDirectory,
    val size:      Long    = if (file.isDirectory) 0L else file.length(),
    val modified:  Long    = file.lastModified(),
    val mimeType:  String  = getMime(file),
) {
    val icon: ImageVector get() = when {
        isDir              -> Icons.Rounded.Folder
        mimeType.startsWith("image/")       -> Icons.Rounded.Image
        mimeType.startsWith("video/")       -> Icons.Rounded.VideoFile
        mimeType.startsWith("audio/")       -> Icons.Rounded.AudioFile
        mimeType == "application/pdf"       -> Icons.Rounded.PictureAsPdf
        mimeType.contains("zip") ||
        mimeType.contains("tar") ||
        mimeType.contains("gzip")           -> Icons.Rounded.FolderZip
        mimeType.contains("msword") ||
        mimeType.contains("document")       -> Icons.Rounded.Description
        mimeType.contains("sheet") ||
        mimeType.contains("excel")          -> Icons.Rounded.TableChart
        else                                -> Icons.Rounded.InsertDriveFile
    }
    val iconColor: Color get() = when {
        isDir              -> AetherColors.NotesAmber
        mimeType.startsWith("image/") -> Color(0xFF4CAF50)
        mimeType.startsWith("video/") -> Color(0xFFE91E63)
        mimeType.startsWith("audio/") -> Color(0xFF9C27B0)
        mimeType == "application/pdf" -> AetherColors.Error
        mimeType.contains("zip")      -> Color(0xFFFF9800)
        else               -> AetherColors.FilesBlue
    }
}

private val ERROR_COLOR = Color(0xFFE53935)
private val AetherColors.Error: Color get() = ERROR_COLOR

fun getMime(file: File): String {
    if (file.isDirectory) return "inode/directory"
    val ext = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

enum class SortMode { NAME, DATE, SIZE, TYPE }

// ════════════════════════════════════════════════════════════════════════════
// REPOSITORY
// ════════════════════════════════════════════════════════════════════════════

class FilesRepository(private val context: Context) {

    val rootDir: File = Environment.getExternalStorageDirectory()

    suspend fun listDir(dir: File, sort: SortMode): List<AetherFile> = withContext(Dispatchers.IO) {
        val files = dir.listFiles()?.map { AetherFile(it) } ?: emptyList()
        val sorted = when (sort) {
            SortMode.NAME -> files.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
            SortMode.DATE -> files.sortedWith(compareBy({ !it.isDir }, { -it.modified }))
            SortMode.SIZE -> files.sortedWith(compareBy({ !it.isDir }, { -it.size }))
            SortMode.TYPE -> files.sortedWith(compareBy({ !it.isDir }, { it.mimeType }))
        }
        sorted
    }

    suspend fun search(root: File, query: String): List<AetherFile> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AetherFile>()
        fun walk(dir: File) {
            dir.listFiles()?.forEach { f ->
                if (f.name.contains(query, ignoreCase = true)) results.add(AetherFile(f))
                if (f.isDirectory && results.size < 200) walk(f)
            }
        }
        walk(root)
        results.sortedBy { it.name.lowercase() }
    }

    fun hasPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

    suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    suspend fun rename(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(file.parent, newName)
        file.renameTo(target)
    }

    fun getContentUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

// ════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ════════════════════════════════════════════════════════════════════════════

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo     = FilesRepository(app)
    private val _current = MutableStateFlow(repo.rootDir)
    private val _files   = MutableStateFlow<List<AetherFile>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _sort    = MutableStateFlow(SortMode.NAME)
    private val _search  = MutableStateFlow("")
    private val _selected= MutableStateFlow<Set<String>>(emptySet())
    private val _breadcrumb = MutableStateFlow<List<File>>(listOf(repo.rootDir))

    val currentDir:  StateFlow<File>             = _current.asStateFlow()
    val files:       StateFlow<List<AetherFile>> = _files.asStateFlow()
    val loading:     StateFlow<Boolean>          = _loading.asStateFlow()
    val sort:        StateFlow<SortMode>         = _sort.asStateFlow()
    val search:      StateFlow<String>           = _search.asStateFlow()
    val selected:    StateFlow<Set<String>>      = _selected.asStateFlow()
    val breadcrumb:  StateFlow<List<File>>       = _breadcrumb.asStateFlow()
    val hasPermission: Boolean get()             = repo.hasPermission()

    init { loadDir(repo.rootDir) }

    fun loadDir(dir: File) {
        viewModelScope.launch {
            _loading.value = true
            _current.value = dir
            _search.value  = ""
            _files.value   = repo.listDir(dir, _sort.value)
            _loading.value = false

            // Breadcrumb
            val crumbs = mutableListOf<File>()
            var f: File? = dir
            while (f != null && f.absolutePath.startsWith(repo.rootDir.absolutePath)) {
                crumbs.add(0, f)
                f = f.parentFile
            }
            if (crumbs.firstOrNull() != repo.rootDir) crumbs.add(0, repo.rootDir)
            _breadcrumb.value = crumbs
        }
    }

    fun goUp() {
        val parent = _current.value.parentFile
        if (parent != null && parent.absolutePath.startsWith(repo.rootDir.absolutePath)) {
            loadDir(parent)
        }
    }

    fun setSort(s: SortMode) {
        _sort.value = s
        loadDir(_current.value)
    }

    fun search(q: String) {
        _search.value = q
        if (q.isBlank()) { loadDir(_current.value); return }
        viewModelScope.launch {
            _loading.value = true
            _files.value   = repo.search(_current.value, q)
            _loading.value = false
        }
    }

    fun toggleSelect(path: String) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (contains(path)) remove(path) else add(path)
        }
    }

    fun clearSelection() { _selected.value = emptySet() }

    fun deleteSelected() = viewModelScope.launch {
        _selected.value.forEach { path -> repo.delete(File(path)) }
        clearSelection()
        loadDir(_current.value)
    }

    fun rename(file: AetherFile, newName: String) = viewModelScope.launch {
        repo.rename(file.file, newName)
        loadDir(_current.value)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// UI
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesApp(vm: FilesViewModel) {
    val context    = LocalContext.current
    val files      by vm.files.collectAsStateWithLifecycle()
    val loading    by vm.loading.collectAsStateWithLifecycle()
    val currentDir by vm.currentDir.collectAsStateWithLifecycle()
    val breadcrumb by vm.breadcrumb.collectAsStateWithLifecycle()
    val selected   by vm.selected.collectAsStateWithLifecycle()
    val sort       by vm.sort.collectAsStateWithLifecycle()
    val search     by vm.search.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<AetherFile?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    if (!vm.hasPermission) {
        PermissionScreen()
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        if (breadcrumb.size > 1) {
                            IconButton(onClick = { vm.goUp() }) {
                                Icon(Icons.Rounded.ArrowBackIosNew, "Retour")
                            }
                        }
                    },
                    title = {
                        Text(currentDir.name.ifBlank { "Stockage" },
                            style    = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    },
                    actions = {
                        // Sélection active → actions groupées
                        if (selected.isNotEmpty()) {
                            Text("${selected.size} sélectionné(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { vm.deleteSelected() }) {
                                Icon(Icons.Rounded.Delete, "Supprimer",
                                    tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = { vm.clearSelection() }) {
                                Icon(Icons.Rounded.Close, "Désélectionner")
                            }
                        } else {
                            // Tri
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.Rounded.Sort, "Trier")
                                }
                                DropdownMenu(expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }) {
                                    SortMode.entries.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(when (mode) {
                                                SortMode.NAME -> "Nom"
                                                SortMode.DATE -> "Date"
                                                SortMode.SIZE -> "Taille"
                                                SortMode.TYPE -> "Type"
                                            }) },
                                            leadingIcon = {
                                                if (sort == mode) Icon(Icons.Rounded.Check, null)
                                            },
                                            onClick = { vm.setSort(mode); showSortMenu = false }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface),
                )

                // Breadcrumb
                BreadcrumbRow(breadcrumb = breadcrumb, onNavigate = { vm.loadDir(it) })

                // Barre de recherche
                SearchBar(query = search, onSearch = { vm.search(it) })
            }
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                AetherEmptyState(
                    icon    = Icons.Rounded.FolderOff,
                    title   = if (search.isBlank()) "Dossier vide" else "Aucun résultat",
                    message = if (search.isNotBlank()) "Aucun fichier ne correspond à \"$search\""
                              else "Ce dossier ne contient aucun fichier",
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(files, key = { it.path }) { entry ->
                    FileRow(
                        entry      = entry,
                        isSelected = selected.contains(entry.path),
                        onOpen     = { openFile(context, vm, entry) },
                        onLongPress = { vm.toggleSelect(entry.path) },
                        onShare    = { shareFile(context, vm, entry) },
                        onRename   = { renameTarget = entry },
                        onDelete   = { viewModelScope { vm.deleteSelected() } },
                        onNote     = { AetherIntents.shareToNotes(
                            context, entry.name,
                            "Fichier : ${entry.path}\nTaille : ${formatSize(entry.size)}")
                        },
                    )
                }
            }
        }
    }

    // Dialogue de renommage
    renameTarget?.let { f ->
        var newName by remember { mutableStateOf(f.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renommer") },
            text  = {
                OutlinedTextField(value = newName, onValueChange = { newName = it },
                    label = { Text("Nouveau nom") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) vm.rename(f, newName)
                    renameTarget = null
                }) { Text("Renommer") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Annuler") }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry:       AetherFile,
    isSelected:  Boolean,
    onOpen:      () -> Unit,
    onLongPress: () -> Unit,
    onShare:     () -> Unit,
    onRename:    () -> Unit,
    onDelete:    () -> Unit,
    onNote:      () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    androidx.compose.foundation.combinedClickable(
        onClick     = { if (isSelected) onLongPress() else onOpen() },
        onLongClick = onLongPress,
    ).let { modifier ->
        ListItem(
            headlineContent  = {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
                Text(
                    buildString {
                        if (!entry.isDir) { append(formatSize(entry.size)); append("  ·  ") }
                        append(SimpleDateFormat("dd/MM/yy  HH:mm", Locale.FRENCH).format(Date(entry.modified)))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingContent = {
                Icon(entry.icon, null, tint = if (isSelected)
                    MaterialTheme.colorScheme.primary else entry.iconColor,
                    modifier = Modifier.size(28.dp))
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, null, modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Partager") },
                            leadingIcon = { Icon(Icons.Rounded.Share, null) },
                            onClick = { onShare(); showMenu = false })
                        DropdownMenuItem(text = { Text("Note Aether") },
                            leadingIcon = { Icon(Icons.Rounded.NoteAdd, null,
                                tint = AetherColors.NotesAmber) },
                            onClick = { onNote(); showMenu = false })
                        DropdownMenuItem(text = { Text("Renommer") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { onRename(); showMenu = false })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null,
                                tint = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(); showMenu = false })
                    }
                }
            },
            modifier = Modifier.then(modifier).let {
                if (isSelected) it.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                else it
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
    HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun BreadcrumbRow(breadcrumb: List<File>, onNavigate: (File) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(breadcrumb) { dir ->
            Text(
                text     = if (dir == breadcrumb.first()) "📱" else dir.name,
                style    = MaterialTheme.typography.labelSmall,
                color    = if (dir == breadcrumb.last()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onNavigate(dir) }.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            if (dir != breadcrumb.last()) {
                Icon(Icons.Rounded.ChevronRight, null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onSearch: (String) -> Unit) {
    OutlinedTextField(
        value         = query,
        onValueChange = onSearch,
        placeholder   = { Text("Rechercher des fichiers…") },
        leadingIcon   = { Icon(Icons.Rounded.Search, null) },
        trailingIcon  = if (query.isNotBlank()) {{
            IconButton(onClick = { onSearch("") }) { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)) }
        }} else null,
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun PermissionScreen() {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.FolderOff, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
            Spacer(Modifier.height(16.dp))
            Text("Accès au stockage requis", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("AetherFiles a besoin d'accéder à votre stockage pour naviguer dans vos fichiers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }) { Text("Autoriser l'accès") }
        }
    }
}

private fun openFile(context: Context, vm: FilesViewModel, entry: AetherFile) {
    if (entry.isDir) { vm.loadDir(entry.file); return }
    val uri    = vm.run { FilesRepository(context).getContentUri(entry.file) }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, entry.mimeType)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

private fun shareFile(context: Context, vm: FilesViewModel, entry: AetherFile) {
    val uri    = FilesRepository(context).getContentUri(entry.file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type  = entry.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(Intent.createChooser(intent, "Partager ${entry.name}").apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    })
}

private fun viewModelScope(block: () -> Unit) = block()

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f Go".format(bytes / 1_073_741_824f)
    bytes >= 1_048_576     -> "%.1f Mo".format(bytes / 1_048_576f)
    bytes >= 1_024         -> "${bytes / 1024} Ko"
    else                   -> "$bytes o"
}

// ════════════════════════════════════════════════════════════════════════════
// ACTIVITY + APP
// ════════════════════════════════════════════════════════════════════════════

class FilesApp : Application()

class MainActivity : ComponentActivity() {
    private lateinit var vm: FilesViewModel

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { vm.loadDir(vm.currentDir.value) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application))[FilesViewModel::class.java]

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ))
        }

        // Naviguer directement vers un dossier depuis un intent BROWSE_DIR
        intent?.getStringExtra(AetherIntents.EXTRA_DIRECTORY)?.let { dirPath ->
            val dir = java.io.File(dirPath)
            if (dir.exists() && dir.isDirectory) vm.loadDir(dir)
        }

        setContent {
            AetherTheme {
                Surface(Modifier.fillMaxSize()) { FilesApp(vm) }
            }
        }
    }
}
