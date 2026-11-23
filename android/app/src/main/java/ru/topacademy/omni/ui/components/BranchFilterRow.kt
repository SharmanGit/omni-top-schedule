package ru.topacademy.omni.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.topacademy.omni.domain.model.Branch
import ru.topacademy.omni.ui.theme.AmberPrimary
import ru.topacademy.omni.ui.theme.BgDark
import ru.topacademy.omni.ui.theme.BorderDark
import ru.topacademy.omni.ui.theme.CardDark
import ru.topacademy.omni.ui.theme.TextMuted
import ru.topacademy.omni.ui.theme.TextPrimary
import ru.topacademy.omni.ui.theme.TextSecondary

@Composable
fun BranchFilterRow(
    branches: List<Branch>,
    selectedBranchId: String?,
    onSelectBranch: (String?) -> Unit
) {
    if (branches.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDark)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Все места"
        val isAllSelected = selectedBranchId == null
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, if (isAllSelected) AmberPrimary else BorderDark, RoundedCornerShape(16.dp))
                .background(if (isAllSelected) Color(0xFF2A2010) else CardDark)
                .clickable { onSelectBranch(null) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Все места",
                color = if (isAllSelected) AmberPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
            )
        }

        for (b in branches) {
            val isSelected = selectedBranchId == b.cityId
            val branchColor = try {
                Color(android.graphics.Color.parseColor(b.colorHex))
            } catch (_: Exception) {
                AmberPrimary
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, if (isSelected) branchColor else BorderDark, RoundedCornerShape(16.dp))
                    .background(if (isSelected) CardDark else Color(0xFF131B2B))
                    .clickable { onSelectBranch(b.cityId) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(branchColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = b.cityName,
                    color = if (isSelected) TextPrimary else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
