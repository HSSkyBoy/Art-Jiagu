package top.nkbe.art.vm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableDexFile;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction31i;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
public class VmpJiaguEntry {
    private static final Map<String, ExtractedMethodInfo> EXTRACTED_METHOD_MAP = new LinkedHashMap<>();
    //这个类是将dex中的方法的字节码抽取并转native的类，只是抽取部分，真正的vmp解释器还没实现
    /**
     * :TODO
     * 解压 APK 中的 AndroidManifest.xml 和连续合法 dex 文件。
     * 合法 dex：
     * classes.dex
     * classes2.dex
     * classes3.dex
     * ...
     * 如果中间断号，例如没有 classes4.dex，则停止继续解压。
     */
    public static void extractManifestAndDex(File apkFile, File outDir) throws IOException {
        if (apkFile == null || !apkFile.isFile()) {
            throw new IOException("APK文件不存在");
        }

        if (outDir == null) {
            throw new IOException("输出目录为空");
        }

        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("创建输出目录失败：" + outDir.getAbsolutePath());
        }

        try (ZipFile zipFile = new ZipFile(apkFile)) {
            extractEntry(zipFile, "AndroidManifest.xml", new File(outDir, "AndroidManifest.xml"));

            int index = 1;
            while (true) {
                String dexName = index == 1 ? "classes.dex" : "classes" + index + ".dex";
                ZipEntry dexEntry = zipFile.getEntry(dexName);

                if (dexEntry == null) {
                    break;
                }

                extractEntry(zipFile, dexName, new File(outDir, dexName));
                index++;
            }
        }
    }

    private static void extractEntry(ZipFile zipFile, String entryName, File outFile) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            throw new IOException("APK中未找到文件：" + entryName);
        }

        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("创建目录失败：" + parent.getAbsolutePath());
        }

        try (InputStream in = zipFile.getInputStream(entry);
             FileOutputStream out = new FileOutputStream(outFile)) {

            byte[] buffer = new byte[8192];
            int len;

            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }
    //----------------------------------------------------------------------------------------------------------
    /**
    * :TODO
    * 生成一个VMP的入口类。
    */
    public static ClassDef createVmpClass(String soName, String packageName) {
        if (soName == null || soName.trim().isEmpty()) {
            throw new IllegalArgumentException("so库名称不能为空");
        }

        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("包名不能为空");
        }

        String cleanPackageName = packageName.trim();
        String classType = "L" + cleanPackageName.replace('.', '/') + "/VMP;";

        List<Method> methods = new ArrayList<>();

        // public VMP()
        MethodImplementation initImpl = new ImmutableMethodImplementation(
                1,
                Arrays.asList(
                        new ImmutableInstruction35c(
                                Opcode.INVOKE_DIRECT,
                                1,
                                0,
                                0,
                                0,
                                0,
                                0,
                                new ImmutableMethodReference(
                                        "Ljava/lang/Object;",
                                        "<init>",
                                        Collections.emptyList(),
                                        "V"
                                )
                        ),
                        new ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                null,
                null
        );

        methods.add(new ImmutableMethod(
                classType,
                "<init>",
                Collections.emptyList(),
                "V",
                AccessFlags.PUBLIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                null,
                null,
                initImpl
        ));

        // static { System.loadLibrary("传入的so库名称"); }
        MethodImplementation clinitImpl = new ImmutableMethodImplementation(
                1,
                Arrays.asList(
                        new ImmutableInstruction21c(
                                Opcode.CONST_STRING,
                                0,
                                new ImmutableStringReference(soName)
                        ),
                        new ImmutableInstruction35c(
                                Opcode.INVOKE_STATIC,
                                1,
                                0,
                                0,
                                0,
                                0,
                                0,
                                new ImmutableMethodReference(
                                        "Ljava/lang/System;",
                                        "loadLibrary",
                                        Collections.singletonList("Ljava/lang/String;"),
                                        "V"
                                )
                        ),
                        new ImmutableInstruction10x(Opcode.RETURN_VOID)
                ),
                null,
                null
        );

        methods.add(new ImmutableMethod(
                classType,
                "<clinit>",
                Collections.emptyList(),
                "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                null,
                null,
                clinitImpl
        ));

        // public static native void init(int id, Class<?> clazz);
        List<MethodParameter> initParams = Arrays.asList(
                new ImmutableMethodParameter("I", Collections.emptySet(), null),
                new ImmutableMethodParameter("Ljava/lang/Class;", Collections.emptySet(), null)
        );

        methods.add(new ImmutableMethod(
                classType,
                "init",
                initParams,
                "V",
                AccessFlags.PUBLIC.getValue()
                        | AccessFlags.STATIC.getValue()
                        | AccessFlags.NATIVE.getValue(),
                null,
                null,
                null
        ));

        return new ImmutableClassDef(
                classType,
                AccessFlags.PUBLIC.getValue(),
                "Ljava/lang/Object;",
                Collections.emptyList(),
                "VMP.java",
                null,
                Collections.emptyList(),
                methods
        );
    }
    /**
     * :TODO
     * 返回一个带 VMP 入口调用的DEX
     * */
    public static DexFile createVmpShellDex(String soName, String packageName) {
        ClassDef vmpClass = createVmpClass(soName, packageName);

        List<ClassDef> classes = new ArrayList<>();
        classes.add(vmpClass);

        return new ImmutableDexFile(
                Opcodes.getDefault(),
                classes
        );
    }

    /**
     * :TODO
     * 生成一张自定义指令映射表
     * */
    public static Map<Opcode, Integer> printDexlib2Opcodes() {
        Map<Opcode, Integer> customOpcodeMap = new LinkedHashMap<>();

        List<Integer> customValues = new ArrayList<>();
        for (int i = 0; i <= 0xFF; i++) {
            customValues.add(i);
        }

        Collections.shuffle(customValues, new Random(System.currentTimeMillis()));

        int index = 0;

        for (Opcode opcode : Opcode.values()) {
            if (index >= customValues.size()) {
                break;
            }

            int rawOpcode = opcode.ordinal();
            int customOpcode = customValues.get(index);
            index++;

            customOpcodeMap.put(opcode, customOpcode);

            System.out.println(
                    "名称=" + opcode.name()
                            + " 值=0x" + String.format("%02x", rawOpcode)
                            + " 自定义字节=0x" + String.format("%02x", customOpcode)
            );
        }

        return customOpcodeMap;
    }
    /**
     * :TODO
     * 打印出待抽取方法的内容
     * */
    public static void printOnCreateExtractInfo(File dexFile) throws IOException {
        if (dexFile == null || !dexFile.isFile()) {
            throw new IOException("dex文件不存在");
        }

        DexBackedDexFile dex = DexFileFactory.loadDexFile(
                dexFile,
                Opcodes.getDefault()
        );

        for (ClassDef classDef : dex.getClasses()) {
            for (Method method : classDef.getMethods()) {
                if (!"onCreate".equals(method.getName())) {
                    continue;
                }

                System.out.println("========================================");
                System.out.println("发现待抽取方法");
                System.out.println("类名: " + classDef.getType());
                System.out.println("方法名: " + method.getName());
                System.out.println("方法签名: " + buildMethodSignature(method));
                System.out.println("访问标志: 0x" + Integer.toHexString(method.getAccessFlags()));
                System.out.println("访问标志文本: " + formatAccessFlags(method.getAccessFlags()));
                System.out.println("返回类型: " + method.getReturnType());

                System.out.println("参数数量: " + method.getParameters().size());
                int paramIndex = 0;
                for (MethodParameter parameter : method.getParameters()) {
                    System.out.println("参数[" + paramIndex + "]: " + parameter.getType());
                    paramIndex++;
                }

                MethodImplementation impl = method.getImplementation();
                if (impl == null) {
                    System.out.println("方法实现: 空，可能已经是native或abstract");
                    continue;
                }

                System.out.println("寄存器数量: " + impl.getRegisterCount());

                int insnIndex = 0;
                for (Instruction instruction : impl.getInstructions()) {
                    System.out.println(
                            "指令[" + insnIndex + "] "
                                    + "opcode=" + instruction.getOpcode().name()
                                    + " 格式=" + instruction.getOpcode().format
                                    + " codeUnits=" + instruction.getCodeUnits()
                    );
                    printInstructionOperands(instruction);
                    printInstructionReference(instruction);

                    insnIndex++;
                }

                System.out.println("try-catch数量: " + impl.getTryBlocks().size());
                int tryIndex = 0;
                for (TryBlock<? extends ExceptionHandler> tryBlock : impl.getTryBlocks()) {
                    System.out.println(
                            "try[" + tryIndex + "] "
                                    + "startCodeAddress=" + tryBlock.getStartCodeAddress()
                                    + " codeUnitCount=" + tryBlock.getCodeUnitCount()
                    );

                    for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                        System.out.println(
                                "  catch type=" + handler.getExceptionType()
                                        + " handlerCodeAddress=" + handler.getHandlerCodeAddress()
                        );
                    }

                    tryIndex++;
                }
            }
        }
    }
    private static void printInstructionOperands(Instruction instruction) {
        if (instruction instanceof OneRegisterInstruction) {
            OneRegisterInstruction insn = (OneRegisterInstruction) instruction;
            System.out.println("  寄存器A: v" + insn.getRegisterA());
        }

        if (instruction instanceof TwoRegisterInstruction) {
            TwoRegisterInstruction insn = (TwoRegisterInstruction) instruction;
            System.out.println("  寄存器A: v" + insn.getRegisterA());
            System.out.println("  寄存器B: v" + insn.getRegisterB());
        }

        if (instruction instanceof ThreeRegisterInstruction) {
            ThreeRegisterInstruction insn = (ThreeRegisterInstruction) instruction;
            System.out.println("  寄存器A: v" + insn.getRegisterA());
            System.out.println("  寄存器B: v" + insn.getRegisterB());
            System.out.println("  寄存器C: v" + insn.getRegisterC());
        }

        if (instruction instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction insn = (FiveRegisterInstruction) instruction;
            System.out.println("  寄存器数量: " + insn.getRegisterCount());
            System.out.println("  寄存器C: v" + insn.getRegisterC());
            System.out.println("  寄存器D: v" + insn.getRegisterD());
            System.out.println("  寄存器E: v" + insn.getRegisterE());
            System.out.println("  寄存器F: v" + insn.getRegisterF());
            System.out.println("  寄存器G: v" + insn.getRegisterG());
        }

        if (instruction instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction insn = (RegisterRangeInstruction) instruction;
            System.out.println("  起始寄存器: v" + insn.getStartRegister());
            System.out.println("  寄存器数量: " + insn.getRegisterCount());
        }

        if (instruction instanceof NarrowLiteralInstruction) {
            NarrowLiteralInstruction insn = (NarrowLiteralInstruction) instruction;
            System.out.println("  int常量: " + insn.getNarrowLiteral());
            System.out.println("  int常量HEX: 0x" + Integer.toHexString(insn.getNarrowLiteral()));
        }

        if (instruction instanceof WideLiteralInstruction) {
            WideLiteralInstruction insn = (WideLiteralInstruction) instruction;
            System.out.println("  long常量: " + insn.getWideLiteral());
            System.out.println("  long常量HEX: 0x" + Long.toHexString(insn.getWideLiteral()));
        }

        if (instruction instanceof OffsetInstruction) {
            OffsetInstruction insn = (OffsetInstruction) instruction;
            System.out.println("  跳转偏移: " + insn.getCodeOffset());
        }
    }
    private static String buildMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        for (CharSequence paramType : method.getParameterTypes()) {
            sb.append(paramType);
        }

        sb.append(")");
        sb.append(method.getReturnType());

        return sb.toString();
    }

    private static String formatAccessFlags(int accessFlags) {
        StringBuilder sb = new StringBuilder();

        for (AccessFlags flag : AccessFlags.values()) {
            if (flag.isSet(accessFlags)) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(flag.name());
            }
        }

        return sb.toString();
    }

    private static void printInstructionReference(Instruction instruction) {
        if (!(instruction instanceof ReferenceInstruction)) {
            return;
        }

        ReferenceInstruction refInsn = (ReferenceInstruction) instruction;
        Reference reference = refInsn.getReference();

        if (reference instanceof StringReference) {
            System.out.println("  字符串引用: " + ((StringReference) reference).getString());
        } else if (reference instanceof TypeReference) {
            System.out.println("  类型引用: " + ((TypeReference) reference).getType());
        } else if (reference instanceof FieldReference) {
            FieldReference field = (FieldReference) reference;
            System.out.println(
                    "  字段引用: "
                            + field.getDefiningClass()
                            + "->"
                            + field.getName()
                            + ":"
                            + field.getType()
            );
        } else if (reference instanceof MethodReference) {
            MethodReference methodRef = (MethodReference) reference;
            System.out.println(
                    "  方法引用: "
                            + methodRef.getDefiningClass()
                            + "->"
                            + methodRef.getName()
                            + buildMethodReferenceSignature(methodRef)
            );
        } else {
            System.out.println("  其他引用: " + reference);
        }
    }

    private static String buildMethodReferenceSignature(MethodReference methodRef) {
        StringBuilder sb = new StringBuilder();
        sb.append("(");

        for (CharSequence paramType : methodRef.getParameterTypes()) {
            sb.append(paramType);
        }

        sb.append(")");
        sb.append(methodRef.getReturnType());

        return sb.toString();
    }

    /**
     * :TODO
     * 抽取方法到bin
     * */
    public static void extractOnCreateToBin(File dexDir, String... methodRules) throws IOException {
        if (dexDir == null || !dexDir.isDirectory()) {
            throw new IOException("dex目录不存在");
        }

        List<MethodRule> rules = parseMethodRules(methodRules);
        if (rules.isEmpty()) {
            throw new IOException("抽取规则为空");
        }

        List<ExtractMethodBlock> blocks = new ArrayList<>();

        Map<String, Integer> opcodeMap = new LinkedHashMap<>();
        List<Integer> opcodePool = buildRandomOpcodePool();
        int[] opcodePoolIndex = new int[]{0};

        int nextClassId = 1;
        int dexIndex = 1;

        while (true) {
            String dexName = dexIndex == 1 ? "classes.dex" : "classes" + dexIndex + ".dex";
            File dexFile = new File(dexDir, dexName);

            if (!dexFile.isFile()) {
                System.out.println("dex编号断开，停止扫描：" + dexName);
                break;
            }

            if (!isValidDexFile(dexFile)) {
                System.out.println("跳过非法dex文件：" + dexFile.getAbsolutePath());
                break;
            }

            DexBackedDexFile dex;
            try {
                dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
            } catch (Throwable e) {
                System.out.println("解析dex失败，停止扫描：" + dexFile.getName());
                System.out.println("失败原因：" + e.getMessage());
                break;
            }

            for (ClassDef classDef : dex.getClasses()) {
                String javaClassName = dexTypeToJavaName(classDef.getType());

                for (Method method : classDef.getMethods()) {
                    if (!matchAnyRule(rules, javaClassName, method.getName())) {
                        continue;
                    }

                    if (isForbiddenExtractMethod(method)) {
                        System.out.println("跳过禁止抽取方法：" + classDef.getType() + "->" + method.getName());
                        continue;
                    }

                    MethodImplementation impl = method.getImplementation();
                    if (impl == null) {
                        System.out.println("跳过无实现方法：" + classDef.getType() + "->" + method.getName());
                        continue;
                    }

                    ExtractMethodBlock block = new ExtractMethodBlock();
                    block.classId = nextClassId++;
                    block.dexName = dexFile.getName();
                    block.className = classDef.getType();
                    block.methodName = method.getName();
                    block.methodSignature = buildMethodSignature(method);
                    block.accessFlags = method.getAccessFlags();
                    block.registerCount = impl.getRegisterCount();
                    block.paramCount = method.getParameters().size();
                    block.returnType = method.getReturnType();

                    for (Instruction instruction : impl.getInstructions()) {
                        block.instructions.add(buildExtractInstruction(
                                instruction,
                                opcodeMap,
                                opcodePool,
                                opcodePoolIndex
                        ));
                    }

                    blocks.add(block);
                    recordExtractedMethod(block);
                    System.out.println("已抽取 classId=" + block.classId
                            + " dex=" + block.dexName
                            + " class=" + block.className
                            + " method=" + block.methodName
                            + block.methodSignature);
                }
            }

            dexIndex++;
        }

        File binFile = new File(dexDir, "vmp.bin");
        File txtFile = new File(dexDir, "vmp.txt");

        List<ClassIndexEntry> indexEntries = writeVmpBinary(binFile, opcodeMap, blocks);
        writeVmpText(txtFile, opcodeMap, indexEntries, blocks);

        System.out.println("抽取完成，总数量=" + blocks.size());
        System.out.println("opcode映射数量=" + opcodeMap.size());
        System.out.println("索引表数量=" + indexEntries.size());
        System.out.println("明文抽取文件: " + txtFile.getAbsolutePath());
        System.out.println("二进制抽取文件: " + binFile.getAbsolutePath());
    }

    private static List<MethodRule> parseMethodRules(String... methodRules) {
        List<MethodRule> rules = new ArrayList<>();

        if (methodRules == null) {
            return rules;
        }

        for (String ruleText : methodRules) {
            if (ruleText == null) {
                continue;
            }

            ruleText = ruleText.trim();
            if (ruleText.isEmpty()) {
                continue;
            }

            String[] parts = ruleText.split("\\.");
            if (parts.length < 3) {
                System.out.println("跳过非法规则：" + ruleText);
                continue;
            }

            String methodName = parts[parts.length - 1];
            String className = parts[parts.length - 2];

            StringBuilder pkg = new StringBuilder();
            for (int i = 0; i < parts.length - 2; i++) {
                if (i > 0) {
                    pkg.append(".");
                }
                pkg.append(parts[i]);
            }

            MethodRule rule = new MethodRule();
            rule.raw = ruleText;
            rule.packageName = pkg.toString();
            rule.className = className;
            rule.methodName = methodName;

            rules.add(rule);

            System.out.println("添加抽取规则：" + rule.raw
                    + " 包名=" + rule.packageName
                    + " 类名=" + rule.className
                    + " 方法=" + rule.methodName);
        }

        return rules;
    }

    private static boolean matchAnyRule(List<MethodRule> rules, String javaClassName, String methodName) {
        for (MethodRule rule : rules) {
            if (matchRule(rule, javaClassName, methodName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchRule(MethodRule rule, String javaClassName, String methodName) {
        if (rule == null || javaClassName == null || methodName == null) {
            return false;
        }

        int lastDot = javaClassName.lastIndexOf('.');
        String pkg = lastDot >= 0 ? javaClassName.substring(0, lastDot) : "";
        String cls = lastDot >= 0 ? javaClassName.substring(lastDot + 1) : javaClassName;

        return matchPart(rule.packageName, pkg)
                && matchPart(rule.className, cls)
                && matchPart(rule.methodName, methodName);
    }

    private static boolean matchPart(String rulePart, String value) {
        if ("*".equals(rulePart)) {
            return true;
        }
        return rulePart.equals(value);
    }

    private static String dexTypeToJavaName(String dexType) {
        if (dexType == null) {
            return "";
        }

        if (dexType.startsWith("L") && dexType.endsWith(";")) {
            dexType = dexType.substring(1, dexType.length() - 1);
        }

        return dexType.replace('/', '.');
    }

    private static class MethodRule {
        String raw;
        String packageName;
        String className;
        String methodName;
    }

    private static boolean isForbiddenExtractMethod(Method method) {
        if (method == null) {
            return true;
        }

        String name = method.getName();
        int flags = method.getAccessFlags();

        if ("<init>".equals(name) || "<clinit>".equals(name)) {
            return true;
        }

        if ((flags & AccessFlags.ABSTRACT.getValue()) != 0) {
            return true;
        }

        if ((flags & AccessFlags.NATIVE.getValue()) != 0) {
            return true;
        }

        if ((flags & AccessFlags.BRIDGE.getValue()) != 0) {
            return true;
        }

        if ((flags & AccessFlags.SYNTHETIC.getValue()) != 0) {
            return true;
        }

        if ((flags & AccessFlags.DECLARED_SYNCHRONIZED.getValue()) != 0) {
            return true;
        }

        if ((flags & AccessFlags.SYNCHRONIZED.getValue()) != 0) {
            return true;
        }

        if ((flags & AccessFlags.VARARGS.getValue()) != 0) {
            return true;
        }

        return false;
    }

    private static List<Integer> buildRandomOpcodePool() {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= 255; i++) {
            list.add(i);
        }
        Collections.shuffle(list, new Random(System.currentTimeMillis()));
        return list;
    }

    private static int getOrCreateVmOpcode(String opcodeName,
                                           Map<String, Integer> opcodeMap,
                                           List<Integer> opcodePool,
                                           int[] opcodePoolIndex) {
        Integer old = opcodeMap.get(opcodeName);
        if (old != null) {
            return old;
        }

        if (opcodePoolIndex[0] >= opcodePool.size()) {
            throw new IllegalStateException("自定义opcode数量超过255");
        }

        int vmOpcode = opcodePool.get(opcodePoolIndex[0]);
        opcodePoolIndex[0]++;

        opcodeMap.put(opcodeName, vmOpcode);

        System.out.println("生成opcode映射: " + opcodeName
                + " -> 自定义opcode=0x" + String.format("%02x", vmOpcode));

        return vmOpcode;
    }

    private static boolean isValidDexFile(File file) {
        if (file == null || !file.isFile() || file.length() < 0x70) {
            return false;
        }

        try (FileInputStream in = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int read = in.read(magic);

            if (read != 4) {
                return false;
            }

            return magic[0] == 'd'
                    && magic[1] == 'e'
                    && magic[2] == 'x'
                    && magic[3] == '\n';
        } catch (Exception e) {
            return false;
        }
    }

    private static int getDexIndex(String name) {
        if ("classes.dex".equals(name)) {
            return 1;
        }

        String num = name.replace("classes", "").replace(".dex", "");
        try {
            return Integer.parseInt(num);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private static ExtractInstruction buildExtractInstruction(Instruction instruction,
                                                              Map<String, Integer> opcodeMap,
                                                              List<Integer> opcodePool,
                                                              int[] opcodePoolIndex) {
        ExtractInstruction out = new ExtractInstruction();

        out.opcodeName = instruction.getOpcode().name();
        out.vmOpcode = getOrCreateVmOpcode(out.opcodeName, opcodeMap, opcodePool, opcodePoolIndex);
        out.dexlibOpcodeValue = instruction.getOpcode().ordinal();
        out.formatName = String.valueOf(instruction.getOpcode().format);
        out.codeUnits = instruction.getCodeUnits();

        if (instruction instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction insn = (FiveRegisterInstruction) instruction;
            int count = insn.getRegisterCount();
            if (count >= 1) out.registers.add(insn.getRegisterC());
            if (count >= 2) out.registers.add(insn.getRegisterD());
            if (count >= 3) out.registers.add(insn.getRegisterE());
            if (count >= 4) out.registers.add(insn.getRegisterF());
            if (count >= 5) out.registers.add(insn.getRegisterG());
        } else if (instruction instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction insn = (RegisterRangeInstruction) instruction;
            for (int i = 0; i < insn.getRegisterCount(); i++) {
                out.registers.add(insn.getStartRegister() + i);
            }
        } else if (instruction instanceof ThreeRegisterInstruction) {
            ThreeRegisterInstruction insn = (ThreeRegisterInstruction) instruction;
            out.registers.add(insn.getRegisterA());
            out.registers.add(insn.getRegisterB());
            out.registers.add(insn.getRegisterC());
        } else if (instruction instanceof TwoRegisterInstruction) {
            TwoRegisterInstruction insn = (TwoRegisterInstruction) instruction;
            out.registers.add(insn.getRegisterA());
            out.registers.add(insn.getRegisterB());
        } else if (instruction instanceof OneRegisterInstruction) {
            OneRegisterInstruction insn = (OneRegisterInstruction) instruction;
            out.registers.add(insn.getRegisterA());
        }

        if (instruction instanceof WideLiteralInstruction) {
            WideLiteralInstruction insn = (WideLiteralInstruction) instruction;
            out.literalType = 2;
            out.literalValue = insn.getWideLiteral();
        } else if (instruction instanceof NarrowLiteralInstruction) {
            NarrowLiteralInstruction insn = (NarrowLiteralInstruction) instruction;
            out.literalType = 1;
            out.literalValue = insn.getNarrowLiteral();
        }

        if (instruction instanceof OffsetInstruction) {
            OffsetInstruction insn = (OffsetInstruction) instruction;
            out.offsetType = 1;
            out.offsetValue = insn.getCodeOffset();
        }

        if (instruction instanceof ReferenceInstruction) {
            Reference reference = ((ReferenceInstruction) instruction).getReference();

            if (reference instanceof StringReference) {
                out.referenceType = 1;
                out.referenceData = ((StringReference) reference).getString();
            } else if (reference instanceof TypeReference) {
                out.referenceType = 2;
                out.referenceData = ((TypeReference) reference).getType();
            } else if (reference instanceof FieldReference) {
                FieldReference field = (FieldReference) reference;
                out.referenceType = 3;
                out.referenceData = field.getDefiningClass()
                        + "->" + field.getName()
                        + ":" + field.getType();
            } else if (reference instanceof MethodReference) {
                MethodReference method = (MethodReference) reference;
                out.referenceType = 4;
                out.referenceData = method.getDefiningClass()
                        + "->" + method.getName()
                        + buildMethodReferenceSignature(method);
            } else {
                out.referenceType = 9;
                out.referenceData = String.valueOf(reference);
            }
        }

        return out;
    }

    private static List<ClassIndexEntry> writeVmpBinary(File outFile,
                                                        Map<String, Integer> opcodeMap,
                                                        List<ExtractMethodBlock> blocks) throws IOException {
        List<ClassIndexEntry> indexEntries = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(outFile, "rw")) {
            raf.setLength(0);

            writeBytes(raf, new byte[]{'A', 'V', 'M', 'P'});
            writeIntLE(raf, 3);

            writeIntLE(raf, opcodeMap.size());
            for (Map.Entry<String, Integer> entry : opcodeMap.entrySet()) {
                writeStringLE(raf, entry.getKey());
                writeIntLE(raf, entry.getValue());
            }

            writeIntLE(raf, blocks.size());

            long indexTableOffset = raf.getFilePointer();
            for (int i = 0; i < blocks.size(); i++) {
                writeIntLE(raf, 0);
                writeLongLE(raf, 0);
                writeIntLE(raf, 0);
            }

            for (ExtractMethodBlock block : blocks) {
                long blockOffset = raf.getFilePointer();

                writeIntLE(raf, block.classId);
                writeStringLE(raf, block.dexName);
                writeStringLE(raf, block.className);

                writeIntLE(raf, 1);

                writeStringLE(raf, block.methodName);
                writeStringLE(raf, block.methodSignature);
                writeIntLE(raf, block.accessFlags);
                writeIntLE(raf, block.registerCount);
                writeIntLE(raf, block.paramCount);
                writeStringLE(raf, block.returnType);
                writeIntLE(raf, block.instructions.size());

                for (ExtractInstruction insn : block.instructions) {
                    writeIntLE(raf, insn.vmOpcode);
                    writeStringLE(raf, insn.opcodeName);
                    writeIntLE(raf, insn.dexlibOpcodeValue);
                    writeStringLE(raf, insn.formatName);
                    writeIntLE(raf, insn.codeUnits);

                    writeIntLE(raf, insn.registers.size());
                    for (Integer reg : insn.registers) {
                        writeIntLE(raf, reg);
                    }

                    writeIntLE(raf, insn.literalType);
                    writeLongLE(raf, insn.literalValue);

                    writeIntLE(raf, insn.offsetType);
                    writeIntLE(raf, insn.offsetValue);

                    writeIntLE(raf, insn.referenceType);
                    writeStringLE(raf, insn.referenceData);
                }

                long blockEnd = raf.getFilePointer();

                ClassIndexEntry index = new ClassIndexEntry();
                index.classId = block.classId;
                index.offset = blockOffset;
                index.size = (int) (blockEnd - blockOffset);
                indexEntries.add(index);

                //System.out.println("写入数据块索引 classId=" + index.classId + " offset=" + index.offset + " size=" + index.size);
            }

            long fileEnd = raf.getFilePointer();

            raf.seek(indexTableOffset);
            for (ClassIndexEntry index : indexEntries) {
                writeIntLE(raf, index.classId);
                writeLongLE(raf, index.offset);
                writeIntLE(raf, index.size);
            }

            raf.seek(fileEnd);

            System.out.println("bin索引表偏移=" + indexTableOffset);
            System.out.println("bin文件总大小=" + fileEnd);
        }

        return indexEntries;
    }

    private static void writeVmpText(File outFile,
                                     Map<String, Integer> opcodeMap,
                                     List<ClassIndexEntry> indexEntries,
                                     List<ExtractMethodBlock> blocks) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(outFile), StandardCharsets.UTF_8))) {

            pw.println("magic=AVMP");
            pw.println("version=3");
            pw.println();

            pw.println("========== 自定义opcode映射表 ==========");
            pw.println("opcodeMapCount=" + opcodeMap.size());
            for (Map.Entry<String, Integer> entry : opcodeMap.entrySet()) {
                pw.println(entry.getKey()
                        + " -> 0x" + String.format("%02x", entry.getValue()));
            }

            pw.println();
            pw.println("========== classId索引表 ==========");
            pw.println("classIndexCount=" + indexEntries.size());
            for (ClassIndexEntry index : indexEntries) {
                pw.println("classId=" + index.classId
                        + " offset=" + index.offset
                        + " size=" + index.size);
            }

            pw.println();
            pw.println("========== 方法数据块 ==========");
            pw.println("classBlockCount=" + blocks.size());
            pw.println();

            for (ExtractMethodBlock block : blocks) {
                pw.println("========================================");
                pw.println("classId=" + block.classId);
                pw.println("dexName=" + block.dexName);
                pw.println("className=" + block.className);
                pw.println("methodCount=1");
                pw.println("methodName=" + block.methodName);
                pw.println("methodSignature=" + block.methodSignature);
                pw.println("accessFlags=0x" + Integer.toHexString(block.accessFlags));
                pw.println("registerCount=" + block.registerCount);
                pw.println("paramCount=" + block.paramCount);
                pw.println("returnType=" + block.returnType);
                pw.println("instructionCount=" + block.instructions.size());

                int index = 0;
                for (ExtractInstruction insn : block.instructions) {
                    pw.println();
                    pw.println("  instruction[" + index + "]");
                    pw.println("    vmOpcode=0x" + String.format("%02x", insn.vmOpcode));
                    pw.println("    realOpcodeName=" + insn.opcodeName);
                    pw.println("    dexlibOpcodeValue=0x" + Integer.toHexString(insn.dexlibOpcodeValue));
                    pw.println("    formatName=" + insn.formatName);
                    pw.println("    codeUnits=" + insn.codeUnits);
                    pw.println("    registers=" + insn.registers);
                    pw.println("    literalType=" + insn.literalType);
                    pw.println("    literalValue=" + insn.literalValue);
                    pw.println("    offsetType=" + insn.offsetType);
                    pw.println("    offsetValue=" + insn.offsetValue);
                    pw.println("    referenceType=" + insn.referenceType);
                    pw.println("    referenceData=" + insn.referenceData);
                    index++;
                }

                pw.println();
            }
        }
    }

    private static void writeStringLE(FileOutputStream out, String value) throws IOException {
        if (value == null) {
            writeIntLE(out, -1);
            return;
        }

        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        writeIntLE(out, data.length);
        writeBytes(out, data);
    }

    private static void writeIntLE(FileOutputStream out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void writeLongLE(FileOutputStream out, long value) throws IOException {
        out.write((int) (value & 0xff));
        out.write((int) ((value >> 8) & 0xff));
        out.write((int) ((value >> 16) & 0xff));
        out.write((int) ((value >> 24) & 0xff));
        out.write((int) ((value >> 32) & 0xff));
        out.write((int) ((value >> 40) & 0xff));
        out.write((int) ((value >> 48) & 0xff));
        out.write((int) ((value >> 56) & 0xff));
    }

    private static void writeBytes(FileOutputStream out, byte[] data) throws IOException {
        out.write(data);
    }

    private static class ExtractMethodBlock {
        int classId;
        String dexName;
        String className;
        String methodName;
        String methodSignature;
        int accessFlags;
        int registerCount;
        int paramCount;
        String returnType;
        List<ExtractInstruction> instructions = new ArrayList<>();
    }

    private static class ExtractInstruction {
        int vmOpcode;
        String opcodeName;
        int dexlibOpcodeValue;
        String formatName;
        int codeUnits;

        List<Integer> registers = new ArrayList<>();

        int literalType = 0;
        long literalValue = 0;

        int offsetType = 0;
        int offsetValue = 0;

        int referenceType = 0;
        String referenceData = null;
    }

    /**
     * :TODO
     * 解析bin文件以校验读取是否正常
     * */
    public static void parseVmpBinByClassId(File binFile, int targetClassId) throws IOException {
        if (binFile == null || !binFile.isFile()) {
            throw new IOException("bin文件不存在");
        }

        long startNs = System.nanoTime();

        try (RandomAccessFile raf = new RandomAccessFile(binFile, "r")) {
            byte[] magic = new byte[4];
            readFully(raf, magic);

            String magicText = new String(magic, StandardCharsets.UTF_8);
            System.out.println("magic=" + magicText);

            if (!"AVMP".equals(magicText)) {
                throw new IOException("bin格式错误，magic不匹配");
            }

            int version = readIntLE(raf);
            System.out.println("version=" + version);

            if (version != 3) {
                throw new IOException("当前解析器只支持version=3，当前version=" + version);
            }

            int opcodeMapCount = readIntLE(raf);
            System.out.println("opcodeMapCount=" + opcodeMapCount);

            Map<Integer, String> vmOpcodeToRealName = new LinkedHashMap<>();

            System.out.println("========== 解析opcode映射表 ==========");
            for (int i = 0; i < opcodeMapCount; i++) {
                String realOpcodeName = readStringLE(raf);
                int vmOpcode = readIntLE(raf);

                vmOpcodeToRealName.put(vmOpcode, realOpcodeName);

                System.out.println("map[" + i + "] 自定义opcode=0x"
                        + String.format("%02x", vmOpcode)
                        + " -> 真实指令=" + realOpcodeName);
            }

            int classIndexCount = readIntLE(raf);
            long indexTableOffset = raf.getFilePointer();

            System.out.println("========== 解析classId索引表 ==========");
            System.out.println("classIndexCount=" + classIndexCount);
            System.out.println("indexTableOffset=" + indexTableOffset);

            ClassIndexEntry targetIndex = null;

            for (int i = 0; i < classIndexCount; i++) {
                ClassIndexEntry index = new ClassIndexEntry();
                index.classId = readIntLE(raf);
                index.offset = readLongLE(raf);
                index.size = readIntLE(raf);

                System.out.println("index[" + i + "] classId=" + index.classId
                        + " offset=" + index.offset
                        + " size=" + index.size);

                if (index.classId == targetClassId) {
                    targetIndex = index;
                }
            }

            if (targetIndex == null) {
                long endNs = System.nanoTime();
                System.out.println("未找到目标classId：" + targetClassId);
                System.out.println("解析耗时=" + ((endNs - startNs) / 1000000.0) + " ms");
                return;
            }

            System.out.println("========================================");
            System.out.println("命中目标索引");
            System.out.println("targetClassId=" + targetClassId);
            System.out.println("blockOffset=" + targetIndex.offset);
            System.out.println("blockSize=" + targetIndex.size);
            System.out.println("开始seek到数据块地址：" + targetIndex.offset);

            raf.seek(targetIndex.offset);

            long blockStartPos = raf.getFilePointer();

            int classId = readIntLE(raf);
            String dexName = readStringLE(raf);
            String className = readStringLE(raf);
            int methodCount = readIntLE(raf);

            System.out.println("========================================");
            System.out.println("开始解析目标数据块");
            System.out.println("blockStartPos=" + blockStartPos);
            System.out.println("classId=" + classId);
            System.out.println("dexName=" + dexName);
            System.out.println("className=" + className);
            System.out.println("methodCount=" + methodCount);

            for (int methodIndex = 0; methodIndex < methodCount; methodIndex++) {
                String methodName = readStringLE(raf);
                String methodSignature = readStringLE(raf);
                int accessFlags = readIntLE(raf);
                int registerCount = readIntLE(raf);
                int paramCount = readIntLE(raf);
                String returnType = readStringLE(raf);
                int instructionCount = readIntLE(raf);

                /*System.out.println("----------------------------------------");
                System.out.println("methodIndex=" + methodIndex);
                System.out.println("methodName=" + methodName);
                System.out.println("methodSignature=" + methodSignature);
                System.out.println("accessFlags=0x" + Integer.toHexString(accessFlags));
                System.out.println("registerCount=" + registerCount);
                System.out.println("paramCount=" + paramCount);
                System.out.println("returnType=" + returnType);
                System.out.println("instructionCount=" + instructionCount);*/

                for (int insnIndex = 0; insnIndex < instructionCount; insnIndex++) {
                    long insnOffset = raf.getFilePointer();

                    int vmOpcode = readIntLE(raf);
                    String opcodeNameInInsn = readStringLE(raf);
                    int dexlibOpcodeValue = readIntLE(raf);
                    String formatName = readStringLE(raf);
                    int codeUnits = readIntLE(raf);

                    int regCount = readIntLE(raf);
                    List<Integer> registers = new ArrayList<>();
                    for (int i = 0; i < regCount; i++) {
                        registers.add(readIntLE(raf));
                    }

                    int literalType = readIntLE(raf);
                    long literalValue = readLongLE(raf);

                    int offsetType = readIntLE(raf);
                    int offsetValue = readIntLE(raf);

                    int referenceType = readIntLE(raf);
                    String referenceData = readStringLE(raf);

                    String realOpcodeName = vmOpcodeToRealName.get(vmOpcode);

                    /*System.out.println();
                    System.out.println("  instruction[" + insnIndex + "]");
                    System.out.println("    insnOffset=" + insnOffset);
                    System.out.println("    vmOpcode=0x" + String.format("%02x", vmOpcode));
                    System.out.println("    mapRealOpcodeName=" + realOpcodeName);
                    System.out.println("    insnRealOpcodeName=" + opcodeNameInInsn);
                    System.out.println("    dexlibOpcodeValue=0x" + Integer.toHexString(dexlibOpcodeValue));
                    System.out.println("    formatName=" + formatName);
                    System.out.println("    codeUnits=" + codeUnits);
                    System.out.println("    registerCount=" + regCount);
                    System.out.println("    registers=" + registers);
                    System.out.println("    literalType=" + literalType);
                    System.out.println("    literalValue=" + literalValue);
                    System.out.println("    offsetType=" + offsetType);
                    System.out.println("    offsetValue=" + offsetValue);
                    System.out.println("    referenceType=" + referenceType);
                    System.out.println("    referenceData=" + referenceData);*/
                }
            }

            long blockEndPos = raf.getFilePointer();
            long parsedSize = blockEndPos - blockStartPos;

            System.out.println("========================================");
            System.out.println("目标classId解析完成：" + targetClassId);
            System.out.println("blockEndPos=" + blockEndPos);
            System.out.println("parsedBlockSize=" + parsedSize);
            System.out.println("indexBlockSize=" + targetIndex.size);

            if (parsedSize != targetIndex.size) {
                System.out.println("警告：解析出来的数据块大小和索引表记录不一致");
            }

            long endNs = System.nanoTime();
            System.out.println("解析耗时=" + ((endNs - startNs) / 1000000.0) + " ms");
        }
    }
    private static class ClassIndexEntry {
        int classId;
        long offset;
        int size;
    }

    private static void writeStringLE(RandomAccessFile out, String value) throws IOException {
        if (value == null) {
            writeIntLE(out, -1);
            return;
        }

        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        writeIntLE(out, data.length);
        writeBytes(out, data);
    }

    private static void writeIntLE(RandomAccessFile out, int value) throws IOException {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void writeLongLE(RandomAccessFile out, long value) throws IOException {
        out.write((int) (value & 0xff));
        out.write((int) ((value >> 8) & 0xff));
        out.write((int) ((value >> 16) & 0xff));
        out.write((int) ((value >> 24) & 0xff));
        out.write((int) ((value >> 32) & 0xff));
        out.write((int) ((value >> 40) & 0xff));
        out.write((int) ((value >> 48) & 0xff));
        out.write((int) ((value >> 56) & 0xff));
    }

    private static void writeBytes(RandomAccessFile out, byte[] data) throws IOException {
        out.write(data);
    }

    private static int readIntLE(RandomAccessFile in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();

        if ((b0 | b1 | b2 | b3) < 0) {
            throw new IOException("读取int失败，文件长度不足");
        }

        return (b0 & 0xff)
                | ((b1 & 0xff) << 8)
                | ((b2 & 0xff) << 16)
                | ((b3 & 0xff) << 24);
    }

    private static long readLongLE(RandomAccessFile in) throws IOException {
        long b0 = in.read();
        long b1 = in.read();
        long b2 = in.read();
        long b3 = in.read();
        long b4 = in.read();
        long b5 = in.read();
        long b6 = in.read();
        long b7 = in.read();

        if ((b0 | b1 | b2 | b3 | b4 | b5 | b6 | b7) < 0) {
            throw new IOException("读取long失败，文件长度不足");
        }

        return (b0 & 0xff)
                | ((b1 & 0xff) << 8)
                | ((b2 & 0xff) << 16)
                | ((b3 & 0xff) << 24)
                | ((b4 & 0xff) << 32)
                | ((b5 & 0xff) << 40)
                | ((b6 & 0xff) << 48)
                | ((b7 & 0xff) << 56);
    }

    private static String readStringLE(RandomAccessFile in) throws IOException {
        int len = readIntLE(in);

        if (len == -1) {
            return null;
        }

        if (len < 0) {
            throw new IOException("字符串长度非法：" + len);
        }

        byte[] data = new byte[len];
        readFully(in, data);

        return new String(data, StandardCharsets.UTF_8);
    }

    private static void readFully(RandomAccessFile in, byte[] data) throws IOException {
        int offset = 0;

        while (offset < data.length) {
            int read = in.read(data, offset, data.length - offset);
            if (read == -1) {
                throw new IOException("读取文件失败，文件长度不足");
            }
            offset += read;
        }
    }

    private static int readIntLE(FileInputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int b3 = in.read();

        if ((b0 | b1 | b2 | b3) < 0) {
            throw new IOException("读取int失败，文件长度不足");
        }

        return (b0 & 0xff)
                | ((b1 & 0xff) << 8)
                | ((b2 & 0xff) << 16)
                | ((b3 & 0xff) << 24);
    }

    private static long readLongLE(FileInputStream in) throws IOException {
        long b0 = in.read();
        long b1 = in.read();
        long b2 = in.read();
        long b3 = in.read();
        long b4 = in.read();
        long b5 = in.read();
        long b6 = in.read();
        long b7 = in.read();

        if ((b0 | b1 | b2 | b3 | b4 | b5 | b6 | b7) < 0) {
            throw new IOException("读取long失败，文件长度不足");
        }

        return (b0 & 0xff)
                | ((b1 & 0xff) << 8)
                | ((b2 & 0xff) << 16)
                | ((b3 & 0xff) << 24)
                | ((b4 & 0xff) << 32)
                | ((b5 & 0xff) << 40)
                | ((b6 & 0xff) << 48)
                | ((b7 & 0xff) << 56);
    }

    private static String readStringLE(FileInputStream in) throws IOException {
        int len = readIntLE(in);

        if (len == -1) {
            return null;
        }

        if (len < 0) {
            throw new IOException("字符串长度非法：" + len);
        }

        byte[] data = new byte[len];
        readFully(in, data);

        return new String(data, StandardCharsets.UTF_8);
    }

    private static void readFully(FileInputStream in, byte[] data) throws IOException {
        int offset = 0;

        while (offset < data.length) {
            int read = in.read(data, offset, data.length - offset);
            if (read == -1) {
                throw new IOException("读取文件失败，文件长度不足");
            }
            offset += read;
        }
    }

    private static class ExtractedMethodInfo {
        int classId;
        String dexName;
        String className;
        String methodName;
        String methodSignature;
        int accessFlags;
        int registerCount;
        int paramCount;
        String returnType;
    }
    private static String buildExtractedMethodKey(String className, String methodName, String methodSignature) {
        return className + "->" + methodName + methodSignature;
    }
    private static void recordExtractedMethod(ExtractMethodBlock block) {
        if (block == null) {
            return;
        }

        ExtractedMethodInfo info = new ExtractedMethodInfo();
        info.classId = block.classId;
        info.dexName = block.dexName;
        info.className = block.className;
        info.methodName = block.methodName;
        info.methodSignature = block.methodSignature;
        info.accessFlags = block.accessFlags;
        info.registerCount = block.registerCount;
        info.paramCount = block.paramCount;
        info.returnType = block.returnType;

        String key = buildExtractedMethodKey(
                block.className,
                block.methodName,
                block.methodSignature
        );

        EXTRACTED_METHOD_MAP.put(key, info);

        System.out.println("记录待native重写方法 key=" + key
                + " classId=" + block.classId
                + " accessFlags=0x" + Integer.toHexString(block.accessFlags)
                + " returnType=" + block.returnType);
    }
    private static ExtractedMethodInfo getExtractedMethodInfo(String className,
                                                              String methodName,
                                                              String methodSignature) {
        String key = buildExtractedMethodKey(className, methodName, methodSignature);
        return EXTRACTED_METHOD_MAP.get(key);
    }


    /**
     * :TODO
     * 重组dex
     * */
    public static void rewriteExtractedMethodsToNativeDex(File dexDir,
                                                          String soName,
                                                          String vmpPackageName) throws IOException {
        if (dexDir == null || !dexDir.isDirectory()) {
            throw new IOException("dex目录不存在");
        }

        if (EXTRACTED_METHOD_MAP.isEmpty()) {
            throw new IOException("EXTRACTED_METHOD_MAP为空，请先执行方法抽取");
        }

        String vmpClassType = "L" + vmpPackageName.replace('.', '/') + "/VMP;";

        int outDexIndex = 1;
        int currentMethodCount = 0;
        List<ClassDef> currentClasses = new ArrayList<>();

        DexFile shellDex = createVmpShellDex(soName, vmpPackageName);
        for (ClassDef shellClass : shellDex.getClasses()) {
            currentClasses.add(shellClass);
            currentMethodCount += countClassMethods(shellClass);
        }

        int inDexIndex = 1;

        while (true) {
            String dexName = inDexIndex == 1 ? "classes.dex" : "classes" + inDexIndex + ".dex";
            File dexFile = new File(dexDir, dexName);

            if (!dexFile.isFile()) {
                System.out.println("dex编号断开，停止重写：" + dexName);
                break;
            }

            if (!isValidDexFile(dexFile)) {
                System.out.println("跳过非法dex文件：" + dexFile.getAbsolutePath());
                break;
            }

            DexBackedDexFile dex;
            try {
                dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
            } catch (Throwable e) {
                System.out.println("解析dex失败，停止重写：" + dexFile.getName());
                System.out.println("失败原因：" + e.getMessage());
                break;
            }

            System.out.println("开始重写dex：" + dexFile.getName());

            for (ClassDef classDef : dex.getClasses()) {
                ClassDef newClass = rewriteClassForNative(classDef, vmpClassType);
                int addMethodCount = countClassMethods(newClass);

                if (!currentClasses.isEmpty() && currentMethodCount + addMethodCount > 60000) {
                    writeCombinedDex(dexDir, outDexIndex, currentClasses);
                    outDexIndex++;

                    currentClasses = new ArrayList<>();
                    currentMethodCount = 0;
                }

                currentClasses.add(newClass);
                currentMethodCount += addMethodCount;
            }

            inDexIndex++;
        }

        if (!currentClasses.isEmpty()) {
            writeCombinedDex(dexDir, outDexIndex, currentClasses);
        }

        System.out.println("native重写完成，输出dex数量=" + outDexIndex);
    }
    private static ClassDef rewriteClassForNative(ClassDef classDef, String vmpClassType) {
        boolean isInterface = (classDef.getAccessFlags() & AccessFlags.INTERFACE.getValue()) != 0;

        if (isInterface) {
            System.out.println("接口类原样复制：" + classDef.getType());
            return classDef;
        }

        List<Method> newMethods = new ArrayList<>();
        List<ExtractedMethodInfo> needInitList = new ArrayList<>();

        boolean hasClinit = false;

        for (Method method : classDef.getMethods()) {
            String signature = buildMethodSignature(method);
            ExtractedMethodInfo info = getExtractedMethodInfo(
                    classDef.getType(),
                    method.getName(),
                    signature
            );

            if ("<clinit>".equals(method.getName())) {
                hasClinit = true;
                newMethods.add(method);
                continue;
            }

            if (info != null) {
                Method nativeMethod = new ImmutableMethod(
                        method.getDefiningClass(),
                        method.getName(),
                        method.getParameters(),
                        method.getReturnType(),
                        (method.getAccessFlags() | AccessFlags.NATIVE.getValue()),
                        method.getAnnotations(),
                        null,
                        null
                );

                newMethods.add(nativeMethod);
                needInitList.add(info);

                System.out.println("方法改为native："
                        + classDef.getType()
                        + "->"
                        + method.getName()
                        + signature
                        + " classId=" + info.classId);
            } else {
                newMethods.add(method);
            }
        }

        if (!needInitList.isEmpty()) {
            List<Method> finalMethods = new ArrayList<>();

            for (Method method : newMethods) {
                if ("<clinit>".equals(method.getName())) {
                    finalMethods.add(rebuildClinitWithVmpInit(
                            classDef.getType(),
                            method,
                            needInitList,
                            vmpClassType
                    ));
                } else {
                    finalMethods.add(method);
                }
            }

            if (!hasClinit) {
                finalMethods.add(createClinitWithVmpInit(
                        classDef.getType(),
                        needInitList,
                        vmpClassType
                ));
            }

            newMethods = finalMethods;
        }

        return new ImmutableClassDef(
                classDef.getType(),
                classDef.getAccessFlags(),
                classDef.getSuperclass(),
                classDef.getInterfaces(),
                classDef.getSourceFile(),
                classDef.getAnnotations(),
                classDef.getFields(),
                newMethods
        );
    }
    private static Method createClinitWithVmpInit(String classType,
                                                  List<ExtractedMethodInfo> infos,
                                                  String vmpClassType) {
        List<Instruction> instructions = new ArrayList<>();
        appendVmpInitInstructions(instructions, infos, classType, vmpClassType);
        instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));

        MethodImplementation impl = new ImmutableMethodImplementation(
                2,
                instructions,
                null,
                null
        );

        return new ImmutableMethod(
                classType,
                "<clinit>",
                Collections.emptyList(),
                "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                null,
                null,
                impl
        );
    }

    private static Method rebuildClinitWithVmpInit(String classType,
                                                   Method oldClinit,
                                                   List<ExtractedMethodInfo> infos,
                                                   String vmpClassType) {
        MethodImplementation oldImpl = oldClinit.getImplementation();

        List<Instruction> instructions = new ArrayList<>();
        int registerCount = 2;

        if (oldImpl != null) {
            registerCount = Math.max(oldImpl.getRegisterCount(), 2);

            for (Instruction instruction : oldImpl.getInstructions()) {
                if (instruction.getOpcode() == Opcode.RETURN_VOID) {
                    continue;
                }
                instructions.add(instruction);
            }
        }

        appendVmpInitInstructions(instructions, infos, classType, vmpClassType);
        instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));

        MethodImplementation impl = new ImmutableMethodImplementation(
                registerCount,
                instructions,
                oldImpl == null ? null : oldImpl.getTryBlocks(),
                oldImpl == null ? null : oldImpl.getDebugItems()
        );

        return new ImmutableMethod(
                classType,
                "<clinit>",
                Collections.emptyList(),
                "V",
                oldClinit.getAccessFlags(),
                oldClinit.getAnnotations(),
                null,
                impl
        );
    }

    private static void appendVmpInitInstructions(List<Instruction> instructions,
                                                  List<ExtractedMethodInfo> infos,
                                                  String currentClassType,
                                                  String vmpClassType) {
        for (ExtractedMethodInfo info : infos) {
            instructions.add(new ImmutableInstruction31i(
                    Opcode.CONST,
                    0,
                    info.classId
            ));

            instructions.add(new ImmutableInstruction21c(
                    Opcode.CONST_CLASS,
                    1,
                    new ImmutableTypeReference(currentClassType)
            ));

            instructions.add(new ImmutableInstruction35c(
                    Opcode.INVOKE_STATIC,
                    2,
                    0,
                    1,
                    0,
                    0,
                    0,
                    new ImmutableMethodReference(
                            vmpClassType,
                            "init",
                            Arrays.asList("I", "Ljava/lang/Class;"),
                            "V"
                    )
            ));

            System.out.println("插入VMP.init classId=" + info.classId
                    + " class=" + currentClassType);
        }
    }
    private static void writeCombinedDex(File dexDir,
                                         int outDexIndex,
                                         List<ClassDef> classes) throws IOException {
        String outName = outDexIndex == 1 ? "classes_c.dex" : "classes" + outDexIndex + "_c.dex";
        File outFile = new File(dexDir, outName);

        DexFile outDex = new ImmutableDexFile(
                Opcodes.getDefault(),
                classes
        );

        DexFileFactory.writeDexFile(outFile.getAbsolutePath(), outDex);

        System.out.println("写出重组dex：" + outFile.getAbsolutePath()
                + " classCount=" + classes.size());
    }
    private static int countClassMethods(ClassDef classDef) {
        int count = 0;
        for (Method ignored : classDef.getMethods()) {
            count++;
        }
        return count;
    }






}