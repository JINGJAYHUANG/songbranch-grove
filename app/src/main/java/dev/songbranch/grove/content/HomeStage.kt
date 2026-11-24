package dev.songbranch.grove.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture

private const val PANEL_ID = "songbranch-panel"

/**
 * Songbranch Grove: a daily birdsong puzzle around the player.
 * One SpatialView holds the grove (birds, branches, whistle rings) and the control panel as an attachment.
 */
@Composable
fun HomeStage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val game = remember { GroveGame(context.applicationContext) }

    DisposableEffect(Unit) { onDispose { game.dispose() } }

    SpatialView(
        modifier = Modifier.pointerInput(Unit) {
            detectSpatialTapGesture(context = context, targetedToEntity = TargetEntity.any()) { tap ->
                tap.targetEntity?.let { game.onTap(it) }
            }
        },
        attachments = {
            AttachmentPanel(PANEL_ID) {
                PicoTheme {
                    HudPanel(
                        ui = game.ui,
                        onDawn = { game.dawn(scope) },
                        onClear = { game.clearWhistles() },
                        onTryAgain = { game.tryAgain() },
                        onNext = { game.nextLevel() },
                        onRules = { game.toggleRules() },
                        onSound = { game.toggleSound() },
                        onList = { game.toggleList() },
                        onBird = { game.tapBird(it) },
                    )
                }
            }
        },
        initial = { content, attachments ->
            content.addEntity(game.root)
            game.start()
            attachments.entity(PANEL_ID)?.apply {
                val (position, angles) = game.panelPose()
                components[TransformComponent::class.java]?.apply {
                    this.position = position
                    eulerAngles = angles
                }
                content.addEntity(this)
            }
        },
    )
}
