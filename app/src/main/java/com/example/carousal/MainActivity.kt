@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.carousal
import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appversal.appstorys.ui.OverlayContainer
import com.appversal.appstorys.utils.appstorys
import com.example.carousal.ui.theme.CarousalTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.appversal.appstorys.ui.CardScratch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarousalTheme {
                Box {
                    MyApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApp() {
    val context = LocalContext.current
    val campaignManager = App.appStorys
    val app = LocalContext.current.applicationContext as App
    val screenName by app.screenNameNavigation.collectAsState()
    var currentScreen by remember { mutableStateOf("HomeScreen") }

    var selectedTab by remember { mutableStateOf(0) } // Track selected tab index

    var confettiTrigger by remember { mutableStateOf(0) }
    var wasFullyScratched by remember { mutableStateOf(false) }
    var isPresented by remember { mutableStateOf(false) }

    LaunchedEffect(screenName) {
        if (screenName.isNotEmpty()) {
            when (screenName) {
                "PayScreen" -> {
                    selectedTab = 1 // Set to PayScreen tab
                    currentScreen = "HomeScreen" // Keep normal navigation
                }
                "HomeScreen" -> {
                    selectedTab = 0
                    currentScreen = "HomeScreen"
                }
                else -> {
                    currentScreen = screenName // For other screens
                }
            }
            app.resetNavigation()
        }
    }

    var edgeToEdgePadding by remember { mutableStateOf(PaddingValues()) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFFFAF8F9),

            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.topbar),
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .height(56.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0752ad),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                )
            },


            bottomBar = {
                BottomNavigationBar(selectedTab) { newIndex -> selectedTab = newIndex }
            }
        ) { innerPadding ->
            edgeToEdgePadding = innerPadding
//            if (currentScreen == "PayScreen") {
//                PayScreen(innerPadding)
//            } else {
                when (selectedTab) {
                    0 -> HomeScreen(
                        innerPadding,
                        isPresented = isPresented,
                        onIsPresentedChange = { isPresented = it }
                    )
                    1 -> PayScreen(innerPadding)
                }
//            }
        }

        App.appStorys.overlayElements(
            topPadding = 70.dp,
            bottomPadding = 70.dp,
            activity = LocalContext.current as Activity
        )
    }
}

@Composable
fun CopyUserIdText() {
    val campaignManager = App.appStorys
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val userId = campaignManager.getUserId()

    Text(
        text = userId,
        modifier = androidx.compose.ui.Modifier.clickable {
            clipboardManager.setText(AnnotatedString(userId))
            Toast.makeText(context, "User ID copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    )
}

@Composable
fun HomeScreen(
    padding: PaddingValues,
    isPresented: Boolean,
    onIsPresentedChange: (Boolean) -> Unit
    ) {
    val context = LocalContext.current
    val campaignManager = App.appStorys

    // State variables for input fields
    var input1 by remember { mutableStateOf("") }
    var input2 by remember { mutableStateOf("") }

    var eventInput1 by remember { mutableStateOf("") }
    var eventInput2 by remember { mutableStateOf("") }
    var eventInput3 by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val screenName  = "Home Screen"
        val positions = listOf("widget_one")
        campaignManager.getScreenCampaigns(
            screenName,
            positions,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFf1f2f4))
    ) {
        val coroutineScope = rememberCoroutineScope()
        // Scrollable Column using LazyColumn
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .appstorys("lazy_column")
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                ), // Add this line,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Image(
                    painter = painterResource(id = R.drawable.home_one),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .fillMaxWidth(),
//                        .clickable { showBottomSheet = true },
                    contentScale = ContentScale.Fit
                )

                campaignManager.Stories()

                CopyUserIdText()

                campaignManager.Widget(
                    modifier = Modifier.appstorys("tooltip_home"),
//                    position = null
                )

                campaignManager.Widget(
                    modifier = Modifier.fillMaxWidth().appstorys("tooltip_home_prem_test"),
                    placeholder = context.getDrawable(R.drawable.ic_launcher_foreground),
                    position = "widget_one",
                )

                campaignManager.Widget(
                    modifier = Modifier.fillMaxWidth().appstorys("tooltip_home_prem_test"),
                    placeholder = context.getDrawable(R.drawable.ic_launcher_foreground),
                    position = "widget_two",
                )

                // NEW: Scratch Card Button
                Box(
                    modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
//                        .padding(top = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            campaignManager.trackEvents(event =  "triggerScratchCard")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_dialog_info),
                            contentDescription = "Scratch Card",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Open Scratch Card")
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Button(
                        onClick = {
                            campaignManager.trackEvents(
//                                event = "Login"
                                event = "NewEventTest"
                            )
                        },
                        modifier = Modifier.appstorys("anuridhtest")
                    ) {
                        Text("Open Bottom Sheet")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = eventInput1,
                            onValueChange = { eventInput1 = it },
                            label = { Text("Event Input 1") },
                            placeholder = { Text("Enter value for name") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = Color.Black,
                                unfocusedLabelColor = Color.Gray
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = eventInput2,
                            onValueChange = { eventInput2 = it },
                            label = { Text("Event Input 2") },
                            placeholder = { Text("Enter value for age") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.Black,
                                unfocusedTextColor = Color.Black,
                                focusedLabelColor = Color.Black,
                                unfocusedLabelColor = Color.Gray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            campaignManager.trackEvents(
                                event = "Login",
                                metadata = mapOf(
                                    "name" to eventInput1,
                                    "age" to eventInput2
                                )
                            )
                        },
                        modifier = Modifier
                    ) {
                        Text("Login Event")
                    }
                }


                Button(
                    onClick = {
                        campaignManager.trackEvents(
                            event = "Added to cart"
                        )
                    },
                    modifier = Modifier.appstorys("toolbar")
                ) {
                    Text("Added to cart Event")
                }

                Button(
                    onClick = {
                        campaignManager.trackEvents(
                            event = "Purchased"
                        )
                    },
                    modifier = Modifier
                ) {
                    Text("Purchased Event")
                }

                Button(
                    onClick = {
                        campaignManager.trackEvents(
                            event = "Logout",
                        )
                    },
                    modifier = Modifier
                ) {
                    Text("Logout Event")
                }

                Button(
                    onClick = {
                        campaignManager.trackEvents(
                            event = "AppStorys Success"
                        )
                    },
                    modifier = Modifier
                ) {
                    Text("AppStorys Success Event")
                }

                Spacer(
                    Modifier.height(30.dp)
                )

                // First user property input and button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input1,
                        onValueChange = { input1 = it },
                        label = { Text("Input 1") },
                        placeholder = { Text("Enter value for key_one") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    Button(
                        onClick = {
                            campaignManager.setUserProperties(
                                mapOf("key_one" to input1)
                            )
                            Toast.makeText(context, "User property set: key_one = $input1", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                    ) {
                        Text("Set Property 1")
                    }
                }

                // Second user property input and button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input2,
                        onValueChange = { input2 = it },
                        label = { Text("Input 2") },
                        placeholder = { Text("Enter value for key_two") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedLabelColor = Color.Black,
                            unfocusedLabelColor = Color.Gray
                        )
                    )

                    Button(
                        onClick = {
                            campaignManager.setUserProperties(
                                mapOf("key_two" to input2)
                            )
                            Toast.makeText(context, "User property set: key_two = $input2", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                    ) {
                        Text("Set Property 2")
                    }
                }



                campaignManager.Reels()

                Image(
                    painter = painterResource(id = R.drawable.home_two),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .fillMaxWidth().appstorys("app_logo"),
                    contentScale = ContentScale.Fit
                )

            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PayScreen(padding: PaddingValues) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            .background(Color(0xFFf1f2f4)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavigationButton(
                    text = "Cashbook",
                    isSelected = pagerState.currentPage == 0,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0)
                        }
                    }
                )

                NavigationButton(
                    text = "Bills",
                    isSelected = pagerState.currentPage == 1,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    }
                )

                NavigationButton(
                    text = "Items",
                    isSelected = pagerState.currentPage == 2,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    }
                )
            }

            // Horizontal pager for screens
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PayScreenPage(
                        topImages = listOf(
                            Triple(R.drawable.more_one, "cashbook", "Cashbook"),
                            Triple(R.drawable.more_two, "bills", "Bills"),
                            Triple(R.drawable.more_three, "items", "Items")
                        ),
                        bottomImage = R.drawable.more_bottom,
                        buttonText = "Cashbook Tab",
//                        campaignManager = campaignManager,
                        screenType = "cashbook"
                    )

                    1 -> PayScreenPage(
                        topImages = listOf(
                            Triple(R.drawable.more_one, "cashbook", "Cashbook"),
                            Triple(R.drawable.more_three, "items", "Items"),
                            Triple(R.drawable.more_two, "bills", "Bills")
                        ),
                        bottomImage = R.drawable.more_bottom,
                        buttonText = "Bills Tab",
//                        campaignManager = campaignManager,
                        screenType = "bills"
                    )

                    2 -> PayScreenPage(
                        topImages = listOf(
                            Triple(R.drawable.more_three, "items", "Items"),
                            Triple(R.drawable.more_one, "cashbook", "Cashbook"),
                            Triple(R.drawable.more_two, "bills", "Bills")
                        ),
                        bottomImage = R.drawable.more_bottom,
                        buttonText = "Items Tab",
                        screenType = "items"
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .padding(horizontal = 4.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun PayScreenPage(
    topImages: List<Triple<Int, String, String>>, // resourceId, appstorys tag, contentDescription
    bottomImage: Int,
    buttonText: String,
    screenType: String
) {
    val campaignManager = App.appStorys
    val imageTags = topImages.map { it.second }

    LaunchedEffect(buttonText) {
        campaignManager.getScreenCampaigns(
            buttonText,
            listOf()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top row with three images
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                topImages.forEach { (imageRes, tag, description) ->
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = description,
                        modifier = Modifier
                            .weight(1f)
                            .appstorys(tag),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom image
            Image(
                painter = painterResource(id = bottomImage),
                contentDescription = "Bottom Image",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action button
            Button(
                onClick = {
                },
                modifier = Modifier

                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(buttonText)
            }

            Text(buttonText)
        }
    }

}

@Composable
fun BottomNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White, // Add this line to set the background color to white
        modifier = Modifier.fillMaxWidth().height(70.dp)

    ) {
        val items = listOf("Parties", "More")
        val icons = listOf(Icons.Filled.Person, Icons.Filled.List)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround // Adjust spacing here
        ) {
            items.forEachIndexed { index, title ->
                NavigationBarItem(
//                    modifier = if (index == 0) Modifier.appstorys("tooltip_home") else Modifier,
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    icon = {
                        Icon(
                            modifier = Modifier.size(24.dp), // Apply modifier from ToolTipWrapper
                            imageVector = icons[index],
                            contentDescription = title,
                            tint = if (selectedTab == index) Color(0xFF186fd9) else Color.Gray
                        )
//                        }
                    },
                    label = {
                        Text(
                            title,
                            color = if (selectedTab == index) Color(0xFF186fd9) else Color.Gray
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF01C198),
                        unselectedIconColor = Color.Gray,
                        indicatorColor = Color.Transparent // Remove default background
                    )
                )
            }
        }
    }
}
