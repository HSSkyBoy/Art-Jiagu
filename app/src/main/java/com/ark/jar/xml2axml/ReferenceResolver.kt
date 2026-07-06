package com.ark.jar.xml2axml

import com.ark.jar.xml2axml.chunks.ValueChunk

interface ReferenceResolver {
    fun resolve(value: ValueChunk, ref: String): Int
}
