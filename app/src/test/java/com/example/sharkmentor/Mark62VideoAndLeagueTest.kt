package com.example.sharkmentor

import com.example.sharkmentor.core.coaching.BookCampManager
import com.example.sharkmentor.core.coaching.CampDayEpisode
import com.example.sharkmentor.core.coaching.CoachingEngine
import com.example.sharkmentor.core.coaching.LiveFocusPresence
import com.example.sharkmentor.core.coaching.ResolvedYouTubeVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mark62VideoAndLeagueTest {

    @Test
    fun cleanLeagueSubjectStripsSubTopicsAndCampDetails() {
        assertEquals("AYT Kimya", CoachingEngine.formatCleanLeagueSubject("AYT Kimya • Organik Kimya 4. Gün"))
        assertEquals("AYT Kimya", CoachingEngine.formatCleanLeagueSubject("AYT Kimya - Alkanlar Konu Anlatımı"))
        assertEquals("AYT Matematik", CoachingEngine.formatCleanLeagueSubject("AYT Matematik: Türev Test 4"))
        assertEquals("TYT Matematik", CoachingEngine.formatCleanLeagueSubject("3D TYT Matematik VDD 12. Gün"))
        assertEquals("Kitap Okuma", CoachingEngine.formatCleanLeagueSubject("Kitap Okuma"))
        assertEquals("AYT", CoachingEngine.formatCleanLeagueSubject("AYT • Türev (Pekiştirme)"))

        val now = 2_000_000_000L
        val presence = LiveFocusPresence(
            isStudying = true,
            activeSubject = "AYT Kimya • Organik Kimya 4. Gün: Alkanlar",
            startedAtMillis = now - 25L * 60_000L
        )
        assertEquals("Masada • AYT Kimya (25 dk)", presence.formattedLeagueStatus(now))
    }

    @Test
    fun embedUrlOmitsPlaylistParamWhenDirectVideoIdIsPresent() {
        val day4Episode = CampDayEpisode(
            dayNumber = 4,
            topicTitle = "Maddenin Halleri",
            videoId = "vid00000004",
            playlistId = "PLFerrumKimya2026List"
        )
        val embedUrl = day4Episode.buildInAppEmbedUrl(adBlockerEnabled = true, autoplay = true)
        assertTrue(embedUrl.startsWith("https://www.youtube-nocookie.com/embed/vid00000004?"))
        assertFalse(
            "Embed URL must NOT append list= when a specific 11-char videoId is present, otherwise YouTube restarts at playlist index 0",
            embedUrl.contains("list=")
        )

        val externalUrl = day4Episode.buildExternalYouTubeUrl()
        assertEquals("https://www.youtube.com/watch?v=vid00000004", externalUrl)
    }

    @Test
    fun introFilterRemovesPromoVideosWithoutRemovingRealGirisLessons() {
        // Non-lesson / intro / promo videos MUST be filtered out
        assertTrue(BookCampManager.isIntroOrNonLessonVideo("2026 TYT Kimya Kampı Tanıtım Videosu", "08:15"))
        assertTrue(BookCampManager.isIntroOrNonLessonVideo("FERRUM KİMYA KAMP BAŞLIYORUZ! Kitap İnceleme", "12:40"))
        assertTrue(BookCampManager.isIntroOrNonLessonVideo("0. Gün - Kamp Programı ve Rehberlik", "06:10"))
        assertTrue(BookCampManager.isIntroOrNonLessonVideo("Önemli Duyuru ve PDF Nasıl İndirilir?", "04:05"))
        assertTrue(BookCampManager.isIntroOrNonLessonVideo("Kısa Teaser #shorts", "0:55"))
        assertTrue(BookCampManager.isIntroOrNonLessonVideo("Hoş Geldiniz", "0:32"))

        // Real lesson videos (including titles with "Giriş") MUST be preserved!
        assertFalse(BookCampManager.isIntroOrNonLessonVideo("1. Gün - Kimya Bilimine Giriş | TYT Kimya", "34:12"))
        assertFalse(BookCampManager.isIntroOrNonLessonVideo("Karbon Kimyasına Giriş - 1", "41:20"))
        assertFalse(BookCampManager.isIntroOrNonLessonVideo("Fizik Bilimine Giriş", "28:00"))
        assertFalse(BookCampManager.isIntroOrNonLessonVideo("4. Ders: Gazlar Soru Çözümü", "36:45"))
    }

    @Test
    fun realPlaylistBuilderStripsFirstTwoIntrosAndPreservesExactOrderAndVideoIds() {
        val rawFerrumPlaylist = listOf(
            ResolvedYouTubeVideo("intro000001", "2026 TYT Kimya Kampı Tanıtım Videosu", "Ferrum", "07:30"),
            ResolvedYouTubeVideo("intro000002", "Kamp Programı ve Kitap İnceleme | Başlıyoruz!", "Ferrum", "10:15"),
            ResolvedYouTubeVideo("lesson00001", "1. Gün - Kimya Bilimi | TYT Kimya Kampı 2026 | Ferrum", "Ferrum", "32:10"),
            ResolvedYouTubeVideo("lesson00002", "2. Gün - Atom ve Periyodik Sistem | TYT Kimya Kampı 2026", "Ferrum", "44:05"),
            ResolvedYouTubeVideo("lesson00003", "3. Gün - Kimyasal Türler Arası Etkileşimler", "Ferrum", "39:50"),
            ResolvedYouTubeVideo("lesson00004", "4. Gün - Maddenin Halleri", "Ferrum", "35:20")
        )

        val episodes = BookCampManager.buildEpisodesFromRealPlaylistVideos("PLFerrumReal12345", rawFerrumPlaylist)
        assertEquals(4, episodes.size)
        assertEquals(1, episodes[0].dayNumber)
        assertEquals("lesson00001", episodes[0].videoId)
        assertEquals("Kimya Bilimi", episodes[0].topicTitle)

        // Selecting Episode 4 must play exact lesson00004 — NEVER intro000001!
        val ep4 = episodes[3]
        assertEquals(4, ep4.dayNumber)
        assertEquals("lesson00004", ep4.videoId)
        assertEquals("Maddenin Halleri", ep4.topicTitle)
        assertTrue(ep4.buildInAppEmbedUrl().contains("/embed/lesson00004?"))
        assertFalse(ep4.buildInAppEmbedUrl().contains("list="))
    }

    @Test
    fun strictYouTubeRelevanceRejectsUnrelatedTeachersWrongSubjectsAndObscureBooks() {
        // Exact relevant brand + subject match
        assertTrue(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "Ferrum TYT Kimya Video Ders Kitabı",
                subject = "TYT Kimya",
                resultTitle = "2026 TYT Kimya Kampı",
                channelName = "Ferrum"
            )
        )
        assertTrue(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "3D TYT Matematik VDD",
                subject = "TYT Matematik",
                resultTitle = "3D TYT Matematik Video Destekli Defter Çözümleri",
                channelName = "Eyüp B."
            )
        )
        assertTrue(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "345 AYT Matematik Soru Bankası",
                subject = "AYT Matematik",
                resultTitle = "ÜçDörtBeş AYT Matematik Tamamı Çözümlü",
                channelName = "Matematik Akademi"
            )
        )

        // Wrong teacher for Ferrum book MUST be rejected
        assertFalse(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "Ferrum TYT Kimya",
                subject = "TYT Kimya",
                resultTitle = "2026 TYT Kimya Kampı - Görkem Şahin",
                channelName = "Benim Hocam"
            )
        )

        // Wrong subject MUST be rejected
        assertFalse(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "Ferrum TYT Kimya",
                subject = "TYT Kimya",
                resultTitle = "Ferrum TYT Fizik Deneme Çözümü",
                channelName = "Ferrum"
            )
        )

        // Exclusive TYT vs AYT mismatch MUST be rejected
        assertFalse(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "Aydın Yayınları AYT Kimya",
                subject = "AYT Kimya",
                resultTitle = "Aydın Yayınları TYT Kimya Soru Bankası",
                channelName = "Aydın Yayınları"
            )
        )

        // Obscure book with no YouTube video support MUST be rejected
        assertFalse(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "Özel Dershane Yerel Föy 2024",
                subject = "AYT Kimya",
                resultTitle = "AYT Kimya Full Tekrar Kampı",
                channelName = "Görkem Şahin"
            )
        )

        // Generic title with zero publisher/brand tokens MUST NOT randomly bind a teacher
        assertFalse(
            BookCampManager.isRelevantYouTubeMatch(
                bookTitle = "AYT Kimya Soru Bankası",
                subject = "AYT Kimya",
                resultTitle = "AYT Kimya Video Ders Kampı",
                channelName = "Benim Hocam"
            )
        )
    }

    @Test
    fun playlistHtmlExtractorsParseBothClassicAndModernYouTubeMarkup() {
        val sampleSearchHtml = """
            {"playlistRenderer":{"playlistId":"PLClassicList123456","title":{"simpleText":"Ferrum TYT Kimya Kampı 2026"},"shortBylineText":{"runs":[{"text":"Ferrum"}]},"videoCount":"34"}}
            {"lockupViewModel":{"contentId":"PLModernList987654","metadata":{"lockupMetadataViewModel":{"title":{"content":"3D TYT Matematik VDD"},"metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"Eyüp B."}}]}]}}}}}}
        """.trimIndent()

        val found = BookCampManager.extractPlaylistsFromSearchHtml(sampleSearchHtml)
        assertEquals(2, found.size)
        assertEquals("PLClassicList123456", found[0].playlistId)
        assertEquals("Ferrum TYT Kimya Kampı 2026", found[0].title)
        assertEquals("Ferrum", found[0].channelName)
        assertEquals("PLModernList987654", found[1].playlistId)
        assertEquals("3D TYT Matematik VDD", found[1].title)

        val samplePlaylistHtml = """
            {"playlistVideoRenderer":{"videoId":"aaa11111111","title":{"runs":[{"text":"Kamp Tanıtımı"}]},"lengthText":{"simpleText":"05:10"}}}
            {"playlistVideoRenderer":{"videoId":"bbb22222222","title":{"runs":[{"text":"1. Gün - Kimya Bilimi"}]},"lengthText":{"simpleText":"31:00"}}}
            {"playlistVideoRenderer":{"videoId":"ccc33333333","title":{"simpleText":"2. Gün - Atom ve Periyodik Sistem"},"lengthText":{"simpleText":"42:15"}}}
        """.trimIndent()

        val rawVideos = BookCampManager.extractPlaylistVideosFromHtml(samplePlaylistHtml)
        assertEquals(3, rawVideos.size)
        val realEpisodes = BookCampManager.buildEpisodesFromRealPlaylistVideos("PLClassicList123456", rawVideos)
        assertEquals(2, realEpisodes.size)
        assertEquals("bbb22222222", realEpisodes[0].videoId)
        assertEquals("ccc33333333", realEpisodes[1].videoId)
    }
}
