package com.example.musicpiped

import com.example.musicpiped.network.OkHttpDownloader
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class ExtractorTest {

    @Test
    fun fetchTrendingMusic() {
        NewPipe.init(OkHttpDownloader(), Localization.DEFAULT, ContentCountry("US"))
        val youtube = ServiceList.YouTube
        
        println("\n--- Testing 'trending_music' Kiosk ---")
        try {
            val kioskList = youtube.kioskList
            
            // Using reflection because of previous ambiguity/unresolved issues
            val extractor = try {
                kioskList.javaClass.getMethod("getExtractorById", String::class.java, Page::class.java).invoke(kioskList, "trending_music", null)
            } catch (e: Exception) {
                println("Failed to get extractor: ${e.message}")
                null
            }

            if (extractor != null) {
                extractor.javaClass.getMethod("fetchPage").invoke(extractor)
                val name = extractor.javaClass.getMethod("getName").invoke(extractor)
                println("Kiosk Name: $name")
                
                val page = extractor.javaClass.getMethod("getInitialPage").invoke(extractor)
                val items = page?.javaClass?.getMethod("getItems")?.invoke(page) as? List<*>
                println("Items count: ${items?.size ?: 0}")
                items?.take(10)?.forEach { item ->
                    if (item != null) {
                        try {
                            val itemName = item.javaClass.getMethod("getName").invoke(item)
                            val uploader = try { item.javaClass.getMethod("getUploaderName").invoke(item) } catch(e: Exception) { "N/A" }
                            println(" - Item: $itemName by $uploader")
                        } catch (e: Exception) {
                            println(" - Could not fetch item details: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error in kiosk testing")
            e.printStackTrace()
        }

        println("\n--- Testing YouTube Music Channel (Explore) ---")
        val channelId = "UC-9-kyTW8ZkZNDHQJ6FgpwQ"
        try {
            val extractor = youtube.getChannelExtractor("https://www.youtube.com/channel/$channelId")
            extractor.fetchPage()
            println("Channel: ${extractor.getName()}")
            
            val tabs = extractor.getTabs()
            println("Tabs count: ${tabs.size}")
            
            tabs.forEach { tab ->
                if (tab != null) {
                    val tabClass = tab.javaClass
                    val tabName = try { tabClass.getMethod("getName").invoke(tab) } catch(e: Exception) { "Unknown" }
                    println("Tab: $tabName")
                    
                    // Try to get items from tab
                    try {
                        val page = tabClass.getMethod("getInitialPage").invoke(tab)
                        val items = page?.javaClass?.getMethod("getItems")?.invoke(page) as? List<*>
                        println("  Tab Items count: ${items?.size ?: 0}")
                        items?.take(5)?.forEach { item ->
                            if (item != null) {
                                try {
                                    val name = item.javaClass.getMethod("getName").invoke(item)
                                    println("    - Item: $name")
                                } catch (e: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        println("  Could not fetch items for tab: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error fetching YouTube Music channel")
            e.printStackTrace()
        }
    }
}
