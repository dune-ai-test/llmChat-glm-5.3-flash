package com.mrrob.llmchat.ui.kit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The design uses Lucide icons; this registry maps every icon the screens
 * reference to the closest Material outlined equivalent already bundled,
 * keyed by the lucide name so screens read naturally.
 */
object IconsL {
    val home: ImageVector get() = Icons.Outlined.Home
    val chatBubble: ImageVector get() = Icons.Outlined.Chat
    val chatSquare: ImageVector get() = Icons.Outlined.ChatBubbleOutline
    val chatAdd: ImageVector get() = Icons.Outlined.AddComment
    val mic: ImageVector get() = Icons.Outlined.Mic
    val micOff: ImageVector get() = Icons.Outlined.MicOff
    val server: ImageVector get() = Icons.Outlined.Dns
    val settings: ImageVector get() = Icons.Outlined.Settings
    val sparkles: ImageVector get() = Icons.Outlined.AutoAwesome
    val plus: ImageVector get() = Icons.Outlined.Add
    val search: ImageVector get() = Icons.Outlined.Search
    val more: ImageVector get() = Icons.Outlined.MoreHoriz
    val info: ImageVector get() = Icons.Outlined.Info
    val arrowBack: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    val arrowUp: ImageVector get() = Icons.Filled.KeyboardArrowUp
    val arrowSwap: ImageVector get() = Icons.Outlined.SwapHoriz
    val chevronLeft: ImageVector get() = Icons.Outlined.ChevronLeft
    val chevronRight: ImageVector get() = Icons.Outlined.ChevronRight
    val chevronDown: ImageVector get() = Icons.Outlined.ExpandMore
    val check: ImageVector get() = Icons.Outlined.Check
    val checkCircle: ImageVector get() = Icons.Outlined.CheckCircle
    val close: ImageVector get() = Icons.Outlined.Close
    val eye: ImageVector get() = Icons.Outlined.Visibility
    val eyeOff: ImageVector get() = Icons.Outlined.VisibilityOff
    val download: ImageVector get() = Icons.Outlined.Download
    val lock: ImageVector get() = Icons.Outlined.Lock
    val shuffle: ImageVector get() = Icons.Outlined.Shuffle
    val hardDrive: ImageVector get() = Icons.Filled.Storage
    val code: ImageVector get() = Icons.Outlined.Code
    val braces: ImageVector get() = Icons.Outlined.Article
    val paperclip: ImageVector get() = Icons.Outlined.AttachFile
    val square: ImageVector get() = Icons.Outlined.Stop
    val trash: ImageVector get() = Icons.Outlined.Delete
    val pencil: ImageVector get() = Icons.Outlined.Edit
    val copy: ImageVector get() = Icons.Outlined.ContentCopy
    val refresh: ImageVector get() = Icons.Outlined.Refresh
    val sync: ImageVector get() = Icons.Outlined.Sync
    val callEnd: ImageVector get() = Icons.Outlined.CallEnd
    val volume: ImageVector get() = Icons.Outlined.VolumeUp
    val audioLines: ImageVector get() = Icons.Outlined.GraphicEq
    val sun: ImageVector get() = Icons.Outlined.LightMode
    val type: ImageVector get() = Icons.Outlined.TextFields
    val list: ImageVector get() = Icons.Outlined.List
    val timer: ImageVector get() = Icons.Outlined.Timer
    val globe: ImageVector get() = Icons.Outlined.Language
    val bug: ImageVector get() = Icons.Outlined.BugReport
    val history: ImageVector get() = Icons.Outlined.History
    val wifiOff: ImageVector get() = Icons.Outlined.WifiOff
    val cloudOff: ImageVector get() = Icons.Outlined.CloudOff
    val hourglass: ImageVector get() = Icons.Outlined.HourglassEmpty
    val block: ImageVector get() = Icons.Outlined.Block
    val lightbulb: ImageVector get() = Icons.Outlined.Lightbulb
    val mail: ImageVector get() = Icons.Outlined.Mail
    val map: ImageVector get() = Icons.Outlined.Map
    val zap: ImageVector get() = Icons.Outlined.Bolt
    val thumbUp: ImageVector get() = Icons.Outlined.ThumbUp
    val thumbDown: ImageVector get() = Icons.Outlined.ThumbDown
    val play: ImageVector get() = Icons.Outlined.PlayArrow
    val keyRound: ImageVector get() = Icons.Outlined.Key
    val pin: ImageVector get() = Icons.Outlined.PushPin
    val archive: ImageVector get() = Icons.Filled.Archive
    val share: ImageVector get() = Icons.Filled.Share
    val send: ImageVector get() = Icons.AutoMirrored.Outlined.Send
    val thermometer: ImageVector get() = Icons.Outlined.Thermostat
    val warning: ImageVector get() = Icons.Outlined.Info // warning triangle falls back to info
    val slider: ImageVector get() = Icons.Outlined.Thermostat
    val menu: ImageVector get() = Icons.Outlined.Menu
    val folder: ImageVector get() = Icons.Outlined.Folder
    val star: ImageVector get() = Icons.Outlined.StarBorder
    val starFilled: ImageVector get() = Icons.Filled.Star
}
