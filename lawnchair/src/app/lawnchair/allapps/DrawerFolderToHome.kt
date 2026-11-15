package app.lawnchair.allapps

import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemFactory
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * Perso : appui long sur un rectangle de dossier du tiroir → menu « Ajouter au bureau »,
 * qui recrée le dossier (même nom, mêmes apps, même ordre) sur l'écran d'accueil.
 */
object DrawerFolderToHome {

    @JvmStatic
    fun showMenu(anchor: View, drawerFolder: FolderInfo): Boolean {
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val menu = PopupMenu(anchor.context, anchor)
        menu.menu.add(0, 1, 0, "Ajouter au bureau")
        menu.setOnMenuItemClickListener {
            addToHome(anchor, drawerFolder)
            true
        }
        menu.show()
        return true
    }

    private fun addToHome(anchor: View, drawerFolder: FolderInfo) {
        val launcher = Launcher.getLauncher(anchor.context)
        val workspace = launcher.workspace
        val cell = IntArray(2)
        var target: Pair<Int, com.android.launcher3.CellLayout>? = null
        val order = workspace.screenOrder
        for (i in 0 until order.size()) {
            val screenId = order[i]
            if (screenId < 0) continue // pages vides temporaires
            val layout = workspace.getScreenWithId(screenId) ?: continue
            if (layout.findCellForSpan(cell, 1, 1)) {
                target = screenId to layout
                break
            }
        }
        val title = drawerFolder.title?.toString().orEmpty()
        if (target == null) {
            Toast.makeText(launcher, "Plus de place sur l'écran d'accueil", Toast.LENGTH_SHORT).show()
            return
        }
        val (screenId, layout) = target
        val icon = launcher.addFolder(
            layout, LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId, cell[0], cell[1],
        )
        val homeFolder = icon.folder
        drawerFolder.getContents().forEachIndexed { index, item ->
            val workspaceItem: WorkspaceItemInfo? = when (item) {
                is WorkspaceItemFactory -> item.makeWorkspaceItem(launcher)
                else -> null
            }
            if (workspaceItem != null) homeFolder.addFolderContent(workspaceItem, index, false)
        }
        (icon.tag as? FolderInfo)?.setTitle(title, launcher.modelWriter)
        icon.onTitleChanged(title)
        Toast.makeText(launcher, "« $title » ajouté à l'écran d'accueil", Toast.LENGTH_SHORT).show()
    }
}
