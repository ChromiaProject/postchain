package net.postchain.admin.cli.util

import java.io.File

data class SslConfig(
    val enabled: Boolean,
    val certificateFile: File?
)
