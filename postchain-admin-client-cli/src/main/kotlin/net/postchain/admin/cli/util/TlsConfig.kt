package net.postchain.admin.cli.util

import java.io.File

data class TlsConfig(
    val enabled: Boolean,
    val certificateFile: File?
)
