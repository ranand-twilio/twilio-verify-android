/*
 * Copyright (c) 2020 Twilio Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twilio.security.storage

import kotlin.reflect.KClass

interface Serializer {

  fun <T : Any> toByteArray(value: T): ByteArray
  fun <T : Any> fromByteArray(
    data: ByteArray,
    kClass: KClass<T>
  ): T?
}

internal class DefaultSerializer : Serializer {
  override fun <T : Any> toByteArray(value: T): ByteArray {
    return when (value) {
      is String -> value.toByteArray()
      is Int,
      is Double,
      is Boolean,
      is Float,
      is Long ->
        value.toString()
          .toByteArray()
      else -> throw IllegalArgumentException("Value type not supported")
    }
  }

  override fun <T : Any> fromByteArray(
    data: ByteArray,
    kClass: KClass<T>
  ): T? {
    return when {
      kClass.isAssignableFrom(String::class) -> String(data) as? T
      kClass.isAssignableFrom(Int::class) -> String(
        data
      ).toInt() as? T
      kClass.isAssignableFrom(Double::class) -> String(
        data
      ).toDouble() as? T
      kClass.isAssignableFrom(Boolean::class) ->
        String(
          data
        ).takeIf {
          it.equals("false", ignoreCase = true) || it.equals(
            "true", ignoreCase = true
          )
        }
          ?.toBoolean() as? T
      kClass.isAssignableFrom(Float::class) -> String(
        data
      ).toFloat() as? T
      kClass.isAssignableFrom(Long::class) -> String(
        data
      ).toLong() as? T
      else -> throw IllegalArgumentException("Value type not supported")
    }
  }
}

internal fun <T : Any, R : Any> KClass<T>.isAssignableFrom(kClass: KClass<R>): Boolean =
  this.javaObjectType.isAssignableFrom(kClass.javaObjectType)
