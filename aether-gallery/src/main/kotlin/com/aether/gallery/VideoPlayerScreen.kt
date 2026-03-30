package com.aether.gallery

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Lecteur vidéo plein écran basé sur ExoPlayer (Media3).
 *
 * Caractéristiques :
 *  - Lecture locale (URI MediaStore) sans réseau
 *  - Contrôles natifs (play/pause, barre de progression, plein écran)
 *  - Libération propre du player à la destruction du Composable
 *  - Croix de fermeture en haut à gauche
 */
@Composable
fun VideoPlayerScreen(uri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current

    // Créer et mémoriser le player — libéré automatiquement
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Vue ExoPlayer native
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true   // contrôles intégrés (play, barre…)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Bouton fermer
        IconButton(
            onClick  = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(.5f), androidx.compose.foundation.shape.CircleShape),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Fermer", tint = Color.White)
        }
    }
}
