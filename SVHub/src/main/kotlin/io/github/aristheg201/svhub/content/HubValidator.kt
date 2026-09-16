package io.github.aristheg201.svhub.content

data class ValidationResult(val errors: List<String>) {
    val ok: Boolean get() = errors.isEmpty()
    fun requireValid() { require(ok) { errors.joinToString("; ") } }
}

object HubValidator {
    private val id = Regex("^[a-z0-9_.:/-]{1,128}$")
    fun validate(content: HubContent): ValidationResult {
        val errors = mutableListOf<String>()
        if (content.schema != HUB_SCHEMA_VERSION) errors += "Unsupported schema ${content.schema}"
        if (content.defaultTheme !in content.themes && content.themes.isNotEmpty()) errors += "Unknown default theme ${content.defaultTheme}"
        val pageIds = mutableSetOf<String>(); val routes = mutableSetOf<String>(); val actions = mutableSetOf<String>()
        content.pages.forEach { page ->
            if (!id.matches(page.id)) errors += "Invalid page id ${page.id}"
            if (!pageIds.add(page.id)) errors += "Duplicate page id ${page.id}"
            if (!routes.add(page.route)) errors += "Duplicate route ${page.route}"
            val componentIds = mutableSetOf<String>()
            page.components.forEach { component ->
                if (!componentIds.add(component.id)) errors += "Duplicate component ${component.id} in ${page.id}"
                component.action?.let { action ->
                    if (!actions.add(action.id)) errors += "Duplicate action ${action.id}"
                    if (action.cooldownMs < 0) errors += "Negative cooldown ${action.id}"
                }
            }
        }
        val fakemonIds = mutableSetOf<String>()
        content.cobblemonWiki.explicitFakemon.forEach { if (!fakemonIds.add(it.id)) errors += "Duplicate Fakemon id ${it.id}" }
        return ValidationResult(errors)
    }
}
