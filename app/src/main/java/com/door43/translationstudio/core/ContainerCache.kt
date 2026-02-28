package com.door43.translationstudio.core

import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Link
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.resourcecontainer.errors.InvalidRCException
import org.unfoldingword.tools.logger.Logger
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides a cache of resource containers.
 * This should usually only be used to load source containers since they will not change very often.
 */
object ContainerCache {
    /**
     * A map of cached containers
     */
    private val resourceContainers = ConcurrentHashMap<String, ResourceContainer>()

    /**
     * A set of container slugs that have already been searched for
     */
    private val inspectedContainers: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * A set of container slugs that are currently being inspected
     */
    private val loadingContainers: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Empties the cache
     */
    fun empty() {
        resourceContainers.clear()
        inspectedContainers.clear()
        loadingContainers.clear()
    }

    /**
     * Caches resource container if it exists.
     * If the container has already been cached it will not touch the disk.
     */
    fun cache(client: Door43Client, resourceContainerSlug: String): ResourceContainer? {
        // wait for other threads
        waitForLoadingContainers(resourceContainerSlug)

        // check cache
        resourceContainers[resourceContainerSlug]?.let { return it }

        // load from disk once
        if (!inspectedContainers.contains(resourceContainerSlug)) {
            // flag as loading
            loadingContainers.add(resourceContainerSlug)
            try {
                val rc = client.open(resourceContainerSlug)
                resourceContainers[rc.slug] = rc
                return rc
            } catch (e: InvalidRCException) {
                Logger.w("ContainerCache", "Deleting corrupt RC $resourceContainerSlug", e)
                // delete invalid container
                client.delete(resourceContainerSlug)
            } catch (e: Exception) {
                Logger.w("ContainerCache", "Failed to open the RC $resourceContainerSlug", e)
            } finally {
                // flag as inspected
                inspectedContainers.add(resourceContainerSlug)
                // remove loading flag
                loadingContainers.remove(resourceContainerSlug)
            }
        }
        return null
    }

    /**
     * Looks up a resource container from the cache or loads a new one from the disk.
     * If an exact match cannot be found for the given language then the closest matching resource container for the project
     * will be cached and returned.
     */
    fun cacheClosest(
        client: Door43Client,
        languageSlug: String?,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer? {
        val lang = if (languageSlug.isNullOrEmpty()) Locale.getDefault().language else languageSlug

        // search for translation
        var translations = client.index.findTranslations(
            lang,
            projectSlug,
            resourceSlug,
            null,
            null,
            0,
            -1
        )
        if (translations.isEmpty()) {
            // search for similar translations
            translations = client.index.findTranslations(
                null,
                projectSlug,
                resourceSlug,
                null,
                null,
                0,
                -1
            )
        }

        // return first successful cache
        for (translation in translations) {
            val rc = cache(client, translation.resourceContainerSlug)
            if (rc != null) return rc
        }
        return null
    }

    /**
     * Puts the thread to sleep while the container is loading
     */
    private fun waitForLoadingContainers(containerSlug: String) {
        while (loadingContainers.contains(containerSlug)) {
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    /**
     * Looks up a resource container from the cache without hitting the disk
     */
    fun get(containerSlug: String): ResourceContainer? {
        waitForLoadingContainers(containerSlug)
        return resourceContainers[containerSlug]
    }

    /**
     * Parses an array of links and caches the needed resource containers.
     * Links that have a matching container will be returned.
     */
    @Deprecated("TRICKY: only english RCs have links, this won't matter for rc0.1 spec")
    fun cacheClosestFromLinks(client: Door43Client, linkData: List<String>): List<Link> {
        val links = mutableListOf<Link>()
        for (rawLink in linkData) {
            try {
                val link = Link.parseLink(rawLink)
                val container = cacheClosest(client, link.language, link.project, link.resource)
                if (container != null) {
                    links.add(link)
                } else {
                    Logger.w("ContainerCache", "RC not found for link $rawLink")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return links
    }

    /**
     * Same as cacheClosestFromLinks except it requires an exact match.
     */
    fun cacheFromLinks(client: Door43Client, linkData: List<String>, language: Language): List<Link> {
        val links = mutableListOf<Link>()
        for (rawLink in linkData) {
            try {
                val link = Link.parseLink(rawLink)
                val lang = link.language ?: language.slug
                val container = cache(client, ContainerTools.makeSlug(lang, link.project, link.resource))
                if (container != null) {
                    links.add(link)
                } else {
                    Logger.w("ContainerCache", "RC not found for link $rawLink")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return links
    }

    /**
     * Removes a resource container from the cache
     */
    fun remove(resourceContainerSlug: String) {
        resourceContainers.remove(resourceContainerSlug)
        inspectedContainers.remove(resourceContainerSlug)
    }
}