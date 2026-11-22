package app.lawnchair.ui.popup

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
        showOverlay(launcher, launcher.getString(R.string.perso_move_title, appName), choices)
    }

    /**
     * Perso : liste affichée dans le lanceur lui-même (pas dans une fenêtre de dialogue).
     * Les fenêtres de dialogue ouvertes depuis le lanceur recevaient les appuis décalés
     * d'une ligne : toucher « Connaissance » choisissait le dossier suivant.
     */
    private fun showOverlay(launcher: LawnchairLauncher, title: String, choices: List<Choice>) {
        val ctx = launcher
        val dp = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()
        val night = (ctx.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bg = ctx.getColor(if (night) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50)
        val fg = ctx.getColor(if (night) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900)
        val pressed = ctx.getColor(if (night) android.R.color.system_accent2_700 else android.R.color.system_accent2_100)
        val accent = ctx.getColor(if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)

        val dragLayer = launcher.dragLayer
        val scrim = android.widget.FrameLayout(ctx).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        fun close() = dragLayer.removeView(scrim)
        scrim.setOnClickListener { close() }
        scrim.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                close()
                true
            } else {
                keyCode == android.view.KeyEvent.KEYCODE_BACK
            }
        }

        val card = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(bg)
                cornerRadius = px(28).toFloat()
            }
            isClickable = true // ne ferme pas en touchant la carte
            setPadding(0, px(20), 0, px(8))
        }
        card.addView(
            android.widget.TextView(ctx).apply {
                text = title
                textSize = 20f
                setTextColor(fg)
                setPadding(px(24), 0, px(24), px(12))
            },
        )
        val rows = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL }
        choices.forEach { choice ->
            rows.addView(
                android.widget.TextView(ctx).apply {
                    text = choice.label
                    textSize = 17f
                    setTextColor(fg)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minHeight = px(52)
                    setPadding(px(24), 0, px(24), 0)
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.ColorDrawable(pressed))
                        addState(intArrayOf(), android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    }
                    setOnClickListener {
                        close()
                        choice.action()
                    }
                },
                android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val maxListHeight = (ctx.resources.displayMetrics.heightPixels * 0.6f).toInt()
        val listHeight = minOf(maxListHeight, choices.size * px(52))
        card.addView(
            android.widget.ScrollView(ctx).apply { addView(rows) },
            android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, listHeight),
        )
        card.addView(
            android.widget.TextView(ctx).apply {
                text = ctx.getString(android.R.string.cancel)
                textSize = 15f
                setTextColor(accent)
                gravity = android.view.Gravity.CENTER
                minHeight = px(48)
                setPadding(px(24), 0, px(24), 0)
                setOnClickListener { close() }
            },
            android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = android.view.Gravity.END
                marginEnd = px(12)
            },
        )
        scrim.addView(
            card,
            android.widget.FrameLayout.LayoutParams(
                minOf(px(420), ctx.resources.displayMetrics.widthPixels - px(48)),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER,
            ),
        )
        dragLayer.addView(
            scrim,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        scrim.requestFocus()
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
