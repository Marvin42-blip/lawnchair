package app.lawnchair.allapps

import android.content.Context
import android.util.AttributeSet
import com.android.launcher3.folder.FolderIcon

/**
 * Perso : dossier du tiroir affiché comme un simple rectangle de texte.
 * L'aperçu rond des apps n'est jamais dessiné, même après la fermeture du dossier.
 */
class FolderRowIcon(context: Context, attrs: AttributeSet?) : FolderIcon(context, attrs) {
    override fun setIconVisible(visible: Boolean) {
        super.setIconVisible(false)
    }
}
