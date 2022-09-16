package net.postchain.managed

/**
 * Any component (class) which is DirectoryComponent has an access to [DirectoryDataSource]
 */
interface DirectoryComponent {
    fun setDirectoryDataSource(directoryDataSource: DirectoryDataSource)
}