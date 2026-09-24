package dev.yashgarg.qbit.utils

/**
 * Turns an RSS article title into a filename for the `.torrent` fetched from it.
 *
 * The fetched file is handed to the add-torrent screen as a `file://` URI, whose only available
 * name is the file's own - so this is what the user sees as "Selected file". Strips control
 * characters and anything a path could choke on (separators plus the Windows-reserved set, since
 * the name reaches the server too), then caps the length: the filesystem limit counts bytes while
 * this cap counts characters, leaving room for titles whose characters are several bytes each.
 */
internal fun torrentFileName(articleTitle: String): String =
    articleTitle
        .filterNot { it.isISOControl() }
        .replace(Regex("""[/\\:*?"<>|]"""), " ")
        .replace(Regex("\\s+"), " ")
        // Capped before the trims, so a cut that lands on a space or a dot is still tidied up.
        .take(80)
        .trim()
        // Dots at either end: a leading one would make it a hidden file, a trailing one would run
        // into the ".torrent" the caller appends. Stripping both also means a title of "." or ".."
        // (or anything reducing to one, like "../..") can't come back as a directory reference.
        .trim('.')
        .trim()
        .ifEmpty { "rss" }
