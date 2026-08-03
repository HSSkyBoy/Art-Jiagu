package top.nkbe.art

import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import java.io.File
import java.util.zip.ZipFile

/**
 * Finds DEX files that contain a libsu RootService implementation.
 *
 * A libsu root process can load its service class directly from base.apk before the
 * application's normal loader runs. Those classes must remain in an ordinary DEX
 * when the optional root-compatibility mode is selected.
 */
object RootServiceDexDetector {
    private const val LIBSU_ROOT_SERVICE = "Lcom/topjohnwu/superuser/ipc/RootService;"

    data class ScanResult(
        val allDexEntries: List<String>,
        val rootServiceDexEntries: List<String>,
    )

    fun scan(apkFile: File): ScanResult {
        val classes = mutableListOf<ClassRecord>()
        val dexEntries = mutableListOf<String>()

        ZipFile(apkFile).use { zip ->
            val names = mutableListOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (dexEntryIndex(entry.name) != null) names += entry.name
            }
            names.sortBy(::dexEntryIndex)

            for (name in names) {
                val entry = zip.getEntry(name) ?: continue
                dexEntries += name
                zip.getInputStream(entry).use { input ->
                    val dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), input)
                    for (classDef in dex.classes) {
                        classes += ClassRecord(
                            dexEntry = name,
                            type = classDef.type,
                            superType = classDef.superclass,
                            dependencyTypes = collectDependencyTypes(classDef),
                        )
                    }
                }
            }
        }

        val parentByType = classes.associate { it.type to it.superType }
        val dexByType = classes.associate { it.type to it.dexEntry }
        val recordsByType = classes.associateBy { it.type }
        val rootTypes = classes.asSequence()
            .map { it.type }
            .filter { type -> extendsLibsuRootService(type, parentByType) }
            .toList()
        val retainedTypes = collectDependencyClosure(rootTypes, recordsByType)
        val rootDexEntries = retainedTypes.asSequence()
            .mapNotNull(dexByType::get)
            .distinct()
            .sortedBy(::dexEntryIndex)
            .toList()

        return ScanResult(dexEntries, rootDexEntries)
    }

    private fun collectDependencyTypes(classDef: com.android.tools.smali.dexlib2.iface.ClassDef): Set<String> {
        val types = linkedSetOf<String>()
        classDef.superclass?.let(types::add)
        classDef.interfaces.forEach(types::add)
        classDef.fields.forEach { types += it.type }
        classDef.methods.forEach { method ->
            types += method.returnType
            method.parameterTypes.forEach { types += it.toString() }
            method.implementation?.instructions?.forEach { instruction ->
                if (instruction !is ReferenceInstruction) return@forEach
                when (val reference = instruction.reference) {
                    is TypeReference -> types += reference.type
                    is FieldReference -> {
                        types += reference.definingClass
                        types += reference.type
                    }
                    is MethodReference -> {
                        types += reference.definingClass
                        types += reference.returnType
                        reference.parameterTypes.forEach { types += it.toString() }
                    }
                }
            }
        }
        return types
    }

    private fun collectDependencyClosure(
        roots: List<String>,
        recordsByType: Map<String, ClassRecord>,
    ): Set<String> {
        val retained = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        roots.forEach { type -> if (retained.add(type)) queue += type }

        while (queue.isNotEmpty()) {
            val record = recordsByType[queue.removeFirst()] ?: continue
            record.dependencyTypes.forEach { type ->
                if (type in recordsByType && retained.add(type)) queue += type
            }
        }
        return retained
    }

    private fun extendsLibsuRootService(
        type: String,
        parentByType: Map<String, String?>,
    ): Boolean {
        val seen = HashSet<String>()
        var current: String? = type
        while (current != null && seen.add(current)) {
            current = parentByType[current]
            if (current == LIBSU_ROOT_SERVICE) return true
        }
        return false
    }

    private fun dexEntryIndex(name: String): Int? = when {
        name == "classes.dex" -> 1
        name.matches(Regex("classes[2-9][0-9]*\\.dex")) ->
            name.removePrefix("classes").removeSuffix(".dex").toIntOrNull()
        else -> null
    }

    private data class ClassRecord(
        val dexEntry: String,
        val type: String,
        val superType: String?,
        val dependencyTypes: Set<String>,
    )
}
