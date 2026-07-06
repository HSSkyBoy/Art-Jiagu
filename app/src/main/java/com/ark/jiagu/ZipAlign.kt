package com.ark.jiagu

class ZipAlign {
    companion object {
        init {
            System.loadLibrary("zipalign")
        }

        @JvmStatic
        external fun doZipAlign(
            inZipFile: String,
            outZipFile: String,
            alignment: Int,
            pageAlignSharedLibs: Boolean,
            force: Boolean
        ): Boolean

        @JvmStatic
        external fun isZipAligned(
            zipFie: String,
            alignment: Int,
            pageAlignSharedLibs: Boolean
        ): Boolean
    }
}
