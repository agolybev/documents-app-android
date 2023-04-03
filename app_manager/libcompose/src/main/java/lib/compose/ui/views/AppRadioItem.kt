package lib.compose.ui.views

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lib.compose.ui.theme.ManagerTheme

@Composable
fun AppRadioItem(
    title: String,
    checked: Boolean = false,
    onCheck: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onCheck(!checked) }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            RadioButton(
                selected = checked,
                onClick = { onCheck(!checked) },
                modifier = Modifier.padding(start = 16.dp),
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
            )
            Text(
                text = title,
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        AppDivider(startIndent = 16.dp)
    }
}

@Composable
fun AppRadioItem(
    @StringRes title: Int,
    checked: Boolean = false,
    onCheck: (Boolean) -> Unit
) {
    AppRadioItem(title = stringResource(id = title), checked = checked, onCheck = onCheck)
}

@Preview
@Composable
private fun AppRadioItemPreview() {
    ManagerTheme {
        Surface {
            Column {
                AppRadioItem(stringResource(id = lib.toolkit.base.R.string.app_title), false) {}
                AppRadioItem(stringResource(id = lib.toolkit.base.R.string.app_title), true) {}
            }
        }
    }
}