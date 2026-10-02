package com.etatech.hashiya.core.network

internal fun readFixture(name: String): String =
    requireNotNull(TestFixtures::class.java.classLoader?.getResource(name)) { "Missing fixture $name" }.readText()

private object TestFixtures
