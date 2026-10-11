cmake_minimum_required(VERSION 3.22.1)
project("mpmanager-native")

add_library(
    mp-native
    SHARED
    native-lib.cpp
    tar_gz_reader.cpp
)

find_library(log-lib log)
target_link_libraries(mp-native ${log-lib} z)
