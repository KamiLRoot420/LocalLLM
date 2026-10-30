cmake_minimum_required(VERSION 3.22.1)
project("native-bridge" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# ---------------------------------------------------------------------------
# llama.cpp
#
# Two supported modes:
#   1. LOCAL  - sources vendored in app/src/main/cpp/llama.cpp (offline builds)
#   2. FETCH  - downloaded at configure time via FetchContent (needs network)
# ---------------------------------------------------------------------------
set(LLAMA_CPP_TAG "b4585" CACHE STRING "llama.cpp git tag used for the build")
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp" CACHE PATH "Path to llama.cpp sources")

if(EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(STATUS "native-bridge: using vendored llama.cpp from ${LLAMA_DIR}")
    add_subdirectory(${LLAMA_DIR} llama_cpp_build EXCLUDE_FROM_ALL)
else()
    message(STATUS "native-bridge: fetching llama.cpp ${LLAMA_CPP_TAG}")
    include(FetchContent)
    FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
        GIT_TAG ${LLAMA_CPP_TAG}
        GIT_SHALLOW TRUE
    )
    FetchContent_MakeAvailable(llama_cpp)
endif()

# ---------------------------------------------------------------------------
# Vulkan
# ---------------------------------------------------------------------------
find_package(Vulkan REQUIRED)

add_library(native-bridge SHARED
    native-bridge.cpp
)

target_include_directories(native-bridge PRIVATE
    ${LLAMA_DIR}
    ${Vulkan_INCLUDE_DIRS}
)

target_link_libraries(native-bridge PRIVATE
    llama
    ggml
    Vulkan::Vulkan
    android
    log
)

# ggml-vulkan is only produced when GGML_VULKAN=ON
if(TARGET ggml-vulkan)
    target_link_libraries(native-bridge PRIVATE ggml-vulkan)
    target_compile_definitions(native-bridge PRIVATE GGML_VULKAN=ON)
endif()

target_compile_options(native-bridge PRIVATE
    -O3
    -fexceptions
    -frtti
    -fvisibility=hidden
)

target_compile_definitions(native-bridge PRIVATE
    $<$<CONFIG:Release>:NDEBUG>
)
