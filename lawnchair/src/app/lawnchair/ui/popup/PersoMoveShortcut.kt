package app.lawnchair.ui.popup

import android.app.AlertDialog
import android.os.Process
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.folder.FolderEntry
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.ReloadHelper
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.R
import com.android.launcher3.model.ItemInstallQueue
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Perso : « Déplacer vers… » dans le menu d'appui long d'une appli.
 *
 * Depuis le tiroir (liste ou dossier du tiroir) : vers un autre dossier du tiroir,
 * hors dossier, ou une copie sur le bureau.
 * Depuis le bureau (écran, dock ou dossier du bureau) : vers un dossier du tiroir,
 * ou retrait du dossier du bureau.
 */
class PersoMoveShortcut(
    launcher: LawnchairLauncher,
    itemInfo: ItemInfo,
    originalView: View,
) : SystemShortcut<LawnchairLauncher>(
    R.drawable.ic_perso_move,
    R.string.perso_move_to,
    launcher,
    itemInfo,
    originalView,
) {

    private class Choice(val label: String, val action: () -> Unit)

    override fun onClick(v: View) {
        val launcher = mTarget
        val info = mItemInfo
        val view = mOriginalView
        AbstractFloatingView.closeAllOpenViews(launcher)
        launcher.lifecycleScope.launch {
            val service = FolderService.INSTANCE.get(launcher)
            val folders = service.getFoldersFlow().first()
            showChoices(launcher, info, view, folders, service)
        }
    }

    private fun showChoices(
        launcher: LawnchairLauncher,
        info: ItemInfo,
        view: View,
        folders: List<FolderEntry>,
        service: FolderService,
    ) {
        val component = info.targetComponent ?: return
        val key = ComponentKey(component, info.user)
        val keyString = key.toString()
        fun FolderEntry.contains() = itemComponentKeys.any { ComponentKey.fromString(it) == key }

        val fromDrawer = info is com.android.launcher3.model.data.AppInfo
        val appName = info.title?.toString().orEmpty()
        val choices = mutableListOf<Choice>()

        fun moveTo(target: FolderEntry?) {
            launcher.lifecycleScope.launch {
                folders.filter { it.contains() && it.id != target?.id }.forEach { f ->
                    service.updateFolderWithItems(
                        f.id,
                        f.title,
                        f.itemComponentKeys.filterNot { ComponentKey.fromString(it) == key },
                    )
                }
                if (target != null && !target.contains()) {
                    service.updateFolderWithItems(target.id, target.title, target.itemComponentKeys + keyString)
                }
                ReloadHelper(launcher).reloadGrid()
                val msg = if (target != null) {
                    launcher.getString(R.string.perso_moved_to_folder, appName, target.title)
                } else {
                    launcher.getString(R.string.perso_moved_out_of_folder, appName)
                }
                Toast.makeText(launcher, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // Dossiers du tiroir qui ne contiennent pas encore l'appli
        val prefix = if (fromDrawer) "" else launcher.getString(R.string.perso_drawer_prefix)
        folders.filterNot { it.contains() }
            .sortedBy { it.title.lowercase() }
            .forEach { f -> choices += Choice(prefix + f.title) { moveTo(f) } }

        // Sortir de son dossier du tiroir
        if (folders.any { it.contains() }) {
            choices += Choice(launcher.getString(R.string.perso_drawer_no_folder)) { moveTo(null) }
        }

        if (fromDrawer) {
            // Copie sur le bureau (l'appli reste aussi dans le tiroir)
            choices += Choice(launcher.getString(R.string.perso_copy_to_home)) {
                ItemInstallQueue.INSTANCE.get(launcher).queueItem(component.packageName, info.user)
                Toast.makeText(
                    launcher,
                    launcher.getString(R.string.perso_copied_to_home, appName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else if (info.container > 0) {
            // Retirer d'un dossier du bureau (l'appli reste dans le tiroir)
            choices += Choice(launcher.getString(R.string.perso_remove_from_home_folder)) {
                launcher.removeItem(view, info, true, "Perso : retirée du dossier")
            }
        }

        if (choices.isEmpty()) return
        AlertDialog.Builder(launcher)
            .setTitle(launcher.getString(R.string.perso_move_title, appName))
            .setItems(choices.map { it.label }.toTypedArray()) { _, which -> choices[which].action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        @JvmField
        val FACTORY = SystemShortcut.Factory { launcher: LawnchairLauncher, itemInfo, originalView ->
            if (itemInfo.itemType != ITEM_TYPE_APPLICATION ||
                itemInfo.targetComponent == null ||
                itemInfo.user != Process.myUserHandle()
            ) {
                null
            } else {
                PersoMoveShortcut(launcher, itemInfo, originalView)
            }
        }
    }
}
