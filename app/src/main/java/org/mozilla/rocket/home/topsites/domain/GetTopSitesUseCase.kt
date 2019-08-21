package org.mozilla.rocket.home.topsites.domain

import org.mozilla.rocket.home.topsites.data.TopSitesRepo
import org.mozilla.rocket.home.topsites.ui.Site
import java.util.Locale

class GetTopSitesUseCase(private val topSitesRepo: TopSitesRepo) {

    private val fixedSites: List<org.mozilla.focus.history.model.Site> by lazy { topSitesRepo.getFixedSites() }

    operator fun invoke(callback: (List<Site>) -> Unit) {
        val pinnedSites = topSitesRepo.getPinnedSites()
        val defaultSites = topSitesRepo.getDefaultSites()

        topSitesRepo.getHistorySitesAsync { historySites ->
            callback(
                composeTopSites(
                    fixedSites,
                    pinnedSites,
                    defaultSites,
                    historySites
                )
            )
        }
    }

    private fun composeTopSites(
        fixedSites: List<org.mozilla.focus.history.model.Site>,
        pinnedSites: List<org.mozilla.focus.history.model.Site>,
        defaultSites: List<org.mozilla.focus.history.model.Site>,
        historySites: List<org.mozilla.focus.history.model.Site>
    ): List<Site> {
        val result = fixedSites.toFixedSite() +
                pinnedSites.toRemovableSite(topSitesRepo) +
                mergeHistoryAndDefaultSites(defaultSites, historySites).toRemovableSite(topSitesRepo)

        return result.distinctBy { removeUrlPostSlash(it.url).toLowerCase(Locale.getDefault()) }
                .take(TOP_SITES_SIZE)
    }

    private fun mergeHistoryAndDefaultSites(
        defaultSites: List<org.mozilla.focus.history.model.Site>,
        historySites: List<org.mozilla.focus.history.model.Site>
    ): List<org.mozilla.focus.history.model.Site> {
        val union = defaultSites + historySites
        val merged = union.groupBy { removeUrlPostSlash(it.url).toLowerCase(Locale.getDefault()) }
                .map {
                    val sameSiteGroup = it.value
                    if (sameSiteGroup.size == 1) {
                        sameSiteGroup.first()
                    } else {
                        var viewCount = 0L
                        var lastViewTimestamp = 0L
                        sameSiteGroup.forEach { site ->
                            viewCount += site.viewCount
                            if (site.lastViewTimestamp > lastViewTimestamp) {
                                lastViewTimestamp = site.lastViewTimestamp
                            }
                        }
                        // use default site if it exists
                        sameSiteGroup.first().apply {
                            setViewCount(viewCount)
                            setLastViewTimestamp(lastViewTimestamp)
                        }
                    }
                }

        return merged.sortedWith(
            compareBy<org.mozilla.focus.history.model.Site> { it.viewCount }.thenBy { it.lastViewTimestamp }
        ).reversed()
    }

    private fun removeUrlPostSlash(url: String): String =
            if (url.isNotEmpty() && url[url.length - 1] == '/') {
                url.dropLast(1)
            } else {
                url
            }

    companion object {
        private const val TOP_SITES_SIZE = 16
    }
}

private fun List<org.mozilla.focus.history.model.Site>.toFixedSite(): List<Site> =
        map { it.toFixedSite() }

private fun org.mozilla.focus.history.model.Site.toFixedSite(): Site =
        Site.FixedSite(
            id = id,
            title = title,
            url = url,
            iconUri = favIconUri,
            viewCount = viewCount,
            lastViewTimestamp = lastViewTimestamp
        )

private fun List<org.mozilla.focus.history.model.Site>.toRemovableSite(topSitesRepo: TopSitesRepo): List<Site> =
        map { it.toRemovableSite(topSitesRepo) }

private fun org.mozilla.focus.history.model.Site.toRemovableSite(topSitesRepo: TopSitesRepo): Site =
        Site.RemovableSite(
            id = id,
            title = title,
            url = url,
            iconUri = favIconUri,
            viewCount = viewCount,
            lastViewTimestamp = lastViewTimestamp,
            isDefault = isDefault,
            isPinned = topSitesRepo.isPinned(this)
        )