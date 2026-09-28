package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricLine

/** Keep only the lines currently being sung visible as active. */
internal fun activeLyricRows(lines: List<LyricLine>, positionMs: Long): List<Int> {
    return lines.withIndex().filter { (_, line) ->
        !line.isGap &&
            (line.hasKnownEnd || line.background?.hasKnownEnd == true) &&
            line.timeMs <= positionMs && positionMs < line.endMs
    }.map { it.index }
}
