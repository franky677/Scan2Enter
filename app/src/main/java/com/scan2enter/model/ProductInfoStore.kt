package com.scan2enter.model

object ProductInfoStore {

    @Volatile
    var current: ProductInfo? = null

}