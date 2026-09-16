package io.github.aristheg201.svhub.content

enum class InspectorValueType { TEXT, INTEGER, FLOAT, BOOLEAN, RESOURCE_ID, PAGE_ID, ACTION_TYPE }

data class InspectorFieldSpec(
    val key: String,
    val label: String,
    val type: InspectorValueType = InspectorValueType.TEXT,
    val maxLength: Int = 512,
    val placeholder: String = ""
)

data class ComponentInspectorSpec(
    val type: String,
    val fields: List<InspectorFieldSpec>,
    val supportsAction: Boolean = false
)

object ComponentInspectorSchemas {
    private val commonText = listOf(InspectorFieldSpec("text", "Nội dung", maxLength = 4096))

    private val specs = listOf(
        ComponentInspectorSpec("heading", commonText + InspectorFieldSpec("scale", "Scale", InspectorValueType.FLOAT, 16, "1.15")),
        ComponentInspectorSpec("text", commonText),
        ComponentInspectorSpec("markdown", commonText),
        ComponentInspectorSpec(
            "animated_text",
            commonText + listOf(
                InspectorFieldSpec("animation", "Animation", placeholder = "pixel_pop"),
                InspectorFieldSpec("scale", "Scale", InspectorValueType.FLOAT, 16, "1.25"),
                InspectorFieldSpec("align", "Align", placeholder = "left / center")
            )
        ),
        ComponentInspectorSpec("notice", commonText),
        ComponentInspectorSpec("badge", listOf(InspectorFieldSpec("text", "Nhãn"))),
        ComponentInspectorSpec("spacer", listOf(InspectorFieldSpec("height", "Chiều cao", InspectorValueType.INTEGER, 8, "16"))),
        ComponentInspectorSpec(
            "image",
            listOf(
                InspectorFieldSpec("asset", "Asset ID", InspectorValueType.RESOURCE_ID),
                InspectorFieldSpec("width", "Width", InspectorValueType.INTEGER, 8),
                InspectorFieldSpec("height", "Height", InspectorValueType.INTEGER, 8, "120")
            )
        ),
        ComponentInspectorSpec(
            "pixel_image",
            listOf(
                InspectorFieldSpec("asset", "Asset ID", InspectorValueType.RESOURCE_ID),
                InspectorFieldSpec("width", "Width", InspectorValueType.INTEGER, 8),
                InspectorFieldSpec("height", "Height", InspectorValueType.INTEGER, 8, "120")
            )
        ),
        ComponentInspectorSpec(
            "animated_image",
            listOf(
                InspectorFieldSpec("asset", "Asset ID", InspectorValueType.RESOURCE_ID),
                InspectorFieldSpec("frames", "Frames", InspectorValueType.INTEGER, 8, "1"),
                InspectorFieldSpec("fps", "FPS", InspectorValueType.INTEGER, 8, "8"),
                InspectorFieldSpec("width", "Width", InspectorValueType.INTEGER, 8),
                InspectorFieldSpec("height", "Height", InspectorValueType.INTEGER, 8, "64")
            )
        ),
        ComponentInspectorSpec(
            "button",
            listOf(
                InspectorFieldSpec("label", "Nhãn nút"),
                InspectorFieldSpec("description", "Mô tả", maxLength = 1024)
            ),
            supportsAction = true
        ),
        ComponentInspectorSpec(
            "link_card",
            listOf(
                InspectorFieldSpec("label", "Tiêu đề"),
                InspectorFieldSpec("description", "Mô tả", maxLength = 1024)
            ),
            supportsAction = true
        ),
        ComponentInspectorSpec(
            "command_card",
            listOf(
                InspectorFieldSpec("command", "Command"),
                InspectorFieldSpec("description", "Mô tả", maxLength = 1024)
            ),
            supportsAction = true
        ),
        ComponentInspectorSpec(
            "grid",
            listOf(
                InspectorFieldSpec("provider", "Provider", InspectorValueType.RESOURCE_ID),
                InspectorFieldSpec("pageSize", "Page size", InspectorValueType.INTEGER, 8, "18")
            )
        ),
        ComponentInspectorSpec(
            "collapse",
            listOf(
                InspectorFieldSpec("title", "Tiêu đề"),
                InspectorFieldSpec("text", "Nội dung", maxLength = 4096)
            )
        ),
        ComponentInspectorSpec(
            "pokemon_model",
            listOf(
                InspectorFieldSpec("species", "Species", InspectorValueType.RESOURCE_ID, 256, "cobblemon:pikachu"),
                InspectorFieldSpec("aspects", "Aspects", maxLength = 1024),
                InspectorFieldSpec("size", "Size", InspectorValueType.INTEGER, 8, "128")
            )
        )
    ).associateBy { it.type }

    fun forType(type: String): ComponentInspectorSpec = specs[type] ?: ComponentInspectorSpec(type, commonText)
    fun knownTypes(): Set<String> = specs.keys
}
