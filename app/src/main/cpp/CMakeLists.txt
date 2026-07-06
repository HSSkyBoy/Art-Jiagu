cmake_minimum_required(VERSION 3.22.1)

project("ArkStub")

add_library(
        ArkStub
        SHARED
        ArkStub.cpp
        sha256.cpp
        ArkDexLoader.cpp
        ArkEnvGuard.cpp
        ArkSelfHash.cpp

)


target_compile_options(
        ArkStub
        PRIVATE
        -Os
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -ffunction-sections
        -fdata-sections
        -fno-exceptions
        -fno-rtti
)

target_link_options(
        ArkStub
        PRIVATE
        "-Wl,--gc-sections"
        "-Wl,--strip-all"
        "-Wl,-z,relro"
        "-Wl,-z,now"
        "-Wl,-z,noexecstack"
        "-Wl,-z,max-page-size=16384"
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
        ArkStub
        ${log-lib}
        ${android-lib}
)

# 加固器工具 so：libArkTool.so
add_library(
        ArkTool
        SHARED
        ArkTool.cpp
)

target_compile_options(
        ArkTool
        PRIVATE
        -Os
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -ffunction-sections
        -fdata-sections
        -fno-exceptions
        -fno-rtti
)

target_link_options(
        ArkTool
        PRIVATE
        "-Wl,--gc-sections"
        "-Wl,--strip-all"
        "-Wl,-z,relro"
        "-Wl,-z,now"
        "-Wl,-z,noexecstack"
        "-Wl,-z,max-page-size=16384"
)

target_link_libraries(
        ArkTool
        ${log-lib}
        ${android-lib}
)

find_library(z-lib z)

target_link_libraries(
        ArkTool
        ${log-lib}
        ${android-lib}
)

add_library(zipalign SHARED
        ZipAlign.cpp
        zipalign/ZipAlign.cpp
        zipalign/ZipEntry.cpp
        zipalign/ZipFile.cpp
        android/native/src/utils/SharedBuffer.cpp
        android/native/src/utils/ZipUtils.cpp
        android/native/src/utils/VectorImpl.cpp)

target_include_directories(zipalign BEFORE PUBLIC
        zipalign/include
        android/base/include
        android/core/include
        android/native/include)

target_link_libraries(zipalign log)

target_link_libraries(zipalign z)
target_compile_definitions(zipalign PRIVATE
        _FILE_OFFSET_BITS=64
)

target_link_options(
        zipalign
        PRIVATE
        "-Wl,--gc-sections"
        "-Wl,--strip-all"
        "-Wl,-z,relro"
        "-Wl,-z,now"
        "-Wl,-z,noexecstack"
        "-Wl,-z,max-page-size=16384"
)