package com.timebox.guard

/**
 * Retyping a chosen reason: when an app restricts its reasons to a list,
 * picking one isn't enough, the user has to type it out too.
 *
 * Comparison ignores letter case and spaces at either end; every other
 * character has to match. Same rules as the Chrome extension's
 * `src/retype.js`.
 */
object Retype {

    enum class Status { EMPTY, PARTIAL, COMPLETE, MISMATCH }

    /**
     * Where [typed] stands against [target]. [remaining] is the part of
     * [target] (original casing) still to type: all of it for EMPTY, the
     * rest for PARTIAL, and "" otherwise.
     */
    data class State(val status: Status, val remaining: String)

    fun state(target: String, typed: String): State {
        val goal = target.trim()
        val done = typed.trim()
        if (done.isEmpty()) return State(Status.EMPTY, goal)
        if (done.equals(goal, ignoreCase = true)) return State(Status.COMPLETE, "")
        val start = typed.trimStart()
        if (goal.startsWith(start, ignoreCase = true)) {
            return State(Status.PARTIAL, goal.substring(start.length))
        }
        return State(Status.MISMATCH, "")
    }
}
