package dev.songbranch.grove.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialTextureParameter
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.Resource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial

/**
 * Small text labels drawn into bitmaps once and shown on planes above the birds:
 * z / zz / zzz for how many singing neighbours wake a bird, x2 for a rooster, and a mark for owls and larks.
 * All birds share these few textures and materials.
 */
class Labels(private val keep: (Resource) -> Unit) {
    val plane: MeshResource = MeshResource.createPlane(0.12f, 0.052f).also(keep)
    private val cache = HashMap<String, UnlitMaterial>()

    fun materialFor(text: String): UnlitMaterial = cache.getOrPut(text) {
        val bitmap = Bitmap.createBitmap(256, 112, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(230, 30, 24, 40)
            textSize = 78f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        val fill = Paint(outline).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val y = 82f
        canvas.drawText(text, 128f, y, outline)
        canvas.drawText(text, 128f, y, fill)
        val texture = TextureResource.create(bitmap).also(keep)
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColorTextureParameter(MaterialTextureParameter(texture))
        }.also(keep)
    }

    companion object {
        fun needText(need: Int): String = "z".repeat(need.coerceIn(1, 3))
    }
}
