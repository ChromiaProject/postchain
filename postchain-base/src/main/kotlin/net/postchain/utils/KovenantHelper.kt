package net.postchain.utils

import nl.komponents.kovenant.Context
import nl.komponents.kovenant.Kovenant

object KovenantHelper {

    fun createContext(name: String, concurrentTasks: Int): Context {
        return Kovenant.createContext {
            workerContext.dispatcher {
                this.name = name
                this.concurrentTasks = concurrentTasks
            }
        }
    }
}