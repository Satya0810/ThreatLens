import sys

path = r'c:\AndroidProjects\ThreatLens_FINAL_v2\ThreatLens\app\src\main\java\com\safeqr\scanner\ui\screens\SettingsScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1
for i, line in enumerate(lines):
    if '// ——— SECURITY Section ———' in line or '//  SECURITY Section ' in line or 'SectionHeader(title = "SECURITY")' in line:
        # Go up a few lines if there's comments
        while i > 0 and lines[i-1].strip().startswith('//'):
            i -= 1
        start_idx = i
        break

end_idx = -1
for i, line in enumerate(lines):
    if '// Set PIN Dialog' in line:
        end_idx = i
        break

if start_idx == -1 or end_idx == -1:
    print('Failed to find indices', start_idx, end_idx)
    sys.exit(1)

new_section = """        // ——— SECURITY Section ———
        SectionHeader(title = "SECURITY")
        Spacer(modifier = Modifier.height(10.dp))
        GlassCard {
            var autoBlock by remember {
                mutableStateOf(com.safeqr.scanner.data.PreferencesManager.getAutoBlock(context))
            }
            var vibrateOnDetection by remember {
                mutableStateOf(com.safeqr.scanner.data.PreferencesManager.getVibrate(context))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-block Malicious Links",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Block dangerous URLs automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = autoBlock,
                    onCheckedChange = {
                        autoBlock = it
                        com.safeqr.scanner.data.PreferencesManager.setAutoBlock(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = NeonCyan,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(GlassBorder)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vibrate on Detection",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Haptic feedback when QR is found",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = vibrateOnDetection,
                    onCheckedChange = {
                        vibrateOnDetection = it
                        com.safeqr.scanner.data.PreferencesManager.setVibrate(context, it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = NeonCyan,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(GlassBorder))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSandbox() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sandbox Browser",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Open secure isolated browsing session",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ——— PARENTAL CONTROL Section ———
        SectionHeader(title = "PARENTAL CONTROL")
        Spacer(modifier = Modifier.height(10.dp))

        var childLockEnabled by remember {
            mutableStateOf(com.safeqr.scanner.data.PreferencesManager.isChildLockEnabled(context))
        }
        var showSetPinDialog by remember { mutableStateOf(false) }
        var showVerifyPinDialog by remember { mutableStateOf(false) }
        var showParentalHub by remember { mutableStateOf(false) }

        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    if (childLockEnabled) {
                        showVerifyPinDialog = true
                    } else {
                        showSetPinDialog = true
                    }
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (childLockEnabled) NeonCyan else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Parental Controls",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (childLockEnabled) "Active — advanced filtering enabled"
                               else "Off — all links accessible",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (childLockEnabled) SafeGreen else TextSecondary
                    )
                }
                Switch(
                    checked = childLockEnabled,
                    onCheckedChange = { wantEnabled ->
                        if (wantEnabled) {
                            showSetPinDialog = true
                        } else {
                            showVerifyPinDialog = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = NeonCyan,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }

            if (childLockEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showVerifyPinDialog = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
                ) {
                    Text("Manage Parental Controls", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            }
        }

"""

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(lines[:start_idx])
    f.write(new_section)
    f.writelines(lines[end_idx:])

print('Success')
