package top.nkbe.art

class ZipAlign private constructor() {
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
            force: Boolean,
        ): Boolean

        @JvmStatic
        external fun isZipAligned(
            zipFile: String,
            alignment: Int,
            pageAlignSharedLibs: Boolean,
        ): Boolean
    }
}
