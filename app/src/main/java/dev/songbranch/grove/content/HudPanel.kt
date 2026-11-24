package dev.songbranch.grove.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial

private val Gold = Color(0xFFFFC94D)
private val Cream = Color(0xFFF6E7C1)
private val Dusk = Color(0xCC1B2338)

const val RULES_TEXT =
    "Each bird shows z, zz or zzz: how many singing neighbours it needs before it joins in.\n" +
        "Select birds to place your whistles, then press Dawn.\n" +
        "Whistled birds sing first, and the song spreads along the branches, wave by wave.\n" +
        "Wake the whole grove. Roosters count as two singers, owls ignore whistles, larks sing from the start."

/** The control panel beside the grove: puzzle, whistles left, actions and the result card. */
@Composable
fun HudPanel(
    ui: GroveUi,
    onDawn: () -> Unit,
    onClear: () -> Unit,
    onTryAgain: () -> Unit,
    onNext: () -> Unit,
    onRules: () -> Unit,
    onSound: () -> Unit,
    onList: () -> Unit,
    onBird: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(460.dp)
            .clip(RoundedCornerShape(24.dp))
            .backgroundMaterial()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(ui.levelLabel.uppercase(), fontSize = 14.sp, color = Gold, fontWeight = FontWeight.Bold)
        Text(ui.title, style = PicoTheme.typography.titleLarge)
        Text(ui.subtitle, fontSize = 15.sp)

        val whistles = "Whistles: " + "●".repeat(ui.whistlesLeft) + "○".repeat((ui.whistlesTotal - ui.whistlesLeft).coerceAtLeast(0)) +
            "  (${ui.whistlesLeft} of ${ui.whistlesTotal} left)"
        Text(whistles, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(ui.hint, fontSize = 16.sp)
        ui.message?.let { Text(it, fontSize = 16.sp, color = Gold) }

        when (ui.phase) {
            UiPhase.Placing -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDawn, enabled = ui.whistlesLeft < ui.whistlesTotal || ui.whistlesTotal == 0) { Text("Dawn") }
                Button(onClick = onClear, enabled = ui.whistlesLeft < ui.whistlesTotal) { Text("Clear") }
            }
            UiPhase.Dawning -> Text("Dawn is breaking…", fontSize = 18.sp, color = Gold)
            // Compact result card, so a nine-bird list still fits in the panel underneath it.
            UiPhase.Won, UiPhase.Failed -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Dusk, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ui.phase == UiPhase.Won) {
                        Text("★".repeat(ui.stars) + "☆".repeat((3 - ui.stars).coerceAtLeast(0)), fontSize = 22.sp, color = Gold)
                    }
                    Text(ui.resultTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Cream)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (ui.waveSizes.isNotEmpty()) WaveBars(ui.waveSizes)
                    Button(onClick = onTryAgain) { Text(if (ui.phase == UiPhase.Won) "Play again" else "Try again") }
                }
                Text(ui.resultDetail, fontSize = 14.sp, color = Cream)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onNext, enabled = ui.phase != UiPhase.Dawning) { Text("Next grove") }
            Button(onClick = onRules) { Text(if (ui.showRules) "Hide rules" else "Rules") }
            Button(onClick = onSound) { Text(if (ui.muted) "Sound off" else "Sound on") }
        }
        if (ui.showRules) Text(RULES_TEXT, fontSize = 15.sp)

        Button(onClick = onList) { Text(if (ui.showList) "Hide bird list" else "Bird list") }
        if (ui.showList) {
            if (ui.phase == UiPhase.Placing) {
                Text("Select a bird to place or take back a whistle (same as selecting it in the grove).", fontSize = 14.sp)
            }
            ui.birdButtons.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { b ->
                        Button(
                            onClick = { onBird(b.id) },
                            enabled = ui.phase == UiPhase.Placing || ui.phase == UiPhase.Won || ui.phase == UiPhase.Failed,
                            modifier = Modifier.semantics { contentDescription = b.description },
                        ) { Text(b.label, fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

/** The song shape: one bar per dawn wave, height = birds that joined in that wave (spoiler-free, like the Reddit share text). */
@Composable
private fun WaveBars(sizes: List<Int>) {
    val max = (sizes.maxOrNull() ?: 1).coerceAtLeast(1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(36.dp),
    ) {
        sizes.forEach { n ->
            Box(
                Modifier
                    .width(14.dp)
                    .height((8 + 28 * n / max).dp)
                    .background(Gold, RoundedCornerShape(3.dp))
            )
        }
    }
}
