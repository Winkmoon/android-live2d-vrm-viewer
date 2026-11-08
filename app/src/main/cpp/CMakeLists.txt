cmake_minimum_required(VERSION 3.22)
project(m3elive2d LANGUAGES C CXX)

set(APP_NAME m3elive2d)
set(SDK_ROOT "${LIVE2D_SDK}")
if(NOT EXISTS "${SDK_ROOT}/Core/include/Live2DCubismCore.h")
    message(FATAL_ERROR "Live2D SDK not found at '${SDK_ROOT}'. Run scripts/fetch_cubism.sh or pass -Plive2dSdk=/path/to/CubismSdkForNative-5-r.5")
endif()

set(CORE_PATH "${SDK_ROOT}/Core")
set(FRAMEWORK_PATH "${SDK_ROOT}/Framework")
set(COMMON_SRC_PATH "${SDK_ROOT}/Samples/Common")
set(THIRD_PARTY_PATH "${SDK_ROOT}/Samples/OpenGL/thirdParty")
set(STB_PATH "${THIRD_PARTY_PATH}/stb")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(Live2DCubismCore STATIC IMPORTED)
set_target_properties(Live2DCubismCore PROPERTIES
    IMPORTED_LOCATION "${CORE_PATH}/lib/android/${ANDROID_ABI}/libLive2DCubismCore.a"
    INTERFACE_INCLUDE_DIRECTORIES "${CORE_PATH}/include"
)

# Use the official OpenGL ES 2.0 framework renderer (ES3 contexts are backward compatible).
set(FRAMEWORK_SOURCE OpenGL)
add_subdirectory("${FRAMEWORK_PATH}" "${CMAKE_CURRENT_BINARY_DIR}/Framework")
target_compile_definitions(Framework PUBLIC CSM_TARGET_ANDROID_ES2)

add_library(${APP_NAME} SHARED)
file(GLOB APP_SOURCES CONFIGURE_DEPENDS "${CMAKE_CURRENT_SOURCE_DIR}/*.cpp")
target_sources(${APP_NAME} PRIVATE ${APP_SOURCES})

# Minimum demo: model + texture + view + allocator, no WAV playback.
set(CSM_MINIMUM_DEMO 1)
add_subdirectory("${COMMON_SRC_PATH}" "${CMAKE_CURRENT_BINARY_DIR}/Common")

target_link_libraries(${APP_NAME}
    Framework
    Live2DCubismCore
    GLESv2
    log
    android
)

target_include_directories(${APP_NAME} PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
    "${COMMON_SRC_PATH}"
    "${STB_PATH}"
)

target_compile_options(${APP_NAME} PRIVATE
    -O3
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -Wall
    -Wextra
    -Wno-unused-parameter
    -Wno-deprecated-declarations
)

target_link_options(${APP_NAME} PRIVATE
    -Wl,-z,max-page-size=16384
    -Wl,--gc-sections
    -Wl,--no-undefined
)
