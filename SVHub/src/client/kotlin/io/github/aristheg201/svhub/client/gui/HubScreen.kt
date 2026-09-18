package io.github.aristheg201.svhub.client.gui

import io.github.aristheg201.svhub.client.ClientHubState
import io.github.aristheg201.svhub.client.cobblemon.CobblemonWikiProvider
import io.github.aristheg201.svhub.client.render.MiniMessageText
import io.github.aristheg201.svhub.client.render.PixelUi
import io.github.aristheg201.svhub.content.DefaultContent
import io.github.aristheg201.svhub.content.HubComponent
import io.github.aristheg201.svhub.content.HubContent
import io.github.aristheg201.svhub.content.HubPage
import io.github.aristheg201.svhub.content.HubTheme
import io.github.aristheg201.svhub.network.HubActionC2S
import io.github.aristheg201.svhub.ui.ScrollbarLayout
import io.github.aristheg201.svhub.ui.ScrollbarMetrics
import io.github.aristheg201.svhub.ui.UiRect
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
) : SVHubScreen(Component.literal("SVHub")) {
    private var route = initialRoute
    private lateinit var search: EditBox
    private var searchX = 0
    private var searchWidth = 0
    private var tickCounter = 0L
    private var scrollOffset = 0
    private var contentHeight = 0
    private var sidebarScroll = 0
    private var sidebarMaxScroll = 0
    private var sidebarScrollbar: ScrollbarMetrics? = null
    private var draggingSidebarScrollbar = false
    private var sidebarDragOffset = 0.0
    private var detailScroll = 0
    private var detailMaxScroll = 0
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
        searchWidth = minOf(360, width - 220).coerceAtLeast(130)
        searchX = width / 2 - searchWidth / 2
        search = EditBox(font, searchX + 7, 10, searchWidth - 14, 22, Component.translatable("gui.svhub.search"))
        search.setHint(Component.translatable("gui.svhub.search_hint"))
        search.setMaxLength(160)
        search.setBordered(false)
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
            HubSearchOverlay.render(gui, font, theme, search.value, width, CHROME_HEIGHT + 7, 12, ::navigate, hitTargets)
            super.render(gui, mouseX, mouseY, partialTick)
            return
        }

        when {
            route.startsWith("pokemon/") || route.startsWith("fakemon/") -> {
                val view = CobblemonWikiProvider.resolveRoute(route, content)
                if (view != null) {
                    detailMaxScroll = PokemonDetailView.render(
                        gui, font, view, theme, width, height, modelYaw, modelZoom, detailScroll
                    )
                    detailScroll = detailScroll.coerceIn(0, detailMaxScroll)
                } else {
                    detailMaxScroll = 0
                    renderNotFound(gui, theme, route)
                }
            }
            route.startsWith("command/") -> if (!CommandDetailView.render(gui, font, route, theme, width, height, CHROME_HEIGHT + 10)) renderNotFound(gui, theme, route)
            else -> renderStaticPage(gui, page ?: content.page("home"), theme, mouseX, mouseY)
        }

        super.render(gui, mouseX, mouseY, partialTick)
    }

    private fun renderChrome(gui: GuiGraphics, theme: HubTheme, mouseX: Int, mouseY: Int) {
        val p = theme.palette
        gui.fill(0, 0, width, CHROME_HEIGHT, PixelUi.withAlpha(p.panel, 248))
        gui.fill(0, CHROME_HEIGHT - 3, width, CHROME_HEIGHT, p.accent)
        gui.fill(0, CHROME_HEIGHT - 1, width, CHROME_HEIGHT, p.accent2)

        val canBack = historyIndex > 0
        if (canBack) {
            val hovered = mouseX in 8 until 36 && mouseY in 8 until 34
            PixelUi.button(gui, 8, 8, 28, 26, hovered, theme)
            gui.drawCenteredString(font, "←", 22, 17, p.text)
            hitTargets += HubHitTarget(8, 8, 36, 34) { goBack() }
        }
        MiniMessageText.draw(
            gui,
            font,
            "<bold><color:#2E7168>SV</color> <color:#C58A35>HUB</color></bold>",
            if (canBack) 45 else 13,
            17,
            p.accent
        )

        PixelUi.panel(gui, searchX, 7, searchWidth, 28, p.panelAlt, if (search.isFocused) p.accent else PixelUi.withAlpha(p.accent, 95))
        search.setTextColor(p.text)
        search.setTextColorUneditable(p.mutedText)

        if (ClientHubState.canEdit && overrideContent == null && width >= 560) {
            val x = width - 82
            val hovered = mouseX in x until x + 72 && mouseY in 8 until 34
            PixelUi.button(gui, x, 8, 72, 26, hovered, theme)
            gui.drawCenteredString(font, Component.translatable("gui.svhub.editor"), x + 36, 17, p.text)
            hitTargets += HubHitTarget(x, 8, x + 72, 34) { io.github.aristheg201.svhub.client.SVHubClient.requestEditor() }
        }
    }

    private fun renderStaticPage(gui: GuiGraphics, page: HubPage?, theme: HubTheme, mouseX: Int, mouseY: Int) {
        val resolved = page ?: return renderNotFound(gui, theme, route)
        val sidebarWidth = if (width >= 760) 184 else 0
        if (sidebarWidth > 0) renderSidebar(gui, theme, sidebarWidth, mouseX, mouseY)
        val left = sidebarWidth + 24
        val right = width - 24
        val top = CHROME_HEIGHT + 10
        val bottom = height - 18
        val availableWidth = (right - left).coerceAtLeast(120)

        gui.enableScissor(left - 4, top, right + 4, bottom)
        var y = top + 2 - scrollOffset

        val title = resolved.title.resolve(content.defaultLocale)
        val subtitle = resolved.subtitle.resolve(content.defaultLocale)
        val subtitleLines = if (subtitle.isBlank()) emptyList() else MiniMessageText.split(font, subtitle, availableWidth - 28).take(4)
        val heroHeight = 35 + subtitleLines.size * 12
        PixelUi.panel(gui, left, y, availableWidth, heroHeight, theme.palette.panel, theme.palette.accent)
        gui.fill(left, y, left + 6, y + heroHeight, theme.palette.accent)
        MiniMessageText.draw(gui, font, title, left + 16, y + 10, theme.palette.accent, true)
        var heroY = y + 25
        subtitleLines.forEach { line ->
            gui.drawString(font, line, left + 16, heroY, theme.palette.mutedText, false)
            heroY += 12
        }
        y += heroHeight + 12

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
            y += HubComponentRenderer.render(gui, component, left, y, availableWidth, state)
        }
        contentHeight = (y + scrollOffset - top + 18).coerceAtLeast(0)
        gui.disableScissor()

        val viewport = bottom - top
        val maxScroll = (contentHeight - viewport).coerceAtLeast(0)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        if (maxScroll > 0) {
            val barHeight = ((viewport.toFloat() / contentHeight) * viewport).roundToInt().coerceAtLeast(24)
            val track = viewport - barHeight
            val barY = top + ((scrollOffset.toFloat() / maxScroll) * track).roundToInt()
            gui.fill(width - 8, top, width - 5, bottom, PixelUi.withAlpha(theme.palette.mutedText, 55))
            gui.fill(width - 8, barY, width - 5, barY + barHeight, theme.palette.accent)
        }
    }

    private fun renderSidebar(gui: GuiGraphics, theme: HubTheme, sidebarWidth: Int, mouseX: Int, mouseY: Int) {
        val p = theme.palette
        val pages = content.pages.filter { it.showInNavigation }
        val viewportTop = CHROME_HEIGHT + 32
        val viewportBottom = (height - 8).coerceAtLeast(viewportTop + 1)
        val viewportHeight = viewportBottom - viewportTop
        val contentPixels = pages.size * 32
        sidebarMaxScroll = (contentPixels - viewportHeight).coerceAtLeast(0)
        sidebarScroll = sidebarScroll.coerceIn(0, sidebarMaxScroll)
        gui.fill(0, CHROME_HEIGHT, sidebarWidth, height, PixelUi.withAlpha(p.panel, 242))
        gui.fill(sidebarWidth - 2, CHROME_HEIGHT, sidebarWidth, height, PixelUi.withAlpha(p.accent, 65))
        gui.drawString(font, Component.translatable("gui.svhub.sidebar.contents"), 16, CHROME_HEIGHT + 13, p.mutedText, true)
        gui.enableScissor(0, viewportTop, sidebarWidth, viewportBottom)
        var y = viewportTop - sidebarScroll
        pages.forEach { page ->
            if (y + 29 > viewportTop && y < viewportBottom) {
                val active = page.route == route || page.id == route
                val hovered = mouseX in 8 until sidebarWidth - 12 && mouseY in maxOf(y, viewportTop) until minOf(y + 29, viewportBottom)
                if (active || hovered) {
                    gui.fill(8, y, sidebarWidth - 12, y + 29, if (active) p.panelAlt else PixelUi.withAlpha(p.panelAlt, 175))
                    gui.fill(8, y, 12, y + 29, if (active) p.accent else p.accent2)
                }
                val parsed = MiniMessageText.component(page.title.resolve(content.defaultLocale))
                val line = font.split(parsed, sidebarWidth - 40).firstOrNull()
                if (line != null) gui.drawString(font, line, 18, y + 10, if (active) p.accent else p.text, active)
                hitTargets += HubHitTarget(8, maxOf(y, viewportTop), sidebarWidth - 12, minOf(y + 29, viewportBottom)) { navigate(page.route) }
            }
            y += 32
        }
        gui.disableScissor()
        val metrics=ScrollbarLayout.resolve(UiRect(0,viewportTop,sidebarWidth,viewportHeight),contentPixels,sidebarScroll)
        sidebarScrollbar=metrics
        sidebarMaxScroll=metrics?.maxScroll?:0
        sidebarScroll=metrics?.clampedScroll?:0
        if(metrics!=null){
            val track=metrics.visualTrack
            val thumb=metrics.visualThumb
            gui.fill(track.x,track.y,track.right,track.bottom,PixelUi.withAlpha(p.panelAlt,210))
            gui.fill(thumb.x,thumb.y,thumb.right,thumb.bottom,if(draggingSidebarScrollbar)p.accent2 else p.accent)
        }
    }

    private fun ensureSidebarRouteVisible() {
        if (width < 760) return
        val pages = content.pages.filter { it.showInNavigation }
        val index = pages.indexOfFirst { it.route == route || it.id == route }
        if (index < 0) return
        val viewportHeight = ((height - 8) - (CHROME_HEIGHT + 32)).coerceAtLeast(1)
        sidebarMaxScroll = (pages.size * 32 - viewportHeight).coerceAtLeast(0)
        val itemTop = index * 32
        val itemBottom = itemTop + 29
        sidebarScroll = when {
            itemTop < sidebarScroll -> itemTop
            itemBottom > sidebarScroll + viewportHeight -> itemBottom - viewportHeight
            else -> sidebarScroll
        }.coerceIn(0, sidebarMaxScroll)
    }

    private fun setSidebarScrollFromThumb(mouseY: Double) {
        val metrics=sidebarScrollbar?:return
        sidebarScroll=ScrollbarLayout.scrollFromPointer(metrics,mouseY,sidebarDragOffset)
    }

    private fun renderNotFound(gui: GuiGraphics, theme: HubTheme, missing: String) {
        val panelWidth = minOf(480, width - 48)
        val left = (width - panelWidth) / 2
        val top = maxOf(CHROME_HEIGHT + 24, height / 3)
        PixelUi.panel(gui, left, top, panelWidth, 92, theme.palette.panel, theme.palette.danger)
        gui.drawCenteredString(font, Component.translatable("gui.svhub.not_found"), width / 2, top + 24, theme.palette.danger)
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
        resetViewState()
        ensureSidebarRouteVisible()
    }

    private fun goBack() {
        if (historyIndex <= 0) return
        historyIndex--
        route = history[historyIndex]
        resetViewState()
        ensureSidebarRouteVisible()
    }

    private fun resetViewState() {
        scrollOffset = 0
        detailScroll = 0
        detailMaxScroll = 0
        gridPages.clear()
        search.value = ""
        modelYaw = 0f
        modelZoom = 1f
        draggingModel = false
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            val scrollbar=sidebarScrollbar
            if(width>=760&&scrollbar!=null&&scrollbar.hitRect.contains(mouseX,mouseY)){
                if(mouseY>=scrollbar.thumbTop&&mouseY<scrollbar.thumbBottom){
                    draggingSidebarScrollbar=true
                    sidebarDragOffset=mouseY-scrollbar.thumbTop
                }else{
                    sidebarDragOffset=(scrollbar.thumbBottom-scrollbar.thumbTop)/2.0
                    setSidebarScrollFromThumb(mouseY)
                    draggingSidebarScrollbar=true
                }
                return true
            }
            hitTargets.asReversed().firstOrNull { it.contains(mouseX, mouseY) }?.let { target ->
                target.action()
                return true
            }
            if ((route.startsWith("pokemon/") || route.startsWith("fakemon/")) &&
                PokemonDetailView.isModelArea(width, mouseX, mouseY, height)) {
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
        if (button == 0) {
            draggingModel = false
            draggingSidebarScrollbar = false
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (draggingSidebarScrollbar && button == 0) {
            setSidebarScrollFromThumb(mouseY)
            return true
        }
        if (draggingModel && button == 0 && (route.startsWith("pokemon/") || route.startsWith("fakemon/"))) {
            modelYaw = (modelYaw + dragX.toFloat() * 1.3f) % 360f
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (width >= 760 && mouseX >= 0.0 && mouseX < 184.0 && mouseY >= CHROME_HEIGHT + 32 && mouseY < height - 8 && sidebarMaxScroll > 0) {
            sidebarScroll = (sidebarScroll - (verticalAmount * 28.0).roundToInt()).coerceIn(0, sidebarMaxScroll)
            return true
        }
        if (route.startsWith("pokemon/") || route.startsWith("fakemon/")) {
            if (PokemonDetailView.isModelArea(width, mouseX, mouseY, height)) {
                modelZoom = (modelZoom + verticalAmount.toFloat() * 0.08f).coerceIn(0.55f, 2.2f)
            } else {
                detailScroll = (detailScroll - (verticalAmount * 28.0).roundToInt()).coerceIn(0, detailMaxScroll)
            }
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

    companion object {
        private const val CHROME_HEIGHT = 42
    }
}
