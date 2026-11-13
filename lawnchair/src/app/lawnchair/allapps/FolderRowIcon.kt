package app.lawnchair.allapps

import android.content.Context
import android.util.AttributeSet
import android.graphics.drawable.GradientDrawable
import app.lawnchair.util.getDrawerFolderRowCorner
import com.android.launcher3.folder.FolderIcon

/**
 * Perso : dossier du tiroir affiché comme un simple rectangle de texte.
 * L'aperçu rond des apps n'est jamais dessiné, même après la fermeture du dossier.
 */
class FolderRowIcon(context: Context, attrs: AttributeSet?) : FolderIcon(context, attrs) {
    override fun setIconVisible(visible: Boolean) {
        super.setIconVisible(false)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // L'animation d'ouverture du dossier a besoin des mesures de l'aperçu, que
        // FolderIcon ne calcule qu'en dessinant l'aperçu (ici jamais dessiné).
        previewItemManager.recomputePreviewDrawingParams()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Arrondi réglable : proportion de la demi-hauteur du rectangle
        (background as? GradientDrawable)?.cornerRadius = getDrawerFolderRowCorner(context) * h / 2f
    }
}
