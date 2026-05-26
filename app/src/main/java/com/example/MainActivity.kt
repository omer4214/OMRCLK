package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.MessageEntity
import com.example.data.local.PeerEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.BrilliantLavender
import com.example.ui.theme.BrilliantLavenderDim
import com.example.ui.theme.IntenseViolet
import com.example.ui.theme.ElegantGreen
import com.example.ui.theme.CoralRose
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.ChatViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) { innerPadding ->
                    KendiAppScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun KendiAppScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val peers by viewModel.allPeers.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val selectedPeer = viewModel.selectedPeer
    var guestMode by remember { mutableStateOf(false) }

    if (viewModel.loggedInEmail.isEmpty() && !guestMode) {
        AuthScreen(
            viewModel = viewModel,
            onGuestMode = { guestMode = true },
            modifier = modifier
        )
    } else {
        // Activity transition animations based on selected contact
        AnimatedContent(
            targetState = selectedPeer,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally(initialOffsetX = { it }) + fadeIn() with
                            slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                } else {
                    slideInHorizontally(initialOffsetX = { -it }) + fadeIn() with
                            slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                }
            },
            label = "ScreenTransition"
        ) { peer ->
            if (peer == null) {
                ConversationListScreen(
                    viewModel = viewModel,
                    peers = peers,
                    onOpenLogin = { guestMode = false },
                    modifier = modifier
                )
            } else {
                ChatScreen(
                    viewModel = viewModel,
                    peer = peer,
                    messages = messages,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
fun AuthScreen(
    viewModel: ChatViewModel,
    onGuestMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isRegisterState by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 450.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Logo",
                    tint = BrilliantLavender,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Kendi",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isRegisterState) "Yeni hesap oluşturun" else "Hesabınıza giriş yapın",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isRegisterState) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Ad Soyad") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("auth_name_field")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-posta Adresi") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Şifre") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_field")
                )

                if (viewModel.authError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = viewModel.authError,
                        color = CoralRose,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (viewModel.isAuthLoading) {
                    CircularProgressIndicator(color = BrilliantLavender)
                } else {
                    Button(
                        onClick = {
                            if (isRegisterState) {
                                viewModel.performRegister(email, password, name)
                            } else {
                                viewModel.performLogin(email, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrilliantLavender)
                    ) {
                        Text(
                            text = if (isRegisterState) "Kayıt Ol" else "Giriş Yap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { isRegisterState = !isRegisterState; viewModel.authError = "" }
                    ) {
                        Text(
                            text = if (isRegisterState) "Zaten bir hesabınız var mı? Giriş yapın" else "Hesabınız yok mu? Kayıt olmak için tıklayın",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrilliantLavender
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onGuestMode
                    ) {
                        Text(
                            text = "Misafir Olarak Giriş Yap (P2P / Wi-Fi Modu)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ==================== HOME: CONVERSATION & PAIRING SCREEN ====================
@Composable
fun ConversationListScreen(
    viewModel: ChatViewModel,
    peers: List<PeerEntity>,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var buddyIp by remember { mutableStateOf("") }
    var buddyName by remember { mutableStateOf("") }
    var onlineRoomCode by remember { mutableStateOf("") }
    var showEditProfile by remember { mutableStateOf(false) }
    var tempNickname by remember { mutableStateOf(viewModel.myNickname) }

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Title Section with Pulsing Logo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Kendi",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Cihazlar Arası Hızlı Transfer & Mesajlaşma",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            IconButton(
                onClick = { showEditProfile = true; tempNickname = viewModel.myNickname },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .testTag("profile_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Ayarlar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Quick View / Authenticated info
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val avatarText = if (viewModel.loggedInEmail.isNotEmpty()) {
                    viewModel.loggedInName.take(2).uppercase()
                } else {
                    viewModel.myNickname.take(2).uppercase()
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarText,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (viewModel.loggedInEmail.isNotEmpty()) {
                        Text(
                            text = viewModel.loggedInName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = viewModel.loggedInEmail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "Misafir Modu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Rumuz: ${viewModel.myNickname}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "📶 Wifi IP: ${viewModel.myLocalIp}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                if (viewModel.loggedInEmail.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.performLogout() },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Çıkış Yap",
                            tint = CoralRose
                        )
                    }
                } else {
                    Button(
                        onClick = onOpenLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = BrilliantLavender),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Giriş Yap", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dual Connection Panels Scroll Layer
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ------------------ MODERN FRIEND SYSTEM SECTION ------------------
            if (viewModel.loggedInEmail.isEmpty()) {
                // Call to action for local guest users
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = "Yıldız", tint = BrilliantLavender)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kolay Sadece E-Posta ile Bağlantı", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Arkadaş ekleme, e-posta ile istek atma ve IP adresi girmeden anında mesajlaşmak için e-posta ve şifrenizle giriş yapın.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenLogin,
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrilliantLavender),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Giriş Yap / Üye Ol", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // FRIEND FINDER & MANAGEMENT CARD
                item {
                    var friendEmailInput by remember { mutableStateOf("") }
                    
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = "Arkadaş", tint = BrilliantLavender)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("E-Posta ile Arkadaş Ekle", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Text(
                                "Arkadaşınızın sisteme üye olduğu e-posta adresini yazarak istek gönderin. Kabul ettiğinde IP girmeden yazışabilirsiniz.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = friendEmailInput,
                                    onValueChange = { friendEmailInput = it },
                                    placeholder = { Text("Örn: arkadas@gmail.com") },
                                    label = { Text("Arkadaşının E-postası") },
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("friend_email_input")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val target = friendEmailInput.trim()
                                        if (target.isEmpty()) {
                                            Toast.makeText(context, "Lütfen bir e-posta adresi yazın.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.sendFriendRequestByEmail(target) { result ->
                                                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                                if (result.contains("başarıyla") || result.contains("kabul")) {
                                                    friendEmailInput = ""
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrilliantLavender),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(52.dp).testTag("friend_request_send_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Gönder", tint = Color.White)
                                }
                            }
                        }
                    }
                }

                // INCOMING FRIEND REQUESTS LIST
                if (viewModel.pendingIncomingList.isNotEmpty()) {
                    item {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Notifications, contentDescription = "İstekler", tint = WarningAmber)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gelen Arkadaşlık İstekleri", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                viewModel.pendingIncomingList.forEach { requestor ->
                                    val displayName = viewModel.userNamesCache[requestor] ?: requestor
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(requestor, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row {
                                            Button(
                                                onClick = {
                                                    viewModel.acceptFriendRequestByEmail(requestor) { res ->
                                                        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = ElegantGreen),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Kabul Et", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            TextButton(
                                                onClick = {
                                                    viewModel.rejectFriendRequestByEmail(requestor) { res ->
                                                        Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Yoksay", fontSize = 11.sp, color = CoralRose)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // FRIENDS DIRECTORY LIST (FAST ACCESS FROM HOME SCREEN)
                item {
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Face, contentDescription = "Arkadaşlar", tint = BrilliantLavender)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sistem Arkadaşlarım", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (viewModel.friendsList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Henüz sıfırdan eklenmiş bir arkadaşınız yok.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            } else {
                                viewModel.friendsList.forEach { friendEmail ->
                                    val friendName = viewModel.userNamesCache[friendEmail] ?: friendEmail
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.selectFriendChat(friendEmail) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(BrilliantLavenderDim, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = friendName.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                color = BrilliantLavender,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(friendName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(friendEmail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Sohbet Aç",
                                            tint = BrilliantLavender,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // OUTGOING PENDING REQUESTS
                if (viewModel.pendingOutgoingList.isNotEmpty()) {
                    item {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Giden Bekleyen İstekler", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(6.dp))
                                viewModel.pendingOutgoingList.forEach { email ->
                                    val displayName = viewModel.userNamesCache[email] ?: email
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Beklemede...", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Wi-Fi Connection Panel (P2P Client backup setup)
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = "WiFi", tint = ElegantGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Lokal Wi-Fi Bağlantısı (P2P)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            "Arkadaşınla aynı Wi-Fi ağında isen, onun IP adresini yazarak doğrudan ve kotadan gitmeden anında mesajlaşabilir, ses kaydı ve devasa dosyaları transfer edebilirsin.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = buddyIp,
                            onValueChange = { buddyIp = it },
                            placeholder = { Text("Örn: 192.168.1.18") },
                            label = { Text("Arkadaşının Port IP Adresi") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = "IP") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ip_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = buddyName,
                            onValueChange = { buddyName = it },
                            placeholder = { Text("Kullanıcı Adı (Opsiyonel)") },
                            label = { Text("Arkadaşının Adı") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("name_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (buddyIp.trim().isEmpty()) {
                                    Toast.makeText(context, "Lütfen bir IP adresi girin.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.connectToLocalPeer(buddyIp, buddyName)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("connect_wifi_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElegantGreen)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Baglan")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wi-Fi ile Hızlı Bağlan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Cloud Internet Connection Panel
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = "Bulut", tint = BrilliantLavender)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "İnternet Ortak Oda Modu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            "Farklı ağlardaysanız dert etmeyin. Aynı 'Oda Kodu'nu (örn: 1234) yazıp bağlanarak bir araya gelebilir, güvenle internet üzerinden chatleşip dosya gönderebilirsiniz.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        OutlinedTextField(
                            value = onlineRoomCode,
                            onValueChange = { onlineRoomCode = it },
                            placeholder = { Text("Örn: ODA345") },
                            label = { Text("Ortak Oda Kodu") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Room") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("room_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (onlineRoomCode.trim().isEmpty()) {
                                    Toast.makeText(context, "Lütfen bir Oda Kodu yazın.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.joinInternetRoom(onlineRoomCode)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("join_room_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = BrilliantLavender)
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Join")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ortak Odaya Gir", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // GitHub Release Updater & Installer Card
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = "Güncelleme", tint = BrilliantLavender)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GitHub Doğrudan Güncelleyici & Kurucu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            "GitHub Releases kısmından en son APK dosyasını veya yazdığınız doğrudan bir .apk linkini anında indirip kurabilirsiniz.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        var repoInputText by remember { mutableStateOf(viewModel.githubRepoInput) }

                        OutlinedTextField(
                            value = repoInputText,
                            onValueChange = { 
                                repoInputText = it
                                viewModel.updateGithubRepoInput(it)
                            },
                            placeholder = { Text("Örn: kullanıcı/depo veya https://.../yeni.apk") },
                            label = { Text("GitHub Deposu veya Doğrudan APK Linki") },
                            leadingIcon = { Icon(Icons.Default.Build, contentDescription = "Sürüm") },
                            shape = RoundedCornerShape(12.dp),
                            enabled = viewModel.updateStatus == "IDLE" || viewModel.updateStatus == "ERROR" || viewModel.updateStatus == "NOT_AVAILABLE",
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("github_repo_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        when (viewModel.updateStatus) {
                            "IDLE" -> {
                                Button(
                                    onClick = {
                                        viewModel.checkForGithubUpdate(context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("check_github_update_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrilliantLavender)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Kontrol et")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Güncelleme/APK Denetle", fontWeight = FontWeight.Bold)
                                }
                            }
                            "CHECKING" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = BrilliantLavender,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("GitHub releases sorgulanıyor...", fontSize = 14.sp)
                                }
                            }
                            "AVAILABLE" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Sürüm Tespit Edildi: ${viewModel.updateVersionName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ElegantGreen
                                    )
                                    Text(
                                        text = "Dosya: ${viewModel.updateApkFileName}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.downloadAndInstallApk(context) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ElegantGreen)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Indir")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Şimdi Kur", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.resetUpdateStatus() },
                                            modifier = Modifier.weight(0.5f)
                                        ) {
                                            Text("Vazgeç")
                                        }
                                    }
                                }
                            }
                            "DOWNLOADING" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val pct = (viewModel.updateDownloadProgress * 100).toInt()
                                    Text(
                                        text = "Yeni sürüm APK indiriliyor: %$pct",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { viewModel.updateDownloadProgress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                        color = BrilliantLavender
                                    )
                                }
                            }
                            "DOWNLOADED" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Dosya başarıyla indirildi!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = ElegantGreen
                                    )
                                    Text(
                                        text = "Kurulum penceresi açıldı. Açılmadıysa alttaki butona tıklayın.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { 
                                                val file = File(context.cacheDir, viewModel.updateApkFileName)
                                                viewModel.installApkIntent(context, file)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrilliantLavender)
                                        ) {
                                            Text("Yükleyiciyi Tekrar Aç", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.resetUpdateStatus() },
                                            modifier = Modifier.weight(0.5f)
                                        ) {
                                            Text("Kapat")
                                        }
                                    }
                                }
                            }
                            "NOT_AVAILABLE" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = viewModel.updateErrorMessage.ifEmpty { "Uygulama zaten güncel!" },
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.resetUpdateStatus() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Tamam")
                                    }
                                }
                            }
                            "ERROR" -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = viewModel.updateErrorMessage,
                                        fontSize = 13.sp,
                                        color = CoralRose,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { viewModel.resetUpdateStatus() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralRose)
                                    ) {
                                        Text("Kapat / Yeniden Dene")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Chat History Title Header
            item {
                Text(
                    "Geçmiş Sohbetler ve Bağlantılar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Empty state for peers
            if (peers.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Bilgi",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Henüz kayıtlı bağlantın yok.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Yukarıdan Wi-Fi IP'si girerek ya da internet odası kurarak anında başlayabilirsin.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                items(peers) { peer ->
                    PeerRowItem(
                        peer = peer,
                        onSelect = { viewModel.selectPeer(peer) },
                        onDelete = { viewModel.deleteSavedPeer(peer.peerId) }
                    )
                }
            }
        }
    }

    // Modal Profile Edit Dialog
    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Profil Düzenleme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Uygulamadaki görünen rumuzunuzu giriniz:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempNickname,
                        onValueChange = { tempNickname = it },
                        singleLine = true,
                        placeholder = { Text("Rumuz yazın...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nickname_edit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempNickname.trim().isNotEmpty()) {
                            viewModel.updateNickname(tempNickname.trim())
                            showEditProfile = false
                        }
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun PeerRowItem(
    peer: PeerEntity,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("peer_card_${peer.peerId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (peer.isInternetFallback) BrilliantLavender else ElegantGreen,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (peer.isInternetFallback) Icons.Default.Cloud else Icons.Default.Wifi,
                        contentDescription = "Status icon",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = peer.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (peer.isInternetFallback) "Bulut Bağlantı Odası" else "Wifi Adresi: ${peer.lastIp}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_peer_button_${peer.peerId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Temizle",
                    tint = CoralRose,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


// ==================== CHAT: SENDING AND RECEIVING FILE CANVAS ====================
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    peer: PeerEntity,
    messages: List<MessageEntity>,
    modifier: Modifier = Modifier
) {
    var txtInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Launcher for general files attachment
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "dosya_${System.currentTimeMillis()}"
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = it.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                }
            }
            viewModel.sendFile(uri = uri, fileName = name, fileSize = size)
        }
    }

    // Microphone audio permissions checker
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording()
        } else {
            Toast.makeText(context, "Mikrofon izni verilmediği için ses kaydedilemiyor.", Toast.LENGTH_SHORT).show()
        }
    }

    // Force auto scroll to end on receipt
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Chat Toolbar Title Header block
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.selectPeer(null) },
                modifier = Modifier.testTag("back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (peer.isInternetFallback) BrilliantLavender else ElegantGreen,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (peer.isInternetFallback) Icons.Default.Cloud else Icons.Default.Wifi,
                    contentDescription = "Type Indicator",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Active Speed Mode badging
                if (peer.isInternetFallback) {
                    Text(
                        "☁️ İnternet Yoluyla Aktarım",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrilliantLavender
                    )
                } else {
                    Text(
                        "⚡ Wi-Fi ile Süper Hızlı Aktarım Aktif",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElegantGreen
                    )
                }
            }
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant)

        // Empty state conversation canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Boş",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Sohbet ve Paylaşım Alanı",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Alt bardaki ikonları kullanarak ses kaydı, fotoğraf, dosya ya da düz metin göndermeye başlayın.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        // Recording Wave Indicator Overlay Banner
        AnimatedVisibility(
            visible = viewModel.isRecording,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CoralRose)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Kayıt Efekti",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        // Simple rotation or dynamic indicator
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "🔴 SESİNİZ KAYDEDİLİYOR... Göndermek için tekrar dokunun.",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Bottom Chat Send Toolbar with input actions
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach File symbol trigger
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("attach_file_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Ekle")
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Microphone recorder icon
                val recordColor = if (viewModel.isRecording) CoralRose else MaterialTheme.colorScheme.surfaceVariant
                IconButton(
                    onClick = {
                        val hasRecordPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasRecordPermission) {
                            if (viewModel.isRecording) {
                                viewModel.stopAndSendVoiceRecording()
                            } else {
                                viewModel.startVoiceRecording()
                            }
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier
                        .background(recordColor, CircleShape)
                        .testTag("microphone_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow, // Microphone or Play indicator representing voice call/mesaj
                        contentDescription = if (viewModel.isRecording) "Kaydı Bitir" else "Ses Kaydet",
                        tint = if (viewModel.isRecording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Text message Field
                OutlinedTextField(
                    value = txtInput,
                    onValueChange = { txtInput = it },
                    placeholder = { Text("Mesaj yazın...") },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 120.dp)
                        .testTag("chat_text_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Sender Dispatch Icon
                IconButton(
                    onClick = {
                        if (txtInput.trim().isNotEmpty()) {
                            viewModel.sendText(txtInput)
                            txtInput = ""
                        }
                    },
                    enabled = txtInput.trim().isNotEmpty(),
                    modifier = Modifier
                        .background(
                            if (txtInput.trim().isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Gönder",
                        tint = if (txtInput.trim().isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ==================== VISUAL BUBBLES WITH MEDIA PLAYER INLINE SUPPORT ====================
@Composable
fun MessageBubble(
    message: MessageEntity,
    viewModel: ChatViewModel
) {
    val isMe = message.sender == "me"
    val timestampStr = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            // Sender name display on peer incoming items
            if (!isMe) {
                Text(
                    text = if (message.peerId.startsWith("192.")) "Mesaj" else "Arkadaş",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            // Message Body canvas
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isMe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 16.dp
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Render specific visual layouts if file-based msg
                    if (message.fileName != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (message.isVoiceMessage) {
                                        viewModel.toggleVoicePlay(message)
                                    } else {
                                        if (message.filePath != null) {
                                            val file = File(message.filePath)
                                            if (file.exists()) {
                                                try {
                                                    val authority = "${context.packageName}.fileprovider"
                                                    val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        authority,
                                                        file
                                                    )
                                                    
                                                    val extension = file.extension.lowercase()
                                                    val mimeType = when (extension) {
                                                        "jpg", "jpeg" -> "image/jpeg"
                                                        "png" -> "image/png"
                                                        "gif" -> "image/gif"
                                                        "pdf" -> "application/pdf"
                                                        "txt" -> "text/plain"
                                                        "mp3", "wav", "m4a", "ogg", "aac", "3gp" -> "audio/*"
                                                        "mp4", "mkv", "avi", "mov" -> "video/*"
                                                        "zip", "rar" -> "application/zip"
                                                        "apk" -> "application/vnd.android.package-archive"
                                                        else -> "*/*"
                                                    }
                                                    
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(contentUri, mimeType)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Uyumlu bir görüntüleyici bulunamadı.", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Dosya bulunamadı.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Dosya indiriliyor veya yükleniyor olabilir. Lütfen bekleyin.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = if (message.isVoiceMessage) Icons.Default.PlayArrow else Icons.Default.Share,
                                contentDescription = "Dosya İkonu",
                                tint = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(message.fileSize),
                                    fontSize = 10.sp,
                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Play slider controller if visual vocal message record
                        if (message.isVoiceMessage && message.filePath != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val isPlayingThis = viewModel.currentlyPlayingMessageId == message.id
                            Button(
                                onClick = { viewModel.toggleVoicePlay(message) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlayingThis) CoralRose else MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis) Icons.Default.Refresh else Icons.Default.PlayArrow,
                                    contentDescription = "Oynatma",
                                    tint = if (isPlayingThis) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPlayingThis) "Durdur" else "Ses Kaydını Dinle",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPlayingThis) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        // Standard raw message text display content
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Footing Row with details and statuses
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = timestampStr,
                            fontSize = 9.sp,
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val statusIcon = when (message.status) {
                                "SENDING" -> Icons.Default.Refresh
                                "FAILED" -> Icons.Default.Warning
                                else -> Icons.Default.Check
                            }
                            val statusTint = when (message.status) {
                                "FAILED" -> CoralRose
                                "SENDING" -> WarningAmber
                                else -> MaterialTheme.colorScheme.onPrimary
                            }
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = "Aktarım Durumu",
                                tint = statusTint,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Formatter helper for bytes readable sizes
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups < 0 || digitGroups >= units.size) return "$bytes B"
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
