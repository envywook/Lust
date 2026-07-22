package com.envy.dualcorevpn.vpn

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TProxyServiceContractTest {
    @Test
    fun `JNI entry points match the bundled v2rayNG native library registration`() {
        val methods = Class.forName(
            "com.v2ray.ang.service.TProxyService",
            false,
            javaClass.classLoader,
        ).declaredMethods
            .associateBy { it.name }

        assertTrue("Missing TProxyStartService JNI method", methods.containsKey("TProxyStartService"))
        assertTrue("Missing TProxyStopService JNI method", methods.containsKey("TProxyStopService"))
        assertTrue("Missing TProxyGetStats JNI method", methods.containsKey("TProxyGetStats"))
        assertEquals(listOf(String::class.java, Int::class.javaPrimitiveType), methods.getValue("TProxyStartService").parameterTypes.toList())
        assertEquals(LongArray::class.java, methods.getValue("TProxyGetStats").returnType)
        assertTrue(Modifier.isNative(methods.getValue("TProxyStartService").modifiers))
        assertTrue(Modifier.isNative(methods.getValue("TProxyStopService").modifiers))
        assertTrue(Modifier.isNative(methods.getValue("TProxyGetStats").modifiers))
    }
}
