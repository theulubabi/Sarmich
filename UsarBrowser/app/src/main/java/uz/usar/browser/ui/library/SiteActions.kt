package uz.usar.browser.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uz.usar.browser.R
import uz.usar.browser.browser.UrlUtils
import uz.usar.browser.data.db.SiteEntity
import uz.usar.browser.data.db.displayName

class SiteActions(
    val onOpen: () -> Unit,
    val onOpenInNewTab: () -> Unit,
    val onTogglePin: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onEdit: () -> Unit,
    val onShare: () -> Unit,
    val onCopy: () -> Unit,
    val onDelete: () -> Unit,
    val onSiteSettings: (() -> Unit)? = null,
    val onMoveUp: (() -> Unit)? = null,
    val onMoveDown: (() -> Unit)? = null,
)

/** Long-press menu shared by the start page and the saved-sites screen. */
@Composable
fun SiteActionsMenu(expanded: Boolean, onDismiss: () -> Unit, site: SiteEntity, actions: SiteActions) {
    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Item(R.string.open, Icons.Filled.OpenInBrowser) { act(actions.onOpen) }
        Item(R.string.open_in_new_tab, Icons.Filled.Tab) { act(actions.onOpenInNewTab) }
        Item(if (site.isPinned) R.string.unpin else R.string.pin, Icons.Filled.PushPin) { act(actions.onTogglePin) }
        Item(
            if (site.isFavorite) R.string.remove_favorite else R.string.add_favorite,
            if (site.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
        ) { act(actions.onToggleFavorite) }
        Item(R.string.edit, Icons.Filled.Edit) { act(actions.onEdit) }
        Item(R.string.share, Icons.Filled.Share) { act(actions.onShare) }
        Item(R.string.copy_url, Icons.Filled.ContentCopy) { act(actions.onCopy) }
        actions.onMoveUp?.let { up -> Item(R.string.move_up, Icons.Filled.KeyboardArrowUp) { act(up) } }
        actions.onMoveDown?.let { down -> Item(R.string.move_down, Icons.Filled.KeyboardArrowDown) { act(down) } }
        actions.onSiteSettings?.let { open -> Item(R.string.site_settings, Icons.Filled.Tune) { act(open) } }
        Item(R.string.delete, Icons.Filled.Delete) { act(actions.onDelete) }
    }
}

@Composable
private fun Item(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

@Composable
fun EditSiteDialog(site: SiteEntity, onSave: (name: String, url: String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(site.displayName) }
    var url by remember { mutableStateOf(site.url) }
    val valid = UrlUtils.isWebUrl(url.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_site)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.custom_name)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.web_address)) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) {
                        { Text(stringResource(R.string.edit_url_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(name.trim(), url.trim())
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun DeleteSiteDialog(site: SiteEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_site_title)) },
        text = { Text(stringResource(R.string.delete_site_message, site.displayName)) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
