package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.ClientHubState
import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.DefaultContent
import io.github.aristheg201.svhub.content.HubComponent
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubPage
import io.github.aristheg201.svhub.content.HubTheme
import io.github.aristheg201.svhub.network.HubActionC2S
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import kotlin.math.roundToInt

class HubScreen(
    initialRoute: String = "home",
    private val overrideContent: HubContent? = null,
    private val returnTo: Screen? = null
) : Screen(Component.literal("SVHub")) {
    private var route = initialRoute
    private lateinit var search: EditBox
    private var tickCounter = 0L
    private var scrollOffset = 0
    private var contentHeight = 0
    private val hitTargets = mutableListOf<HubHitTarget>()
    private val gridPages = mutableMapOf<String, Int>()
    private val expanded = mutableSetOf<String>()
    private val history = mutableListOf(initialRoute)
    private var historyIndex = 0
    private var modelYaw = 0f
    private var modelZoom = 1f
    private var draggingModel = false

    private val content: HubContent
        get() = overrideContent ?: ClientHubState.playerContent ?: DefaultContent.create()

    override fun init() {
        val searchWidth = minOf(340, width - 180).coerceAtLeast(140)
        search = EditBox(font, width / 2 - searchWidth / 2, 9, searchWidth, 22, Component.literal("Tìm kiếm"))
        search.setHint(Component.literal("Tìm Pokémon, Fakemon, lệnh, hướng dẫn..."))
        search.setMaxLength(160)
        search.setResponder { scrollOffset = 0 }
        addRenderableWidget(search)
    }

    override fun tick() {
        tickCounter++
    }

    override fun onClose() {
        Minecraft.getInstance().setScreen(returnTo)
    }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val page = content.page(route)
        val theme = content.themeFor(page) ?: content.themes[content.defaultTheme] ?: HubTheme("fallback")
        PixelUi.background(gui, width, height, content, theme, tickCounter)
        hitTargets.clear()

        renderChrome(gui, theme, mouseX, mouseY)

        if (search.value.isNotBlank()) {
            HubSearchOverlay.render(gui, font, theme, search.value, width, 44, 12, ::navigate, hitTargets)
            super.render(gui, mouseX, mouseY, partialTick)
            return
        }

        when {
            route.startsWith("pokemon/") || route.startsWith("fakemon/") -> {
                val view = CobblemonWikiProvider.resolveRoute(route, content)
                if (view != null) PokemonDetailView.render(gui, font, view, theme, width, height, modelYaw, modelZoom)
                else renderNotFound(gui, theme, route)
            }
            route.startsWith("command/") -> if (!CommandDetailView.render(gui, font, route, theme, width, height)) renderNotFound(gui, theme, route)
            route.startsWith("mod/") -> if (!ModDetailView.render(gui, font, route, theme, width, height)) renderNotFound(gui, theme, route)
            else -> renderStaticPage(gui, page ?: content.page("home"), theme, mouseX, mouseY)
        }

        super.render(gui, mouseX, mouseY, partialTick)
    }

    private fun renderChrome(gui: GuiGraphics, theme: HubTheme, mouseX: Int, mouseY: Int) {
        gui.fill(0, 0, width, 39, 0xE80B0F18.toInt())
        val canBack = historyIndex > 0
        if (canBack) {
            val hovered = mouseX in 8 until 36 && mouseY in 7 until 33
            PixelUi.button(gui, 8, 7, 28, 26, hovered, theme)
            gui.drawCenteredString(font, "←", 22, 16, theme.palette.text)
            hitTargets += HubHitTarget(8, 7, 36, 33) { goBack() }
        }
        gui.drawString(font, "SV HUB", if (canBack) 44 else 12, 15, theme.palette.accent, true)
        if (ClientHubState.canEdit && overrideContent == null && width >= 520) {
            val x = width - 78
            val hovered = mouseX in x until x + 68 && mouseY in 8 until 31
            PixelUi.button(gui, x, 8, 68, 23, hovered, theme)
            gui.drawCenteredString(font, "Editor", x + 34, 16, theme.palette.text)
            hitTargets += HubHitTarget(x, 8, x + 68, 31) { io.github.aristheg201.svhub.client.SVHubClient.requestEditor() }
        }
    }

    private fun renderStaticPage(gui: GuiGraphics, page: HubPage?, theme: HubTheme, mouseX: Int, mouseY: Int) {
        val resolved = page ?: return renderNotFound(gui, theme, route)
        val sidebarWidth = if (width >= 760) 170 else 0
        if (sidebarWidth > 0) renderSidebar(gui, theme, sidebarWidth, mouseX, mouseY)
        val left = sidebarWidth + 24
        val right = width - 24
        val top = 48
        val bottom = height - 18
        val availableWidth = (right - left).coerceAtLeast(120)

        gui.enableScissor(left - 4, top, right + 4, bottom)
        var y = top + 4 - scrollOffset
        gui.drawString(font, resolved.title.resolve(content.defaultLocale), left, y, theme.palette.accent, true)
        y += 17
        val subtitle = resolved.subtitle.resolve(content.defaultLocale)
        if (subtitle.isNotBlank()) {
            font.split(Component.literal(subtitle), availableWidth).take(3).forEach { line ->
                gui.drawString(font, line, left, y, theme.palette.mutedText, false)
                y += 11
            }
            y += 5
        }

        val state = HubComponentRenderer.RenderState(
            content = content,
            theme = theme,
            font = font,
            width = width,
            mouseX = mouseX,
            mouseY = mouseY,
            tick = tickCounter,
            hits = hitTargets,
            gridPage = { id -> gridPages[id] ?: 0 },
            setGridPage = { id, value -> gridPages[id] = value; scrollOffset = scrollOffset.coerceAtLeast(0) },
            isExpanded = { id -> id in expanded },
            toggleExpanded = { id -> if (!expanded.add(id)) expanded.remove(id) },
            navigate = ::navigate,
            execute = ::executeComponent
        )

        resolved.components.forEach { component ->
            val consumed = HubComponentRenderer.render(gui, component, left, y, availableWidth, state)
            y += consumed
        }
        contentHeight = (y + scrollOffset - top + 18).coerceAtLeast(0)
        gui.disableScissor()

        val viewport = bottom - top
        val maxScroll = (contentHeight - viewport).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        if (maxScroll > 0) {
            val barHeight = ((viewport.toFloat() / contentHeight) * viewport).roundToInt().coerceAtLeast(24)
            val track = viewport - barHeight
            val barY = top + if (maxScroll == 0) 0 else ((scrollOffset.toFloat() / maxScroll) * track).roundToInt()
            gui.fill(width - 8, top, width - 5, bottom, 0x442B3448)
            gui.fill(width - 8, barY, width - 5, barY + barHeight, theme.palette.accent2)
        }
    }

    private fun renderSidebar(gui: GuiGraphics, theme: HubTheme, sidebarWidth: Int, mouseX: Int, mouseY: Int) {
        gui.fill(0, 39, sidebarWidth, height, 0xB80C111C.toInt())
        var y = 52
        content.pages.filter { it.showInNavigation }.take(18).forEach { page ->
            val active = page.route == route || page.id == route
            val hovered = mouseX in 8 until sidebarWidth - 8 && mouseY in y until y + 28
            if (active || hovered) {
                gui.fill(8, y, sidebarWidth - 8, y + 28, if (active) 0xAA314A68.toInt() else 0x66314158)
            }
            gui.drawString(font, font.plainSubstrByWidth(page.title.resolve(content.defaultLocale), sidebarWidth - 28), 16, y + 10, if (active) theme.palette.accent else theme.palette.text, active)
            hitTargets += HubHitTarget(8, y, sidebarWidth - 8, y + 28) { navigate(page.route) }
            y += 31
        }
    }

    private fun renderNotFound(gui: GuiGraphics, theme: HubTheme, missing: String) {
        val panelWidth = minOf(480, width - 48)
        val left = (width - panelWidth) / 2
        val top = maxOf(64, height / 3)
        PixelUi.panel(gui, left, top, panelWidth, 92, theme.palette.panel, theme.palette.danger)
        gui.drawCenteredString(font, "Không tìm thấy nội dung", width / 2, top + 24, theme.palette.danger)
        gui.drawCenteredString(font, font.plainSubstrByWidth(missing, panelWidth - 24), width / 2, top + 48, theme.palette.mutedText)
    }

    private fun executeComponent(component: HubComponent) {
        val action = component.action ?: return
        when (action.type) {
            "open_page" -> navigate(action.value)
            "back" -> goBack()
            "close" -> onClose()
            "copy_text" -> handleComponentClicked(
                Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, action.value))
            )
            "open_url" -> handleComponentClicked(
                Style.EMPTY.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, action.value))
            )
            else -> ClientPlayNetworking.send(HubActionC2S(action.id))
        }
    }

    private fun navigate(target: String) {
        val normalized = content.page(target)?.route ?: target
        if (normalized == route) return
        while (history.size > historyIndex + 1) history.removeAt(history.lastIndex)
        history += normalized
        historyIndex = history.lastIndex
        route = normalized
        scrollOffset = 0
        gridPages.clear()
        search.value = ""
        modelYaw = 0f
        modelZoom = 1f
    }

    private fun goBack() {
        if (historyIndex <= 0) return
        historyIndex--
        route = history[historyIndex]
        scrollOffset = 0
        search.value = ""
        modelYaw = 0f
        modelZoom = 1f
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            hitTargets.asReversed().firstOrNull { it.contains(mouseX, mouseY) }?.let { target ->
                target.action()
                return true
            }
            if ((route.startsWith("pokemon/") || route.startsWith("fakemon/")) && mouseY >= 54) {
                draggingModel = true
            }
        }
        if (button == 1 && historyIndex > 0) {
            goBack()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) draggingModel = false
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingModel && button == 0 && (route.startsWith("pokemon/") || route.startsWith("fakemon/"))) {
            modelYaw = (modelYaw + dragX.toFloat() * 1.3f) % 360f
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (route.startsWith("pokemon/") || route.startsWith("fakemon/")) {
            modelZoom = (modelZoom + verticalAmount.toFloat() * 0.08f).coerceIn(0.55f, 2.2f)
            return true
        }
        scrollOffset = (scrollOffset - (verticalAmount * 28.0).roundToInt()).coerceAtLeast(0)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if ((modifiers and 2) != 0 && keyCode == 70) {
            setFocused(search)
            search.isFocused = true
            return true
        }
        if (keyCode == 259 && historyIndex > 0 && !search.isFocused) {
            goBack()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
