package dev.j3pf.rp.ui

import android.app.Application
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import dev.j3pf.rp.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * La marca es un activo, no comportamiento: lo que puede romperse sin que nadie lo note es el
 * cableado (un icono adaptativo sin capa de fondo, un drawable renombrado, la silueta de la
 * barra de estado desaparecida). Esto pinta las tres ranuras en las que aparece la marca.
 *
 * Geometría y decisiones: design/rp-icon/01-caret-nodo.html y odd/tasks/logo-rebrand.md.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrandingAssetsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `el icono adaptativo tiene foreground y background`() {
        val icon = context.getDrawable(R.mipmap.ic_launcher)
        assertIs<AdaptiveIconDrawable>(icon, "ic_launcher debe ser un icono adaptativo (capa foreground + background)")
        assertNotNull(icon.foreground, "falta la capa foreground (la marca)")
        assertNotNull(icon.background, "falta la capa background")
    }

    @Test
    fun `la marca y la silueta de la barra de estado se pueden pintar`() {
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground), "falta el drawable de la marca")
        assertNotNull(context.getDrawable(R.drawable.ic_stat_rp), "falta el icono de la barra de estado")
    }
}
