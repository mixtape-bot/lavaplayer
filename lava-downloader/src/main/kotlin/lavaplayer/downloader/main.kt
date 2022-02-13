package lavaplayer.downloader

suspend fun main(args: Array<out String>) {
    val args = args.toMutableList()

    val query = args.removeFirstOrNull()
        ?: error("I need a search query")

    val format = args.removeFirstOrNull()
        ?: "wav"

    Downloader(args).start(query, format)
}
