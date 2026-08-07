package com.scan2enter.labels.a4.packaging

data class PackagingOptions(
    val type: PackagingType,
    val includeHook: Boolean,
    val showPrice: Boolean
)