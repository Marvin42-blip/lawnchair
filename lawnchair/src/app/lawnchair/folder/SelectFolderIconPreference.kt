package app.lawnchair.folder

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Perso : choix de l'icône d'un dossier du bureau (pack d'icônes, icônes du système ou galerie). */
@Composable
fun SelectFolderIconPreference(folderId: Int) {
    val context = LocalContext.current
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    fun done() {
        (context as Activity).let {
            it.setResult(Activity.RESULT_OK)
            it.finish()
        }
    }

    OnResult<IconPickerItem> { item ->
        PersoFolderIcons.setPackIcon(context, folderId, item)
        done()
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { PersoFolderIcons.setImage(context, folderId, uri) }
                if (ok) done()
            }
        }
    }

    val hasIcon = remember { PersoFolderIcons.has(context, folderId) }

    PreferenceLayoutLazyColumn(label = stringResource(id = R.string.perso_folder_custom_icon)) {
        preferenceGroupItems(if (hasIcon) 2 else 1, isFirstChild = true) { index ->
            if (index == 0) {
                ClickablePreference(
                    label = stringResource(id = R.string.perso_folder_icon_gallery),
                    onClick = {
                        gallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            } else {
                ClickablePreference(
                    label = stringResource(id = R.string.perso_folder_default_icon),
                    onClick = {
                        PersoFolderIcons.reset(context, folderId)
                        done()
                    },
                )
            }
        }
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = false,
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate(IconPicker())
                    } else {
                        navController.navigate(IconPicker(iconPack.packageName))
                    }
                },
            )
        }
    }
}
