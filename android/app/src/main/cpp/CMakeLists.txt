cmake_minimum_required(VERSION 3.22.1)
project(kayan_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── llama.cpp integration ───────────────────────────────────────────────────
# Expected layout (populated by setup script or git submodule):
#   app/src/main/cpp/llama/   ← clone of ggml-org/llama.cpp
#
# We deliberately do NOT vendor the entire llama.cpp source in the initial
# commit to keep the repository lean. The build will fail clearly if missing.

set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama)

if(EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(STATUS "llama.cpp found — building real native engine")
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
    add_subdirectory(${LLAMA_DIR} llama_build)
    set(LLAMA_AVAILABLE TRUE)
else()
    message(WARNING "llama.cpp NOT found at ${LLAMA_DIR}")
    message(WARNING "Run: ./scripts/setup_llama.sh  before building native")
    set(LLAMA_AVAILABLE FALSE)
endif()

# ─── Our JNI bridge ──────────────────────────────────────────────────────────
add_library(kayan_jni SHARED
    jni/kayan_jni.cpp
    jni/engine_wrapper.cpp
)

target_include_directories(kayan_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/jni
)

if(LLAMA_AVAILABLE)
    target_link_libraries(kayan_jni llama android log)
    target_compile_definitions(kayan_jni PRIVATE KAYAN_HAS_LLAMA=1)
else()
    target_link_libraries(kayan_jni android log)
    target_compile_definitions(kayan_jni PRIVATE KAYAN_HAS_LLAMA=0)
endif()
