package app.lawnchair.folder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.util.Log
import android.view.View
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.SelectFolderIcon
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.DropTarget
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator
import com.android.launcher3.views.OptionsPopupView
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Perso : icône personnalisée pour un dossier du bureau, comme sur Nova.
 * L'icône vient d'un pack d'icônes (ou des icônes du système), ou d'une image de la galerie.
 * Réglage enregistré par identifiant de dossier.
 */
object PersoFolderIcons {
    private const val TAG = "PersoFolderIcons"
    private const val PREFS = "perso_folder_icons"
    private const val SEP = "\u0001"
    private const val IMAGE = "image"

    private val cache = ConcurrentHashMap<Int, Pair<String, Drawable>>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun imageFile(context: Context, folderId: Int) =
        File(File(context.applicationContext.filesDir, "perso_folder_icons").apply { mkdirs() }, "$folderId.png")

    @JvmStatic
    fun has(context: Context, folderId: Int): Boolean = prefs(context).contains(folderId.toString())

    fun setPackIcon(context: Context, folderId: Int, item: IconPickerItem) {
        val spec = listOf(item.packPackageName, item.drawableName, item.type.name).joinToString(SEP)
        prefs(context).edit().putString(folderId.toString(), spec).commit()
        cache.remove(folderId)
    }

    /** Copie l'image choisie (recadrée en carré, 256 px) dans les fichiers de l'appli. */
    fun setImage(context: Context, folderId: Int, uri: Uri): Boolean = try {
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: error("image illisible")
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
        imageFile(context, folderId).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        prefs(context).edit().putString(folderId.toString(), IMAGE + SEP + System.currentTimeMillis()).commit()
        cache.remove(folderId)
        true
    } catch (t: Throwable) {
        Log.w(TAG, "setImage", t)
        false
    }

    fun reset(context: Context, folderId: Int) {
        prefs(context).edit().remove(folderId.toString()).commit()
        imageFile(context, folderId).delete()
        cache.remove(folderId)
    }

    /** Charge (en arrière-plan) l'icône personnalisée du dossier et l'applique. */
    @JvmStatic
    fun apply(icon: FolderIcon) {
        val info = icon.mInfo ?: return
        if (info.container == ItemInfo.NO_ID) return
        val context = icon.context
        val folderId = info.id
        val spec = prefs(context).getString(folderId.toString(), null)
        if (spec == null) {
            icon.setPersoCustomIcon(null)
            return
        }
        val key = spec + "|" + ThemeManager.INSTANCE.get(context).iconState.iconMask
        cache[folderId]?.let { (k, d) ->
            if (k == key) {
                icon.setPersoCustomIcon(d)
                return
            }
        }
        Executors.MODEL_EXECUTOR.execute {
            val drawable = load(context, folderId, spec)
            if (drawable != null) cache[folderId] = key to drawable
            Executors.MAIN_EXECUTOR.execute {
                if (icon.mInfo?.id == folderId) icon.setPersoCustomIcon(drawable)
            }
        }
    }

    /** Réapplique les icônes de tous les dossiers du bureau et du dock. */
    @JvmStatic
    fun refreshAll(launcher: Launcher) {
        launcher.workspace?.mapOverItems(
            ItemOperator { _, v ->
                if (v is FolderIcon) apply(v)
                false
            },
        )
    }

    private fun load(context: Context, folderId: Int, spec: String): Drawable? = try {
        val parts = spec.split(SEP)
        val raw: Drawable? = if (parts[0] == IMAGE) {
            BitmapFactory.decodeFile(imageFile(context, folderId).path)?.let {
                // Image pleine : rognée à la forme des icônes comme une icône adaptative
                AdaptiveIconDrawable(ColorDrawable(Color.WHITE), BitmapDrawable(context.resources, it))
            }
        } else {
            val entry = IconEntry(parts[0], parts[1], IconType.valueOf(parts[2]))
            IconPackProvider.INSTANCE.get(context)
                .getDrawable(entry, context.resources.displayMetrics.densityDpi, Process.myUserHandle())
        }
        raw?.let { d ->
            LauncherIcons.obtain(context).use { li ->
                val bitmapInfo = li.createBadgedIconBitmap(d, BaseIconFactory.IconOptions())
                WorkspaceItemInfo().apply { bitmap = bitmapInfo }.newIcon(context)
            }
        }
    } catch (t: Throwable) {
        Log.w(TAG, "load $spec", t)
        null
    }

    /**
     * Menu de l'appui long sur un dossier du bureau. Le menu reste ouvert si on relâche ;
     * il se ferme et le dossier se déplace si on fait glisser le doigt.
     */
    @JvmStatic
    fun showHomeFolderPopup(launcher: Launcher, icon: FolderIcon): DragOptions.PreDragCondition? {
        val info = icon.mInfo ?: return null
        val folderId = info.id
        val bounds = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(icon, bounds)
        val items = mutableListOf(
            OptionsPopupView.OptionItem(
                launcher,
                R.string.perso_folder_custom_icon,
                R.drawable.ic_perso_folder_icon,
                StatsLogManager.LauncherEvent.IGNORE,
            ) {
                launcher.startActivity(PreferenceActivity.createIntent(launcher, SelectFolderIcon(folderId)))
                true
            },
        )
        items += OptionsPopupView.OptionItem(
            launcher,
            R.string.perso_folder_sort_az,
            R.drawable.ic_perso_sort_az,
            StatsLogManager.LauncherEvent.IGNORE,
        ) {
            icon.folder.persoSortAlphabetically()
            true
        }
        if (has(launcher, folderId)) {
            items += OptionsPopupView.OptionItem(
                launcher,
                R.string.perso_folder_default_icon,
                R.drawable.ic_undo,
                StatsLogManager.LauncherEvent.IGNORE,
            ) {
                reset(launcher, folderId)
                icon.setPersoCustomIcon(null)
                true
            }
        }
        val popup = OptionsPopupView.show<Launcher>(launcher, RectF(bounds), items, true) ?: return null
        val threshold = launcher.resources.getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold)
        return object : DragOptions.PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double) = distanceDragged > threshold

            override fun onPreDragStart(dragObject: DropTarget.DragObject) {
                icon.visibility = View.VISIBLE
            }

            override fun onPreDragEnd(dragObject: DropTarget.DragObject, dragStarted: Boolean) {
                if (dragStarted) {
                    popup.close(true)
                    icon.visibility = View.INVISIBLE
                } else {
                    icon.visibility = View.VISIBLE
                }
            }
        }
    }
}
