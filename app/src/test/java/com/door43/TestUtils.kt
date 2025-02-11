package com.door43

import java.lang.reflect.Field
import java.net.URL

object TestUtils {

    /**
     * Sets a property of an object using reflection
     * @param obj the source object
     * @param fieldName the name of the field
     * @param value the value to set
     */
    fun setPropertyReflection(obj: Any, fieldName: String, value: Any) {
        val cls = obj::class.java
        val field = findField(cls, fieldName)
        field?.isAccessible = true
        field?.set(obj, value)
    }

    /**
     * Finds a field in a class hierarchy
     */
    private fun findField(cls: Class<*>, fieldName: String): Field? {
        var field: Field? = null
        try {
            field = cls.getDeclaredField(fieldName)
        } catch (e: NoSuchFieldException) {
            if (cls.superclass != null) {
                field = findField(cls.superclass, fieldName)
            }
        }
        return field
    }

    /**
     * Gets a resource from the test resources folder
     */
    fun getResource(name: String): URL? {
        return this.javaClass.classLoader?.getResource(name)
    }
}