package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.notifications.NotificationLeads
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.OnCyan
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800

/**
 * Editor multi-selección de anticipaciones de notificación (compartido por Config,
 * los diálogos de la Agenda y los ítems de junta). Selecciona varias: "30 min antes"
 * + "1 día antes" + "1 semana antes"... Vacío = solo aviso a la hora exacta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotifLeadsEditor(
    selected: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NotificationLeads.CATALOG.forEach { lead ->
            val isSelected = lead in selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) CyanNeon else Slate800)
                    .border(1.dp, if (isSelected) CyanNeon else Slate700, RoundedCornerShape(12.dp))
                    .clickable {
                        onSelectionChange(
                            if (isSelected) selected - lead
                            else selected + lead
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = OnCyan,
                        modifier = Modifier.size(11.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(2.dp))
                }
                Text(
                    NotificationLeads.label(lead),
                    color = if (isSelected) OnCyan else Slate400,
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
