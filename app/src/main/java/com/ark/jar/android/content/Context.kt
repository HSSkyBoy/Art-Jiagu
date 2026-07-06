package com.ark.jar.android.content
import com.ark.jar.android.content.res.Resources

class Context {
    private val resources = Resources()
    fun getResources(): Resources = resources
    fun getPackageName(): String = "com.example.reforceapp"
}
