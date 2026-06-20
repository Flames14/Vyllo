package com.vyllo.music.test

import org.schabi.newpipe.extractor.comments.CommentsInfo

class Test {
    fun test() {
        val methods = CommentsInfo::class.java.methods
        methods.forEach { println(it.name) }
    }
}
