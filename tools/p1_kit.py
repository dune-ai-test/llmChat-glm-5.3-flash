import io

# --------------------------------------------------------------- Kit.kt changes
p = 'app/src/main/java/com/mrrob/llmchat/ui/kit/Kit.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

def rep(o, n, cnt=1):
    global s
    assert s.count(o) == cnt, (o[:60], s.count(o))
    s = s.replace(o, n)

# drawer opener provider + menu leading on ScreenTopBar
rep('''/** The uniform, non-scrolling top bar: large title + optional leading + actions. */
@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val c = LocalScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }''',
'''/** Set to a lambda by MainScaffold when navigation mode is "drawer". */
val LocalDrawerOpener = androidx.compose.runtime.compositionLocalOf<(() -> Unit)?> { null }

/** The uniform, non-scrolling top bar: large title + optional leading + actions. */
@Composable
fun ScreenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    val c = LocalScheme.current
    val opener = LocalDrawerOpener.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading == null && opener != null) {
            CircleIconButton(
                icon = IconsL.menu,
                onClick = opener,
                size = 40.dp,
                iconSize = 20.dp,
                bordered = false
            )
            Spacer(Modifier.width(10.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }''')

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('kit ok')

# --------------------------------------------------------------- IconsL: menu
p = 'app/src/main/java/com/mrrob/llmchat/ui/kit/IconsL.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')
if 'val menu' not in s:
    s = s.replace('import androidx.compose.material.icons.outlined.Add\n',
                  'import androidx.compose.material.icons.outlined.Add\nimport androidx.compose.material.icons.outlined.Menu\n', 1)
    s = s.replace('    val star: ImageVector get() = Icons.Outlined.StarBorder',
                  '''    val menu: ImageVector get() = Icons.Outlined.Menu
    val star: ImageVector get() = Icons.Outlined.StarBorder''')
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('icons ok')
