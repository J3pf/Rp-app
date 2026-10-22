package dev.j3pf.rp.data.repo

sealed interface PairResult {
    data object Success : PairResult

    data class Failure(
        val message: String,
    ) : PairResult
}
