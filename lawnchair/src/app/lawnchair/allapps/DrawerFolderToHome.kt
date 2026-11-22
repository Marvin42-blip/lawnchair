package app.lawnchair.allapps

import android.view.View
import android.widget.Toast
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.WorkspaceItemFactory
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator

/**
 * Perso : « Ajouter au bureau » sur un rectangle de dossier du tiroir. Recrée le dossier
 * (même nom, mêmes applis, même ordre) sur l'écran d'accueil.
 */
object DrawerFolderToHome {

    @JvmStatic
    fun addToHome(anchor: View, drawerFolder: FolderInfo) {
        val launcher = Launcher.getLauncher(anchor.context)
        val title = drawerFolder.title?.toString().orEmpty()
        val apps = drawerFolder.getContents()

        // Une seule appli : pas de dossier, l'appli elle-même va sur l'écran d'accueil
        if (apps.size == 1) {
            val app = apps.first() as? AppInfo
            val component = app?.targetComponent
            if (component != null) {
                ItemInstallQueue.INSTANCE.get(launcher).queueItem(component.packageName, app.user)
                toast(launcher, launcher.getString(R.string.perso_copied_to_home, app.title))
            }
            return
        }

        // Déjà un dossier du même nom sur l'écran d'accueil : pas de doublon
        var exists = false
        launcher.workspace.mapOverItems(
            ItemOperator { info, view ->
                if (view is FolderIcon && info is FolderInfo &&
                    info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                    info.title?.toString() == title
                ) {
                    exists = true
                }
                exists
            },
        )
        if (exists) {
            toast(launcher, launcher.getString(R.string.perso_folder_already_on_home, title))
            return
        }

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
        if (target == null) {
            toast(launcher, launcher.getString(R.string.perso_home_full))
            return
        }
        val (screenId, layout) = target
        val icon = launcher.addFolder(
            layout, LauncherSettings.Favorites.CONTAINER_DESKTOP, screenId, cell[0], cell[1],
        )
        val homeFolder = icon.folder
        apps.forEachIndexed { index, item ->
            val workspaceItem: WorkspaceItemInfo? = (item as? WorkspaceItemFactory)?.makeWorkspaceItem(launcher)
            if (workspaceItem != null) homeFolder.addFolderContent(workspaceItem, index, false)
        }
        (icon.tag as? FolderInfo)?.setTitle(title, launcher.modelWriter)
        icon.onTitleChanged(title)
        toast(launcher, launcher.getString(R.string.perso_folder_added_to_home, title))
    }

    private fun toast(launcher: Launcher, text: String) =
        Toast.makeText(launcher, text, Toast.LENGTH_SHORT).show()
}
