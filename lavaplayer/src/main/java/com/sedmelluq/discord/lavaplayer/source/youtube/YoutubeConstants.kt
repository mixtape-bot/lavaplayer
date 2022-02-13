package com.sedmelluq.discord.lavaplayer.source.youtube

object YoutubeConstants {
    /* YOUTUBE */
    const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    const val YOUTUBE_MUSIC_ORIGIN = "https://music.youtube.com"

    const val WATCH_URL_PREFIX = "$YOUTUBE_ORIGIN/watch?v="
    const val WATCH_MUSIC_URL_PREFIX = "$YOUTUBE_MUSIC_ORIGIN/watch?v="

    const val INNERTUBE_API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w" // "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    const val CLIENT_NAME = "ANDROID"
    const val CLIENT_VERSION = "16.24"
    const val CLIENT_SCREEN = "EMBED"

    const val BASE_URL = "$YOUTUBE_ORIGIN/youtubei/v1"
    const val BASE_PAYLOAD = "{\"context\":{\"client\":{\"clientName\":\"$CLIENT_NAME\",\"clientVersion\":\"$CLIENT_VERSION\""

    const val SCREEN_PART_PAYLOAD = ",\"screenDensityFloat\":1,\"screenHeightPoints\":1080,\"screenPixelDensity\":1,\"screenWidthPoints\":1920"
    const val EMBED_PART_PAYLOAD = ",\"clientScreen\":\"$CLIENT_SCREEN\"},\"thirdParty\":{\"embedUrl\":\"$YOUTUBE_ORIGIN\""
    const val CLOSE_BASE_PAYLOAD = "}},"

    const val PLAYER_URL = "$BASE_URL/player?key=$INNERTUBE_API_KEY"
    const val PLAYER_PAYLOAD = "$BASE_PAYLOAD$SCREEN_PART_PAYLOAD$CLOSE_BASE_PAYLOAD\"racyCheckOk\":true,\"contentCheckOk\":true,\"videoId\":\"%s\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":%s}}}"
    const val PLAYER_EMBED_PAYLOAD = "$BASE_PAYLOAD$SCREEN_PART_PAYLOAD$EMBED_PART_PAYLOAD$CLOSE_BASE_PAYLOAD\"racyCheckOk\":true,\"contentCheckOk\":true,\"videoId\":\"%s\",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":%s}}}"

    const val VERIFY_AGE_URL = "$BASE_URL/verify_age?key=$INNERTUBE_API_KEY"
    const val VERIFY_AGE_PAYLOAD = "$BASE_PAYLOAD$SCREEN_PART_PAYLOAD$CLOSE_BASE_PAYLOAD\"nextEndpoint\":{\"urlEndpoint\":{\"url\":\"%s\"}},\"setControvercy\":true}"

    const val SEARCH_URL = "$BASE_URL/search?key=$INNERTUBE_API_KEY"
    const val SEARCH_PAYLOAD = "$BASE_PAYLOAD$SCREEN_PART_PAYLOAD$CLOSE_BASE_PAYLOAD\"query\":\"%s\",\"params\":\"EgIQAQ==\"}"

    const val BROWSE_URL = "$BASE_URL/browse?key=$INNERTUBE_API_KEY"
    const val BROWSE_CONTINUATION_PAYLOAD = "$BASE_PAYLOAD$SCREEN_PART_PAYLOAD$CLOSE_BASE_PAYLOAD\"continuation\":\"%s\"}"
    const val BROWSE_PLAYLIST_PAYLOAD = "$BASE_PAYLOAD$SCREEN_PART_PAYLOAD$CLOSE_BASE_PAYLOAD\"browseId\":\"VL%s\"}"

    const val NEXT_URL = "$BASE_URL/next?key=$INNERTUBE_API_KEY"
    const val NEXT_PAYLOAD = "$BASE_PAYLOAD$SCREEN_PART_PAYLOAD$CLOSE_BASE_PAYLOAD\"videoId\":\"%s\",\"playlistId\":\"%s\"}"

    /* YOUTUBE MUSIC */
    const val MUSIC_INNERTUBE_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"

    const val MUSIC_CLIENT_NAME = "WEB_REMIX"
    const val MUSIC_CLIENT_VERSION = "0.1"

    const val MUSIC_BASE_URL = "$YOUTUBE_MUSIC_ORIGIN/youtubei/v1"
    const val MUSIC_BASE_PAYLOAD = "{\"context\":{\"client\":{\"clientName\":\"$MUSIC_CLIENT_NAME\",\"clientVersion\":\"$MUSIC_CLIENT_VERSION\"}},"

    const val MUSIC_SEARCH_URL = "$MUSIC_BASE_URL/search?key=$MUSIC_INNERTUBE_API_KEY"
    const val MUSIC_SEARCH_PAYLOAD = "$MUSIC_BASE_PAYLOAD\"query\":\"%s\",\"params\":\"Eg-KAQwIARAAGAAgACgAMABqChADEAQQCRAFEAo=\"}"
}
