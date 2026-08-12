import re

with open('app/src/main/java/com/safeqr/scanner/ui/screens/QrGeneratorScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

start_idx = -1
for i, line in enumerate(lines):
    if 'private fun QrInputFields(' in line:
        start_idx = i
        break

if start_idx != -1:
    end_idx = -1
    for i in range(start_idx, len(lines)):
        if '@Composable' in lines[i] and 'fun DateTimePickerButton' in lines[i+1]:
            end_idx = i
            break
            
    if end_idx != -1:
        snippet = ''.join(lines[start_idx:end_idx])
        print('Found QrInputFields, length:', len(snippet))
        
        # Now let's do the replacements
        snippet = snippet.replace('label = { Text("Campaign Title") }', 'label = { Text("Campaign Title") }, leadingIcon = { Icon(Icons.Outlined.Title, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Target URL") }', 'label = { Text("Target URL") }, leadingIcon = { Icon(Icons.Outlined.Link, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Alternate URLs (comma separated) for Rotation") }', 'label = { Text("Alternate URLs (comma separated) for Rotation") }, leadingIcon = { Icon(Icons.Outlined.AltRoute, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Password Protection (Optional PIN)") }', 'label = { Text("Password Protection (Optional PIN)") }, leadingIcon = { Icon(Icons.Outlined.Password, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Allowed Geo-Region (e.g. US, IN) - Optional") }', 'label = { Text("Allowed Geo-Region (e.g. US, IN) - Optional") }, leadingIcon = { Icon(Icons.Outlined.Map, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("URL / Link") }', 'label = { Text("URL / Link") }, leadingIcon = { Icon(Icons.Outlined.Link, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Text Content") }', 'label = { Text("Text Content") }, leadingIcon = { Icon(Icons.Outlined.Notes, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Email Address") }', 'label = { Text("Email Address") }, leadingIcon = { Icon(Icons.Outlined.Email, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Subject") }', 'label = { Text("Subject") }, leadingIcon = { Icon(Icons.Outlined.Subject, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Body") }', 'label = { Text("Body") }, leadingIcon = { Icon(Icons.Outlined.Notes, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Phone Number") }', 'label = { Text("Phone Number") }, leadingIcon = { Icon(Icons.Outlined.Phone, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Message") }', 'label = { Text("Message") }, leadingIcon = { Icon(Icons.Outlined.Message, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Network Name (SSID)") }', 'label = { Text("Network Name (SSID)") }, leadingIcon = { Icon(Icons.Outlined.Wifi, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Password") }', 'label = { Text("Password") }, leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Full Name") }', 'label = { Text("Full Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Phone") }', 'label = { Text("Phone") }, leadingIcon = { Icon(Icons.Outlined.Phone, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Email") }', 'label = { Text("Email") }, leadingIcon = { Icon(Icons.Outlined.Email, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Organization / Company") }', 'label = { Text("Organization / Company") }, leadingIcon = { Icon(Icons.Outlined.Business, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("UPI ID") }', 'label = { Text("UPI ID") }, leadingIcon = { Icon(Icons.Outlined.Payment, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Payee Name") }', 'label = { Text("Payee Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("PayPal Username / Email") }', 'label = { Text("PayPal Username / Email") }, leadingIcon = { Icon(Icons.Outlined.Payment, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Telegram Username") }', 'label = { Text("Telegram Username") }, leadingIcon = { Icon(Icons.Outlined.Send, null, tint = NeonCyan) }')
        # Search by Address or Place already has trailing icon, we can add leading too, but maybe not needed. Let's add it.
        snippet = snippet.replace('label = { Text("Search by Address or Place") }', 'label = { Text("Search by Address or Place") }, leadingIcon = { Icon(Icons.Outlined.Place, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Latitude") }', 'label = { Text("Latitude") }, leadingIcon = { Icon(Icons.Outlined.Explore, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Longitude") }', 'label = { Text("Longitude") }, leadingIcon = { Icon(Icons.Outlined.Explore, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Event Name") }', 'label = { Text("Event Name") }, leadingIcon = { Icon(Icons.Outlined.Event, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Description") }', 'label = { Text("Description") }, leadingIcon = { Icon(Icons.Outlined.Description, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Location") }', 'label = { Text("Location") }, leadingIcon = { Icon(Icons.Outlined.LocationOn, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Wallet Address") }', 'label = { Text("Wallet Address") }, leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Amount (Optional)") }', 'label = { Text("Amount (Optional)") }, leadingIcon = { Icon(Icons.Outlined.AttachMoney, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Ticket ID (e.g. TKT-1234)") }', 'label = { Text("Ticket ID (e.g. TKT-1234)") }, leadingIcon = { Icon(Icons.Outlined.ConfirmationNumber, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Attendee Name") }', 'label = { Text("Attendee Name") }, leadingIcon = { Icon(Icons.Outlined.Person, null, tint = NeonCyan) }')
        snippet = snippet.replace('label = { Text("Tier (e.g. VIP)") }', 'label = { Text("Tier (e.g. VIP)") }, leadingIcon = { Icon(Icons.Outlined.Star, null, tint = NeonCyan) }')
        
        lines[start_idx:end_idx] = [snippet]
        with open('app/src/main/java/com/safeqr/scanner/ui/screens/QrGeneratorScreen.kt', 'w', encoding='utf-8') as f:
            f.writelines(lines)
            
        print('Updated QrInputFields')
