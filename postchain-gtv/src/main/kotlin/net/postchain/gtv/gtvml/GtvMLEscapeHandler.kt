package net.postchain.gtv.gtvml

/*
 * Adapted from org.glassfish.jaxb.core.marshaller.MinimumEscapeHandler which is:
 * Copyright (c) 1997, 2021 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import org.glassfish.jaxb.core.marshaller.CharacterEscapeHandler
import java.io.IOException
import java.io.Writer

/**
 * [Characters not allowed in XML](https://www.w3.org/TR/xml/#NT-Char)
 * will be escaped with `&#nn;` but produce ill-formed XML output if [strict] is false,
 * or throw [IllegalArgumentException] if [strict] is true.
 */
class GtvMLEscapeHandler(val strict: Boolean) : CharacterEscapeHandler {
    @Throws(IOException::class)
    override fun escape(ch: CharArray, start: Int, length: Int, isAttVal: Boolean, out: Writer) {
        var current = start
        val limit = current + length
        for (i in current until limit) {
            val c = ch[i]
            if (strict && c < ' ' && c != '\n' && c != '\r' && c != '\t')
                throw IllegalArgumentException("Character ${c.code} is not allowed in XML")
            if (c == '&' || c == '<' || c == '>' || (c == '\"' && isAttVal) || (c == '\n' && isAttVal) || (c < ' ' && c != '\n')) {
                if (i != current) out.write(ch, current, i - current)
                current = i + 1
                when (c) {
                    '&' -> out.write("&amp;")
                    '<' -> out.write("&lt;")
                    '>' -> out.write("&gt;")
                    '\"' -> out.write("&quot;")
                    else -> {
                        out.write("&#")
                        out.write(c.code.toString())
                        out.write(';'.code)
                    }
                }
            }
        }

        if (current != limit) out.write(ch, current, limit - current)
    }
}
