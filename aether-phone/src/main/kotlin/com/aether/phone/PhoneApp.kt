package com.aether.phone

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import java.text.SimpleDateFormat
import java.util.*

// ════════════════════════════════════════════════════════════════════════════
// MODEL
// ════════════════════════════════════════════════════════════════════════════

data class CallLogEntry(
    val id:          Long,
    val number:      String,
    val contactName: String?,
    val type:        CallType,
    val date:        Long,
    val duration:    Long,   // secondes
    val photoUri:    String? = null,
) {
    val displayName: String get() = contactName?.takeIf { it.isNotBlank() } ?: number
    val isAnswered: Boolean get() = type == CallType.INCOMING || type == CallType.OUTGOING
}

enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED }

// ════════════════════════════════════════════════════════════════════════════
// REPOSITORY
// ════════════════════════════════════════════════════════════════════════════

class PhoneRepository(private val context: Context) {

    suspend fun loadCallLog(limit: Int = 100): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<CallLogEntry>()
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return@withContext entries

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
                    CallLog.Calls.CACHED_PHOTO_URI),
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )?.use { c ->
            while (c.moveToNext()) {
                val type = when (c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE))) {
                    CallLog.Calls.INCOMING_TYPE  -> CallType.INCOMING
                    CallLog.Calls.OUTGOING_TYPE  -> CallType.OUTGOING
                    CallLog.Calls.MISSED_TYPE    -> CallType.MISSED
                    CallLog.Calls.REJECTED_TYPE  -> CallType.REJECTED
                    else                         -> CallType.INCOMING
                }
                entries.add(CallLogEntry(
                    id          = c.getLong(0),
                    number      = c.getString(1) ?: "",
                    contactName = c.getString(2),
                    type        = type,
                    date        = c.getLong(4),
                    duration    = c.getLong(5),
                    photoUri    = c.getString(6),
                ))
            }
        }
        entries
    }

    fun lookupContact(context: Context, number: String): Pair<String?, String?> {
        var name: String? = null
        var photo: String? = null
        runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    name  = c.getString(0)
                    photo = c.getString(1)
                }
            }
        }
        return name to photo
    }

    fun makeCall(context: Context, number: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_CALL,
            Uri.parse("tel:$number")).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }

    suspend fun deleteCallLogEntry(id: Long) = withContext(Dispatchers.IO) {
        context.contentResolver.delete(
            CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id.toString())
        )
    }

    suspend fun clearAllCallLog() = withContext(Dispatchers.IO) {
        context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
    }
}

// ════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ════════════════════════════════════════════════════════════════════════════

class PhoneViewModel(app: Application) : AndroidViewModel(app) {
    private val repo     = PhoneRepository(app)
    private val _calls   = MutableStateFlow<List<CallLogEntry>>(emptyList())
    private val _loading = MutableStateFlow(true)
    private val _dialPad = MutableStateFlow("")
    private val _tab     = MutableStateFlow(0)  // 0=clavier 1=récents
    private val _search  = MutableStateFlow("")

    val loading: StateFlow<Boolean>            = _loading.asStateFlow()
    val dialPad: StateFlow<String>             = _dialPad.asStateFlow()
    val tab:     StateFlow<Int>                = _tab.asStateFlow()
    val search:  StateFlow<String>             = _search.asStateFlow()

    val calls: StateFlow<List<CallLogEntry>> = combine(_calls, _search) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.displayName.contains(q, true) || it.number.contains(q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val missed: StateFlow<Int> = _calls.map { it.count { c -> c.type == CallType.MISSED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init { loadCalls() }

    fun loadCalls() = viewModelScope.launch {
        _loading.value = true
        _calls.value = repo.loadCallLog()
        _loading.value = false
    }
    fun appendDigit(d: String)  { if (_dialPad.value.length < 15) _dialPad.value += d }
    fun backspace()              { if (_dialPad.value.isNotEmpty()) _dialPad.value = _dialPad.value.dropLast(1) }
    fun setTab(t: Int)          { _tab.value = t }
    fun setSearch(q: String)    { _search.value = q }
    fun setDialPad(n: String)   { _dialPad.value = n }
    fun clearDialPad()          { _dialPad.value = "" }
    fun deleteSingle(id: Long)  = viewModelScope.launch { repo.deleteCallLogEntry(id); loadCalls() }
    fun clearAll()              = viewModelScope.launch { repo.clearAllCallLog(); loadCalls() }
}

// ════════════════════════════════════════════════════════════════════════════
// UI
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneApp(vm: PhoneViewModel) {
    val tab     by vm.tab.collectAsStateWithLifecycle()
    val missed  by vm.missed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick  = { vm.setTab(0) },
                    icon     = { Icon(Icons.Rounded.Dialpad, null) },
                    label    = { Text("Clavier") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick  = { vm.setTab(1) },
                    icon     = {
                        BadgedBox(badge = {
                            if (missed > 0) Badge { Text(missed.toString()) }
                        }) { Icon(Icons.Rounded.History, null) }
                    },
                    label    = { Text("Récents") },
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> DialerScreen(vm = vm)
                1 -> CallLogScreen(vm = vm)
            }
        }
    }
}

@Composable
fun DialerScreen(vm: PhoneViewModel) {
    val context = LocalContext.current
    val dialPad by vm.dialPad.collectAsStateWithLifecycle()
    val repo    = remember { PhoneRepository(context) }
    val (contactName, contactPhoto) = remember(dialPad) {
        if (dialPad.length >= 3) repo.lookupContact(context, dialPad)
        else null to null
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        // Numéro saisi
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), Alignment.Center) {
            Text(
                text  = dialPad.ifBlank { " " },
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
            )
        }

        // Nom du contact si trouvé
        AnimatedVisibility(visible = contactName != null) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                AetherAvatar(contactName ?: "", contactPhoto, 28.dp, AetherColors.ContactsTeal.copy(.2f))
                Text(contactName ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Pavé numérique
        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to ""),
        )
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { (digit, letters) ->
                    DialKey(digit = digit, letters = letters, onClick = { vm.appendDigit(digit) })
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(12.dp))

        // Ligne actions
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // SMS si numéro saisi
            AnimatedVisibility(visible = dialPad.isNotBlank()) {
                IconButton(onClick = { AetherIntents.sendSms(context, dialPad) }) {
                    Icon(Icons.Rounded.Message, "SMS", tint = AetherColors.SmsViolet,
                        modifier = Modifier.size(28.dp))
                }
            }
            if (dialPad.isBlank()) Spacer(Modifier.size(48.dp))

            // Bouton appel
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(AetherColors.PhoneGreen)
                    .clickable {
                        if (dialPad.isNotBlank()) repo.makeCall(context, dialPad)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Call, "Appeler", tint = Color.White,
                    modifier = Modifier.size(32.dp))
            }

            // Effacer
            AnimatedVisibility(visible = dialPad.isNotBlank()) {
                IconButton(onClick = { vm.backspace() }) {
                    Icon(Icons.Rounded.Backspace, "Effacer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp))
                }
            }
            if (dialPad.isBlank()) Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DialKey(digit: String, letters: String, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digit, style = MaterialTheme.typography.headlineMedium)
            if (letters.isNotBlank()) {
                Text(letters, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogScreen(vm: PhoneViewModel) {
    val context = LocalContext.current
    val calls   by vm.calls.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val search  by vm.search.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AetherTopBar(
                title          = "Récents",
                searchable     = true,
                searchQuery    = search,
                onSearchChange = vm::setSearch,
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Rounded.DeleteSweep, "Effacer tout",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (calls.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                AetherEmptyState(
                    icon    = Icons.Rounded.PhoneDisabled,
                    title   = "Aucun appel récent",
                    message = "Votre journal d'appels est vide",
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(calls, key = { it.id }) { entry ->
                    CallLogRow(entry = entry,
                        onCall     = { vm.setTab(0); vm.setDialPad(entry.number) },
                        onSms      = { AetherIntents.sendSms(context, entry.number) },
                        onContact  = { AetherIntents.createContactFromNumber(context, entry.number) },
                        onDelete   = { vm.deleteSingle(entry.id) },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Effacer tout le journal ?") },
            text  = { Text("Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClearDialog = false }) {
                    Text("Effacer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun CallLogRow(
    entry:     CallLogEntry,
    onCall:    () -> Unit,
    onSms:     () -> Unit,
    onContact: () -> Unit,
    onDelete:  () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val typeColor = when (entry.type) {
        CallType.MISSED, CallType.REJECTED -> MaterialTheme.colorScheme.error
        CallType.INCOMING -> AetherColors.PhoneGreen
        CallType.OUTGOING -> MaterialTheme.colorScheme.primary
    }
    val typeIcon = when (entry.type) {
        CallType.INCOMING -> Icons.Rounded.CallReceived
        CallType.OUTGOING -> Icons.Rounded.CallMade
        CallType.MISSED   -> Icons.Rounded.PhoneMissed
        CallType.REJECTED -> Icons.Rounded.PhoneDisabled
    }

    Column {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AetherAvatar(entry.displayName, entry.photoUri,
                accentColor = MaterialTheme.colorScheme.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(entry.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (entry.type == CallType.MISSED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(typeIcon, null, modifier = Modifier.size(13.dp), tint = typeColor)
                    Text(formatCallDate(entry.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.duration > 0) {
                        Text("· ${formatDuration(entry.duration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onCall, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Call, "Rappeler", tint = AetherColors.PhoneGreen)
            }
        }

        // Expansion : actions détaillées
        AnimatedVisibility(visible = expanded) {
            Row(
                Modifier.fillMaxWidth().padding(start = 74.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AetherAppChip("SMS", Icons.Rounded.Message, AetherColors.SmsViolet, onSms)
                if (entry.contactName == null) {
                    AetherAppChip("Ajouter contact", Icons.Rounded.PersonAdd, AetherColors.ContactsTeal, onContact)
                }
                AetherAppChip("Supprimer", Icons.Rounded.Delete, MaterialTheme.colorScheme.error, onDelete)
            }
        }

        HorizontalDivider(Modifier.padding(start = 74.dp), thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

private fun formatCallDate(ts: Long): String {
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DATE) == then.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
        else -> SimpleDateFormat("dd/MM · HH:mm", Locale.getDefault()).format(Date(ts))
    }
}

private fun formatDuration(secs: Long): String = when {
    secs < 60   -> "${secs}s"
    secs < 3600 -> "${secs / 60}min ${secs % 60}s"
    else        -> "${secs / 3600}h ${(secs % 3600) / 60}min"
}

// ════════════════════════════════════════════════════════════════════════════
// ACTIVITY + APP
// ════════════════════════════════════════════════════════════════════════════

class PhoneApp : Application()

class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { vm.loadCalls() }
    private lateinit var vm: PhoneViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[PhoneViewModel::class.java]
        permLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.CALL_PHONE))

        // Numéro pré-rempli depuis un Intent DIAL
        intent?.data?.schemeSpecificPart?.let { number ->
            vm.setTab(0)
            vm.setDialPad(number.removePrefix("//"))
        }

        setContent {
            AetherTheme {
                Surface(Modifier.fillMaxSize()) { PhoneApp(vm) }
            }
        }
    }
}
