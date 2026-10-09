# -*- coding: utf-8 -*-
import io
p = 'app/src/main/java/com/example/MainActivity.kt'
s = io.open(p, encoding='utf-8').read()

# 1) Bottom bar: ítems con píldora animada
old = '''                        bottomBar = {
                            // La barra de navegación se oculta con el teclado abierto
                            if (imeBottom == 0) {
                                NavigationBar(
                                    containerColor = Slate900,
                                    contentColor = CyanNeon
                                ) {
                                    NavigationTab.values().forEach { tab ->
                                        val isSelected = currentTab == tab
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(tab.icon, contentDescription = tab.label)
                                            },
                                            label = {
                                                Text(
                                                    tab.label,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) CyanNeon else Slate400
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = CyanNeon,
                                                selectedTextColor = CyanNeon,
                                                indicatorColor = Slate800,
                                                unselectedIconColor = Slate400,
                                                unselectedTextColor = Slate400
                                            ),
                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }'''
new = '''                        bottomBar = {
                            // La barra de navegación se oculta con el teclado abierto
                            if (imeBottom == 0) {
                                NavigationBar(
                                    containerColor = Slate900,
                                    contentColor = CyanNeon,
                                    tonalElevation = 0.dp
                                ) {
                                    NavigationTab.values().forEach { tab ->
                                        val isSelected = currentTab == tab
                                        val pillColor by animateColorAsState(
                                            targetValue = if (isSelected) CyanNeon.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
                                            animationSpec = tween(220),
                                            label = "nav_pill_${tab.name}"
                                        )
                                        val iconTint by animateColorAsState(
                                            targetValue = if (isSelected) CyanNeon else Slate400,
                                            animationSpec = tween(220),
                                            label = "nav_icon_${tab.name}"
                                        )
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(pillColor)
                                                        .padding(horizontal = 14.dp, vertical = 5.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        tab.icon,
                                                        contentDescription = tab.label,
                                                        tint = iconTint,
                                                        modifier = Modifier.size(21.dp)
                                                    )
                                                }
                                            },
                                            label = {
                                                Text(
                                                    tab.label,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    letterSpacing = 0.1.sp,
                                                    color = if (isSelected) CyanNeon else Slate400
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = CyanNeon,
                                                selectedTextColor = CyanNeon,
                                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                                unselectedIconColor = Slate400,
                                                unselectedTextColor = Slate400
                                            ),
                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }'''
assert old in s, "nav"
s = s.replace(old, new, 1)

io.open(p, 'w', encoding='utf-8').write(s)
print("Nav OK")
