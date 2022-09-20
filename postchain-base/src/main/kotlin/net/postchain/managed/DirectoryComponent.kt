package net.postchain.managed

/**
 * Any component (class) which is DirectoryComponent has an access to [DirectoryDataSource]
 */
interface DirectoryComponent {
    var directoryDataSource: DirectoryDataSource
}