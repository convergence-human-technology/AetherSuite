package com.aether.contacts

import android.app.Application
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

// ════════════════════════════════════════════════════════════════════════════
// MODEL
// ════════════════════════════════════════════════════════════════════════════

data class AetherContact(
    val id:           Long   = 0L,
    val displayName:  String = "",
    val givenName:    String = "",
    val familyName:   String = "",
    val phones:       List<ContactPhone>  = emptyList(),
    val emails:       List<ContactEmail>  = emptyList(),
    val photoUri:     String? = null,
    val organization: String = "",
    val notes:        String = "",
    val isFavorite:   Boolean = false,
) {
    val initials: String get() = displayName.split(" ").filter { it.isNotBlank() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    val firstPhone: String get() = phones.firstOrNull()?.number ?: ""
    val firstEmail: String get() = emails.firstOrNull()?.address ?: ""
}

data class ContactPhone(val number: String, val type: String = "Mobile")
data class ContactEmail(val address: String, val type: String = "Personnel")

// ════════════════════════════════════════════════════════════════════════════
// REPOSITORY
// ════════════════════════════════════════════════════════════════════════════

class ContactsRepository(private val context: Context) {

    suspend fun loadAll(): List<AetherContact> = withContext(Dispatchers.IO) {
        val contacts = mutableMapOf<Long, AetherContact>()

        // Noms
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, null, null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id   = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)) ?: ""
                val fav  = c.getInt(c.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)) == 1
                val photo = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI))
                contacts[id] = AetherContact(id = id, displayName = name, isFavorite = fav, photoUri = photo)
            }
        }

        // Téléphones
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val cid    = c.getLong(0)
                val number = c.getString(1) ?: continue
                val type   = when (c.getInt(2)) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME   -> "Domicile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK   -> "Travail"
                    else -> "Autre"
                }
                contacts[cid] = contacts[cid]?.copy(
                    phones = contacts[cid]!!.phones + ContactPhone(number, type)
                ) ?: return@use
            }
        }

        // Emails
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val cid     = c.getLong(0)
                val address = c.getString(1) ?: continue
                val type    = when (c.getInt(2)) {
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME    -> "Personnel"
                    ContactsContract.CommonDataKinds.Email.TYPE_WORK    -> "Professionnel"
                    else -> "Autre"
                }
                contacts[cid] = contacts[cid]?.copy(
                    emails = contacts[cid]!!.emails + ContactEmail(address, type)
                ) ?: return@use
            }
        }

        contacts.values.filter { it.displayName.isNotBlank() }.sortedBy { it.displayName }
    }

    suspend fun save(contact: AetherContact): Long = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()
        val rawIdx = 0

        if (contact.id == 0L) {
            // Nouveau contact
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build())

            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                .build())

            contact.phones.forEach { phone ->
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                    .build())
            }

            contact.emails.forEach { email ->
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME)
                    .build())
            }

            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            results[0].uri?.lastPathSegment?.toLongOrNull() ?: 0L
        } else {
            // Mise à jour — update le nom affiché
            context.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                ContentValues().apply {
                    put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                },
                "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                arrayOf(contact.id.toString(),
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            )
            contact.id
        }
    }

    suspend fun delete(contactId: Long) = withContext(Dispatchers.IO) {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
        context.contentResolver.delete(uri, null, null)
    }

    suspend fun toggleFavorite(contact: AetherContact) = withContext(Dispatchers.IO) {
        context.contentResolver.update(
            ContactsContract.Contacts.CONTENT_URI,
            ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (contact.isFavorite) 0 else 1)
            },
            "${ContactsContract.Contacts._ID}=?",
            arrayOf(contact.id.toString())
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// VIEWMODEL
// ════════════════════════════════════════════════════════════════════════════

class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ContactsRepository(app)

    private val _contacts = MutableStateFlow<List<AetherContact>>(emptyList())
    private val _search   = MutableStateFlow("")
    private val _loading  = MutableStateFlow(true)
    private val _selected = MutableStateFlow<AetherContact?>(null)
    private val _editMode = MutableStateFlow(false)

    val loading:  StateFlow<Boolean>          = _loading.asStateFlow()
    val selected: StateFlow<AetherContact?>   = _selected.asStateFlow()
    val editMode: StateFlow<Boolean>          = _editMode.asStateFlow()
    val search:   StateFlow<String>           = _search.asStateFlow()

    val contacts: StateFlow<List<AetherContact>> = combine(_contacts, _search) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.displayName.contains(q, true) ||
            it.firstPhone.contains(q) ||
            it.firstEmail.contains(q, true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<AetherContact>> = _contacts.map { it.filter { c -> c.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alphabet: StateFlow<List<Char>> = _contacts.map { list ->
        list.mapNotNull { it.displayName.firstOrNull()?.uppercaseChar() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        _contacts.value = repo.loadAll()
        _loading.value = false
    }

    fun setSearch(q: String)                { _search.value = q }
    fun select(c: AetherContact?)           { _selected.value = c }
    fun setEditMode(v: Boolean)             { _editMode.value = v }

    fun save(contact: AetherContact) = viewModelScope.launch {
        repo.save(contact)
        load()
        _editMode.value = false
    }

    fun delete(contact: AetherContact) = viewModelScope.launch {
        repo.delete(contact.id)
        _selected.value = null
        load()
    }

    fun toggleFavorite(contact: AetherContact) = viewModelScope.launch {
        repo.toggleFavorite(contact)
        load()
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ÉCRANS
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsApp(vm: ContactsViewModel) {
    val contacts  by vm.contacts.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val alphabet  by vm.alphabet.collectAsStateWithLifecycle()
    val loading   by vm.loading.collectAsStateWithLifecycle()
    val selected  by vm.selected.collectAsStateWithLifecycle()
    val editMode  by vm.editMode.collectAsStateWithLifecycle()
    val search    by vm.search.collectAsStateWithLifecycle()
    val ctx       = LocalContext.current
    var showNew   by remember { mutableStateOf(false) }

    // Détail d'un contact sélectionné
    if (selected != null && !editMode) {
        ContactDetailScreen(
            contact     = selected!!,
            onBack      = { vm.select(null) },
            onEdit      = { vm.setEditMode(true) },
            onDelete    = { vm.delete(it); vm.select(null) },
            onCall      = { AetherIntents.dialNumber(ctx, it) },
            onSms       = { AetherIntents.sendSms(ctx, it) },
            onFavorite  = { vm.toggleFavorite(it) },
        )
        return
    }

    if (editMode && selected != null) {
        ContactEditScreen(
            contact = selected!!,
            onSave  = { vm.save(it) },
            onCancel = { vm.setEditMode(false) },
        )
        return
    }

    if (showNew) {
        ContactEditScreen(
            contact  = AetherContact(),
            onSave   = { vm.save(it); showNew = false },
            onCancel = { showNew = false },
        )
        return
    }

    Scaffold(
        topBar = {
            AetherTopBar(
                title          = "Contacts",
                searchable     = true,
                searchQuery    = search,
                onSearchChange = vm::setSearch,
            )
        },
        floatingActionButton = {
            AetherFab(onClick = { showNew = true }, label = "Nouveau") {
                Icon(Icons.Rounded.PersonAdd, null)
            }
        },
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                AetherEmptyState(
                    icon    = Icons.Rounded.People,
                    title   = "Aucun contact",
                    message = "Appuyez sur + pour ajouter votre premier contact",
                    action  = "Nouveau contact" to { showNew = true },
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 80.dp)) {
                // Favoris
                if (favorites.isNotEmpty() && search.isBlank()) {
                    item {
                        Text("Favoris", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    item {
                        LazyRow(
                            contentPadding      = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(favorites) { c ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { vm.select(c) }
                                ) {
                                    AetherAvatar(c.displayName, c.photoUri, 52.dp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(c.displayName.split(" ").firstOrNull() ?: "",
                                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                    }
                }

                // Liste alphabétique
                var lastLetter = ' '
                items(contacts, key = { it.id }) { contact ->
                    val letter = contact.displayName.firstOrNull()?.uppercaseChar() ?: '?'
                    if (letter != lastLetter) {
                        lastLetter = letter
                        Text(letter.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    ContactRow(contact = contact, onClick = { vm.select(contact) })
                }
            }
        }
    }
}

@Composable
fun ContactRow(contact: AetherContact, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AetherAvatar(contact.displayName, contact.photoUri)
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, style = MaterialTheme.typography.bodyLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (contact.firstPhone.isNotBlank()) {
                Text(contact.firstPhone, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (contact.isFavorite) {
            Icon(Icons.Rounded.Star, null, tint = AetherColors.NotesAmber, modifier = Modifier.size(16.dp))
        }
    }
    HorizontalDivider(Modifier.padding(start = 74.dp), thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contact:    AetherContact,
    onBack:     () -> Unit,
    onEdit:     () -> Unit,
    onDelete:   (AetherContact) -> Unit,
    onCall:     (String) -> Unit,
    onSms:      (String) -> Unit,
    onFavorite: (AetherContact) -> Unit,
) {
    val ctx = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBackIosNew, "Retour") } },
                title = {},
                actions = {
                    IconButton(onClick = { onFavorite(contact) }) {
                        Icon(if (contact.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            null, tint = if (contact.isFavorite) AetherColors.NotesAmber
                                         else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Modifier") }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Rounded.Delete, "Supprimer", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            // En-tête
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AetherAvatar(contact.displayName, contact.photoUri, 88.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(contact.displayName, style = MaterialTheme.typography.headlineMedium)
                    if (contact.organization.isNotBlank()) {
                        Text(contact.organization, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Actions rapides inter-apps
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (contact.firstPhone.isNotBlank()) {
                            AetherAppChip("Appeler", Icons.Rounded.Call, AetherColors.PhoneGreen) {
                                onCall(contact.firstPhone)
                            }
                            AetherAppChip("SMS", Icons.Rounded.Message, AetherColors.SmsViolet) {
                                onSms(contact.firstPhone)
                            }
                        }
                        AetherAppChip("Note", Icons.Rounded.Note, AetherColors.NotesAmber) {
                            AetherIntents.shareToNotes(ctx, contact.displayName, "Contact : ${contact.displayName}\n${contact.firstPhone}")
                        }
                    }
                }
                HorizontalDivider()
            }
            // Téléphones
            contact.phones.forEach { phone ->
                item {
                    InfoRow(Icons.Rounded.Phone, phone.type, phone.number,
                        onClick = { onCall(phone.number) })
                }
            }
            // Emails
            contact.emails.forEach { email ->
                item { InfoRow(Icons.Rounded.Email, email.type, email.address) }
            }
            // Notes
            if (contact.notes.isNotBlank()) {
                item { InfoRow(Icons.Rounded.Note, "Notes", contact.notes) }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon  = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Supprimer ${contact.displayName} ?") },
            text  = { Text("Ce contact sera définitivement supprimé du téléphone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(contact); showDelete = false }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String, value: String, onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
    HorizontalDivider(Modifier.padding(start = 54.dp), thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.surfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    contact:  AetherContact,
    onSave:   (AetherContact) -> Unit,
    onCancel: () -> Unit,
) {
    var name  by remember { mutableStateOf(contact.displayName) }
    var phone by remember { mutableStateOf(contact.firstPhone) }
    var email by remember { mutableStateOf(contact.firstEmail) }
    var org   by remember { mutableStateOf(contact.organization) }
    var notes by remember { mutableStateOf(contact.notes) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Annuler") } },
                title = { Text(if (contact.id == 0L) "Nouveau contact" else "Modifier") },
                actions = {
                    TextButton(
                        onClick  = {
                            if (name.isNotBlank()) onSave(contact.copy(
                                displayName  = name.trim(),
                                phones       = if (phone.isNotBlank()) listOf(ContactPhone(phone)) else contact.phones,
                                emails       = if (email.isNotBlank()) listOf(ContactEmail(email)) else contact.emails,
                                organization = org.trim(),
                                notes        = notes.trim(),
                            ))
                        },
                        enabled = name.isNotBlank(),
                    ) { Text("Enregistrer") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            item { AetherAvatar(name.ifBlank { "?" }, null, 72.dp) }

            item { EditField(Icons.Rounded.Person, "Nom complet *", name, { name = it },
                KeyboardType.Text, ImeAction.Next, KeyboardCapitalization.Words) }
            item { EditField(Icons.Rounded.Phone, "Téléphone", phone, { phone = it },
                KeyboardType.Phone, ImeAction.Next) }
            item { EditField(Icons.Rounded.Email, "E-mail", email, { email = it },
                KeyboardType.Email, ImeAction.Next) }
            item { EditField(Icons.Rounded.Business, "Organisation", org, { org = it },
                ImeAction = ImeAction.Next) }
            item { EditField(Icons.Rounded.Note, "Notes", notes, { notes = it },
                ImeAction = ImeAction.Done, multiline = true) }
        }
    }
}

@Composable
private fun EditField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    ImeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    multiline: Boolean = false,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        leadingIcon   = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        singleLine    = !multiline,
        maxLines      = if (multiline) 4 else 1,
        shape         = RoundedCornerShape(12.dp),
        modifier      = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType   = keyboardType,
            imeAction      = ImeAction,
            capitalization = capitalization,
        ),
    )
}

// ════════════════════════════════════════════════════════════════════════════
// ACTIVITY + APPLICATION
// ════════════════════════════════════════════════════════════════════════════

class ContactsApp : Application()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[ContactsViewModel::class.java]

        // Gérer les intents entrants (VIEW_CONTACT, CREATE_CONTACT)
        when (intent?.action) {
            AetherIntents.ACTION_CREATE_CONTACT -> {
                val phone = intent.getStringExtra(AetherIntents.EXTRA_PHONE_NUMBER) ?: ""
                if (phone.isNotBlank()) {
                    // Pré-remplir le formulaire via un intent → géré dans l'UI via un état
                }
            }
        }

        setContent {
            AetherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ContactsApp(vm = vm)
                }
            }
        }
    }
}
