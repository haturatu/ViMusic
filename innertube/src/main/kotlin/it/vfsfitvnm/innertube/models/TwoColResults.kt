package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class TwoColResults (
    val secondaryContents: SecondaryContents?,
    val tabs: List<Tabs.Tab>?
) {
    @Serializable
    data class SecondaryContents (
        val sectionListRenderer: SectionListRenderer?
    )
}